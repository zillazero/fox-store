package com.looker.droidify.filmverleih

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Speichert NUR Tokens (nie Passwörter), verschlüsselt mit AES-256-GCM.
 * Der Schlüssel liegt im Android Keystore und verlässt das Gerät nicht.
 * (Ersatz für das abgekündigte EncryptedSharedPreferences, ohne Zusatzbibliothek.)
 */
class TokenStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("filmverleih_secure", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun save(tokens: TokenResponse) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(json.encodeToString(TokenResponse.serializer(), tokens).toByteArray())
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun load(): TokenResponse? {
        val iv = prefs.getString("iv", null) ?: return null
        val ct = prefs.getString("ct", null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            json.decodeFromString(TokenResponse.serializer(), String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP))))
        } catch (e: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "foxstore_filmverleih_tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
