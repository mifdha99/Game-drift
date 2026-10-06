package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DriftDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = MidnightAsphalt,
    secondary = DriftAmber,
    onSecondary = MidnightAsphalt,
    tertiary = RedlineCrimson,
    background = MidnightAsphalt,
    onBackground = TitaniumWhite,
    surface = CarbonSurface,
    onSurface = TitaniumWhite,
    surfaceVariant = PitWallPanel,
    onSurfaceVariant = SteelGray,
    error = RedlineCrimson
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DriftDarkColorScheme,
        typography = Typography,
        content = content
    )
}
