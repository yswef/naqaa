package com.naqaa.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A neon palette on a deep space background: cyan as the main accent, violet and magenta as
 * companions. Contrast stays high for text that can be pressed. The scheme is dark only; the
 * look is the point of the theme, so the system setting does not switch it.
 */
object Neon {
    val Space = Color(0xFF070A1A)
    val Panel = Color(0xFF111A3F)
    val Cyan = Color(0xFF00E5FF)
    val Violet = Color(0xFF9D6BFF)
    val Magenta = Color(0xFFFF2E93)
    val Lime = Color(0xFF39FF88)
    val Amber = Color(0xFFFFC857)
}

private val NeonScheme = darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Color(0xFF001A20),
    primaryContainer = Color(0xFF0B3A57),
    onPrimaryContainer = Color(0xFFBDF4FF),
    secondary = Neon.Violet,
    onSecondary = Color(0xFF12002E),
    secondaryContainer = Color(0xFF2C1B63),
    onSecondaryContainer = Color(0xFFE6DAFF),
    tertiary = Neon.Magenta,
    onTertiary = Color(0xFF2E0018),
    background = Neon.Space,
    onBackground = Color(0xFFE8ECFF),
    surface = Color(0xFF0C1230),
    onSurface = Color(0xFFE8ECFF),
    surfaceVariant = Neon.Panel,
    onSurfaceVariant = Color(0xFFBCC6F0),
    outline = Color(0xFF6B7BC4),
    error = Color(0xFFFF5470),
    onError = Color(0xFF3A0010)
)

private val BaseTypography = Typography()
private val NaqaaTypography = BaseTypography.copy(
    // Material buttons and text buttons use labelLarge. A slightly larger, semibold label is
    // easier to read in Arabic and keeps the text distinct from the button outline.
    labelLarge = BaseTypography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    // Arabic needs more height between lines than the Latin defaults give it.
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
)

private val NaqaaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun NaqaaOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    errorTextColor = MaterialTheme.colorScheme.error,
    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
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

/** Slow drifting glows behind every screen; two floats animate, so the cost is small. */
@Composable
fun NeonBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF060818), Neon.Space, Color(0xFF0A0F2B))))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Neon.Cyan.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * (0.15f + 0.25f * drift), size.height * 0.12f),
                        radius = size.minDimension * 0.9f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Neon.Violet.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width * (0.9f - 0.3f * drift), size.height * 0.55f),
                        radius = size.minDimension * 1.0f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Neon.Magenta.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * (0.2f + 0.2f * drift), size.height * 0.95f),
                        radius = size.minDimension * 0.8f
                    )
                )
            }
    )
}

@Composable
fun NaqaaTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonScheme,
        typography = NaqaaTypography,
        shapes = NaqaaShapes
    ) {
        // The backdrop is drawn once under every screen; screens above it keep transparent
        // backgrounds. LocalContentColor gives text the right colour on that backdrop.
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NeonBackdrop()
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                content()
            }
        }
    }
}
