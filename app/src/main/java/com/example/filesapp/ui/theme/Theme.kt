package com.example.filesapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SoftCreamBackground = Color(0xFFF7F3ED)
val WhiteCardSurface = Color(0xFFFFFFFF)
val PrimaryAccentTaupe = Color(0xFF8C533E)
val TextDarkHeadings = Color(0xFF2C2825)
val TextMutedSubtitles = Color(0xFF8C827A)
val SoftBorderColor = Color(0xFFEADBCE)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccentTaupe,
    onPrimary = Color.White,
    background = SoftCreamBackground,
    onBackground = TextDarkHeadings,
    surface = WhiteCardSurface,
    onSurface = TextDarkHeadings,
    outline = SoftBorderColor
)

@Composable
fun FilesAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
