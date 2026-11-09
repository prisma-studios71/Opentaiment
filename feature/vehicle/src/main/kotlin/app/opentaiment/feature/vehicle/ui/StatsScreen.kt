package app.opentaiment.feature.vehicle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.data.FuelType
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import kotlin.math.roundToInt

class StatsActions(
    val onBack: () -> Unit,
    val connect: ObdConnectActions,
    val onDisconnect: () -> Unit,
    val onResetTrip: () -> Unit,
    val onProfileChange: (VehicleProfile) -> Unit,
)

private val gap = 20.dp

@Composable
fun StatsScreen(
    connection: ConnectionState,
    stats: VehicleStats,
    profile: VehicleProfile,
    actions: StatsActions,
    modifier: Modifier = Modifier,
) {
    var showProfile by remember { mutableStateOf(false) }
    val onConnectClick = rememberObdConnector(actions.connect)

    Column(modifier) {
        Header(connection, actions, onConnectClick, onProfile = { showProfile = true })
        Spacer(Modifier.height(16.dp))
        if (connection is ConnectionState.Connected && stats.hasData) {
            Dashboard(stats, Modifier.fillMaxSize())
        } else {
            ConnectPrompt(connection, onConnectClick, actions.connect.onDemo, Modifier.fillMaxSize())
        }
    }

    if (showProfile) {
        ProfileDialog(
            profile = profile,
            onSave = {
                showProfile = false
                actions.onProfileChange(it)
            },
            onDismiss = { showProfile = false },
        )
    }
}

@Composable
private fun Header(
    connection: ConnectionState,
    actions: StatsActions,
    onConnect: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onBack, modifier = Modifier.size(72.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Wróć", Modifier.size(40.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text("Statystyki", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(20.dp))
        StatusChip(connection)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeaderButton(Icons.Rounded.Tune, "Auto", onProfile)
            if (connection is ConnectionState.Connected) {
                HeaderButton(Icons.Rounded.RestartAlt, "Nowa trasa", actions.onResetTrip)
                HeaderButton(Icons.Rounded.LinkOff, "Rozłącz", actions.onDisconnect, primary = true)
            } else {
                HeaderButton(Icons.Rounded.PlayArrow, "Demo", actions.connect.onDemo)
                HeaderButton(Icons.Rounded.Bluetooth, "Połącz OBD", onConnect, primary = true)
            }
        }
    }
}

@Composable
internal fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit, primary: Boolean = false) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
    if (primary) {
        Button(onClick = onClick, modifier = Modifier.height(56.dp)) { content() }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.height(56.dp)) { content() }
    }
}

@Composable
internal fun StatusChip(connection: ConnectionState) {
    val (color, text) = when (connection) {
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant to "Niepołączono"
        is ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary to "Łączę…"
        is ConnectionState.Connected ->
            (if (connection.demo) MaterialTheme.colorScheme.secondary else Color(0xFF66BB6A)) to connection.deviceName
        is ConnectionState.Failed -> MaterialTheme.colorScheme.error to "Błąd połączenia"
    }
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun Dashboard(stats: VehicleStats, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    SpeedCard(stats, Modifier.weight(1.25f).fillMaxWidth())
                    ConsumptionCard(stats, Modifier.weight(1f).fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    PerformanceCard(stats, Modifier.weight(1.25f).fillMaxWidth())
                    SprintCard(stats, Modifier.weight(1f).fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    EngineCard(stats, Modifier.weight(1.1f).fillMaxWidth())
                    TripCard(stats, Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                val pairs: List<Pair<@Composable (Modifier) -> Unit, @Composable (Modifier) -> Unit>> = listOf(
                    Pair({ m -> SpeedCard(stats, m) }, { m -> ConsumptionCard(stats, m) }),
                    Pair({ m -> PerformanceCard(stats, m) }, { m -> SprintCard(stats, m) }),
                    Pair({ m -> EngineCard(stats, m) }, { m -> TripCard(stats, m) }),
                )
                pairs.forEach { (left, right) ->
                    Row(Modifier.fillMaxWidth().height(400.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        left(Modifier.weight(1f).fillMaxHeight())
                        right(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDialog(profile: VehicleProfile, onSave: (VehicleProfile) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(profile) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Twoje auto") },
        text = {
            Column {
                Text("Paliwo", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FuelType.entries.forEach { fuel ->
                        if (fuel == draft.fuelType) {
                            Button(onClick = {}) { Text(fuel.label) }
                        } else {
                            OutlinedButton(onClick = { draft = draft.copy(fuelType = fuel) }) { Text(fuel.label) }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Masa auta z kierowcą: ${draft.massKg.roundToInt()} kg", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = draft.massKg.toFloat(),
                    onValueChange = { draft = draft.copy(massKg = (it / 50).roundToInt() * 50.0) },
                    valueRange = 800f..3000f,
                    steps = 43,
                )
                Text("Pojemność silnika: ${draft.displacementL.fmt(1)} l", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = draft.displacementL.toFloat(),
                    onValueChange = { draft = draft.copy(displacementL = (it * 10).roundToInt() / 10.0) },
                    valueRange = 0.8f..5.0f,
                    steps = 41,
                )
                Text(
                    "Masa jest potrzebna do wyliczenia mocy, pojemność – do spalania w autach bez czujnika MAF.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
