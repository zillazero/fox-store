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
 * Synchronisiert das App-Design mit GET/PUT /theme, solange ein Kunde angemeldet ist.
 * Server-Schreibweise: "gruen" (intern im Shop "green"); "standard" = kein Theme gewählt (App behält ihr lokales).
 */
object ThemeSync {
    @Volatile private var suppressPush: Theme? = null
    private var api: ShopApi? = null

    fun api(context: Context): ShopApi = api ?: synchronized(this) {
        api ?: ShopApi(context.applicationContext).also { api = it }
    }

    fun serverKey(t: Theme): String = t.key // foxhole|magenta|amber|gruen|polizei

    fun fromServer(value: String): Theme? =
        if (value == "standard") null else Theme.entries.firstOrNull { it.key == value || (value == "green" && it == Theme.GRUEN) }

    /** Lokale Änderungen hochladen (in Application.onCreate starten). */
    fun start(context: Context, scope: CoroutineScope, settings: SettingsRepository) {
        val shop = api(context)
        scope.launch {
            settings.data.map { it.theme }.distinctUntilChanged().drop(1).collect { theme ->
                if (suppressPush == theme) { suppressPush = null; return@collect }
                if (shop.isLoggedIn) runCatching { shop.putTheme(serverKey(theme)) }
            }
        }
    }

    /** Server-Design übernehmen (nach Login / beim Öffnen des Bereichs). */
    suspend fun pull(context: Context, settings: SettingsRepository) {
        val shop = api(context)
        if (!shop.isLoggedIn) return
        val state = shop.getTheme()
        val server = fromServer(state.theme)
        if (server == null) {
            // Shop hat noch kein Design: lokales hochladen
            val local = settings.getInitial().theme
            shop.putTheme(serverKey(local))
        } else {
            if (settings.getInitial().theme != server) {
                suppressPush = server
                settings.setTheme(server)
            }
        }
    }
}
