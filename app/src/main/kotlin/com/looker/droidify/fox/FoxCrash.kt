package com.looker.droidify.fox

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.looker.droidify.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FOX Store 1.4.0: lokale Absturzberichte. Werden nur auf dem Gerät gespeichert (max. 10)
 * und nie automatisch versendet; Export nur auf Knopfdruck über das Teilen-Menü.
 */
object FoxCrash {
    private const val MAX = 10
    private fun dir(context: Context) = File(context.filesDir, "crash").apply { mkdirs() }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                File(dir(app), "crash-$stamp.txt").writeText(
                    buildString {
                        appendLine("FOX Store ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Gerät: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("Zeit: $stamp, Thread: ${thread.name}")
                        appendLine()
                        appendLine(error.stackTraceToString())
                    },
                )
                dir(app).listFiles()?.sortedByDescending { it.name }?.drop(MAX)?.forEach { it.delete() }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun count(context: Context): Int = dir(context).listFiles()?.size ?: 0

    fun export(context: Context) {
        val files = dir(context).listFiles()?.sortedByDescending { it.name }.orEmpty()
        if (files.isEmpty()) {
            Toast.makeText(context, "Keine Absturzberichte vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        val text = files.joinToString("\n\n==========\n\n") { it.readText() }.take(200_000)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "FOX Store Absturzberichte")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Absturzberichte exportieren").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
