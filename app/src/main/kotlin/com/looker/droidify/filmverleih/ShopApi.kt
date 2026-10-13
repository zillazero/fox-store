package com.looker.droidify.filmverleih

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Shop-Konfiguration (FOX Filmverleih, shop.pk187.site). */
object ShopConfig {
    const val BASE = "https://shop.pk187.site"
    const val APP_API = "$BASE/wp-json/pk187/v1"
    const val STORE_API = "$BASE/wp-json/wc/store/v1"
    const val CLIENT_ID = "site.pk187.foxstore"
    const val RETURN_SCHEME = "foxstore"
    const val IMPRESSUM = "$BASE/impressum/"
    const val AGB = "$BASE/agb/"
    const val DATENSCHUTZ = "$BASE/datenschutzerklaerung/"
    const val WIDERRUF = "$BASE/widerrufsbelehrung/"
    const val VERTRAG_WIDERRUFEN = "$BASE/vertrag-widerrufen/"
    const val MEIN_KONTO = "$BASE/mein-konto/"
    const val PASSWORT_VERGESSEN = "$BASE/mein-konto/lost-password/"
    const val MEINE_PAKETE_WEB = "$BASE/mein-konto/meine-pakete/"
}

/** Fehler der App-API mit verständlicher deutscher Meldung. */
class ShopException(val status: Int, val code: String, message: String) : Exception(message)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Long = 2592000,
    @SerialName("session_id") val sessionId: String = "",
)

@Serializable
data class Me(
    val id: Long = 0,
    val username: String = "",
    val email: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("display_name") val displayName: String = "",
    val theme: String = "standard",
    val sounds: Boolean = true,
    val animations: Boolean = true,
)

@Serializable
data class ThemeState(
    val theme: String = "standard",
    val accent: String? = null,
    val animations: Boolean = true,
    val sounds: Boolean = true,
    val values: List<String> = emptyList(),
)

@Serializable
data class StorePrices(
    val price: String = "0",
    @SerialName("currency_minor_unit") val minorUnit: Int = 2,
    @SerialName("currency_suffix") val suffix: String = " €",
    @SerialName("price_range") val range: PriceRange? = null,
)

@Serializable
data class PriceRange(@SerialName("min_amount") val min: String = "0", @SerialName("max_amount") val max: String = "0")

@Serializable
data class StoreImage(val src: String = "")

@Serializable
data class StoreTerm(val name: String = "")

@Serializable
data class StoreAttribute(val name: String = "", val terms: List<StoreTerm> = emptyList())

@Serializable
data class StoreProduct(
    val id: Long,
    val name: String = "",
    val type: String = "",
    val permalink: String = "",
    val sku: String = "",
    @SerialName("short_description") val shortDescription: String = "",
    @SerialName("is_purchasable") val purchasable: Boolean = true,
    val prices: StorePrices = StorePrices(),
    val images: List<StoreImage> = emptyList(),
    val attributes: List<StoreAttribute> = emptyList(),
)

/**
 * Client für pk187/v1 (Stufe 1 live, Stufe 2 vorbereitet) und WC Store API (nur lesend).
 * Bezahlung findet NIE in der App statt, sondern im Browser/Custom Tab.
 */
class ShopApi(context: Context) {
    private val store = TokenStore(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val refreshMutex = Mutex()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val isLoggedIn: Boolean get() = store.load() != null

    suspend fun login(username: String, password: String): Me {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
            put("client_id", ShopConfig.CLIENT_ID)
            put("device_name", deviceName())
        }
        val tokens = json.decodeFromString<TokenResponse>(call("POST", "${ShopConfig.APP_API}/app/login", body, null))
        store.save(tokens)
        return me()
    }

    suspend fun logout(all: Boolean = false) {
        val t = store.load()
        try {
            if (t != null) call("POST", "${ShopConfig.APP_API}/app/logout", buildJsonObject { put("all", all) }, t.accessToken)
        } catch (_: Exception) {
            // lokal trotzdem abmelden
        } finally {
            store.clear()
        }
    }

    suspend fun me(): Me = json.decodeFromString(authed("GET", "/me"))
    suspend fun getTheme(): ThemeState = json.decodeFromString(authed("GET", "/theme"))
    suspend fun putTheme(theme: String): ThemeState =
        json.decodeFromString(authed("PUT", "/theme", buildJsonObject { put("theme", theme) }))

