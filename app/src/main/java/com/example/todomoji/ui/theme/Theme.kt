package com.example.todomoji.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape


private val DarkColors = darkColorScheme(
    primary = Color(0xFF00D1B2),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF083B36),
    onPrimaryContainer = Color(0xFFBFFFF4),
    secondary = Color(0xFF7EE1D2),
    onSecondary = Color(0xFF00201C),
    background = Color(0xFF0E0F10),
    onBackground = Color(0xFFE8EAE9),
    surface = Color(0xFF141617),
    onSurface = Color(0xFFE2E4E3),
    surfaceVariant = Color(0xFF1B1D1F),
    onSurfaceVariant = Color(0xFFBFC3C6),
    outline = Color(0xFF3B3F42)
)

@Composable
fun TodomojiTheme(content: @Composable () -> Unit) {
    val colors = DarkColors // always dark for now
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8),
            small = RoundedCornerShape(12),
            medium = RoundedCornerShape(16),
            large = RoundedCornerShape(20),
            extraLarge = RoundedCornerShape(28)
        ),
        content = content
    )
}
