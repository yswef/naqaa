package com.naqaa.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A calm palette: forest green for protection, sand for surfaces, and no strong accent
 * that would push the eye. Both schemes are defined here rather than in resources so the
 * theme stays a single readable file.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC3E8D2),
    onPrimaryContainer = Color(0xFF0A2A1B),
    secondary = Color(0xFF5A6B5F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE7DD),
    onSecondaryContainer = Color(0xFF17211A),
    tertiary = Color(0xFF7A5C3E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F6F1),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFDFCF8),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFDFE4DC),
    onSurfaceVariant = Color(0xFF43483F),
    outline = Color(0xFF73796E),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8ED3AC),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF165234),
    onPrimaryContainer = Color(0xFFC3E8D2),
    secondary = Color(0xFFB9C9BB),
    onSecondary = Color(0xFF243027),
    secondaryContainer = Color(0xFF3A463C),
    onSecondaryContainer = Color(0xFFDCE7DD),
    tertiary = Color(0xFFE6C19C),
    onTertiary = Color(0xFF442C11),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE3E3DD),
    surface = Color(0xFF1A1C1A),
    onSurface = Color(0xFFE3E3DD),
    surfaceVariant = Color(0xFF43483F),
    onSurfaceVariant = Color(0xFFC3C8BD),
    outline = Color(0xFF8D9288),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun NaqaaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}
