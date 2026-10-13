package com.looker.droidify.filmverleih

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Deep-Link-Integration zu den anderen FOX-Apps. */
object FoxApps {
    const val FOXFIN = "site.pk187.foxfin"
    const val FOXPLAYER = "site.pk187.foxplayer"

    fun isInstalled(context: Context, pkg: String) =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /**
     * Öffnet die App (mit optionaler URL, z. B. Jellyfin-web_url, als Daten), sonst die Store-Seite der App
     * (market://details?id=… wird von MainActivity im FOX Store selbst behandelt).
     */
    fun open(context: Context, pkg: String, url: String? = null) {
        if (isInstalled(context, pkg)) {
            val withData = url?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(pkg) }
            try {
                if (withData != null && withData.resolveActivity(context.packageManager) != null) {
                    context.startActivity(withData); return
                }
            } catch (_: ActivityNotFoundException) {}
            context.packageManager.getLaunchIntentForPackage(pkg)
                ?: context.packageManager.getLeanbackLaunchIntentForPackage(pkg)
        } else {
            null
        }?.let { context.startActivity(it); return }
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).setPackage(context.packageName),
        )
    }
}
