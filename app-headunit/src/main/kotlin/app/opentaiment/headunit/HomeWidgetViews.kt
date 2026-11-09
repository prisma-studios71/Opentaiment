package app.opentaiment.headunit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.media.MediaPanel
import app.opentaiment.feature.media.NowPlaying
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.data.TripRecord
import app.opentaiment.feature.vehicle.data.TripSummaries
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.data.dtcCountLabel
import app.opentaiment.feature.weather.WeatherCard
import app.opentaiment.feature.weather.WeatherState
import app.opentaiment.headunit.tiles.HomeWidget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything widgets can show, as plain values. */
class WidgetData(
    val now: LocalDateTime,
    val weather: WeatherState,
    val nowPlaying: NowPlaying?,
    val hasMediaAccess: Boolean,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val stats: VehicleStats = VehicleStats(),
    val diagnostics: DiagnosticsState = DiagnosticsState.Idle,
    val trips: List<TripRecord> = emptyList(),
) {
    val carConnected: Boolean get() = connection is ConnectionState.Connected && stats.hasData
}

class WidgetActions(
    val onRefreshWeather: () -> Unit = {},
    val onGrantMediaAccess: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onOpenPlayer: () -> Unit = {},
    val onOpenDrive: () -> Unit = {},
    val onOpenStats: () -> Unit = {},
    val onOpenDiagnostics: () -> Unit = {},
    val onOpenTrips: () -> Unit = {},
)

private val PL = Locale("pl")
private fun Double?.f(decimals: Int = 0): String =
    if (this == null || isNaN()) "—" else String.format(PL, "%,.${decimals}f", this)

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", PL)

private const val NOT_CONNECTED = "Połącz auto w Statystykach"

@Composable
fun WidgetView(widget: HomeWidget, data: WidgetData, actions: WidgetActions, modifier: Modifier = Modifier) {
    val s = data.stats
    val car = data.carConnected
    when (widget) {
        HomeWidget.CLOCK -> ClockCard(data.now, modifier)
        HomeWidget.WEATHER -> WeatherCard(data.weather, actions.onRefreshWeather, modifier)
        HomeWidget.MUSIC -> MediaPanel(
            hasAccess = data.hasMediaAccess,
            nowPlaying = data.nowPlaying,
            onGrantAccess = actions.onGrantMediaAccess,
            onPlayPause = actions.onPlayPause,
            onNext = actions.onNext,
            onPrevious = actions.onPrevious,
            onOpenPlayer = actions.onOpenPlayer,
            modifier = modifier,
        )
        HomeWidget.SPEED -> MetricCard(
            title = "Prędkość", value = if (car) s.speedKmh.f() else "—", unit = "km/h",
            lines = listOf("Obroty" to "${s.rpm.f()} obr/min", "Przepustnica" to "${s.throttlePct.f()} %"),
            note = if (car) null else NOT_CONNECTED, onClick = actions.onOpenDrive, modifier = modifier,
        )
        HomeWidget.CONSUMPTION -> MetricCard(
            title = "Spalanie",
            value = if (!car) "—" else s.instantLPer100Km?.f(1) ?: s.fuelRateLh.f(1),
            unit = if (s.instantLPer100Km != null) "l/100 km" else "l/h",
            lines = listOf("Średnio" to "${s.avgLPer100Km.f(1)} l/100", "Paliwo w baku" to "${s.fuelLevelPct.f()} %"),
            note = if (car) null else NOT_CONNECTED, onClick = actions.onOpenStats, modifier = modifier,
        )
        HomeWidget.ECO -> MetricCard(
            title = "Eco-score", value = if (car) s.eco?.score?.toString() ?: "…" else "—", unit = "/100",
            lines = emptyList(),
            note = when {
                !car -> NOT_CONNECTED
                s.eco == null -> "Ocena pojawi się po minucie jazdy"
                else -> s.eco!!.tips.first()
            },
            valueColor = ecoColor(s.eco?.score.takeIf { car }),
            onClick = actions.onOpenDrive, modifier = modifier,
        )
        HomeWidget.TRIP -> MetricCard(
            title = "Trasa", value = if (car) s.trip.distanceKm.f(1) else "—", unit = "km",
            lines = listOf(
                "Czas" to duration(s.trip.durationMs),
                "Koszt paliwa" to "${s.trip.fuelCost.f(2)} zł",
                "Średnio" to "${s.trip.avgSpeedKmh.f()} km/h",
            ),
            note = if (car) null else NOT_CONNECTED, onClick = actions.onOpenStats, modifier = modifier,
        )
        HomeWidget.ENGINE -> MetricCard(
            title = "Silnik", value = if (car) s.coolantTempC.f() else "—", unit = "°C",
            lines = listOf(
                "Olej" to "${s.oilTempC.f()} °C",
                "Akumulator" to "${s.voltage.f(1)} V",
                "Obciążenie" to "${s.engineLoadPct.f()} %",
            ),
            note = if (car) null else NOT_CONNECTED, onClick = actions.onOpenStats, modifier = modifier,
        )
        HomeWidget.DIAGNOSTICS -> {
            val report = (data.diagnostics as? DiagnosticsState.Ready)?.report
            MetricCard(
                title = "Błędy",
                value = report?.totalCount?.let { if (it == 0) "OK" else "$it" } ?: "—",
                unit = report?.totalCount?.takeIf { it > 0 }?.let { dtcCountLabel(it).substringAfter(' ') } ?: "",
                lines = emptyList(),
                note = when {
                    report == null -> if (car) "Sprawdzam…" else NOT_CONNECTED
                    report.milOn == true -> "Check Engine świeci – stuknij po szczegóły"
                    report.totalCount == 0 -> "Żaden sterownik nie zgłasza usterek"
                    else -> "Check Engine nie świeci"
                },
                valueColor = when {
                    report == null -> MaterialTheme.colorScheme.onSurface
                    report.milOn == true -> MaterialTheme.colorScheme.error
                    report.totalCount > 0 -> MaterialTheme.colorScheme.secondary
                    else -> OkGreen
                },
                onClick = actions.onOpenDiagnostics, modifier = modifier,
            )
        }
        HomeWidget.WEEK -> {
            val w = TripSummaries.thisWeek(data.trips)
            MetricCard(
                title = "Ten tydzień", value = w.distanceKm.f(), unit = "km",
                lines = listOf(
                    "Paliwo" to "${w.cost.f(2)} zł",
                    "Średnio" to "${w.avgLPer100Km.f(1)} l/100",
                    "Eco" to (w.avgEcoScore?.toString() ?: "—"),
                ),
                note = if (w.trips == 0) "Brak zapisanych jazd w tym tygodniu" else null,
                onClick = actions.onOpenTrips, modifier = modifier,
            )
        }
    }
}

