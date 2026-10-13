package com.looker.droidify.filmverleih

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import kotlinx.coroutines.runBlocking

/**
 * FOX-SSO-Anbieter (APP-API.md §8, Android-SSO-Leitfaden).
 * Geschützt durch die signature-Permission site.pk187.permission.SSO (Manifest) und zusätzlich zur Laufzeit:
 * Aufrufer ∈ Allowlist, client_id == Aufrufer-Paket, gleiche Signatur wie der Store.
 * Gibt NIE den eigenen Refresh-Token weiter, sondern das Ergebnis von POST /app/exchange (eigene Sitzung je App).
 */
class SsoProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "site.pk187.foxstore.sso"
        val ALLOWED = setOf("site.pk187.foxfin", "site.pk187.foxplayer")
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return err("server", "Kein Kontext")
        val caller = callingPackage
        val uid = Binder.getCallingUid()
        val pm = ctx.packageManager
        val uidPackages = pm.getPackagesForUid(uid)?.toSet().orEmpty()
        val sameSig = runCatching { pm.checkSignatures(ctx.packageName, caller ?: "") == PackageManager.SIGNATURE_MATCH }.getOrDefault(false)
        if (caller == null || caller !in ALLOWED || caller !in uidPackages || !sameSig) {
            return err("forbidden", "App nicht berechtigt")
        }
        val api = ThemeSync.api(ctx)
        return when (method) {
            "status" -> Bundle().apply { putBoolean("ok", true); putBoolean("logged_in", api.isLoggedIn) }
            "exchange" -> {
                val clientId = extras?.getString("client_id") ?: arg ?: caller
                if (clientId != caller) return err("forbidden", "client_id passt nicht zur App")
                if (!api.isLoggedIn) return err("not_logged_in", "Im FOX Store ist niemand angemeldet.")
                val device = extras?.getString("device_name")?.take(60)
                // call() läuft auf einem Binder-Thread → blockierender Netzwerkaufruf ist zulässig.
                val ident = Binder.clearCallingIdentity()
                try {
                    val t = runBlocking { api.exchange(clientId, device) }
                    Bundle().apply {
                        putBoolean("ok", true)
                        putString("access_token", t.accessToken)
                        putString("refresh_token", t.refreshToken) // neue, eigene Sitzung der Ziel-App – nicht die des Stores
                        putLong("expires_in", t.expiresIn)
                        putLong("refresh_expires_in", t.refreshExpiresIn)
                        putString("session_id", t.sessionId)
                        putString("client_id", clientId)
                    }
                } catch (e: ShopException) {
                    err(if (e.status == 401) "not_logged_in" else if (e.status == 0) "network" else "server", e.message ?: "Fehler")
                } finally {
                    Binder.restoreCallingIdentity(ident)
                }
            }
            else -> err("unknown_method", method)
        }
    }

    private fun err(code: String, msg: String) = Bundle().apply {
        putBoolean("ok", false); putString("error", code); putString("message", msg)
    }

    // Kein Datenzugriff über query/insert – nur call().
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
