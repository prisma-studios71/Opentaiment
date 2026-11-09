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
    /** Null on the phone, where screens live in tabs and there is nothing to go back to. */
    val onBack: (() -> Unit)?,
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
        ScreenHeader("Statystyki", connection, actions.onBack) {
            HeaderButton(Icons.Rounded.Tune, "Auto", { showProfile = true })
            if (connection is ConnectionState.Connected) {
                HeaderButton(Icons.Rounded.RestartAlt, "Nowa trasa", actions.onResetTrip)
                HeaderButton(Icons.Rounded.LinkOff, "Rozłącz", actions.onDisconnect, primary = true)
            } else {
                HeaderButton(Icons.Rounded.PlayArrow, "Demo", actions.connect.onDemo)
                HeaderButton(Icons.Rounded.Bluetooth, "Połącz OBD", onConnectClick, primary = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (connection is ConnectionState.Connected && stats.hasData) {
            AlertBanner(stats.alerts, Modifier.padding(bottom = 16.dp))
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
private fun Dashboard(stats: VehicleStats, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val cards: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> SpeedCard(stats, m) }, { m -> ConsumptionCard(stats, m) },
            { m -> PerformanceCard(stats, m) }, { m -> SprintCard(stats, m) },
            { m -> EngineCard(stats, m) }, { m -> TripCard(stats, m) },
        )
        when {
            maxWidth < NarrowWidth -> Column(
                // Phone: one card per row.
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                cards.forEach { card -> card(Modifier.fillMaxWidth().height(380.dp)) }
            }
            maxWidth >= maxHeight -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                cards.chunked(2).forEach { (top, bottom) ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        top(Modifier.weight(1.2f).fillMaxWidth())
                        bottom(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                cards.chunked(2).forEach { (left, right) ->
                    Row(Modifier.fillMaxWidth().height(420.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
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
            VehicleProfileEditor(draft, { draft = it }, Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
