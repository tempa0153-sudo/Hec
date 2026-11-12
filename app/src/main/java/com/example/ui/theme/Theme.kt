package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun MoviesAndSeriesTheme(
    accentColor: Color = GoogleSkyBlue,
    accentColorHex: String? = null,
    content: @Composable () -> Unit
) {
    val rawAccent = if (!accentColorHex.isNullOrBlank()) parseHexColor(accentColorHex, accentColor) else accentColor
    // Elevate luminance for dark/grey accents so primary elements stay crisp and high contrast
    val lum = 0.299 * rawAccent.red + 0.587 * rawAccent.green + 0.114 * rawAccent.blue
    val resolvedAccent = if (lum < 0.38) {
        val scale = (0.55 / lum.coerceAtLeast(0.08)).coerceIn(1.3, 3.8)
        Color(
            red = (rawAccent.red * scale).coerceIn(0.55, 0.95).toFloat(),
            green = (rawAccent.green * scale).coerceIn(0.55, 0.95).toFloat(),
            blue = (rawAccent.blue * scale).coerceIn(0.55, 0.95).toFloat(),
            alpha = 1f
        )
    } else rawAccent

    val finalLum = 0.299 * resolvedAccent.red + 0.587 * resolvedAccent.green + 0.114 * resolvedAccent.blue
    val onPrimaryColor = if (finalLum > 0.65) Color(0xFF202124) else Color.White

    val colorScheme = darkColorScheme(
        primary = resolvedAccent,
        onPrimary = onPrimaryColor,
        primaryContainer = resolvedAccent.copy(alpha = 0.22f),
        onPrimaryContainer = Color.White,
        secondary = resolvedAccent,
        onSecondary = onPrimaryColor,
        secondaryContainer = SurfaceElevated,
        onSecondaryContainer = Color.White,
        background = BackgroundDark,
        onBackground = Color(0xFFF1F5F9),
        surface = SurfaceDark,
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = SurfaceCard,
        onSurfaceVariant = Color(0xFFCBD5E1),
        outline = Color(0xFF44474E),
        outlineVariant = Color(0xFF35363A)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
