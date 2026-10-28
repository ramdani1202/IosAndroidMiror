package com.example.mirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class TouchAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TouchAccessibilityService? = null
    }

    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun handle(m: JSONObject) {
        when (m.optString("t")) {
            "g" -> gesture(m)
            "key" -> when (m.optString("k")) {
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            "text" -> editText(m.optString("v"), false)
            "del" -> editText("", true)
        }
    }

    /** Koordinat dari browser berupa pecahan 0..1, dikali ukuran layar asli. */
    private fun gesture(m: JSONObject) {
        val (w, h) = ScreenService.realSize(this)
        val x1 = (m.getDouble("x1") * w).toFloat()
        val y1 = (m.getDouble("y1") * h).toFloat()
        val x2 = (m.getDouble("x2") * w).toFloat()
        val y2 = (m.getDouble("y2") * h).toFloat()
        val dur = m.optLong("d", 50).coerceIn(40L, 2000L)
        val path = Path().apply {
            moveTo(x1, y1)
            if (Math.abs(x2 - x1) > 4 || Math.abs(y2 - y1) > 4) lineTo(x2, y2)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, dur))
            .build()
        dispatchGesture(g, null, null)
    }

    private fun editText(add: String, backspace: Boolean) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        var cur = if (node.isShowingHintText) "" else (node.text?.toString() ?: "")
        cur = if (backspace) cur.dropLast(1) else cur + add
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
