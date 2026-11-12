package app.opentaiment.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** Accent colours the user can pick. */
enum class Accent(val label: String, val color: Color, val onColor: Color) {
    BLUE("Błękit", Color(0xFF4FC3F7), Color(0xFF00222E)),
    GREEN("Zieleń", Color(0xFF69F0AE), Color(0xFF00291A)),
    YELLOW("Żółty", Color(0xFFFFE14A), Color(0xFF2A2400)),
    ORANGE("Pomarańcz", Color(0xFFFFB74D), Color(0xFF2B1700)),
    RED("Czerwień", Color(0xFFFF6E6E), Color(0xFF330000)),
    PURPLE("Fiolet", Color(0xFFB388FF), Color(0xFF1D0047)),
    PINK("Róż", Color(0xFFFF80AB), Color(0xFF3A0018)),
}

/** Whole-app visual style. Each one changes colours, fonts, card shapes and how big numbers are drawn. */
enum class UiStyle(val label: String, val description: String) {
    CLASSIC("Klasyczny", "Spokojny Material, jak do tej pory."),
    COCKPIT("Cockpit", "Cyfry jak na zegarach nowego auta, świecący akcent i cienkie ramki."),
    GLASS("Glass", "Półprzezroczyste karty na tle, które oddycha kolorem akcentu."),
    BIG_TYPE("Big type", "Ogromne liczby, ostre krawędzie, zero ozdób. Najczytelniejszy w trakcie jazdy."),
    RETRO("Retro VFD", "Wyświetlacz segmentowy jak w kaseciaku z 2003. Kolor świecenia to kolor akcentu."),
}

/**
 * Style tokens that Material's [ColorScheme] and [Typography] can't express.
 * Read them with [OtTheme.style]; [OtCard], [OtBackground] and [OtNumber] already do.
 */
@Immutable
class OtStyle(
    val style: UiStyle,
    val cardShape: Shape,
    val cardColor: Color,
    val cardBorder: BorderStroke?,
    val cardPadding: Dp,
    /** Background of bottom bars and docks. */
    val barColor: Color,
    /** Font of big numbers; null keeps the text font. */
    val numberFont: FontFamily?,
    val numberWeight: FontWeight,
    /** Some faces look bigger or smaller than others at the same size. */
    val numberScale: Float,
    val numberLetterSpacing: TextUnit,
    /** Numbers get a soft halo in their own colour. */
    val glow: Boolean,
    /** Big numbers are drawn as seven-segment digits. */
    val segmentDigits: Boolean,
    /** Cards get faint horizontal lines, like a vacuum-fluorescent display. */
    val scanlines: Boolean,
)

val LocalOtStyle = staticCompositionLocalOf { otStyle(UiStyle.CLASSIC, otColorScheme(Accent.BLUE, night = false)) }

object OtTheme {
    val style: OtStyle
        @Composable @ReadOnlyComposable
        get() = LocalOtStyle.current
}

/**
 * Car-friendly dark palettes: no pure white glare, strong accent for touch targets.
 * Night mode is the same red-on-black for every style – it keeps the driver's eyes adapted to the dark.
 */
