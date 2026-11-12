package app.opentaiment.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Drawing pieces shared by the per-style layouts (Cockpit gauge, Retro bar graphs and spectrum…).
 * They only take plain numbers, so feature modules feed them whatever they measure.
 */

private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

/**
 * A 270° dial: a thick value arc with a soft glow, a thin outer arc (e.g. rpm) and labelled ticks.
 * Leave the middle free – put the readout on top of it in a Box.
 */
@Composable
fun ArcGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    outerFraction: Float? = null,
    outerColor: Color = MaterialTheme.colorScheme.secondary,
    max: Int = 200,
    labelStep: Int = 40,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.outline
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = label, fontSize = 13.sp, fontFamily = OtTheme.style.numberFont ?: JetBrainsMono)
    Canvas(modifier) {
        val d = min(size.width, size.height)
        val stroke = d * 0.04f
        val outerR = d / 2f - stroke
        val r = outerR - stroke * 1.4f
        val c = Offset(size.width / 2f, size.height / 2f)
        fun arc(radius: Float, sweep: Float, col: Color, width: Float) = drawArc(
            col, ARC_START, sweep, useCenter = false,
            topLeft = Offset(c.x - radius, c.y - radius), size = Size(radius * 2, radius * 2),
            style = Stroke(width, cap = StrokeCap.Round),
        )
        if (outerFraction != null) {
            arc(outerR, ARC_SWEEP, track, stroke * 0.3f)
            arc(outerR, ARC_SWEEP * outerFraction.coerceIn(0f, 1f), outerColor, stroke * 0.3f)
        }
        arc(r, ARC_SWEEP, track, stroke)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            arc(r, ARC_SWEEP * f, color.copy(alpha = 0.22f), stroke * 2.2f)
            arc(r, ARC_SWEEP * f, color, stroke)
        }
        val steps = max / 10
        for (i in 0..steps) {
            val v = i * 10
            val big = v % labelStep == 0
            val a = Math.toRadians((ARC_START + ARC_SWEEP * v / max).toDouble())
            val r1 = r - stroke * (if (big) 1.9f else 1.5f)
            val r2 = r - stroke * 1.0f
            drawLine(
                if (big) tick.copy(alpha = 1f) else tick.copy(alpha = 0.6f),
                Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()),
                strokeWidth = if (big) 3f else 2f,
            )
            if (big) {
                val lr = r - stroke * 3.1f
                val text = measurer.measure(v.toString(), labelStyle)
                drawText(
                    text,
                    topLeft = Offset(
                        c.x + lr * cos(a).toFloat() - text.size.width / 2f,
                        c.y + lr * sin(a).toFloat() - text.size.height / 2f,
                    ),
                )
            }
        }
    }
}

/** Colour for an eco-score: green when calm, amber when so-so, red when harsh; accent when unknown. */
@Composable
fun ecoTint(score: Int?): Color = when {
    score == null -> MaterialTheme.colorScheme.primary
    score >= 75 -> Color(0xFF4BE39A)
    score >= 50 -> Color(0xFFFFC34D)
    else -> MaterialTheme.colorScheme.error
}

/** A row of small blocks lit up to [fraction], like an LED bar graph. Blocks from [hotFrom] on use [hotColor]. */
@Composable
fun SegmentBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    count: Int = 24,
    color: Color = MaterialTheme.colorScheme.secondary,
    hotFrom: Float = 0.75f,
    hotColor: Color = MaterialTheme.colorScheme.error,
    height: Dp = 14.dp,
) {
    val off = MaterialTheme.colorScheme.surfaceVariant
    val lit = (fraction.coerceIn(0f, 1f) * count).roundToInt()
    val hot = (hotFrom * count).roundToInt()
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { i ->
            val c = when {
                i >= lit -> off
                i >= hot -> hotColor
                else -> color
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c))
        }
    }
}

/** Ten squares for a 0–100 score: filled ones lit, the rest outlined. Big type and Retro use it for eco. */
@Composable
fun BlockMeter(
    score: Int?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    height: Dp = 26.dp,
    outlined: Boolean = true,
) {
    val lit = ((score ?: 0) / 10f).roundToInt()
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(10) { i ->
            val on = i < lit
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (outlined) Modifier.border(3.dp, color) else Modifier)
                    .background(if (on) color else if (outlined) Color.Transparent else color.copy(alpha = 0.08f)),
            )
        }
    }
}

/**
 * A bouncing spectrum analyzer in VFD blocks. It is decorative: the radio stream is not analysed,
 * the bars just move while something plays and rest when paused.
 */
@Composable
fun SpectrumBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    bands: Int = 18,
    levels: Int = 10,
    color: Color = MaterialTheme.colorScheme.primary,
    peakColor: Color = MaterialTheme.colorScheme.secondary,
) {
    val t by rememberInfiniteTransition(label = "spectrum").animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat() * 20,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val off = color.copy(alpha = 0.08f)
    Canvas(modifier) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (bands - 1)) / bands
        val h = (size.height - gap * (levels - 1)) / levels
        val time = if (playing) t else 0.9f
        for (b in 0 until bands) {
            val level = if (!playing) 0.12f else {
                val v = 0.35f + 0.45f * abs(sin(time * 2.1f + b * 0.7f)) * abs(cos(time * 0.9f + b * 0.31f)) +
                    0.2f * abs(sin(time * 5.3f + b * 1.7f))
                (v * (1f - b / (bands * 2.4f))).coerceIn(0f, 1f)
            }
            val lit = (level * levels).roundToInt()
            for (l in 0 until levels) {
                val c = when {
                    l >= lit -> off
                    l == lit - 1 && lit > 7 -> peakColor
                    else -> color
                }
                drawRect(c, Offset(b * (w + gap), size.height - (l + 1) * h - l * gap), Size(w, h))
            }
        }
    }
}

/** Short station mark for cover-art squares: "Radio 357" → "357", "RMF FM" → "RMF". */
fun stationMark(name: String?): String {
    if (name.isNullOrBlank()) return "FM"
    val skip = setOf("radio", "polskie", "the")
    val word = name.split(' ', '-', '–').map { it.trim() }.firstOrNull { it.isNotEmpty() && it.lowercase() !in skip }
        ?: name.trim()
    return word.take(5).uppercase()
}


/** A chunky plastic button with a little LED, like on a 2000s head unit (Retro style). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HardwareKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val led = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.shadow(6.dp, shape).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2B2E33), Color(0xFF17191C))))
            .border(1.dp, Color(0xFF3A3E44), shape)
            .combinedClickable(onClickLabel = label, onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (active) led else Color(0xFFB9C0C7))
            Spacer(Modifier.height(2.dp))
        }
        Text(
            label.uppercase(), style = textStyle, color = Color(0xFFB9C0C7),
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(5.dp))
        Box(Modifier.size(20.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(if (active) led else Color(0xFF26282C)))
    }
}