    // ---- Stufe 2 ----
    suspend fun myPackages(): MyPackages = json.decodeFromString(authed("GET", "/my-packages"))
    suspend fun playerLinks(): PlayerLinks = json.decodeFromString(authed("GET", "/player/links"))
    suspend fun credentials(): Credentials = json.decodeFromString(authed("GET", "/credentials"))

    // ---- Stufe 3 ----
    suspend fun trialEligibility(): TrialEligibility = json.decodeFromString(authed("GET", "/trial-eligibility"))

    /** Liefert einen einmaligen 5-Minuten-Link zum vorbefüllten Web-Checkout. */
    suspend fun checkoutLink(req: CheckoutRequest): CheckoutLink =
        json.decodeFromString(
            authed(
                "POST", "/checkout-link",
                buildJsonObject {
                    put("lang", req.lang); put("tier", req.tier); put("term", req.term)
                    put("connections", req.connections); put("action", req.action)
                },
            ),
        )

    // ---- WC Store API (öffentlich, ohne Login) ----
    suspend fun products(): List<StoreProduct> =
        json.decodeFromString<List<StoreProduct>>(call("GET", "${ShopConfig.STORE_API}/products?per_page=50", null, null))
            .filter { it.type == "variable" || it.purchasable }

    /** Bearer-Aufruf mit genau einem Auto-Refresh bei 401. */
    private suspend fun authed(method: String, path: String, body: JsonObject? = null): String {
        val first = store.load() ?: throw ShopException(401, "not_logged_in", "Bitte melde dich an.")
        return try {
            call(method, ShopConfig.APP_API + path, body, first.accessToken)
        } catch (e: ShopException) {
            if (e.status != 401) throw e
            val fresh = refresh(first.refreshToken)
            call(method, ShopConfig.APP_API + path, body, fresh.accessToken)
        }
    }

    private suspend fun refresh(usedRefresh: String): TokenResponse = refreshMutex.withLock {
        val current = store.load() ?: throw sessionExpired()
        // Ein anderer Aufruf hat bereits rotiert: neue Tokens verwenden (alten Refresh-Token NIE erneut senden).
        if (current.refreshToken != usedRefresh) return current
        try {
            val t = json.decodeFromString<TokenResponse>(
                call("POST", "${ShopConfig.APP_API}/app/refresh", buildJsonObject { put("refresh_token", current.refreshToken) }, null),
            )
            store.save(t)
            t
        } catch (e: ShopException) {
            if (e.status == 401 || e.status == 400) {
                store.clear()
                throw sessionExpired()
            }
            throw e
        }
    }

    private fun sessionExpired() = ShopException(401, "session_expired", "Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.")

    private suspend fun call(method: String, url: String, body: JsonObject?, bearer: String?): String =
        withContext(Dispatchers.IO) {
            val rb = body?.toString()?.toRequestBody(jsonType)
            val req = Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "FOXStore-Filmverleih/1")
                .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
                .method(method, rb ?: if (method == "GET") null else "{}".toRequestBody(jsonType))
                .build()
            val resp = try {
                client.newCall(req).execute()
            } catch (e: IOException) {
                throw ShopException(0, "network", "Keine Verbindung zum Shop. Bitte prüfe deine Internetverbindung.")
            }
            resp.use {
                val text = it.body.string()
                if (it.isSuccessful) return@withContext text
                val code = runCatching {
                    json.parseToJsonElement(text).let { el -> (el as JsonObject)["code"]?.toString()?.trim('"') }
                }.getOrNull() ?: "http_${it.code}"
                throw ShopException(it.code, code, friendly(it.code, code))
            }
        }

    private fun friendly(status: Int, code: String): String = when {
        code == "pk187_login_failed" -> "Benutzername/E-Mail oder Passwort ist falsch."
        status == 429 || code == "pk187_rate_limited" -> "Zu viele Versuche. Bitte warte etwa 15 Minuten und versuche es dann erneut."
        code == "pk187_invalid_token" -> "Deine Sitzung ist abgelaufen. Bitte melde dich erneut an."
        code == "pk187_missing_fields" -> "Bitte Benutzername und Passwort eingeben."
        code == "pk187_invalid_client" -> "Diese App-Version wird vom Shop nicht akzeptiert. Bitte aktualisiere den FOX Store."
        code == "pk187_https_required" -> "Sichere Verbindung erforderlich."
        code == "pk187_trial_used" -> "Den 24-Stunden-Test hast du bereits genutzt."
        code == "pk187_no_package" -> "Für diese Sprache gibt es noch kein Paket zum Verlängern."
        code == "pk187_upgrade_invalid" -> "Ein Wechsel auf Premium ist nur für ein laufendes Basis-Paket möglich."
        code == "pk187_no_active_package" -> "Verbindungen kannst du nur zu einem laufenden Paket hinzubuchen."
        code == "pk187_tier_mismatch" -> "Zusätzliche Verbindungen müssen die gleiche Stufe (Basis/Premium) haben wie dein laufendes Paket."
        code == "pk187_not_available" -> "Diese Kombination gibt es nicht (der 24-Stunden-Test ist nur als Premium verfügbar)."
        code.startsWith("pk187_invalid_") && code != "pk187_invalid_token" -> "Ungültige Auswahl ($code)."
        code == "pk187_invalid_theme" -> "Dieses Design kennt der Shop nicht."
        status == 401 -> "Nicht angemeldet oder keine Berechtigung."
        status == 404 -> "Diese Funktion ist im Shop noch nicht verfügbar."
        status >= 500 -> "Der Shop ist gerade nicht erreichbar (Fehler $status). Bitte später erneut versuchen."
        else -> "Unerwarteter Fehler ($status)."
    }

    private fun deviceName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(60)
}

