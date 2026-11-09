package app.opentaiment.feature.vehicle.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.ObdDevice

/** Everything a screen needs to offer "connect to the car" (or demo mode). */
class ObdConnectActions(
    val pairedDevices: () -> List<ObdDevice>,
    val hasBluetoothPermission: () -> Boolean,
    val onConnect: (ObdDevice) -> Unit,
    val onDemo: () -> Unit,
    val onOpenBluetoothSettings: () -> Unit,
)

/**
 * Handles the Bluetooth permission and the paired-device picker.
 * Returns the click handler for a "Połącz OBD" button.
 */
@Composable
fun rememberObdConnector(actions: ObdConnectActions): () -> Unit {
    var showDevices by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showDevices = true
    }
    if (showDevices) {
        DevicePickerDialog(
            devices = remember { actions.pairedDevices() },
            onPick = {
                showDevices = false
                actions.onConnect(it)
            },
            onOpenSettings = {
                showDevices = false
                actions.onOpenBluetoothSettings()
            },
            onDismiss = { showDevices = false },
        )
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !actions.hasBluetoothPermission()) {
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            showDevices = true
        }
    }
}

@Composable
internal fun ConnectPrompt(
    connection: ConnectionState,
    onConnect: () -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OtCard(modifier) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (connection) {
                is ConnectionState.Connecting, is ConnectionState.Connected -> {
                    CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 6.dp)
                    Spacer(Modifier.height(24.dp))
                    val name = (connection as? ConnectionState.Connecting)?.deviceName
                        ?: (connection as ConnectionState.Connected).deviceName
                    Text("Łączę z $name…", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pierwsze połączenie z autem może potrwać do 15 sekund.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Icon(
                        Icons.Rounded.DirectionsCar, contentDescription = null, Modifier.size(88.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Podłącz auto przez OBD", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "1. Wepnij adapter ELM327 (Bluetooth) w gniazdo OBD-II – zwykle pod kierownicą.\n" +
                            "2. Włącz zapłon.\n" +
                            "3. Sparuj adapter w ustawieniach Bluetooth (PIN zwykle 1234 albo 0000).\n" +
                            "4. Kliknij „Połącz OBD”. Opentaiment zapamięta adapter i następnym razem połączy się sam.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                    if (connection is ConnectionState.Failed) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "⚠️ ${connection.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onConnect, modifier = Modifier.height(64.dp)) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (connection is ConnectionState.Failed) "Spróbuj ponownie" else "Połącz OBD",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        OutlinedButton(onClick = onDemo, modifier = Modifier.height(64.dp)) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Zobacz demo", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DevicePickerDialog(
    devices: List<ObdDevice>,
    onPick: (ObdDevice) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz adapter OBD") },
        text = {
            if (devices.isEmpty()) {
                Text(
                    "Brak sparowanych urządzeń Bluetooth. Sparuj adapter OBD w ustawieniach " +
                        "(PIN zwykle 1234 albo 0000) i wróć tutaj.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    devices.forEach { device ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPick(device) }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (device.looksLikeObd) Icons.Rounded.DirectionsCar else Icons.Rounded.Bluetooth,
                                contentDescription = null,
                                tint = if (device.looksLikeObd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(device.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    device.address, style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Ustawienia Bluetooth") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

