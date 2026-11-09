package app.opentaiment.feature.vehicle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.data.ReminderLevel
import app.opentaiment.feature.vehicle.data.ReminderStatus
import app.opentaiment.feature.vehicle.data.ServiceReminder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class ServiceActions(
    val onBack: (() -> Unit)?,
    val onSetOdometer: (Double) -> Unit,
    val onDone: (String) -> Unit,
    val onSave: (ServiceReminder) -> Unit,
    val onDelete: (String) -> Unit,
)

private val dayFormat = DateTimeFormatter.ofPattern("d.MM.yyyy")

@Composable
internal fun levelColor(level: ReminderLevel): Color = when (level) {
    ReminderLevel.DUE -> MaterialTheme.colorScheme.error
    ReminderLevel.SOON -> MaterialTheme.colorScheme.secondary
    ReminderLevel.OK -> ConnectedGreen
}

/** Service reminders: oil, inspection, insurance… counted by kilometres and dates. */
@Composable
fun ServiceScreen(
    statuses: List<ReminderStatus>,
    odometerKm: Double?,
    odometerFromCar: Boolean,
    actions: ServiceActions,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<ServiceReminder?>(null) }
    var editOdometer by remember { mutableStateOf(false) }

    Column(modifier) {
        ScreenHeader("Serwis", connection = null, onBack = actions.onBack) {
            HeaderButton(Icons.Rounded.Add, "Dodaj", {
                editing = ServiceReminder("custom_${System.currentTimeMillis()}", "", 10_000, 365, odometerKm, LocalDate.now().toEpochDay())
            })
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "odo") {
                OtCard(Modifier.fillMaxWidth().height(180.dp), onClick = if (odometerFromCar) null else ({ editOdometer = true })) {
                    Text("Przebieg", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(odometerKm.fmt(), style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("km", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(
                        when {
                            odometerFromCar -> "Odczytany z auta"
                            odometerKm == null -> "Stuknij i wpisz przebieg z licznika – dalej liczę sam z tras"
                            else -> "Twój odczyt + kilometry z zapisanych tras · stuknij, żeby poprawić"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(statuses, key = { it.reminder.id }) { status ->
                ReminderItem(status, onDone = { actions.onDone(status.reminder.id) }, onEdit = { editing = status.reminder })
            }
        }
    }

    if (editOdometer) {
        NumberDialog(
            title = "Przebieg z licznika",
            initial = odometerKm?.let { String.format(Locale.US, "%.0f", it) } ?: "",
            suffix = "km",
            onDismiss = { editOdometer = false },
            onConfirm = {
                actions.onSetOdometer(it)
                editOdometer = false
            },
        )
    }
    editing?.let { reminder ->
        ReminderEditor(
            reminder = reminder,
            isNew = statuses.none { it.reminder.id == reminder.id },
            onDismiss = { editing = null },
            onSave = {
                actions.onSave(it)
                editing = null
            },
            onDelete = {
                actions.onDelete(reminder.id)
                editing = null
            },
        )
    }
}

@Composable
private fun ReminderItem(status: ReminderStatus, onDone: () -> Unit, onEdit: () -> Unit) {
    val color = levelColor(status.level)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Build, contentDescription = null, tint = color) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(status.reminder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(status.label, style = MaterialTheme.typography.bodyLarge, color = color)
                val r = status.reminder
                Text(
                    listOfNotNull(
                        r.intervalKm?.let { "co ${it.toDouble().fmt()} km" },
                        r.intervalDays?.let { if (it % 365 == 0) "co ${it / 365} ${if (it == 365) "rok" else "lata"}" else "co $it dni" },
                        r.lastDoneEpochDay?.let { "ostatnio ${LocalDate.ofEpochDay(it).format(dayFormat)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.height(52.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Zrobione")
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edytuj", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReminderEditor(
    reminder: ServiceReminder,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ServiceReminder) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(reminder.name) }
    var km by remember { mutableStateOf(reminder.intervalKm?.toString() ?: "") }
    var days by remember { mutableStateOf(reminder.intervalDays?.toString() ?: "") }
    var lastKm by remember { mutableStateOf(reminder.lastDoneKm?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var lastDate by remember { mutableStateOf(reminder.lastDoneEpochDay?.let { LocalDate.ofEpochDay(it).format(dayFormat) } ?: "") }
    val parsedDate = try {
        lastDate.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it.trim(), dayFormat) }
    } catch (e: DateTimeParseException) {
        null
    }
    val dateValid = lastDate.isBlank() || parsedDate != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nowe przypomnienie" else reminder.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true)
                OutlinedTextField(km, { km = it.filter(Char::isDigit) }, label = { Text("Co ile km (puste = nie liczę km)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("Co ile dni (puste = bez daty)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(lastKm, { lastKm = it.filter(Char::isDigit) }, label = { Text("Ostatnio przy przebiegu (km)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(lastDate, { lastDate = it }, label = { Text("Ostatnio dnia (dd.mm.rrrr)") }, singleLine = true, isError = !dateValid)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && dateValid,
                onClick = {
                    onSave(
                        reminder.copy(
                            name = name.trim(),
                            intervalKm = km.toIntOrNull(),
                            intervalDays = days.toIntOrNull(),
                            lastDoneKm = lastKm.toDoubleOrNull(),
                            lastDoneEpochDay = parsedDate?.toEpochDay(),
                        ),
                    )
                },
            ) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    )
}

@Composable
internal fun NumberDialog(title: String, initial: String, suffix: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it.filter { c -> c.isDigit() || c == '.' } },
                suffix = { Text(suffix) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = text.toDoubleOrNull() != null, onClick = { text.toDoubleOrNull()?.let(onConfirm) }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