fun otColorScheme(accent: Accent, night: Boolean, style: UiStyle = UiStyle.CLASSIC): ColorScheme {
    if (night) return NightScheme
    // A warm accent would clash with the warm secondary colour – use blue for secondary then.
    val warmAccent = accent == Accent.ORANGE || accent == Accent.YELLOW
    return when (style) {
        UiStyle.CLASSIC -> darkColorScheme(
            primary = accent.color,
            onPrimary = accent.onColor,
            primaryContainer = accent.color.copy(alpha = 0.25f),
            onPrimaryContainer = accent.color,
            secondary = if (warmAccent) Accent.BLUE.color else Color(0xFFFFB74D),
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
        UiStyle.COCKPIT -> darkColorScheme(
            primary = accent.color,
            onPrimary = accent.onColor,
            primaryContainer = accent.color.copy(alpha = 0.18f),
            onPrimaryContainer = accent.color,
            secondary = if (warmAccent) Accent.BLUE.color else Color(0xFFFFB547),
            onSecondary = Color(0xFF2B1700),
            background = Color(0xFF07090C),
            onBackground = Color(0xFFE9F1F7),
            surface = Color(0xFF0E1217),
            onSurface = Color(0xFFE9F1F7),
            surfaceVariant = Color(0xFF151C24),
            onSurfaceVariant = Color(0xFF75838F),
            outline = Color(0xFF1F2832),
            error = Color(0xFFFF5D73),
        )
        UiStyle.GLASS -> darkColorScheme(
            primary = accent.color,
            onPrimary = accent.onColor,
            primaryContainer = Color.White.copy(alpha = 0.16f),
            onPrimaryContainer = Color.White,
            secondary = if (warmAccent) Accent.BLUE.color else Color(0xFFFFC77A),
            onSecondary = Color(0xFF2B1700),
            background = Color(0xFF08070D),
            onBackground = Color.White,
            surface = Color(0xFF17141F),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF242030),
            onSurfaceVariant = Color(0xFFB7B1C4),
            outline = Color.White.copy(alpha = 0.18f),
            error = Color(0xFFFF8A8A),
        )
        UiStyle.BIG_TYPE -> darkColorScheme(
            primary = accent.color,
            onPrimary = accent.onColor,
            primaryContainer = accent.color,
            onPrimaryContainer = accent.onColor,
            secondary = if (accent == Accent.YELLOW) Color(0xFFF2F0EA) else Color(0xFFFFE14A),
            onSecondary = Color(0xFF0D0D0D),
            background = Color(0xFF0D0D0D),
            onBackground = Color(0xFFF2F0EA),
            surface = Color(0xFF0D0D0D),
            onSurface = Color(0xFFF2F0EA),
            surfaceVariant = Color(0xFF1E1E1C),
            onSurfaceVariant = Color(0xFFA3A39A),
            outline = Color(0xFF3A3A36),
            error = Color(0xFFFF4B33),
            onError = Color(0xFF0D0D0D),
        )
        UiStyle.RETRO -> {
            // The whole display glows in the accent colour, like a real VFD.
            val vfd = accent.color
            darkColorScheme(
                primary = vfd,
                onPrimary = Color(0xFF020605),
                primaryContainer = vfd.copy(alpha = 0.15f),
                onPrimaryContainer = vfd,
                secondary = if (warmAccent || accent == Accent.RED) Color(0xFF6DFFE1) else Color(0xFFFFB25B),
                onSecondary = Color(0xFF020605),
                background = Color(0xFF0D0E10),
                onBackground = vfd,
                surface = Color(0xFF060A09),
                onSurface = vfd,
                surfaceVariant = Color(0xFF0C1412),
                onSurfaceVariant = vfd.copy(alpha = 0.62f),
                outline = Color(0xFF1E2A27),
                error = Color(0xFFFF5D5D),
            )
        }
    }
}

