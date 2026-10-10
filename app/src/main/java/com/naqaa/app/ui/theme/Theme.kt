package com.naqaa.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Calm forest-green and sand palette with deliberately high contrast for interactive text. */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B5C42),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4EADC),
    onPrimaryContainer = Color(0xFF102A1B),
    secondary = Color(0xFF53665A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE7DD),
    onSecondaryContainer = Color(0xFF17211A),
    tertiary = Color(0xFF705339),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F6F1),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFDFCF8),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFE3E7E0),
    onSurfaceVariant = Color(0xFF3B463E),
    outline = Color(0xFF626A60),
    error = Color(0xFFA52320),
    onError = Color(0xFFFFFFFF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA8E5C0),
    onPrimary = Color(0xFF073824),
    primaryContainer = Color(0xFF214E36),
    onPrimaryContainer = Color(0xFFD4EADC),
    secondary = Color(0xFFBDCEC1),
    onSecondary = Color(0xFF223127),
    secondaryContainer = Color(0xFF3A463C),
    onSecondaryContainer = Color(0xFFDCE7DD),
    tertiary = Color(0xFFE6C19C),
    onTertiary = Color(0xFF442C11),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE3E3DD),
    surface = Color(0xFF1A1C1A),
    onSurface = Color(0xFFE3E3DD),
    surfaceVariant = Color(0xFF43483F),
    onSurfaceVariant = Color(0xFFD0D5CA),
    outline = Color(0xFF9AA195),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

private val BaseTypography = Typography()
private val NaqaaTypography = BaseTypography.copy(
    // Material buttons and text buttons use labelLarge. A slightly larger, semibold label is
    // easier to read in Arabic and keeps the text distinct from the button outline.
    labelLarge = BaseTypography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun NaqaaOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    errorTextColor = MaterialTheme.colorScheme.error,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    errorContainerColor = MaterialTheme.colorScheme.surface,
    cursorColor = MaterialTheme.colorScheme.primary,
    errorCursorColor = MaterialTheme.colorScheme.error,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    errorLabelColor = MaterialTheme.colorScheme.error,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    errorPlaceholderColor = MaterialTheme.colorScheme.error
)

@Composable
fun NaqaaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = NaqaaTypography,
        content = content
    )
}
