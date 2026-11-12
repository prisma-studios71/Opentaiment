package app.opentaiment.feature.vehicle.ui

import app.opentaiment.core.ui.OtNumber
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.race.RaceState
import kotlin.math.min

class RaceActions(
    val onBack: (() -> Unit)?,
    val hasGpsPermission: () -> Boolean,
    val onStartGps: () -> Unit,
    val onStopGps: () -> Unit,
    val onSetFinishLine: () -> Unit,
    val onReset: () -> Unit,
)

private val gap = 20.dp

/** Track-day toys: g-meter, GPS lap timer, sprint and ¼ mile times. */
@Composable
fun RaceScreen(race: RaceState, stats: VehicleStats, actions: RaceActions, modifier: Modifier = Modifier) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) actions.onStartGps()
    }
    val startGps = {
        if (actions.hasGpsPermission()) actions.onStartGps() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Column(modifier) {
        ScreenHeader("Wyścig", connection = null, onBack = actions.onBack) {
            if (race.gpsActive) {
                HeaderButton(Icons.Rounded.Flag, "Meta tutaj", actions.onSetFinishLine)
                HeaderButton(Icons.Rounded.RestartAlt, "Reset", actions.onReset)
                HeaderButton(Icons.Rounded.GpsOff, "Stop GPS", actions.onStopGps, primary = true)
            } else {
                HeaderButton(Icons.Rounded.RestartAlt, "Reset", actions.onReset)
                HeaderButton(Icons.Rounded.GpsFixed, "Włącz GPS", startGps, primary = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= maxHeight && maxWidth >= NarrowWidth) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    GMeterCard(race, Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        LapsCard(race, Modifier.weight(1.1f).fillMaxWidth())
                        TimesCard(stats, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                    GMeterCard(race, Modifier.fillMaxWidth().height(460.dp))
                    LapsCard(race, Modifier.fillMaxWidth().height(320.dp))
                    TimesCard(stats, Modifier.fillMaxWidth().height(300.dp))
                }
            }
        }
    }
}

@Composable
private fun GMeterCard(race: RaceState, modifier: Modifier) {
    val ring = MaterialTheme.colorScheme.outline
    val trail = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.secondary
    OtCard(modifier) {
        Text("G-meter", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Canvas(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true).align(Alignment.CenterHorizontally).padding(8.dp)) {
            val r = min(size.width, size.height) / 2
            val c = Offset(size.width / 2, size.height / 2)
            // Rings at 0.5 g and 1.0 g; 1.2 g fills the circle.
            listOf(0.5f, 1.0f).forEach { g -> drawCircle(ring, radius = r * g / 1.2f, center = c, style = Stroke(2f)) }
            drawLine(ring, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
            drawLine(ring, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
            fun pos(lat: Double, lon: Double) = Offset(
                c.x + (lat / 1.2 * r).toFloat().coerceIn(-r, r),
                c.y - (lon / 1.2 * r).toFloat().coerceIn(-r, r), // accelerating = up
            )
            race.trail.forEachIndexed { i, p ->
                drawCircle(trail.copy(alpha = 0.15f + 0.6f * i / race.trail.size), radius = 6f, center = pos(p.lateral, p.longitudinal))
            }
            drawCircle(dot, radius = 16f, center = pos(race.g.lateral, race.g.longitudinal))
        }
        Text("Maksymalne przeciążenia", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth()) {
            GValue("w zakręcie", race.maxLateralG, Modifier.weight(1f))
            GValue("ruszanie", race.maxAccelG, Modifier.weight(1f))
            GValue("hamowanie", race.maxBrakeG, Modifier.weight(1f))
        }
        if (!race.gpsActive) {
            Text(
                "Włącz GPS, żeby mierzyć siły w zakrętach i okrążenia.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GValue(label: String, g: Double, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${g.fmt(2)} g", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun LapsCard(race: RaceState, modifier: Modifier) {
    OtCard(modifier) {
        Text("Okrążenia", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        when {
            !race.hasFinishLine -> Text(
                "Stań na linii startu/mety i kliknij „Meta tutaj”. Każdy przejazd obok niej to nowe okrążenie.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> {
                OtNumber(
                    race.currentLapSec?.let { formatLap(it, decimals = 1) } ?: "czekam na start…",
                    56.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                MetricLine("Najlepsze", race.bestLapSec?.let { formatLap(it) } ?: NO_VALUE)
                race.laps.withIndex().toList().takeLast(4).reversed().forEach { (i, t) ->
                    MetricLine("Okrążenie ${i + 1}", formatLap(t))
                }
            }
        }
    }
}

@Composable
private fun TimesCard(stats: VehicleStats, modifier: Modifier) {
    OtCard(modifier) {
        Text("Czasy (z OBD)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        stats.sprints.forEach { s ->
            MetricLine(s.label, listOfNotNull(s.lastSec?.let { "${it.fmt(1)} s" }, s.bestSec?.let { "rekord ${it.fmt(1)} s" }).joinToString(" · ").ifEmpty { NO_VALUE })
        }
        val q = stats.quarterMile
        MetricLine(
            "¼ mili (402 m)",
            q?.runningSec?.let { "${it.fmt(1)} s · ${q.runningDistanceM.fmt()} m" }
                ?: listOfNotNull(
                    q?.lastSec?.let { "${it.fmt(2)} s @ ${q.lastTrapKmh.fmt()} km/h" },
                    q?.bestSec?.let { "rekord ${it.fmt(2)} s" },
                ).joinToString(" · ").ifEmpty { NO_VALUE },
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Mierz tylko na zamkniętym torze. Na drodze publicznej obowiązują przepisy.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** 81.733 -> "1:21,733", 37.2 -> "37,2 s" (with decimals = 1). */
internal fun formatLap(sec: Double, decimals: Int = 3): String {
    val m = (sec / 60).toInt()
    val s = sec - m * 60
    val pl = java.util.Locale("pl")
    return if (m > 0) {
        String.format(pl, "%d:%0${decimals + 3}.${decimals}f", m, s)
    } else {
        String.format(pl, "%.${decimals}f s", s)
    }
}