private val OkGreen = Color(0xFF66BB6A)

@Composable
private fun ecoColor(score: Int?): Color = when {
    score == null -> MaterialTheme.colorScheme.onSurface
    score >= 80 -> OkGreen
    score >= 50 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}

private fun duration(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) String.format(PL, "%d h %02d min", m / 60, m % 60) else "$m min"
}

@Composable
internal fun ClockCard(now: LocalDateTime, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val big = maxHeight > 380.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(
                    now.format(timeFormat),
                    style = MaterialTheme.typography.displayLarge,
                    fontSize = if (big) 150.sp else MaterialTheme.typography.displayLarge.fontSize,
                    maxLines = 1,
                )
                Text(
                    now.format(dateFormat).replaceFirstChar { it.titlecase(PL) },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One big number with a unit, a few detail lines and an optional note. Scales with the card size. */
@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    lines: List<Pair<String, String>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    OtCard(modifier, onClick = onClick) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val valueSize = when {
                maxHeight > 380.dp -> 120.sp
                maxHeight > 240.dp -> 64.sp
                else -> 48.sp
            }
            // Show as many detail lines as fit under the number.
            val roomForLines = ((maxHeight.value - 170) / 34).toInt().coerceIn(0, lines.size)
            Column(Modifier.fillMaxSize()) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontSize = valueSize, fontWeight = FontWeight.Light, color = valueColor, maxLines = 1)
                    if (unit.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            unit, style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = (valueSize.value * 0.18f).dp),
                        )
                    }
                }
                if (note != null) {
                    Text(
                        note, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                lines.take(roomForLines).forEach { (label, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            label, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1,
                        )
                        Text(v, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                if (roomForLines > 0) Spacer(Modifier.height(4.dp))
            }
        }
    }
}