// ---- Modelle Stufe 2/3 (Zeiten ISO 8601 UTC) ----
@Serializable
data class Booking(
    @SerialName("order_id") val orderId: Long = 0, val tier: String = "", val state: String = "", val trial: Boolean = false,
    val start: String? = null, val end: String? = null,
    @SerialName("paid_connections") val paidConnections: Int = 0, val streams: Int = 0,
    @SerialName("booking_mode") val bookingMode: String = "",
)

@Serializable
data class Package(
    val lang: String = "", val name: String = "", val status: String = "", val tier: String = "",
    @SerialName("trial_only") val trialOnly: Boolean = false,
    val start: String? = null, val end: String? = null,
    @SerialName("premium_until") val premiumUntil: String? = null,
    @SerialName("days_left") val daysLeft: Int? = null,
    @SerialName("streams_now") val streamsNow: Int = 0,
    val bookings: List<Booking> = emptyList(),
)

@Serializable
data class MyPackages(
    val packages: List<Package> = emptyList(),
    @SerialName("shop_url") val shopUrl: String? = null,
    @SerialName("account_url") val accountUrl: String? = null,
)

@Serializable
data class PlayerTarget(
    val allowed: Boolean = false, val available: Boolean = false, val reason: String? = null,
    val kind: String? = null, @SerialName("web_url") val webUrl: String? = null,
)

@Serializable
data class PlayerPackage(
    val lang: String = "", val name: String = "", val tier: String = "", val trial: Boolean = false, val end: String? = null,
    val live: PlayerTarget? = null, val vod: PlayerTarget? = null,
)

@Serializable
data class PlayerLinks(
    @SerialName("player_enabled") val playerEnabled: Boolean = false,
    @SerialName("jellyfin_base") val jellyfinBase: String? = null,
    val packages: List<PlayerPackage> = emptyList(),
)

@Serializable
data class ShopCreds(val username: String? = null, @SerialName("password_reset_url") val passwordResetUrl: String? = null)

@Serializable
data class JellyfinCreds(@SerialName("server_url") val serverUrl: String? = null, val username: String? = null, val hint: String? = null)

@Serializable
data class PortalCreds(
    val url: String? = null, @SerialName("profile_url") val profileUrl: String? = null,
    @SerialName("password_reset_url") val passwordResetUrl: String? = null,
)

@Serializable
data class Credentials(val shop: ShopCreds? = null, val jellyfin: JellyfinCreds? = null, val portal: PortalCreds? = null)

@Serializable
data class TrialEligibility(val eligible: Boolean = false, val reason: String? = null)

data class CheckoutRequest(val lang: String, val tier: String, val term: String, val connections: Int, val action: String)

@Serializable
data class CheckoutSummary(
    val lang: String = "", val tier: String = "", val term: String = "", val connections: Int = 1,
    val streams: Int = 1, @SerialName("booking_mode") val bookingMode: String = "", val action: String = "",
)

@Serializable
data class CheckoutLink(val url: String, @SerialName("expires_in") val expiresIn: Int = 300, val summary: CheckoutSummary? = null)
