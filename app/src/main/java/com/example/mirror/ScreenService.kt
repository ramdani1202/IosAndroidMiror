package com.example.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

class ScreenService : Service() {

    companion object {
        const val PORT = 8080
        var token: String = ""

        /** Ukuran layar sebenarnya (piksel). */
        fun realSize(ctx: Context): Pair<Int, Int> {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            ctx.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(dm)
            return Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var server: Server? = null
    private val thread = HandlerThread("capture").also { it.start() }
    private val main = Handler(Looper.getMainLooper())
    private val clients = CopyOnWriteArraySet<Sock>()
    private var lastFrame = 0L
    private val minInterval = 100L   // ~10 fps
    private val quality = 50         // kualitas JPEG 0-100

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == "stop") {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        val code = intent.getIntExtra("code", 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data).also {
            // Wajib didaftarkan sebelum membuat VirtualDisplay (Android 14+)
            it.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, Handler(thread.looper))
        }
        startCapture()
        server = Server().also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("mirror", "Mirror", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "mirror")
            .setContentTitle("Mirror berjalan")
            .setContentText("Layar dibagikan lewat jaringan lokal")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    private fun startCapture() {
        val (sw, sh) = realSize(this)
        val w = sw / 2   // diperkecil setengah agar ringan
        val h = sh / 2
        val dpi = resources.displayMetrics.densityDpi
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        display = projection!!.createVirtualDisplay(
            "mirror", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, null
        )
        reader!!.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = SystemClock.uptimeMillis()
                if (clients.isEmpty() || now - lastFrame < minInterval) return@setOnImageAvailableListener
                lastFrame = now
                val p = img.planes[0]
                val rowPadding = p.rowStride - p.pixelStride * w
                val full = Bitmap.createBitmap(w + rowPadding / p.pixelStride, h, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(p.buffer)
                val cropped = Bitmap.createBitmap(full, 0, 0, w, h)
                val out = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)
                full.recycle(); cropped.recycle()
                val bytes = out.toByteArray()
                for (c in clients) {
                    try { c.send(bytes) } catch (e: IOException) { clients.remove(c) }
                }
            } finally {
                img.close()
            }
        }, Handler(thread.looper))
    }

    override fun onDestroy() {
        server?.stop()
        display?.release()
        reader?.close()
        projection?.stop()
        thread.quitSafely()
        super.onDestroy()
    }

    inner class Server : NanoWSD(PORT) {
        override fun openWebSocket(handshake: IHTTPSession): WebSocket = Sock(handshake)

        override fun serveHttp(session: IHTTPSession): Response {
            return try {
                val html = assets.open("index.html").readBytes().toString(Charsets.UTF_8)
                NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", html)
            } catch (e: IOException) {
                NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        }
    }

    inner class Sock(private val handshake: NanoHTTPD.IHTTPSession) : NanoWSD.WebSocket(handshake) {
        override fun onOpen() {
            val t = handshake.parameters["t"]?.firstOrNull()
            if (token.isEmpty() || t != token) {
                try { close(NanoWSD.WebSocketFrame.CloseCode.PolicyViolation, "auth", false) } catch (_: IOException) {}
                return
            }
            clients.add(this)
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            clients.remove(this)
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            if (!clients.contains(this)) return
            try {
                val json = JSONObject(message.textPayload)
                main.post { TouchAccessibilityService.instance?.handle(json) }
            } catch (_: Exception) {}
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame?) {}
        override fun onException(exception: IOException?) { clients.remove(this) }
    }
}