private val NightScheme = darkColorScheme(
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

fun otStyle(style: UiStyle, scheme: ColorScheme): OtStyle = when (style) {
    UiStyle.CLASSIC -> OtStyle(
        style, RoundedCornerShape(28.dp), scheme.surface, null, 24.dp, scheme.surface,
        numberFont = null, numberWeight = FontWeight.Light, numberScale = 1f, numberLetterSpacing = TextUnit.Unspecified,
        glow = false, segmentDigits = false, scanlines = false,
    )
    UiStyle.COCKPIT -> OtStyle(
        style, RoundedCornerShape(20.dp), scheme.surface, BorderStroke(1.dp, scheme.outline), 22.dp, scheme.background,
        numberFont = JetBrainsMono, numberWeight = FontWeight.Light, numberScale = 0.92f, numberLetterSpacing = (-0.04).em,
        glow = true, segmentDigits = false, scanlines = false,
    )
    UiStyle.GLASS -> OtStyle(
        style, RoundedCornerShape(28.dp), Color.White.copy(alpha = 0.08f), BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), 24.dp,
        Color.White.copy(alpha = 0.07f),
        numberFont = Outfit, numberWeight = FontWeight.ExtraLight, numberScale = 1.05f, numberLetterSpacing = (-0.04).em,
        glow = false, segmentDigits = false, scanlines = false,
    )
    UiStyle.BIG_TYPE -> OtStyle(
        style, RectangleShape, scheme.background, BorderStroke(3.dp, scheme.onSurface), 18.dp, scheme.background,
        numberFont = ArchivoBlack, numberWeight = FontWeight.Black, numberScale = 0.86f, numberLetterSpacing = (-0.04).em,
        glow = false, segmentDigits = false, scanlines = false,
    )
    UiStyle.RETRO -> OtStyle(
        style, RoundedCornerShape(10.dp), scheme.surface, BorderStroke(1.dp, scheme.outline), 20.dp, Color(0xFF131518),
        numberFont = Vt323, numberWeight = FontWeight.Normal, numberScale = 1.25f, numberLetterSpacing = 0.sp,
        glow = true, segmentDigits = true, scanlines = true,
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

private fun TextStyle.with(
    family: FontFamily,
    scale: Float = 1f,
    weight: FontWeight? = null,
    spacing: TextUnit = TextUnit.Unspecified,
) = copy(
    fontFamily = family,
    fontSize = fontSize * scale,
    lineHeight = lineHeight * scale,
    fontWeight = weight ?: fontWeight,
    letterSpacing = if (spacing.isSpecified) spacing else letterSpacing,
)

private fun Typography.roles(
    display: (TextStyle) -> TextStyle,
    headline: (TextStyle) -> TextStyle,
    title: (TextStyle) -> TextStyle,
    body: (TextStyle) -> TextStyle,
    label: (TextStyle) -> TextStyle,
) = copy(
    displayLarge = display(displayLarge), displayMedium = display(displayMedium), displaySmall = display(displaySmall),
    headlineLarge = headline(headlineLarge), headlineMedium = headline(headlineMedium), headlineSmall = headline(headlineSmall),
    titleLarge = title(titleLarge), titleMedium = title(titleMedium), titleSmall = title(titleSmall),
    bodyLarge = body(bodyLarge), bodyMedium = body(bodyMedium), bodySmall = body(bodySmall),
    labelLarge = label(labelLarge), labelMedium = label(labelMedium), labelSmall = label(labelSmall),
)

fun otTypography(style: UiStyle): Typography = when (style) {
    UiStyle.CLASSIC -> OtTypography
    UiStyle.COCKPIT -> OtTypography.roles(
        display = { it.with(JetBrainsMono, 0.92f, FontWeight.Light, (-0.03).em) },
        headline = { it.with(SpaceGrotesk) },
        title = { it.with(SpaceGrotesk, weight = FontWeight.Medium) },
        body = { it.with(SpaceGrotesk) },
        label = { it.with(SpaceGrotesk, weight = FontWeight.Medium) },
    )
    UiStyle.GLASS -> OtTypography.roles(
        display = { it.with(Outfit, 1.05f, FontWeight.ExtraLight, (-0.03).em) },
        headline = { it.with(Outfit, weight = FontWeight.SemiBold) },
        title = { it.with(Outfit, weight = FontWeight.Medium) },
        body = { it.with(Outfit, weight = FontWeight.Light) },
        label = { it.with(Outfit) },
    )
    UiStyle.BIG_TYPE -> OtTypography.roles(
        display = { it.with(ArchivoBlack, 0.86f, FontWeight.Black, (-0.03).em) },
        headline = { it.with(ArchivoBlack, 0.95f, FontWeight.Black, (-0.01).em) },
        title = { it.with(IbmPlexMono, 0.9f, FontWeight.SemiBold) },
        body = { it.with(IbmPlexMono, 0.88f) },
        label = { it.with(IbmPlexMono, 0.85f, FontWeight.SemiBold, 0.04.em) },
    )
    UiStyle.RETRO -> OtTypography.roles(
        display = { it.with(Vt323, 1.3f, FontWeight.Normal, 0.sp) },
        headline = { it.with(Vt323, 1.3f, FontWeight.Normal, 0.04.em) },
        title = { it.with(Vt323, 1.3f, FontWeight.Normal, 0.03.em) },
        body = { it.with(IbmPlexMono, 0.88f) },
        label = { it.with(IbmPlexMono, 0.85f, FontWeight.SemiBold, 0.06.em) },
    )
}

private fun otShapes(style: UiStyle): Shapes = when (style) {
    UiStyle.BIG_TYPE -> RoundedCornerShape(0.dp).let { Shapes(it, it, it, it, it) }
    UiStyle.RETRO -> Shapes(
        RoundedCornerShape(4.dp), RoundedCornerShape(6.dp), RoundedCornerShape(8.dp),
        RoundedCornerShape(10.dp), RoundedCornerShape(12.dp),
    )
    else -> Shapes()
}

@Composable
fun OpentaimentTheme(
    accent: Accent = Accent.BLUE,
    night: Boolean = false,
    style: UiStyle = UiStyle.CLASSIC,
    content: @Composable () -> Unit,
) {
    val scheme = remember(accent, night, style) { otColorScheme(accent, night, style) }
    val tokens = remember(style, scheme) { otStyle(style, scheme) }
    CompositionLocalProvider(LocalOtStyle provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = remember(style) { otTypography(style) },
            shapes = remember(style) { otShapes(style) },
            content = content,
        )
    }
}
