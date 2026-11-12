package app.opentaiment.core.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt

/**
 * A big readout (speed, consumption, time…) drawn the way the current [UiStyle] wants it:
 * a light face, a glowing monospace, a heavy poster face or seven-segment digits.
 * Use it for the headline number of a card; regular text stays a plain [Text].
 */
@Composable
fun OtNumber(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val style = OtTheme.style
    val tint = color.takeOrElse { LocalContentColor.current }
    val density = LocalDensity.current
    if (style.segmentDigits && fontSize.value >= MIN_SEGMENT_SP && SevenSegment.canDraw(text)) {
        val height = with(density) { fontSize.toDp() } * SEGMENT_HEIGHT
        SevenSegmentText(text, height, tint, modifier)
        return
    }
    val glow = if (style.glow) Shadow(tint.copy(alpha = 0.55f), blurRadius = with(density) { fontSize.toPx() } * 0.22f) else null
    Text(
        text,
        modifier,
        color = tint,
        fontSize = fontSize * style.numberScale,
        fontFamily = style.numberFont,
        fontWeight = style.numberWeight,
        letterSpacing = style.numberLetterSpacing,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum", shadow = glow),
    )
}

private const val MIN_SEGMENT_SP = 32f

/** Digit height as a share of the font size – roughly the cap height of a normal face. */
private const val SEGMENT_HEIGHT = 0.74f

/**
 * Seven-segment readout with "ghost" segments: the unlit ones stay faintly visible, like on a real VFD.
 * Shrinks to fit when the available width is too small.
 */
@Composable
fun SevenSegmentText(text: String, height: Dp, color: Color, modifier: Modifier = Modifier) {
    val units = SevenSegment.widthUnits(text)
    Spacer(
        modifier
            .semantics { contentDescription = text }
            .layout { measurable, constraints ->
                val wantH = height.roundToPx()
                val wantW = (wantH * units / SevenSegment.HEIGHT_UNITS).roundToInt()
                val k = if (constraints.hasBoundedWidth && wantW > constraints.maxWidth) constraints.maxWidth.toFloat() / wantW else 1f
                val w = (wantW * k).roundToInt()
                val h = (wantH * k).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .drawBehind { SevenSegment.draw(this, text, color) },
    )
}

internal object SevenSegment {
    /** Glyph box in design units: a digit is 56×100, drawn with a slight italic slant. */
    const val HEIGHT_UNITS = 108f
    private const val PAD = 4f
    private const val SLANT = 0.105f // tan(6°)

    private val segments = mapOf(
        'a' to floatArrayOf(8f, 0f, 40f, 9f),
        'b' to floatArrayOf(48f, 8f, 9f, 38f),
        'c' to floatArrayOf(48f, 54f, 9f, 38f),
        'd' to floatArrayOf(8f, 91f, 40f, 9f),
        'e' to floatArrayOf(-1f, 54f, 9f, 38f),
        'f' to floatArrayOf(-1f, 8f, 9f, 38f),
        'g' to floatArrayOf(8f, 45.5f, 40f, 9f),
    )
    private val digits = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
        '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
        ' ' to "", '-' to "g", '—' to "g", '–' to "g",
    )

    fun canDraw(text: String) =
        text.any { it.isDigit() } && text.all { it in digits || it == ':' || it == '.' || it == ',' }

    private fun advance(c: Char) = when (c) {
        ':' -> 22f
        '.', ',' -> 8f
        else -> 70f
    }

    fun widthUnits(text: String) = text.sumOf { advance(it).toDouble() }.toFloat() + 2 * PAD + 100f * SLANT

    fun draw(scope: DrawScope, text: String, color: Color) = with(scope) {
        val s = size.height / HEIGHT_UNITS
        val ghost = color.copy(alpha = 0.08f)
        val halo = color.copy(alpha = 0.22f)
        fun px(x: Float, y: Float) = Offset((x + (100f - y) * SLANT + PAD) * s, (y + PAD) * s)
        fun dot(x: Float, y: Float) {
            drawCircle(halo, 8f * s, px(x, y))
            drawCircle(color, 5f * s, px(x, y))
        }
        var x = 0f
        for (c in text) {
            when (c) {
                ':' -> { dot(x + 6f, 32f); dot(x + 6f, 68f) }
                '.', ',' -> dot(x - 6f, 95f)
                else -> {
                    val lit = digits[c].orEmpty()
                    segments.forEach { (name, r) ->
                        val path = Path().apply {
                            val p1 = px(x + r[0], r[1]); val p2 = px(x + r[0] + r[2], r[1])
                            val p3 = px(x + r[0] + r[2], r[1] + r[3]); val p4 = px(x + r[0], r[1] + r[3])
                            moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); lineTo(p4.x, p4.y); close()
                        }
                        if (name in lit) {
                            drawPath(path, halo, style = Stroke(width = 5f * s))
                            drawPath(path, color)
                        } else {
                            drawPath(path, ghost)
                        }
                    }
                }
            }
            x += advance(c)
        }
    }
}
