package com.looker.droidify.filmverleih

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import com.looker.droidify.compose.theme.DroidifyTheme
import com.looker.droidify.datastore.SettingsRepository
import com.looker.droidify.datastore.model.Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bereich „Filmverleih“: Kundenfunktionen von shop.pk187.site.
 * Kauf/Bezahlung ausschließlich im Browser (Custom Tab), Rücksprung foxstore://order/{id}.
 */
@AndroidEntryPoint
class FilmverleihActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var returnedOrder by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val themeFlow = settingsRepository.data.map { it.theme }
        setContent {
            val theme by themeFlow.collectAsState(initial = Theme.DEFAULT)
            DroidifyTheme(foxTheme = theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FilmverleihScreen(
                        api = ThemeSync.api(this),
                        settings = settingsRepository,
                        returnedOrder = returnedOrder,
                        onDismissOrder = { returnedOrder = null },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == ShopConfig.RETURN_SCHEME && data.host == "order") {
            returnedOrder = data.pathSegments.firstOrNull()?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
        }
    }
}

/** Öffnet Shop-Seiten im Custom Tab, Fallback: Browser; auf TV ohne Browser: false (Hinweis mit URL). */
fun openInBrowser(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    return try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        true
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e2: ActivityNotFoundException) {
            false
        }
    }
}

private enum class Tab(val label: String) { KATALOG("Katalog"), BUCHEN("Buchen"), PAKETE("Meine Pakete"), KONTO("Konto") }

@Composable
private fun FilmverleihScreen(
    api: ShopApi,
    settings: SettingsRepository,
    returnedOrder: String?,
    onDismissOrder: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.KATALOG) }
    var loggedIn by remember { mutableStateOf(api.isLoggedIn) }
    var noBrowserUrl by remember { mutableStateOf<String?>(null) }
    var preset by remember { mutableStateOf(CheckoutRequest("DE", "basis", "1m", 1, "new")) }
    val open: (String) -> Unit = { url -> if (!openInBrowser(context, url)) noBrowserUrl = url }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "FILMVERLEIH",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            FoxButton("Zurück", primary = false, onClick = onClose)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tab.entries.forEach { t -> FoxButton(t.label, primary = t == tab) { tab = t } }
        }
        if (returnedOrder != null) {
            Notice("Danke! Bestellung #$returnedOrder ist eingegangen. Den Status siehst du unter „Meine Pakete“ bzw. im Kundenkonto.", onDismissOrder)
        }
        noBrowserUrl?.let {
            Notice("Auf diesem Gerät ist kein Browser installiert. Bitte öffne die Seite auf Handy oder PC:\n$it") { noBrowserUrl = null }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
            when (tab) {
                Tab.KATALOG -> CatalogTab(api, open) { lang ->
                    preset = preset.copy(lang = lang, action = "new"); tab = Tab.BUCHEN
                }
                Tab.BUCHEN -> BookTab(api, loggedIn, preset, { preset = it }, open) { tab = Tab.KONTO }
                Tab.PAKETE -> PackagesTab(api, loggedIn, open, { tab = Tab.KONTO }) { req -> preset = req; tab = Tab.BUCHEN }
                Tab.KONTO -> AccountTab(api, settings, loggedIn, { loggedIn = it }, open)
            }
        }
        LegalFooter(open)
    }
}

@Composable
private fun CatalogTab(api: ShopApi, open: (String) -> Unit, book: (String) -> Unit) {
    var products by remember { mutableStateOf<List<StoreProduct>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        error = null
        try {
            products = api.products()
        } catch (e: ShopException) {
            error = e.message
        } catch (e: Exception) {
            error = "Die Filmpakete konnten nicht geladen werden."
        }
    }
    val err = error
    val list = products
    when {
        err != null -> Column {
            Text(err, color = MaterialTheme.colorScheme.error)
            FoxButton("Erneut versuchen") { reload++ }
        }
        list == null -> Text("Lade Filmpakete …")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Prepaid, keine automatische Verlängerung. Paket, Laufzeit und Verbindungen wählst du auf der Shop-Seite; " +
                        "bezahlt wird sicher im Browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list, key = { it.id }) { p -> ProductCard(p, open, book) }
        }
    }
}

private fun money(minor: String, unit: Int, suffix: String): String {
    val v = minor.toLongOrNull() ?: return ""
    var div = 1.0
    repeat(unit) { div *= 10 }
    return String.format(java.util.Locale.GERMANY, "%.${unit}f", v / div) + suffix
}

