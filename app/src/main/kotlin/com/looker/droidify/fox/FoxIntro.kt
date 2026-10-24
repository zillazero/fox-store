package com.looker.droidify.fox

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import com.looker.droidify.R
import com.looker.droidify.datastore.SettingsRepository
import com.looker.droidify.datastore.model.Theme
import kotlinx.coroutines.launch

/**
 * FOX Store 1.5.0: Einführung (4 Seiten, Logo A, D-Pad + Touch, überspringbar) und
 * Einrichtungsassistent (Theme, Auto-Updates). Erneut aufrufbar über Einstellungen › „Einführung anzeigen“.
 */
object FoxIntro {
    private const val PREFS = "fox_intro"; private const val KEY = "done_v1"

    fun shouldShow(ctx: Context) = !ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    private fun markDone(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()

    fun show(activity: ComponentActivity, repo: SettingsRepository) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        lateinit var view: ComposeView
        view = ComposeView(activity).apply {
            setContent {
                IntroFlow(
                    onTheme = { t -> activity.lifecycleScope.launch { repo.setTheme(t) } },
                    onAutoUpdate = { a -> activity.lifecycleScope.launch { repo.setAutoUpdate(a) } },
                    onWifiOnly = { w -> activity.lifecycleScope.launch { repo.setAutoUpdateWifiOnly(w) } },
                    onDone = { markDone(activity); decor.removeView(view) },
                )
            }
        }
        decor.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun reopen(activity: Activity) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        activity.recreate()
    }
}

private val Bg = Color(0xFF0A0A0B); private val Bg2 = Color(0xFF111214)
private val Txt = Color(0xFFECECEE); private val Muted = Color(0xFF9A9DA4); private val Accent = Color(0xFFFF3B30)

private data class Page(val title: String, val body: String)
private val pages = listOf(
    Page("Willkommen im FOX Store", "Alle FOX-Apps an einem Ort – geprüft, signiert und immer aktuell."),
    Page("Fernbedienung oder Touch", "Mit dem Steuerkreuz navigieren, OK wählt aus, Zurück geht einen Schritt zurück. Auf Handy und Tablet einfach tippen."),
    Page("Updates im Hintergrund", "Auf Wunsch installiert der Store Updates automatisch – nur im WLAN, nachts oder beim Laden."),
    Page("Gleich geht’s los", "Wähle dein Theme und deine Update-Einstellungen. Alles lässt sich später in den Einstellungen ändern."),
)

@Composable
private fun IntroFlow(onTheme: (Theme) -> Unit, onAutoUpdate: (Boolean) -> Unit, onWifiOnly: (Boolean) -> Unit, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var theme by remember { mutableStateOf(Theme.FOXHOLE) }
    var auto by remember { mutableStateOf(true) }
    var wifi by remember { mutableStateOf(true) }
    val total = pages.size + 2
    Box(Modifier.fillMaxSize().background(Bg).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(32.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "FOX Logo", modifier = Modifier.size(140.dp))
            Text2("Schritt ${step + 1} von $total", Muted, 13)
            Spacer(Modifier.height(12.dp))
            when {
                step < pages.size -> {
                    Text2(pages[step].title, Txt, 26, bold = true, heading = true)
                    Spacer(Modifier.height(12.dp)); Text2(pages[step].body, Muted, 17)
                }
                step == pages.size -> {
                    Text2("Theme wählen", Txt, 26, bold = true, heading = true); Spacer(Modifier.height(12.dp))
                    Theme.entries.filter { it.accent != 0L }.take(5).forEach { t ->
                        FoxButton((if (t == theme) "● " else "○ ") + t.label, selected = t == theme) { theme = t; onTheme(t) }
                    }
                }
                else -> {
                    Text2("Automatische Updates", Txt, 26, bold = true, heading = true); Spacer(Modifier.height(12.dp))
                    FoxButton((if (auto) "☑ " else "☐ ") + "Updates automatisch installieren", selected = auto) { auto = !auto }
                    FoxButton((if (wifi) "☑ " else "☐ ") + "Nur im WLAN", selected = wifi) { wifi = !wifi }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step < pages.size) FoxButton("Überspringen") { step = pages.size }
                if (step > 0) FoxButton("Zurück") { step-- }
                FoxButton(if (step == total - 1) "Fertig" else "Weiter", primary = true, autofocus = true) {
                    if (step == total - 1) { onAutoUpdate(auto); onWifiOnly(wifi); onDone() } else step++
                }
            }
        }
    }
}

@Composable
private fun Text2(s: String, c: Color, size: Int, bold: Boolean = false, heading: Boolean = false) =
    androidx.compose.material3.Text(s, color = c, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else null,
        modifier = if (heading) Modifier.semantics { heading() } else Modifier)

@Composable
private fun FoxButton(label: String, primary: Boolean = false, selected: Boolean = false, autofocus: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val fr = remember { FocusRequester() }
    if (autofocus) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(
        Modifier.padding(vertical = 4.dp).heightIn(min = 48.dp)
            .background(if (primary) Accent else Bg2, RoundedCornerShape(12.dp))
            .border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Txt else if (selected) Accent else Color(0xFF2A2C31)), RoundedCornerShape(12.dp))
            .focusRequester(fr)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Text(label, color = if (primary) Color.Black else Txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}
