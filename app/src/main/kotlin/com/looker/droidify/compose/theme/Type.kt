package com.looker.droidify.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.looker.droidify.R

// FOX Designsystem: Orbitron (Head), Rajdhani (UI), Inter (Body) – lokal eingebunden.
private val Orbitron = FontFamily(Font(R.font.orbitron_latin_700_normal, FontWeight.Bold))
private val Rajdhani = FontFamily(Font(R.font.rajdhani_latin_700_normal, FontWeight.Bold))
private val Inter = FontFamily(Font(R.font.inter_latin_400_normal, FontWeight.Normal))

private val baseline = Typography()

val Typography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = Orbitron),
    displayMedium = baseline.displayMedium.copy(fontFamily = Orbitron),
    displaySmall = baseline.displaySmall.copy(fontFamily = Orbitron),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = Orbitron),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = Orbitron),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = Orbitron),
    titleLarge = baseline.titleLarge.copy(fontFamily = Orbitron),
    titleMedium = baseline.titleMedium.copy(fontFamily = Rajdhani, letterSpacing = 0.06.em),
    titleSmall = baseline.titleSmall.copy(fontFamily = Rajdhani, letterSpacing = 0.06.em),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = Inter),
    bodySmall = baseline.bodySmall.copy(fontFamily = Inter),
    labelLarge = baseline.labelLarge.copy(fontFamily = Rajdhani, letterSpacing = 0.08.em),
    labelMedium = baseline.labelMedium.copy(fontFamily = Rajdhani, letterSpacing = 0.08.em),
    labelSmall = baseline.labelSmall.copy(fontFamily = Rajdhani, letterSpacing = 0.08.em),
)