@Composable
private fun ProductCard(p: StoreProduct, open: (String) -> Unit, book: (String) -> Unit) {
    val lang = p.sku.removePrefix("FV-").takeIf { p.sku.startsWith("FV-") && it.length == 2 }
    val r = p.prices.range
    val price = if (r != null && r.min != r.max) {
        "ab " + money(r.min, p.prices.minorUnit, p.prices.suffix)
    } else {
        money(p.prices.price, p.prices.minorUnit, p.prices.suffix)
    }
    FocusCard(onClick = { if (lang != null) book(lang) else open(p.permalink) }) {
        Row {
            p.images.firstOrNull()?.let {
                AsyncImage(model = it.src, contentDescription = p.name, modifier = Modifier.size(96.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(p.name.uppercase(), style = MaterialTheme.typography.titleMedium)
                Text(
                    HtmlCompat.fromHtml(p.shortDescription, 0).toString().trim(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                )
                p.attributes.forEach { a ->
                    Text(
                        "${a.name}: ${a.terms.joinToString { it.name }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (p.type == "variable") Text(price, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(if (lang != null) "Buchen ›" else "Im Shop ansehen ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private val LANGS = listOf("DE" to "Deutsch", "EN" to "Englisch", "FR" to "Französisch", "IT" to "Italienisch", "ES" to "Spanisch")
private val TERMS = listOf("24h" to "24h-Test", "1m" to "1 Monat", "3m" to "3 Monate", "6m" to "6 Monate", "12m" to "12 Monate")
private val ACTIONS = listOf("new" to "Neu", "extend" to "Verlängern", "add_connections" to "Verbindungen +", "upgrade" to "Auf Premium")

private fun fmtDate(iso: String?): String = iso?.let {
    runCatching {
        java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }.getOrDefault(it)
} ?: "–"

private fun statusDe(s: String) = when (s) {
    "active" -> "aktiv"; "scheduled" -> "geplant"; "expired" -> "abgelaufen"; "cancelled" -> "storniert"; else -> s
}

private fun reasonDe(r: String?) = when (r) {
    "tier" -> "nur mit Premium oder Test"; "off" -> "Player ist derzeit abgeschaltet"
    "not_configured" -> "noch nicht eingerichtet"; null -> "nicht verfügbar"; else -> r
}

@Composable
private fun <T> Choice(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { (v, l) -> FoxButton(l, primary = v == selected, small = true) { onSelect(v) } }
    }
}

@Composable
private fun BookTab(
    api: ShopApi,
    loggedIn: Boolean,
    req: CheckoutRequest,
    setReq: (CheckoutRequest) -> Unit,
    open: (String) -> Unit,
    goLogin: () -> Unit,
) {
    if (!loggedIn) {
        Column {
            Text("Zum Buchen bitte anmelden. Ohne Konto kannst du auch direkt im Shop bestellen.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FoxButton("Zur Anmeldung", onClick = goLogin)
                FoxButton("Im Shop bestellen", primary = false) { open(ShopConfig.BASE + "/shop/") }
            }
        }
        return
    }
    val scope = rememberCoroutineScope()
    var trial by remember { mutableStateOf<TrialEligibility?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { trial = runCatching { api.trialEligibility() }.getOrNull() }
    val isTrial = req.term == "24h"
    val paid = req.connections
    val streams = paid + paid / 4
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Choice("Aktion", ACTIONS, req.action) { setReq(req.copy(action = it)) } }
        item { Choice("Sprache", LANGS, req.lang) { setReq(req.copy(lang = it)) } }
        item { Choice("Paket", listOf("basis" to "Basis", "premium" to "Premium"), req.tier) { setReq(req.copy(tier = it)) } }
        item {
            val terms = if (trial?.eligible == true && req.action == "new") TERMS else TERMS.drop(1)
            Choice("Laufzeit", terms, req.term) { t ->
                setReq(if (t == "24h") req.copy(term = t, tier = "premium", connections = 1, action = "new") else req.copy(term = t))
            }
        }
        if (!isTrial) item { Choice("Verbindungen (bezahlt)", (1..8).map { it to "$it" }, req.connections) { setReq(req.copy(connections = it)) } }
        item {
            Text(
                "Streams gleichzeitig: ${if (isTrial) 1 else streams}" + (if (!isTrial && paid >= 4) " (inkl. ${paid / 4} gratis)" else "") +
                    "\nPrepaid, keine automatische Verlängerung. Der Preis wird im Shop angezeigt; bezahlt wird im Browser.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        msg?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.error) } }
        item {
            FoxButton(if (busy) "Bitte warten …" else "Weiter zur Kasse (Browser)") {
                if (!busy) {
                    busy = true; msg = null
                    scope.launch {
                        try {
                            val link = api.checkoutLink(req)
                            open(link.url) // einmalig, 5 Minuten gültig
                        } catch (e: ShopException) {
                            msg = e.message
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackagesTab(
    api: ShopApi,
    loggedIn: Boolean,
    open: (String) -> Unit,
    goLogin: () -> Unit,
    book: (CheckoutRequest) -> Unit,
) {
    if (!loggedIn) {
        Column {
            Text("Melde dich an, um deine Pakete zu sehen.")
            FoxButton("Zur Anmeldung", onClick = goLogin)
        }
        return
    }
    var packages by remember { mutableStateOf<MyPackages?>(null) }
    var creds by remember { mutableStateOf<Credentials?>(null) }
    var links by remember { mutableStateOf<PlayerLinks?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        error = null
        try {
            packages = api.myPackages()
            links = runCatching { api.playerLinks() }.getOrNull()
            creds = runCatching { api.credentials() }.getOrNull()
        } catch (e: ShopException) {
            error = e.message
        } catch (e: Exception) {
            error = "Pakete konnten nicht geladen werden."
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        error?.let { e -> item { Column { Text(e, color = MaterialTheme.colorScheme.error); FoxButton("Erneut versuchen") { reload++ } } } }
        val list = packages?.packages
        if (packages == null && error == null) item { Text("Lade …") }
        if (list != null && list.isEmpty()) item { Text("Du hast noch keine Pakete. Unter „Buchen“ geht’s los.") }
        list?.forEach { p ->
            item(key = "pkg-" + p.lang) {
                FocusCard({}) {
                    Column {
                        Text("${p.name.uppercase()} · ${p.tier.uppercase()} · ${statusDe(p.status)}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Start ${fmtDate(p.start)} · Ende ${fmtDate(p.end)}" +
                                (p.daysLeft?.let { " · noch $it Tage" } ?: "") + " · Streams ${p.streamsNow}" +
                                (p.premiumUntil?.let { "\nPremium bis ${fmtDate(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        p.bookings.forEach { b ->
                            Text(
                                "#${b.orderId} ${b.tier} ${statusDe(b.state)}${if (b.trial) " (Test)" else ""} · ${fmtDate(b.start)}–${fmtDate(b.end)} · " +
                                    "${b.paidConnections} Verb./${b.streams} Streams · ${if (b.bookingMode == "parallel") "parallel" else "Anschluss"}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val pl = links?.packages?.firstOrNull { it.lang == p.lang }
                        if (pl != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            listOf("Live" to pl.live, "Filme (VOD)" to pl.vod).forEach { (label, t) ->
                                val url = t?.webUrl
                                if (t != null && t.allowed && t.available && url != null) FoxButton("▶ $label", small = true) { open(url) }
                                else Text("$label: ${reasonDe(t?.reason)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            val tier = if (p.tier == "premium") "premium" else "basis"
                            FoxButton("Verlängern", primary = false, small = true) { book(CheckoutRequest(p.lang, tier, "1m", 1, "extend")) }
                            if (p.status == "active") FoxButton("Verbindungen +", primary = false, small = true) {
                                book(CheckoutRequest(p.lang, tier, "1m", 1, "add_connections"))
                            }
                            if (p.status == "active" && p.tier == "basis") FoxButton("Auf Premium", primary = false, small = true) {
                                book(CheckoutRequest(p.lang, "premium", "1m", 1, "upgrade"))
                            }
                        }
                    }
                }
            }
        }
        links?.let { l -> if (!l.playerEnabled) item { Text("Der Player ist derzeit abgeschaltet.", style = MaterialTheme.typography.bodySmall) } }
        creds?.let { c ->
            item {
                FocusCard({}) {
                    Column {
                        Text("ZUGANGSDATEN", style = MaterialTheme.typography.titleMedium)
                        Text("Shop-Benutzer: ${c.shop?.username ?: "–"}", style = MaterialTheme.typography.bodySmall)
                        Text("Player (Jellyfin): ${c.jellyfin?.serverUrl ?: "–"} · Benutzer: ${c.jellyfin?.username ?: "siehe Portal-Profil"}", style = MaterialTheme.typography.bodySmall)
                        c.jellyfin?.hint?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        Text("Passwörter werden in der App nie angezeigt.", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            c.portal?.profileUrl?.let { u -> FoxButton("Portal-Profil", primary = false, small = true) { open(u) } }
                            c.shop?.passwordResetUrl?.let { u -> FoxButton("Shop-Passwort zurücksetzen", primary = false, small = true) { open(u) } }
                            c.portal?.passwordResetUrl?.let { u -> FoxButton("Portal-Passwort zurücksetzen", primary = false, small = true) { open(u) } }
                        }
                    }
                }
            }
        }
        item { FoxButton("Meine Pakete im Shop", primary = false) { open(packages?.accountUrl ?: ShopConfig.MEINE_PAKETE_WEB) } }
    }
}

@Composable
private fun AccountTab(
    api: ShopApi,
    settings: SettingsRepository,
    loggedIn: Boolean,
    setLoggedIn: (Boolean) -> Unit,
    open: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<Me?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            try {
                me = api.me()
                ThemeSync.pull(context, settings)
            } catch (e: ShopException) {
                error = e.message
                if (e.status == 401) setLoggedIn(api.isLoggedIn)
            }
        }
    }
    if (!loggedIn) {
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Mit deinem Shop-Konto anmelden", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Benutzername oder E-Mail") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Passwort") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoxButton(if (busy) "Bitte warten …" else "Anmelden") {
                        if (!busy) {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    me = api.login(user.trim(), pass)
                                    pass = "" // Passwort wird nie gespeichert
                                    setLoggedIn(true)
                                } catch (e: ShopException) {
                                    error = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                    FoxButton("Passwort vergessen", primary = false) { open(ShopConfig.PASSWORT_VERGESSEN) }
                }
            }
            item { Text("Noch kein Konto? Es wird beim ersten Kauf im Shop angelegt.", style = MaterialTheme.typography.bodySmall) }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Angemeldet als ${me?.displayName ?: "…"}", style = MaterialTheme.typography.titleMedium) }
            me?.let { m -> item { Text("${m.username} · ${m.email}", style = MaterialTheme.typography.bodySmall) } }
            item { Text("Dein Design wird mit dem Shop abgeglichen (Einstellungen › Design).", style = MaterialTheme.typography.bodySmall) }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoxButton("Kundenkonto im Shop") { open(ShopConfig.MEIN_KONTO) }
                    FoxButton("Abmelden", primary = false) {
                        scope.launch { api.logout(); me = null; setLoggedIn(false) }
                    }
                    FoxButton("Alle Geräte abmelden", primary = false) {
                        scope.launch { api.logout(all = true); me = null; setLoggedIn(false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalFooter(open: (String) -> Unit) {
    val links = listOf(
        "Impressum" to ShopConfig.IMPRESSUM,
        "AGB" to ShopConfig.AGB,
        "Datenschutz" to ShopConfig.DATENSCHUTZ,
        "Widerruf" to ShopConfig.WIDERRUF,
        "Vertrag widerrufen" to ShopConfig.VERTRAG_WIDERRUFEN,
    )
    LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(links) { (label, url) -> FoxButton(label, primary = false, small = true) { open(url) } }
    }
}

@Composable
private fun Notice(text: String, onDismiss: () -> Unit) {
    FocusCard(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
        Text("$text\n(OK zum Schließen)", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Karte mit sichtbarem D-Pad-Fokus (2-dp-Akzentrahmen). */
@Composable
private fun FocusCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxWidth()
            .border(if (focused) 2.dp else 1.dp, if (focused) accent else accent.copy(alpha = 0.24f), RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(3.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun FoxButton(label: String, primary: Boolean = true, small: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val mod = Modifier
        .onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(2.dp))
    val style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge
    if (primary) {
        Button(onClick = onClick, modifier = mod, shape = RoundedCornerShape(2.dp)) { Text(label.uppercase(), style = style) }
    } else {
        OutlinedButton(onClick = onClick, modifier = mod, shape = RoundedCornerShape(2.dp)) { Text(label.uppercase(), style = style) }
    }
}
