package app.opentaiment.feature.vehicle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.data.PeriodSummary
import app.opentaiment.feature.vehicle.data.TripRecord
import app.opentaiment.feature.vehicle.data.TripSummaries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TripsActions(
    val onBack: (() -> Unit)?,
    val onDelete: (Long) -> Unit,
    val onClearAll: () -> Unit,
)

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale("pl"))

@Composable
fun TripsScreen(
    trips: List<TripRecord>,
    actions: TripsActions,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var confirmClear by remember { mutableStateOf(false) }
    val week = remember(trips, now) { TripSummaries.thisWeek(trips, now, zone) }
    val month = remember(trips, now) { TripSummaries.thisMonth(trips, now, zone) }

    Column(modifier) {
        ScreenHeader("Trasy", connection = null, onBack = actions.onBack) {
            if (trips.isNotEmpty()) HeaderButton(Icons.Rounded.DeleteSweep, "Wyczyść", { confirmClear = true })
        }
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow = maxWidth < NarrowWidth
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "summary") {
                    if (narrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryCard("Ten tydzień", week, Modifier.fillMaxWidth())
                            SummaryCard("Ten miesiąc", month, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SummaryCard("Ten tydzień", week, Modifier.weight(1f))
                            SummaryCard("Ten miesiąc", month, Modifier.weight(1f))
                        }
                    }
                }
                if (trips.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Nie ma jeszcze żadnych tras. Każda jazda z podłączonym OBD zapisze się tu sama " +
                                "(po rozłączeniu, „Nowej trasie” albo 3 minutach ze zgaszonym silnikiem).",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                items(trips, key = { it.id }) { trip -> TripItem(trip, zone, onDelete = { actions.onDelete(trip.id) }) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Usunąć wszystkie trasy?") },
            text = { Text("Historii nie da się przywrócić.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    actions.onClearAll()
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun SummaryCard(title: String, s: PeriodSummary, modifier: Modifier = Modifier) {
    OtCard(modifier.height(200.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(s.distanceKm.fmt(0), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.width(8.dp))
            Text("km · ${s.trips} tras", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            Mini("Paliwo", "${s.cost.fmt(2)} zł", Modifier.weight(1f))
            Mini("Średnio", "${s.avgLPer100Km.fmt(1)} l/100", Modifier.weight(1f))
            Mini("Eco", s.avgEcoScore?.toString() ?: NO_VALUE, Modifier.weight(0.6f))
        }
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TripItem(trip: TripRecord, zone: ZoneId, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EcoBadge(trip.ecoScore)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Instant.ofEpochMilli(trip.startEpochMs).atZone(zone).format(dateFormat)
                            .replaceFirstChar { it.titlecase(Locale("pl")) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (trip.demo) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "DEMO",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    "${trip.distanceKm.fmt(1)} km · ${formatDuration(trip.durationMs)} · " +
                        "${trip.avgLPer100Km.fmt(1)} l/100 · ${trip.cost.fmt(2)} zł",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val extras = listOfNotNull(
                    "max ${trip.maxSpeedKmh.fmt()} km/h",
                    trip.best0to100Sec?.let { "0–100 ${it.fmt(1)} s" },
                    trip.quarterMileSec?.let { "¼ mili ${it.fmt(1)} s" },
                )
                Text(extras.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Usuń trasę", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EcoBadge(score: Int?) {
    val color = ecoColor(score)
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: NO_VALUE, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Text("eco", style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}
