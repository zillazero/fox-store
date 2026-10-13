package com.looker.droidify.filmverleih

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Interceptor
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Geschützte Quelle „Privat“ (HTTP Basic Auth).
 * Zugangsdaten gibt der Nutzer ein; sie werden AES-256-GCM-verschlüsselt (Android Keystore) gespeichert
 * und NICHT in die Repository-Datenbank geschrieben. Der Auth-Header wird per OkHttp-Interceptor
 * nur für Anfragen unter [PREFIX] gesetzt (Index, Icons, APKs).
 */
object PrivateRepo {
    const val ADDRESS = "https://apps.pk187.site/private/repo"
    const val FINGERPRINT = "EA6F1CB7C0A11202EBBCA033D9A51AA6E765854952F25FCE62609E8EBFCAA36B"
    const val NAME = "Privat"
    private const val PREFIX = "https://apps.pk187.site/private/"
    private const val ALIAS = "foxstore_private_repo"

    @Volatile private var cachedHeader: String? = null
    @Volatile private var loaded = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isPrivate(address: String) = address.trimEnd('/') == ADDRESS

    private fun prefs() = appContext.getSharedPreferences("private_repo_secure", Context.MODE_PRIVATE)

    fun hasCredentials(): Boolean = header() != null

    /** Sichtbar nur mit Zugangsdaten und wenn nicht ausgeblendet. */
    fun isVisible(): Boolean = hasCredentials() && !prefs().getBoolean("hidden", false)

    fun isHidden(): Boolean = prefs().getBoolean("hidden", false)
    fun setHidden(hidden: Boolean) = prefs().edit().putBoolean("hidden", hidden).apply()

    fun username(): String? = decrypt()?.substringBefore(':')

    @Synchronized
    fun save(username: String, password: String) {
        val plain = "$username:$password"
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray())
        prefs().edit()
            .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .putString("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
            .putBoolean("hidden", false)
            .apply()
        cachedHeader = "Basic " + Base64.encodeToString(plain.toByteArray(), Base64.NO_WRAP)
        loaded = true
    }

    @Synchronized
    fun clear() {
        prefs().edit().remove("iv").remove("ct").apply()
        cachedHeader = null
        loaded = true
    }

    @Synchronized
    private fun header(): String? {
        if (!::appContext.isInitialized) return null
        if (!loaded) {
            cachedHeader = decrypt()?.let { "Basic " + Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }
            loaded = true
        }
        return cachedHeader
    }

    private fun decrypt(): String? {
        val iv = prefs().getString("iv", null) ?: return null
        val ct = prefs().getString("ct", null) ?: return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)))
        } catch (e: Exception) {
            null
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build(),
            )
        }.generateKey()
    }

    /** Setzt Basic Auth nur für apps.pk187.site/private/ (HTTPS), nie für andere Hosts. */
    val interceptor = Interceptor { chain ->
        val req = chain.request()
        val url = req.url.toString()
        val h = if (url.startsWith(PREFIX) && req.header("Authorization") == null) header() else null
        chain.proceed(if (h != null) req.newBuilder().header("Authorization", h).build() else req)
    }
}
