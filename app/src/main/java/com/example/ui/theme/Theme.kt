package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AuraPrimary,
    onPrimary = Color(0xFF031024),
    primaryContainer = AuraSurfaceVariantDark,
    onPrimaryContainer = AuraPrimary,
    secondary = AuraSecondary,
    onSecondary = Color.White,
    secondaryContainer = AuraSurfaceHighlight,
    onSecondaryContainer = AuraSecondary,
    tertiary = AuraTertiary,
    onTertiary = Color.White,
    background = AuraBackgroundDark,
    onBackground = AuraTextPrimaryDark,
    surface = AuraSurfaceDark,
    onSurface = AuraTextPrimaryDark,
    surfaceVariant = AuraSurfaceVariantDark,
    onSurfaceVariant = AuraTextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = AuraPrimaryVariant,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = AuraPrimaryVariant,
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFFE11D48),
    onTertiary = Color.White,
    background = AuraBackgroundLight,
    onBackground = AuraTextPrimaryLight,
    surface = AuraSurfaceLight,
    onSurface = AuraTextPrimaryLight,
    surfaceVariant = AuraSurfaceVariantLight,
    onSurfaceVariant = AuraTextSecondaryLight
)

@Composable
fun AuraMusicTheme(
    darkTheme: Boolean = true, // Music players default to dark theme for visual immersion
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward-compatible alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AuraMusicTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
