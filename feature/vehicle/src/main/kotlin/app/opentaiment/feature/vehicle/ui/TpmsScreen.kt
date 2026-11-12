package app.opentaiment.feature.vehicle.ui

import app.opentaiment.core.ui.OtNumber
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TireRepair
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.tpms.TpmsLimits
import app.opentaiment.feature.vehicle.tpms.TpmsProblem
import app.opentaiment.feature.vehicle.tpms.TpmsState
import app.opentaiment.feature.vehicle.tpms.Wheel
import app.opentaiment.feature.vehicle.tpms.WheelStatus
import kotlin.math.roundToInt

class TpmsActions(
    val onBack: (() -> Unit)?,
    val enabled: Boolean,
    val hasPermission: () -> Boolean,
    val onEnable: (Boolean) -> Unit,
    val onAssign: (sensorId: String, Wheel) -> Unit,
    val onForget: (Wheel) -> Unit,
    val onLimits: (TpmsLimits) -> Unit,
)

/** Tyre pressures from BLE TPMS sensors, laid out like the car seen from above. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TpmsScreen(state: TpmsState, actions: TpmsActions, modifier: Modifier = Modifier) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) actions.onEnable(true)
    }
    val enable: (Boolean) -> Unit = { on ->
        if (on && !actions.hasPermission()) permission.launch(blePermissions()) else actions.onEnable(on)
    }
    Column(modifier) {
        ScreenHeader("Opony", connection = null, onBack = actions.onBack) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Czujniki TPMS", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Switch(checked = actions.enabled, onCheckedChange = enable)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!actions.enabled) {
            OtCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.TireRepair, contentDescription = null, Modifier.height(88.dp).width(88.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Ciśnienie w oponach na żywo", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Działa z tanimi czujnikami TPMS na Bluetooth (BLE) – nakrętki na wentyle albo czujniki w środku koła, " +
                            "sprzedawane jako „TPMS BLE” do aplikacji „TPMS II”. Czujniki numerowane 1–4 przypiszą się same " +
                            "(1 = przód lewe, 2 = przód prawe, 3 = tył lewe, 4 = tył prawe).",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { enable(true) }, modifier = Modifier.height(64.dp)) {
                        Text("Włącz czujniki", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            return
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow = maxWidth < NarrowWidth
            val wheels: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(Wheel.FRONT_LEFT to Wheel.FRONT_RIGHT, Wheel.REAR_LEFT to Wheel.REAR_RIGHT).forEach { (l, r) ->
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            WheelCard(state.wheels.first { it.wheel == l }, state.limits, { actions.onForget(l) }, Modifier.weight(1f).fillMaxHeight())
                            WheelCard(state.wheels.first { it.wheel == r }, state.limits, { actions.onForget(r) }, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            val side: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    state.error?.let { Text("⚠️ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
                    if (state.unassigned.isNotEmpty()) {
                        OtCard(Modifier.fillMaxWidth()) {
                            Text("Nowe czujniki w pobliżu", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            state.unassigned.take(6).forEach { r ->
                                Spacer(Modifier.height(12.dp))
                                Text("${r.sensorId} · ${r.pressureBar.fmt(2)} bar · ${r.temperatureC.fmt()} °C", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Wheel.entries.forEach { w -> OutlinedButton(onClick = { actions.onAssign(r.sensorId, w) }) { Text(w.short) } }
                                }
                            }
                        }
                    } else if (!state.hasAnyReading) {
                        Text(
                            if (state.scanning) "Nasłuchuję czujników… Czujniki wysyłają dane co kilka sekund podczas jazdy, a na postoju rzadziej."
                            else "Skanowanie wyłączone.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OtCard(Modifier.fillMaxWidth()) {
                        Text("Ostrzegaj poniżej ${state.limits.minBar.fmt(1)} bar", style = MaterialTheme.typography.titleMedium)
                        Slider(
                            value = state.limits.minBar.toFloat(),
                            onValueChange = { actions.onLimits(state.limits.copy(minBar = (it * 10).roundToInt() / 10.0)) },
                            valueRange = 1.0f..3.0f,
                        )
                        Text("i powyżej ${state.limits.maxBar.fmt(1)} bar", style = MaterialTheme.typography.titleMedium)
                        Slider(
                            value = state.limits.maxBar.toFloat(),
                            onValueChange = { actions.onLimits(state.limits.copy(maxBar = (it * 10).roundToInt() / 10.0)) },
                            valueRange = 2.5f..4.5f,
                        )
                    }
                }
            }
            if (narrow) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    wheels(Modifier.weight(1f).fillMaxWidth())
                    side(Modifier.weight(0.8f).fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    wheels(Modifier.weight(1.6f).fillMaxHeight())
                    side(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun WheelCard(status: WheelStatus, limits: TpmsLimits, onForget: () -> Unit, modifier: Modifier = Modifier) {
    val r = status.reading
    val critical = status.problems.any { it == TpmsProblem.LOW || it == TpmsProblem.LEAK || it == TpmsProblem.HOT }
    val color = when {
        r == null -> MaterialTheme.colorScheme.onSurfaceVariant
        critical -> MaterialTheme.colorScheme.error
        status.problems.isNotEmpty() -> MaterialTheme.colorScheme.secondary
        else -> ConnectedGreen
    }
    OtCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status.wheel.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            if (r != null) Text("${r.batteryPct?.let { "🔋 $it%" } ?: ""}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            OtNumber(r?.pressureBar.fmt(1), 56.sp, color = color)
            Spacer(Modifier.width(6.dp))
            Text("bar", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            when {
                r == null -> "Brak czujnika"
                status.problems.isNotEmpty() -> status.problems.joinToString(" · ") { it.label }
                else -> "${r.temperatureC.fmt()} °C · OK"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (status.problems.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else color,
            maxLines = 2,
        )
        Spacer(Modifier.weight(1f))
        if (r != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (status.problems.isNotEmpty()) "Norma: ${limits.minBar.fmt(1)}–${limits.maxBar.fmt(1)} bar" else r.sensorId,
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), maxLines = 1,
                )
                TextButton(onClick = onForget) { Text("Odepnij") }
            }
        }
    }
}
