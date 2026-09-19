package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VaultPrimaryDark,
    onPrimary = Color(0xFF003554),
    primaryContainer = Color(0xFF004D74),
    onPrimaryContainer = Color(0xFFBBE9FF),
    secondary = VaultSecondaryDark,
    onSecondary = Color(0xFF003831),
    secondaryContainer = Color(0xFF005147),
    onSecondaryContainer = Color(0xFF73F8DE),
    tertiary = VaultTertiaryDark,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDEAC),
    background = VaultDarkBg,
    onBackground = Color(0xFFE2E8F0),
    surface = VaultDarkSurface,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = VaultDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = VaultDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = VaultPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6FF),
    onPrimaryContainer = Color(0xFF001E30),
    secondary = VaultSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2F5EA),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = VaultTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE088),
    onTertiaryContainer = Color(0xFF261900),
    background = VaultLightBg,
    onBackground = Color(0xFF0F172A),
    surface = VaultLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = VaultLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = VaultLightBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

