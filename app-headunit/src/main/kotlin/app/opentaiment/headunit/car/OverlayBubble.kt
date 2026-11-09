package app.opentaiment.headunit.car

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import app.opentaiment.headunit.MainActivity
import kotlin.math.abs

/**
 * A small draggable pill drawn over navigation (or any other app): speed, consumption and
 * warnings. Tap opens Opentaiment. Plain views – no Compose lifecycle needed in an overlay.
 */
class OverlayBubble(context: Context) {
    private val appContext = context.applicationContext
    private val wm = appContext.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs().getInt("x", 24)
        y = prefs().getInt("y", 120)
    }

    private fun prefs() = appContext.getSharedPreferences("overlay", Context.MODE_PRIVATE)

    fun show(text: String, warning: Boolean) {
        if (!canDraw(appContext)) return hide()
        val v = view ?: create().also {
            try {
                wm.addView(it, params)
                view = it
            } catch (e: Exception) {
                return
            }
        }
        v.text = text
        (v.background as GradientDrawable).setColor(if (warning) Color.argb(235, 183, 28, 28) else Color.argb(220, 18, 18, 22))
    }

    fun hide() {
        view?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        view = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun create(): TextView = TextView(appContext).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        val pad = (14 * resources.displayMetrics.density).toInt()
        setPadding(pad * 2, pad, pad * 2, pad)
        background = GradientDrawable().apply { cornerRadius = 100f * resources.displayMetrics.density }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt()
                    params.y = startY + (e.rawY - downY).toInt()
                    try { wm.updateViewLayout(this, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(e.rawX - downX) < 12 && abs(e.rawY - downY) < 12) {
                        appContext.startActivity(
                            Intent(appContext, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                        )
                    } else {
                        prefs().edit().putInt("x", params.x).putInt("y", params.y).apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun permissionIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
