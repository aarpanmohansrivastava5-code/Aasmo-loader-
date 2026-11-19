package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Core Obsidian Surfaces matching Prism Browser CSS
val ObsidianBg1 = Color(0xFF000000)
val ObsidianBg2 = Color(0xFF07070C)
val SurfaceElevated = Color(0xFF10111C)

// Liquid Glass Tokens
val GlassHi = Color(0x21FFFFFF)       // rgba(255,255,255,.13)
val GlassBase = Color(0x0EFFFFFF)     // rgba(255,255,255,.055)
val GlassEdge = Color(0x61FFFFFF)     // rgba(255,255,255,.38)
val GlassEdgeLo = Color(0x14FFFFFF)   // rgba(255,255,255,.08)
val OmniInsetBg = Color(0x38000000)   // rgba(0,0,0,.22)

val TextPrimary = Color(0xFFFFFFFF)
val TextMuted = Color(0xA8FFFFFF)     // rgba(255,255,255,.66)
val TextSubtle = Color(0x66FFFFFF)

// Prism Default Orbs & Status Colors
val PrismIndigo = Color(0xFF5B6CFF)
val PrismPink = Color(0xFFFF6FA8)
val PrismTeal = Color(0xFF27D3C3)
val PrismAmber = Color(0xFFFFB454)

val StatusSuccess = Color(0xFF2FBF71)
val StatusWarning = Color(0xFFFFB454)
val StatusError = Color(0xFFFF5C77)

enum class AccentPreset(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val orb3: Color,
    val orb4: Color,
) {
    PRISM_INDIGO(
        id = "prism",
        label = "Prism Indigo",
        primary = Color(0xFF5B6CFF),
        secondary = Color(0xFFFF6FA8),
        orb3 = Color(0xFF27D3C3),
        orb4 = Color(0xFFFFB454),
    ),
    NEON_CYAN(
        id = "cyan",
        label = "Neon Cyan",
        primary = Color(0xFF00F0FF),
        secondary = Color(0xFF5B6CFF),
        orb3 = Color(0xFF27D3C3),
        orb4 = Color(0xFFA855F7),
    ),
    ELECTRIC_PURPLE(
        id = "purple",
        label = "Electric Purple",
        primary = Color(0xFFA855F7),
        secondary = Color(0xFFFF6FA8),
        orb3 = Color(0xFF5B6CFF),
        orb4 = Color(0xFF27D3C3),
    ),
    EMERALD_PULSE(
        id = "emerald",
        label = "Emerald Pulse",
        primary = Color(0xFF10B981),
        secondary = Color(0xFF27D3C3),
        orb3 = Color(0xFF3B82F6),
        orb4 = Color(0xFFFFB454),
    ),
    SUNSET_AMBER(
        id = "amber",
        label = "Sunset Amber",
        primary = Color(0xFFF59E0B),
        secondary = Color(0xFFFF6A2B),
        orb3 = Color(0xFFFF6FA8),
        orb4 = Color(0xFF5B6CFF),
    );

    companion object {
        fun fromId(id: String?): AccentPreset =
            entries.firstOrNull { it.id == id } ?: PRISM_INDIGO
    }
}
