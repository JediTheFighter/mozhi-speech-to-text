package com.mozhi.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MozhiDark = darkColorScheme(
    primary = MonsoonTeal,
    onPrimary = NightInk,
    secondary = LotusGold,
    onSecondary = NightInk,
    tertiary = WaveViolet,
    background = NightInk,
    onBackground = Mist,
    surface = NightRaised,
    onSurface = Mist,
    onSurfaceVariant = MistMuted,
    error = LotusCoral,
    outline = Color(0xFF2A3558),
)

@Composable
fun MozhiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MozhiDark,
        typography = MozhiTypography,
        content = content,
    )
}
