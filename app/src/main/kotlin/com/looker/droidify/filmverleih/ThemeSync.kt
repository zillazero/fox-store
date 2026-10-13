package com.looker.droidify.filmverleih

import android.content.Context
import com.looker.droidify.datastore.SettingsRepository
import com.looker.droidify.datastore.model.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Synchronisiert das App-Design über /sync/settings (Stufe 4) mit updated_at/409-Konfliktschutz.
 * "standard" = im Shop kein Design gewählt → lokales hochladen. Schreibweise "gruen" (Shop intern "green").
 */
object ThemeSync {
    @Volatile private var suppressPush: Theme? = null
    private var api: ShopApi? = null

    fun api(context: Context): ShopApi = api ?: synchronized(this) {
        api ?: ShopApi(context.applicationContext).also { api = it }
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("filmverleih_sync", Context.MODE_PRIVATE)
    private fun base(c: Context) = prefs(c).getString("settings_updated_at", null)
    private fun setBase(c: Context, v: String?) = prefs(c).edit().putString("settings_updated_at", v).apply()

    fun serverKey(t: Theme): String = t.key

    fun fromServer(value: String?): Theme? =
        if (value == null || value == "standard") null
        else Theme.entries.firstOrNull { it.key == value || (value == "green" && it == Theme.GRUEN) }

    fun start(context: Context, scope: CoroutineScope, settings: SettingsRepository) {
        val shop = api(context)
        scope.launch {
            settings.data.map { it.theme }.distinctUntilChanged().drop(1).collect { theme ->
                if (suppressPush == theme) { suppressPush = null; return@collect }
                if (shop.isLoggedIn) runCatching { push(context, settings, theme) }
            }
        }
    }

    private suspend fun push(context: Context, settings: SettingsRepository, theme: Theme) {
        try {
            setBase(context, shopPut(context, theme, base(context)))
        } catch (c: SettingsConflict) {
            // Server neuer: Server-Stand gewinnt (anderes Gerät/Website hat zuletzt geändert)
            apply(context, settings, c.server ?: api(context).getSyncSettings())
        }
    }

    private suspend fun shopPut(context: Context, theme: Theme, base: String?): String? =
        api(context).putSyncSettings(serverKey(theme), base).updatedAt

    private suspend fun apply(context: Context, settings: SettingsRepository, s: SyncSettings) {
        setBase(context, s.updatedAt)
        val server = fromServer(s.theme) ?: return
        if (settings.getInitial().theme != server) {
            suppressPush = server
            settings.setTheme(server)
        }
    }

    /** Server-Stand übernehmen (nach Login / beim Öffnen des Bereichs). */
    suspend fun pull(context: Context, settings: SettingsRepository) {
        val shop = api(context)
        if (!shop.isLoggedIn) return
        val s = shop.getSyncSettings()
        if (fromServer(s.theme) == null) {
            setBase(context, s.updatedAt)
            push(context, settings, settings.getInitial().theme)
        } else {
            apply(context, settings, s)
        }
    }

    fun clear(context: Context) = setBase(context, null)
}
