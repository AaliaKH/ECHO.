package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = EmergencyRed,
    onPrimary = Color.White,
    primaryContainer = EmergencyRedLight,
    onPrimaryContainer = EmergencyRedDark,
    secondary = SignalGreen,
    onSecondary = Color.White,
    secondaryContainer = SignalGreenSoft,
    onSecondaryContainer = SignalGreenLight,
    tertiary = WarningAmber,
    onTertiary = Color.White,
    background = EmergencyBackground,
    onBackground = TextPrimary,
    surface = EmergencySurface,
    onSurface = TextPrimary,
    surfaceVariant = EmergencySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Forced clean white with soft green accents per design mandate
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
