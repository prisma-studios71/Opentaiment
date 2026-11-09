package app.opentaiment.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Car-friendly dark palette: no pure white, low glare at night, strong accent for touch targets.
 */
private val OtColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00222E),
    primaryContainer = Color(0xFF0F3A4A),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF2B1700),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF151B23),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1E2630),
    onSurfaceVariant = Color(0xFF9AA7B4),
    outline = Color(0xFF2E3A47),
    error = Color(0xFFFF6B6B),
)

private val base = Typography()

/** Bigger than phone defaults – readable at arm's length on a dashboard. */
private val OtTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontSize = 88.sp, lineHeight = 96.sp, fontWeight = FontWeight.Light),
    displayMedium = base.displayMedium.copy(fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light),
    headlineMedium = base.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = base.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
    labelLarge = base.labelLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
)

@Composable
fun OpentaimentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OtColors, typography = OtTypography, content = content)
}
