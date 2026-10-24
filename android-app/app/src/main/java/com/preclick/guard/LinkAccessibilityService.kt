package com.preclick.guard

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Finds links visible on screen, asks the server about each one, and shows a short note
 * with an arrow near the link BEFORE the user taps it. Only the link is sent, never the message.
 */
class LinkAccessibilityService : AccessibilityService() {
    private val pool = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val seen = HashSet<String>()
    private var lastRun = 0L
    private var overlay: View? = null
    private val hideTask = Runnable { hide() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        pool.execute { try { Scorer.load(applicationContext) } catch (e: Throwable) { } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !Prefs.isEnabled(this) || !Scorer.isLoaded()) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        val now = SystemClock.uptimeMillis()
        if (now - lastRun < 1500) return
        lastRun = now

        val root = rootInActiveWindow ?: return
        val found = ArrayList<Pair<String, Rect>>()
        collect(root, found, 0)

        var started = 0
        for ((url, rect) in found) {
            if (started >= 3) break
            if (!seen.add(url)) continue
            started++
            pool.execute {
                val r = Backend.check(applicationContext, url) ?: return@execute
                ui.post { show(r, rect) }
            }
        }
        if (seen.size > 300) seen.clear()
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Pair<String, Rect>>, depth: Int) {
        if (depth > 40 || out.size > 20) return
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            val urls = Scorer.extractUrls(text)
            if (urls.isNotEmpty()) {
                val b = Rect()
                node.getBoundsInScreen(b)
                urls.forEach { out.add(it to Rect(b)) }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    private fun show(r: Backend.LinkResult, rect: Rect) {
        hide()
        val dm = resources.displayMetrics
        val color = when (r.level) {
            "High risk" -> Color.parseColor("#C62828")
            "Be careful" -> Color.parseColor("#EF6C00")
            else -> Color.parseColor("#2E7D32")
        }
        val tv = TextView(this).apply {
            text = "\u25B2 " + r.level.uppercase() + "\n" + r.notes.joinToString("\n") { "\u2022 $it" }
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setPadding(24, 16, 24, 16)
            textSize = 13f
            maxWidth = (dm.widthPixels * 0.8).toInt()
            setOnClickListener { hide() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = rect.left.coerceIn(0, dm.widthPixels / 5)
        lp.y = rect.bottom.coerceIn(0, dm.heightPixels - 500)
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(tv, lp)
            overlay = tv
            ui.removeCallbacks(hideTask)
            ui.postDelayed(hideTask, 8000)
        } catch (e: Throwable) {
            overlay = null
        }
    }

    private fun hide() {
        overlay?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (e: Throwable) {
                // already removed
            }
        }
        overlay = null
    }

    override fun onInterrupt() {
        hide()
    }
}
