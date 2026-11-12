package app.opentaiment.headunit

import app.opentaiment.core.ui.OtNumber
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.player.MusicPlayer
import app.opentaiment.feature.player.PlaybackKind
import app.opentaiment.feature.vehicle.data.ReminderLevel
import app.opentaiment.feature.vehicle.tpms.TpmsProblem
import app.opentaiment.feature.vehicle.tpms.Wheel
import java.util.Locale

private val PL = Locale("pl")
private val OkGreen = Color(0xFF66BB6A)

/** Now playing on our own radio + the first favourite stations as one-tap buttons. */
@Composable
internal fun RadioWidget(data: WidgetData, actions: WidgetActions, modifier: Modifier = Modifier) {
    val p = data.player
    OtCard(modifier, onClick = actions.onOpenRadio) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val big = maxHeight > 300.dp
            val roomForStations = if (big) 6 else if (maxHeight > 200.dp) 2 else 0
            Column(Modifier.fillMaxSize()) {
                Text("Radio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(
                    p.title ?: data.favorites.firstOrNull()?.name ?: "Wybierz stację",
                    style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        p.kind == PlaybackKind.NONE -> if (data.favorites.isEmpty()) "Dodaj ulubione stacje" else "Stuknij ▶, żeby grać"
                        else -> p.error ?: p.subtitle ?: ""
                    },
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onRadioPrevious, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Poprzednia", Modifier.size(36.dp)) }
                    FilledIconButton(
                        onClick = {
                            if (p.kind == PlaybackKind.NONE) data.favorites.firstOrNull()?.let { actions.onPlayStation(it, data.favorites) } ?: actions.onOpenRadio()
                            else actions.onRadioToggle()
                        },
                        modifier = Modifier.size(64.dp),
                    ) { Icon(if (p.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p.isPlaying) "Pauza" else "Graj", Modifier.size(40.dp)) }
                    IconButton(onClick = actions.onRadioNext, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Następna", Modifier.size(36.dp)) }
                }
                if (roomForStations > 0 && data.favorites.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    data.favorites.take(roomForStations).chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { station ->
                                val playing = p.currentId == MusicPlayer.RADIO_PREFIX + station.id
                                Text(
                                    station.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (playing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                        .background(if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                                        .clickable { actions.onPlayStation(station, data.favorites) }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun NavigationWidget(data: WidgetData, actions: WidgetActions, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        Text("Nawigacja", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NavButton(Icons.Rounded.Home, "Do domu", data.homeAddress.ifBlank { "Ustaw adres w Ustawieniach" }, actions.onNavigateHome, Modifier.weight(1f))
            NavButton(Icons.Rounded.Work, "Do pracy", data.workAddress.ifBlank { "Ustaw adres w Ustawieniach" }, actions.onNavigateWork, Modifier.weight(1f))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NavButton(Icons.Rounded.LocalGasStation, "Paliwo", null, { actions.onSearchNearby("stacja paliw") }, Modifier.weight(1f).fillMaxHeight())
                NavButton(Icons.Rounded.Navigation, "Mapa", null, actions.onOpenNavigation, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, label: String, sub: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Four pressures in a car-shaped grid. */
@Composable
internal fun TpmsWidget(data: WidgetData, actions: WidgetActions, modifier: Modifier = Modifier) {
    val t = data.tpms
    OtCard(modifier, onClick = actions.onOpenTpms) {
        Text("Opony", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (!data.tpmsEnabled) {
            Spacer(Modifier.weight(1f))
            Text("Czujniki TPMS wyłączone", style = MaterialTheme.typography.titleLarge)
            Text("Stuknij, żeby włączyć", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.weight(1f))
            return@OtCard
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Wheel.FRONT_LEFT to Wheel.FRONT_RIGHT, Wheel.REAR_LEFT to Wheel.REAR_RIGHT).forEach { (l, r) ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(l, r).forEach { w ->
                        val status = t.wheels.first { it.wheel == w }
                        val reading = status.reading
                        val bad = status.problems.any { it != TpmsProblem.LOW_BATTERY && it != TpmsProblem.STALE }
                        val color = when {
                            reading == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            bad -> MaterialTheme.colorScheme.error
                            status.problems.isNotEmpty() -> MaterialTheme.colorScheme.secondary
                            else -> OkGreen
                        }
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.14f)).padding(10.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            OtNumber(reading?.pressureBar?.let { String.format(PL, "%.1f", it) } ?: "—", 30.sp, color = color)
                            Text(w.short, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ServiceWidget(data: WidgetData, actions: WidgetActions, modifier: Modifier = Modifier) {
    OtCard(modifier, onClick = actions.onOpenService) {
        Text("Serwis", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        val list = data.reminders.filter { it.kmLeft != null || it.daysLeft != null }
        if (list.isEmpty()) {
            Spacer(Modifier.weight(1f))
            Text("Ustaw, kiedy był olej i przegląd", style = MaterialTheme.typography.titleLarge)
            Text("Przypomnę, zanim minie termin", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            return@OtCard
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rows = ((maxHeight.value - 10) / 64).toInt().coerceIn(1, 5)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.take(rows).forEach { st ->
                    val color = when (st.level) {
                        ReminderLevel.DUE -> MaterialTheme.colorScheme.error
                        ReminderLevel.SOON -> MaterialTheme.colorScheme.secondary
                        ReminderLevel.OK -> OkGreen
                    }
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(st.reminder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(st.label, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
