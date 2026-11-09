package app.opentaiment.feature.vehicle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.data.FuelType
import app.opentaiment.feature.vehicle.data.VehicleAlert
import app.opentaiment.feature.vehicle.data.VehicleProfile
import kotlin.math.roundToInt

/** Below this width (phones) screens stack vertically and headers wrap. */
internal val NarrowWidth: Dp = 600.dp

/**
 * Title row used by all vehicle screens. On wide screens: back, title, status, actions in one row.
 * On phones the actions move to a second, horizontally scrollable row.
 */
@Composable
internal fun ScreenHeader(
    title: String,
    connection: ConnectionState?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < NarrowWidth
        val titleRow: @Composable RowScope.() -> Unit = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Wróć", Modifier.size(36.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            if (connection != null) {
                Spacer(Modifier.width(16.dp))
                StatusChip(connection)
            }
        }
        if (narrow) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, content = titleRow)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = actions,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                titleRow()
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
            }
        }
    }
}

@Composable
internal fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit, primary: Boolean = false) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
    if (primary) {
        Button(onClick = onClick, modifier = Modifier.height(56.dp), content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.height(56.dp), content = content)
    }
}

internal val ConnectedGreen = Color(0xFF66BB6A)

@Composable
internal fun StatusChip(connection: ConnectionState) {
    val (color, text) = when (connection) {
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant to "Niepołączono"
        is ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary to "Łączę…"
        is ConnectionState.Connected ->
            (if (connection.demo) MaterialTheme.colorScheme.secondary else ConnectedGreen) to connection.deviceName
        is ConnectionState.Failed -> MaterialTheme.colorScheme.error to "Błąd połączenia"
    }
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Big, impossible-to-miss warning strip (overheating, no charging, low fuel…). */
@Composable
fun AlertBanner(alerts: List<VehicleAlert>, modifier: Modifier = Modifier) {
    if (alerts.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        alerts.forEach { alert ->
            val color = if (alert.type.critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(color.copy(alpha = 0.18f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(alert.type.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                    Text(alert.message, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * Fuel type, mass, displacement and fuel price – used in the "Auto" dialog and in settings.
 * Sliders are continuous (no tick marks) and values are rounded to sensible steps.
 */
@Composable
fun VehicleProfileEditor(profile: VehicleProfile, onChange: (VehicleProfile) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Paliwo", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelType.entries.forEach { fuel ->
                if (fuel == profile.fuelType) {
                    Button(onClick = {}) { Text(fuel.label) }
                } else {
                    OutlinedButton(onClick = { onChange(profile.copy(fuelType = fuel)) }) { Text(fuel.label) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Cena paliwa: ${profile.fuelPricePerL.fmt(2)} zł/l", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = profile.fuelPricePerL.toFloat(),
            onValueChange = { onChange(profile.copy(fuelPricePerL = (it * 20).roundToInt() / 20.0)) },
            valueRange = 2f..10f,
        )
        Text("Masa auta z kierowcą: ${profile.massKg.roundToInt()} kg", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = profile.massKg.toFloat(),
            onValueChange = { onChange(profile.copy(massKg = (it / 50).roundToInt() * 50.0)) },
            valueRange = 800f..3000f,
        )
        Text("Pojemność silnika: ${profile.displacementL.fmt(1)} l", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = profile.displacementL.toFloat(),
            onValueChange = { onChange(profile.copy(displacementL = (it * 10).roundToInt() / 10.0)) },
            valueRange = 0.8f..5.0f,
        )
        Text(
            "Cena – do kosztu tras. Masa – do wyliczenia mocy. Pojemność – do spalania w autach bez czujnika MAF.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
