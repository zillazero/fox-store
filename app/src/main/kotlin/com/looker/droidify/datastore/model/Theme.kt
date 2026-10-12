package com.looker.droidify.datastore.model

/**
 * FOX Designsystem: die fünf Pflicht-Designs (Quelle: fox-designsystem/tokens.json).
 * [key] ist der Schlüssel aus tokens.json (Grün = "gruen").
 */
enum class Theme(
    val key: String,
    val label: String,
    val accent: Long,
    val accent2: Long,
    val bg: Long,
    val bg2: Long,
    val text: Long,
    val muted: Long,
) {
    FOXHOLE("foxhole", "Foxhole", 0xFFFF3B30, 0xFFFF8A80, 0xFF0A0A0B, 0xFF111214, 0xFFECECEE, 0xFF9A9DA4),
    MAGENTA("magenta", "Magenta", 0xFFFF2D6F, 0xFFFF7AA2, 0xFF0A0A0B, 0xFF111214, 0xFFECECEE, 0xFF9A9DA4),
    AMBER("amber", "Amber", 0xFFFFB000, 0xFFFFD36B, 0xFF0A0A0B, 0xFF111214, 0xFFECECEE, 0xFF9A9DA4),
    GRUEN("gruen", "Grün", 0xFF19D26B, 0xFF7CF2AE, 0xFF0A0A0B, 0xFF111214, 0xFFECECEE, 0xFF9A9DA4),
    POLIZEI("polizei", "Polizei", 0xFF4D8BFF, 0xFF36D6FF, 0xFF050A17, 0xFF0A1328, 0xFFE6EEFF, 0xFF9FB2D6);

    companion object {
        val DEFAULT = FOXHOLE
        fun fromStored(value: String?): Theme =
            entries.firstOrNull { it.name == value || it.key == value || (value == "green" && it == GRUEN) } ?: DEFAULT
    }
}
