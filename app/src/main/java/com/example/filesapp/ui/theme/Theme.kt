package com.example.filesapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Warm Cream & Soft Beige Palette (matching the warm beige folder app icon)
val WarmCreamBg = Color(0xFFF7F3ED)             // Primary background: calming warm cream
val WarmBeigeCard = Color(0xFFFFFFFF)           // Primary surface
val WarmBeigeSubtle = Color(0xFFF0EBE1)         // Subtle surface (search bars, pill tabs, chips)
val WarmTaupeAccent = Color(0xFF9E6B55)         // Warm refined taupe/caramel primary
val WarmTaupeSecondary = Color(0xFF825845)      // Deeper taupe
val WarmGreigeMuted = Color(0xFF8C827A)         // Subtitles, metadata, secondary text
val WarmCharcoalText = Color(0xFF2C2825)        // Primary text: dark roasted coffee charcoal
val WarmBorderHighlight = Color(0xFFEADBCE)     // Soft border and glass highlight

// Gentle Warm Dark Mode (Deep warm charcoal & espresso tones, NOT cold blue-black)
val WarmDarkBg = Color(0xFF191715)              // Deep warm roasted espresso canvas
val WarmDarkCard = Color(0xFF24211D)            // Warm charcoal surface
val WarmDarkSubtle = Color(0xFF2E2A25)          // Warm charcoal chip & input surface
val WarmDarkAccent = Color(0xFFC48E77)          // Warm glowing terracotta taupe
val WarmDarkTextPrimary = Color(0xFFF5EFEB)     // Warm ivory text
val WarmDarkTextMuted = Color(0xFFA89F96)       // Warm soft greige text
val WarmDarkBorder = Color(0xFF38332D)          // Subtle warm dark border

// Glass Highlights & Border Brushes (Compatible with Android 10 / API 29 without RenderEffect)
fun glassBorderBrush(isDark: Boolean): Brush {
    return if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f), // Frosted top reflection
                Color(0xFF38332D).copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f), // Clean top light reflection
                Color(0xFFEADBCE).copy(alpha = 0.6f),
                Color.White.copy(alpha = 0.4f)
            )
        )
    }
}

fun glassSurfaceBrush(isDark: Boolean, alpha: Float = 0.82f): Brush {
    return if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2A2621).copy(alpha = alpha),
                Color(0xFF1E1B18).copy(alpha = alpha * 0.95f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFFFF).copy(alpha = alpha),
                Color(0xFFF8F4EE).copy(alpha = alpha * 0.95f)
            )
        )
    }
}

private val LightColorScheme = lightColorScheme(
    primary = WarmTaupeAccent,
    onPrimary = Color.White,
    background = WarmCreamBg,
    onBackground = WarmCharcoalText,
    surface = WarmBeigeCard,
    onSurface = WarmCharcoalText,
    outline = WarmBorderHighlight
)

private val DarkColorScheme = darkColorScheme(
    primary = WarmDarkAccent,
    onPrimary = WarmDarkBg,
    background = WarmDarkBg,
    onBackground = WarmDarkTextPrimary,
    surface = WarmDarkCard,
    onSurface = WarmDarkTextPrimary,
    outline = WarmDarkBorder
)

@Composable
fun FilesAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
