package app.opentaiment.feature.vehicle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.data.FuelSource
import app.opentaiment.feature.vehicle.data.HistoryPoint
import app.opentaiment.feature.vehicle.data.VehicleStats
import kotlin.math.max

private const val RPM_SCALE = 7000.0
private const val RPM_RED_ZONE = 5000.0

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun BigValue(value: String, unit: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.displayMedium, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(
            unit, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun SpeedCard(stats: VehicleStats, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        CardTitle("Prędkość")
        BigValue(stats.speedKmh.fmt(), "km/h")
        val rpm = stats.rpm
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Obroty", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${rpm.fmt()} obr/min", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Bar(
            ((rpm ?: 0.0) / RPM_SCALE).toFloat(),
            if ((rpm ?: 0.0) >= RPM_RED_ZONE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        HistoryChart(stats.history, Modifier.fillMaxWidth().weight(1f))
        Text(
            "Prędkość i moc – ostatnia minuta",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryChart(points: List<HistoryPoint>, modifier: Modifier = Modifier) {
    val speedColor = MaterialTheme.colorScheme.primary
    val powerColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        if (size.height <= 0f) return@Canvas
        for (i in 1..3) {
            val y = size.height * i / 4
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (points.size < 2) return@Canvas
        val end = points.last().timeMs
        val start = end - 60_000
        fun x(t: Long) = ((t - start) / 60_000f) * size.width
        val maxSpeed = max(120.0, points.maxOf { it.speedKmh })
        val maxPower = max(60.0, points.maxOf { it.powerKw })

        fun line(value: (HistoryPoint) -> Double, maxValue: Double): Path = Path().apply {
            points.forEachIndexed { i, p ->
                val px = x(p.timeMs)
                val py = size.height - (value(p) / maxValue).toFloat() * size.height
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }

        val speedPath = line({ it.speedKmh }, maxSpeed)
        val fill = Path().apply {
            addPath(speedPath)
            lineTo(x(points.last().timeMs), size.height)
            lineTo(x(points.first().timeMs), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(speedColor.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(speedPath, speedColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawPath(line({ it.powerKw }, maxPower), powerColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

@Composable
fun ConsumptionCard(stats: VehicleStats, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        CardTitle("Spalanie")
        if (stats.instantLPer100Km != null) {
            BigValue(stats.instantLPer100Km.fmt(1), "l/100 km")
        } else {
            BigValue(stats.fuelRateLh.fmt(1), "l/h")
        }
        MetricRow("Średnio na trasie", "${stats.avgLPer100Km.fmt(1)} l/100 km")
        MetricRow("Chwilowo", "${stats.fuelRateLh.fmt(2)} l/h")
        Spacer(Modifier.weight(1f))
        stats.fuelLevelPct?.let {
            MetricRow("Poziom paliwa", "${it.fmt()} %")
            Bar((it / 100).toFloat(), if (it < 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
        }
        SourceNote(stats.fuelSource.takeIf { it != FuelSource.NONE }?.let { "Dane ${it.label}" } ?: FuelSource.NONE.label)
    }
}

@Composable
fun PerformanceCard(stats: VehicleStats, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        CardTitle("Moc i przyspieszenie")
        BigValue(stats.powerKw?.let(::kwToHp).fmt(), "KM")
        MetricRow("Moc teraz", "${stats.powerKw.fmt()} kW")
        MetricRow("Moc szczytowa", "${kwToHp(stats.peakPowerKw).fmt()} KM")
        MetricRow("Moment obrotowy", "${stats.torqueNm.fmt()} Nm")
        MetricRow(
            "Przyspieszenie",
            stats.accelMs2?.let { "${it.fmt(1)} m/s² · ${(it / 9.81).fmt(2)} g" } ?: NO_VALUE,
        )
        MetricRow("Max przyspieszenie", "${(stats.maxAccelMs2 / 9.81).fmt(2)} g")
        Spacer(Modifier.weight(1f))
        SourceNote("Moc i moment szacowane z prędkości i masy auta")
    }
}

@Composable
fun SprintCard(stats: VehicleStats, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        CardTitle("Sprinty")
        Spacer(Modifier.height(4.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            stats.sprints.forEach { sprint ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sprint.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Rekord: ${sprint.bestSec?.let { "${it.fmt(1)} s" } ?: NO_VALUE}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val running = sprint.runningSec
                    Text(
                        text = (running ?: sprint.lastSec)?.let { "${it.fmt(1)} s" } ?: NO_VALUE,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        color = if (running != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        SourceNote("Startuje sam, gdy ruszasz z miejsca / mijasz 80 km/h")
    }
}

@Composable
fun EngineCard(stats: VehicleStats, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        CardTitle("Silnik")
        Spacer(Modifier.height(8.dp))
        val metrics = listOf(
            "Płyn chłodniczy" to "${stats.coolantTempC.fmt()} °C",
            "Olej" to "${stats.oilTempC.fmt()} °C",
            "Powietrze w dolocie" to "${stats.intakeTempC.fmt()} °C",
            "Na zewnątrz" to "${stats.ambientTempC.fmt()} °C",
            "Przepustnica" to "${stats.throttlePct.fmt()} %",
            "Obciążenie" to "${stats.engineLoadPct.fmt()} %",
            "Akumulator" to "${stats.voltage.fmt(1)} V",
            "Max obroty" to "${stats.trip.maxRpm.fmt()}",
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            metrics.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) -> Metric(label, value, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun TripCard(stats: VehicleStats, modifier: Modifier = Modifier) {
    val trip = stats.trip
    OtCard(modifier) {
        CardTitle("Trasa")
        BigValue(trip.distanceKm.fmt(1), "km")
        MetricRow("Czas jazdy", formatDuration(trip.durationMs))
        MetricRow("Średnia prędkość", "${trip.avgSpeedKmh.fmt()} km/h")
        MetricRow("Max prędkość", "${trip.maxSpeedKmh.fmt()} km/h")
        MetricRow("Spalone paliwo", "${trip.fuelUsedL.fmt(2)} l · ${trip.fuelCost.fmt(2)} zł")
        MetricRow("Eco-score", stats.eco?.let { "${it.score}/100" } ?: "zbieram dane…")
    }
}

@Composable
private fun ColumnScope.SourceNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
