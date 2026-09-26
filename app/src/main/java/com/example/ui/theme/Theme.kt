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
    primary = SyedCyan,
    onPrimary = SyedNavy,
    primaryContainer = SyedSlate,
    onPrimaryContainer = Color.White,
    secondary = SyedTealLight,
    onSecondary = SyedNavy,
    background = SyedBgDark,
    surface = SyedSurfaceDark,
    surfaceVariant = SyedSurfaceVariantDark,
    onBackground = SyedTextPrimaryDark,
    onSurface = SyedTextPrimaryDark,
    onSurfaceVariant = SyedTextSecondaryDark,
    outline = SyedBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = SyedBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = SyedNavy,
    secondary = SyedTeal,
    onSecondary = Color.White,
    background = SyedBgLight,
    surface = SyedSurfaceLight,
    surfaceVariant = SyedSurfaceVariantLight,
    onBackground = SyedTextPrimaryLight,
    onSurface = SyedTextPrimaryLight,
    onSurfaceVariant = SyedTextSecondaryLight,
    outline = SyedBorderLight
)

@Composable
fun SyedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent branding by default
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
