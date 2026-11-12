package app.opentaiment.feature.vehicle.ui

import app.opentaiment.core.ui.OtNumber
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.data.FuelType
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import kotlin.math.cos
import kotlin.math.sin

/** One dial: range, where the red zone starts and how to print the value. */
data class GaugeSpec(
    val title: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val redFrom: Double?,
    val decimals: Int = 0,
    /** Below this the dial shows blue ("still cold"). */
    val blueUntil: Double? = null,
)

internal fun gaugeSpecs(profile: VehicleProfile): List<GaugeSpec> {
    val diesel = profile.fuelType == FuelType.DIESEL
    val redline = if (diesel) 4500.0 else 6500.0
    return listOf(
        GaugeSpec("Obroty", "obr/min", 0.0, redline + 1000, redFrom = redline),
        GaugeSpec("Prędkość", "km/h", 0.0, 240.0, redFrom = null),
        GaugeSpec("Doładowanie", "bar", -1.0, 2.0, redFrom = 1.6, decimals = 2),
        GaugeSpec("Płyn chłodniczy", "°C", 40.0, 130.0, redFrom = 110.0, blueUntil = 70.0),
        GaugeSpec("Olej", "°C", 40.0, 150.0, redFrom = 130.0, blueUntil = 70.0),
        GaugeSpec("Akumulator", "V", 10.0, 16.0, redFrom = 15.0, decimals = 1, blueUntil = 12.0),
    )
}

internal fun gaugeValues(stats: VehicleStats): List<Double?> = listOf(
    stats.rpm,
    stats.speedKmh,
    stats.boostKpa?.let { it / 100.0 },
    stats.coolantTempC,
    stats.oilTempC,
    stats.voltage,
)

/** "Zegary": analog dials like a sports dashboard – rpm, speed, boost, temperatures, battery. */
@Composable
fun GaugesScreen(
    connection: ConnectionState,
    stats: VehicleStats,
    profile: VehicleProfile,
    onConnect: () -> Unit,
    onDemo: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ScreenHeader("Zegary", connection = connection, onBack = onBack) {}
        Spacer(Modifier.height(16.dp))
        if (connection !is ConnectionState.Connected || !stats.hasData) {
            ConnectPrompt(connection, onConnect, onDemo, Modifier.fillMaxSize())
            return
        }
        val specs = gaugeSpecs(profile)
        val values = gaugeValues(stats)
        // Hide dials the car can't feed (e.g. no oil temperature sensor), keep at least rpm + speed.
        val shown = specs.indices.filter { it < 2 || values[it] != null }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = when {
                maxWidth < NarrowWidth -> 2
                shown.size <= 4 -> shown.size
                else -> 3
            }
            val rows = shown.chunked(columns)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                rows.forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { i -> Gauge(specs[i], values[i], Modifier.weight(1f).fillMaxHeight()) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private const val SWEEP = 240f
private const val START = 150f

@Composable
fun Gauge(spec: GaugeSpec, value: Double?, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val red = MaterialTheme.colorScheme.error
    val blue = Color(0xFF42A5F5)
    val needleColor = MaterialTheme.colorScheme.onSurface
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    OtCard(modifier) {
        Text(spec.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * 0.07f
                    val inset = stroke / 2 + 4f
                    val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
                    val topLeft = Offset(inset, inset)
                    fun frac(v: Double) = ((v - spec.min) / (spec.max - spec.min)).toFloat().coerceIn(0f, 1f)
                    drawArc(track, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    spec.blueUntil?.let { drawArc(blue.copy(alpha = 0.5f), START, SWEEP * frac(it), false, topLeft, arcSize, style = Stroke(stroke)) }
                    spec.redFrom?.let {
                        val f = frac(it)
                        drawArc(red.copy(alpha = 0.6f), START + SWEEP * f, SWEEP * (1 - f), false, topLeft, arcSize, style = Stroke(stroke))
                    }
                    if (value != null) {
                        val f = frac(value)
                        val color = when {
                            spec.redFrom != null && value >= spec.redFrom -> red
                            spec.blueUntil != null && value < spec.blueUntil -> blue
                            else -> accent
                        }
                        drawArc(color, START, SWEEP * f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = arcSize.width / 2
                    for (i in 0..10) {
                        val a = Math.toRadians((START + SWEEP * i / 10).toDouble())
                        val outer = radius - stroke
                        val inner = outer - if (i % 5 == 0) stroke * 0.9f else stroke * 0.45f
                        drawLine(
                            tick,
                            Offset(center.x + (cos(a) * inner).toFloat(), center.y + (sin(a) * inner).toFloat()),
                            Offset(center.x + (cos(a) * outer).toFloat(), center.y + (sin(a) * outer).toFloat()),
                            strokeWidth = stroke * 0.18f,
                        )
                    }
                    if (value != null) {
                        val a = Math.toRadians((START + SWEEP * frac(value)).toDouble())
                        val len = radius - stroke * 1.6f
                        drawLine(
                            needleColor, center,
                            Offset(center.x + (cos(a) * len).toFloat(), center.y + (sin(a) * len).toFloat()),
                            strokeWidth = stroke * 0.3f, cap = StrokeCap.Round,
                        )
                        drawCircle(needleColor, stroke * 0.45f, center)
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    val big = (maxWidth.value * 0.17f).coerceIn(18f, 64f)
                    val bottomGap = (maxHeight.value * 0.04f).dp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OtNumber(value.fmt(spec.decimals), big.sp)
                        Text(spec.unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(bottomGap))
                    }
                }
            }
        }
    }
}
