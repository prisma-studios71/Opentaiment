package app.opentaiment.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Accent colours the user can pick. */
enum class Accent(val label: String, val color: Color, val onColor: Color) {
    BLUE("Błękit", Color(0xFF4FC3F7), Color(0xFF00222E)),
    GREEN("Zieleń", Color(0xFF69F0AE), Color(0xFF00291A)),
    ORANGE("Pomarańcz", Color(0xFFFFB74D), Color(0xFF2B1700)),
    RED("Czerwień", Color(0xFFFF6E6E), Color(0xFF330000)),
    PURPLE("Fiolet", Color(0xFFB388FF), Color(0xFF1D0047)),
    PINK("Róż", Color(0xFFFF80AB), Color(0xFF3A0018)),
}

/**
 * Car-friendly dark palette: no pure white, low glare, strong accent for touch targets.
 * The night variant goes red-on-black and dims text, which keeps the driver's eyes adapted to the dark.
 */
fun otColorScheme(accent: Accent, night: Boolean): ColorScheme {
    if (night) {
        return darkColorScheme(
            primary = Color(0xFFE53935),
            onPrimary = Color(0xFF1A0000),
            primaryContainer = Color(0xFF3B0A0A),
            onPrimaryContainer = Color(0xFFFFB4AB),
            secondary = Color(0xFFFF8A65),
            onSecondary = Color(0xFF1A0800),
            background = Color(0xFF000000),
            onBackground = Color(0xFFB8A4A4),
            surface = Color(0xFF0F0A0A),
            onSurface = Color(0xFFB8A4A4),
            surfaceVariant = Color(0xFF1A1212),
            onSurfaceVariant = Color(0xFF7A6666),
            outline = Color(0xFF2E2020),
            error = Color(0xFFFF5252),
        )
    }
    // Orange accent would clash with the orange secondary colour – swap it for blue then.
    val secondary = if (accent == Accent.ORANGE) Accent.BLUE.color else Color(0xFFFFB74D)
    return darkColorScheme(
        primary = accent.color,
        onPrimary = accent.onColor,
        primaryContainer = accent.color.copy(alpha = 0.25f),
        onPrimaryContainer = accent.color,
        secondary = secondary,
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
}

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
fun OpentaimentTheme(
    accent: Accent = Accent.BLUE,
    night: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = otColorScheme(accent, night), typography = OtTypography, content = content)
}
