package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Default Google Sky Blue palette
val GoogleSkyBlue = Color(0xFF38BDF8)
val GoogleSkyBlueDark = Color(0xFF0288D1)
val GoogleSkyBlueLight = Color(0xFFBAE6FD)

// Cinema & Ratings Colors
val CinemaRed = Color(0xFFE50914)
val ImdbGold = Color(0xFFF5C518)

// AMOLED Pitch Black Foundation
val BackgroundDark = Color(0xFF000000) // Pure AMOLED Pitch Black
val SurfaceDark = Color(0xFF0B0B0C)    // Dark Surface
val SurfaceElevated = Color(0xFF161618) // Surface elevated / chips
val SurfaceCard = Color(0xFF121214)     // Card Surface
val BottomNavDark = Color(0xEE121214)   // Bottom Navigation bar

// Streaming Brand Colors
val AppleTvColor = Color(0xFFFFFFFF)
val NetflixRed = Color(0xFFE50914)
val HboMaxPurple = Color(0xFF9933FF)
val DisneyBlue = Color(0xFF113CCF)
val PrimeVideoBlue = Color(0xFF00A8E1)

// Google Blue brand color (Requested)
val GoogleBlue = Color(0xFF1A73E8)

// Accent Palette Presets for Profile Settings
data class AccentPreset(
    val name: String,
    val hex: String,
    val color: Color
)

val AccentColorPresets = listOf(
    AccentPreset("Google Blue", "#1A73E8", Color(0xFF1A73E8)),
    AccentPreset("Sky Blue", "#38BDF8", Color(0xFF38BDF8)),
    AccentPreset("Slate Grey", "#94A3B8", Color(0xFF94A3B8)),
    AccentPreset("Graphite Silver", "#A1A1AA", Color(0xFFA1A1AA)),
    AccentPreset("Charcoal Grey", "#8E99A8", Color(0xFF8E99A8)),
    AccentPreset("Platinum Grey", "#B0BEC5", Color(0xFFB0BEC5)),
    AccentPreset("Electric Magenta", "#D946EF", Color(0xFFD946EF)),
    AccentPreset("Deep Magenta", "#C026D3", Color(0xFFC026D3)),
    AccentPreset("Cinema Red", "#E50914", Color(0xFFE50914)),
    AccentPreset("Crimson", "#DC2626", Color(0xFFDC2626)),
    AccentPreset("Neon Rose", "#F43F5E", Color(0xFFF43F5E)),
    AccentPreset("Hot Pink", "#EC4899", Color(0xFFEC4899)),
    AccentPreset("Royal Purple", "#8B5CF6", Color(0xFF8B5CF6)),
    AccentPreset("Deep Purple", "#7C3AED", Color(0xFF7C3AED)),
    AccentPreset("Indigo", "#6366F1", Color(0xFF6366F1)),
    AccentPreset("Ocean Cyan", "#06B6D4", Color(0xFF06B6D4)),
    AccentPreset("Teal", "#14B8A6", Color(0xFF14B8A6)),
    AccentPreset("Emerald Green", "#10B981", Color(0xFF10B981)),
    AccentPreset("Mint Green", "#34D399", Color(0xFF34D399)),
    AccentPreset("Forest Green", "#15803D", Color(0xFF15803D)),
    AccentPreset("Lime Green", "#84CC16", Color(0xFF84CC16)),
    AccentPreset("Amber Orange", "#F59E0B", Color(0xFFF59E0B)),
    AccentPreset("Sunset Orange", "#F97316", Color(0xFFF97316)),
    AccentPreset("Golden Yellow", "#EAB308", Color(0xFFEAB308)),
    AccentPreset("Warm Brown", "#A1887F", Color(0xFFA1887F))
)

fun parseHexColor(hex: String, fallback: Color = GoogleSkyBlue): Color {
    return try {
        val clean = hex.removePrefix("#")
        val longVal = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or longVal)
        } else if (clean.length == 8) {
            Color(longVal)
        } else fallback
    } catch (_: Exception) {
        fallback
    }
}
