package com.looker.droidify.fox

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.animation.doOnEnd
import com.looker.droidify.R

/**
 * FOX Store 1.4.0: Startanimation für Android 11 und älter (z. B. Shield).
 * Ab Android 12 übernimmt das System (SplashScreen-API, siehe values-v31/fox_splash_theme.xml).
 * NORMAL: Fuchs pulsiert/glüht 760 ms; REDUZIERT: Einblenden 400 ms; AUS: statisch 250 ms.
 */
object FoxSplash {
    fun show(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val decor = activity.window.decorView as? ViewGroup ?: return
        val dp = activity.resources.displayMetrics.density
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#0A0A0B"))
            isClickable = true
        }
        val glow = ImageView(activity).apply { setImageResource(R.drawable.ic_launcher_foreground); alpha = 0f }
        val icon = ImageView(activity).apply { setImageResource(R.drawable.ic_launcher_foreground) }
        val size = (240 * dp).toInt()
        overlay.addView(glow, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        overlay.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        fun finish() {
            overlay.animate().alpha(0f).setDuration(150).withEndAction { decor.removeView(overlay) }.start()
        }
        when (FoxDevice.motion(activity)) {
            FoxDevice.Motion.AUS -> overlay.postDelayed({ decor.removeView(overlay) }, 250)
            FoxDevice.Motion.REDUZIERT -> {
                icon.alpha = 0f
                icon.animate().alpha(1f).setDuration(250).withEndAction { overlay.postDelayed(::finish, 150) }.start()
            }
            FoxDevice.Motion.NORMAL -> AnimatorSet().apply {
                val g = ObjectAnimator.ofFloat(glow, "alpha", 0f, 1f, 0f)
                val sx = ObjectAnimator.ofFloat(glow, "scaleX", 1f, 1.06f, 1f)
                val sy = ObjectAnimator.ofFloat(glow, "scaleY", 1f, 1.06f, 1f)
                playTogether(g, sx, sy)
                duration = 760
                doOnEnd { finish() }
                start()
            }
        }
    }
}
