package com.looker.droidify.fox

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings

/** FOX Store 1.4.0: Geräteprofil (Stick sparsam / Shield voll) und Bewegungsstufe. */
object FoxDevice {
    enum class Profile { STICK, VOLL }
    enum class Motion { NORMAL, REDUZIERT, AUS }

    @Volatile private var cached: Profile? = null

    fun profile(context: Context): Profile = cached ?: run {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val low = am == null || am.isLowRamDevice || info.totalMem < 3L * 1024 * 1024 * 1024 || am.memoryClass <= 192
        (if (low) Profile.STICK else Profile.VOLL).also { cached = it }
    }

    /** Systemweite Animationsskala: 0 = aus, < 1 = reduziert. Stick wird immer reduziert. */
    fun motion(context: Context): Motion {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return when {
            scale == 0f -> Motion.AUS
            scale < 1f || profile(context) == Profile.STICK -> Motion.REDUZIERT
            else -> Motion.NORMAL
        }
    }
}
