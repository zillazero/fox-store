package com.looker.droidify.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.looker.droidify.service.Connection
import com.looker.droidify.service.SyncService
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * FOX Store: periodischer Auto-Update-Lauf (WorkManager).
 * Synchronisiert alle aktiven Quellen; SyncService.handleUpdates installiert danach je nach
 * Einstellung FOX-/alle Updates sowie das Store-Selbst-Update.
 */
class AutoUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FoxAutoUpdate"
        private const val KEY_NIGHT = "night"
        private const val KEY_HOURS = "hours"
        private const val PREFS = "fox_auto_update_state"

        fun schedule(context: Context, enabled: Boolean, hours: Int, wifiOnly: Boolean, charging: Boolean, nightOnly: Boolean = false) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(TAG)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(charging)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AutoUpdateWorker>(
                // "Nur nachts": stündlich wecken, gearbeitet wird nur zwischen 1 und 6 Uhr
                (if (nightOnly) 1 else hours.coerceIn(1, 168)).toLong(), TimeUnit.HOURS,
            ).setConstraints(constraints)
                .setInputData(androidx.work.workDataOf(KEY_NIGHT to nightOnly, KEY_HOURS to hours))
                .build()
            wm.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
            Log.i(TAG, "scheduled every ${hours}h wifi=$wifiOnly charging=$charging")
        }
    }

    override suspend fun doWork(): Result {
        if (inputData.getBoolean(KEY_NIGHT, false)) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (hour !in 1..5) return Result.success()
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = prefs.getLong("last_night_run", 0L)
            if (System.currentTimeMillis() - last < 12 * 3600 * 1000L) return Result.success()
            prefs.edit().putLong("last_night_run", System.currentTimeMillis()).apply()
        }
        return runSync()
    }

    private suspend fun runSync(): Result = withContext(Dispatchers.Main) {
        var connection: Connection<SyncService.Binder, SyncService>? = null
        try {
            val binder = suspendCancellableCoroutine { cont ->
                val c = Connection(
                    SyncService::class.java,
                    onBind = { _, b -> if (cont.isActive) cont.resume(b) },
                )
                connection = c
                c.bind(applicationContext)
            }
            coroutineScope {
                val finished = async(start = CoroutineStart.UNDISPATCHED) {
                    binder.state.filterIsInstance<SyncService.State.Finish>().first()
                }
                binder.sync(SyncService.SyncRequest.AUTO)
                // Sync + Update-Anstoß abwarten (max. 20 min)
                withTimeoutOrNull(20 * 60 * 1000L) { finished.await() } ?: finished.cancel()
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "auto update failed", e)
            Result.retry()
        } finally {
            connection?.unbind(applicationContext)
        }
    }
}
