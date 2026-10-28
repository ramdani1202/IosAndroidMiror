package com.example.mirror

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        info = TextView(this).apply {
            textSize = 16f
            text = "Langkah: 1) aktifkan Aksesibilitas, 2) mulai berbagi layar, " +
                "3) nyalakan hotspot dan buka alamat yang tampil di Safari iPhone."
        }
        root.addView(info)
        fun btn(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }
        btn("1. Aktifkan Aksesibilitas") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btn("2. Mulai berbagi layar") {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            startActivityForResult(mpm.createScreenCaptureIntent(), 100)
        }
        btn("Berhenti") {
            startService(Intent(this, ScreenService::class.java).setAction("stop"))
            info.text = "Dihentikan."
        }
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 100 || resultCode != RESULT_OK || data == null) return
        val token = Random.nextInt(100000, 999999).toString()
        ScreenService.token = token
        startForegroundService(
            Intent(this, ScreenService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
        )
        val urls = localIps().joinToString("\n") { "http://$it:${ScreenService.PORT}/?t=$token" }
        info.text = "Berjalan. Buka salah satu alamat ini di Safari iPhone:\n\n$urls"
    }

    private fun localIps(): List<String> =
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filter { !it.isLoopbackAddress && it is Inet4Address }
            .mapNotNull { it.hostAddress }
}
