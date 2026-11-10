package app.opentaiment.headunit.car

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.OtCard
import kotlin.math.roundToInt

/** Which special permissions are granted right now. */
data class CarPermissions(
    val brightness: Boolean = false,
    val overlay: Boolean = false,
    val keysEverywhere: Boolean = false,
    val preciseLocation: Boolean = false,
)

class CarSettingsActions(
    val onChange: ((CarSettings) -> CarSettings) -> Unit,
    val onGrantBrightness: () -> Unit,
    val onGrantOverlay: () -> Unit,
    val onOpenAccessibility: () -> Unit,
    val onGrantLocation: () -> Unit,
    val onAutoReconnect: (Boolean) -> Unit,
    val autoReconnect: Boolean,
)

/** Settings section that turns a tablet into a car radio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarSettingsSection(settings: CarSettings, permissions: CarPermissions, actions: CarSettingsActions, modifier: Modifier = Modifier) {
    var learning by remember { mutableStateOf(false) }

    OtCard(modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Title("Radio samochodowe")
        ToggleRow("Uruchamiaj razem z autem", "Po włączeniu radia/tabletu Opentaiment startuje sam, łączy się z OBD i włącza ostatnią stację.", settings.autostart) { v -> actions.onChange { it.copy(autostart = v) } }
        ToggleRow("Zapłon = zasilanie USB", "Dla tabletu na ładowarce w aucie: odłączenie prądu zapisuje trasę i pauzuje muzykę, podłączenie wznawia wszystko.", settings.ignitionByPower) { v -> actions.onChange { it.copy(ignitionByPower = v) } }
        ToggleRow("Wznawiaj radio po starcie", null, settings.resumeRadio) { v -> actions.onChange { it.copy(resumeRadio = v) } }
        ToggleRow("Łącz ponownie z OBD samo", "Po zerwaniu połączenia (np. przy odpalaniu silnika) próbuje co kilka sekund.", actions.autoReconnect, actions.onAutoReconnect)

        Divider()
        Title("Głośność zależna od prędkości")
        Text(
            "Szybciej = głośniej, jak w fabrycznym radiu. Prędkość z OBD, a bez niego z GPS." +
                if (!permissions.preciseLocation) " (Bez OBD potrzebna dokładna lokalizacja.)" else "",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Choice(SpeedVolumeLevel.entries, settings.speedVolume, { it.label }) { v -> actions.onChange { it.copy(speedVolume = v) } }
        if (!permissions.preciseLocation) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = actions.onGrantLocation) { Text("Zezwól na dokładną lokalizację") }
        }

        Divider()
        Title("Jasność ekranu")
        ToggleRow("Ustawiaj jasność na dzień i noc", "Zmienia jasność całego systemu, nie tylko Opentaiment – też nad nawigacją.", settings.brightnessControl) { v ->
            if (v && !permissions.brightness) actions.onGrantBrightness()
            actions.onChange { it.copy(brightnessControl = v) }
        }
        if (settings.brightnessControl) {
            if (!permissions.brightness) Grant("Potrzebna zgoda „Modyfikowanie ustawień systemu”", actions.onGrantBrightness)
            Text("W dzień: ${settings.dayBrightnessPct} %", style = MaterialTheme.typography.titleMedium)
            Slider(settings.dayBrightnessPct.toFloat(), { v -> actions.onChange { it.copy(dayBrightnessPct = v.roundToInt()) } }, valueRange = 10f..100f)
            Text("W nocy: ${settings.nightBrightnessPct} %", style = MaterialTheme.typography.titleMedium)
            Slider(settings.nightBrightnessPct.toFloat(), { v -> actions.onChange { it.copy(nightBrightnessPct = v.roundToInt()) } }, valueRange = 2f..60f)
        }

        Divider()
        Title("Dymek nad nawigacją")
        ToggleRow("Pokazuj prędkość i spalanie nad innymi aplikacjami", "Mały, przesuwany palcem dymek. Robi się czerwony przy ostrzeżeniu z auta albo opon.", settings.overlay) { v ->
            if (v && !permissions.overlay) actions.onGrantOverlay()
            actions.onChange { it.copy(overlay = v) }
        }
        if (settings.overlay && !permissions.overlay) Grant("Potrzebna zgoda „Wyświetlanie nad innymi aplikacjami”", actions.onGrantOverlay)

        Divider()
        Title("Adresy")
        var home by remember(settings.homeAddress) { mutableStateOf(settings.homeAddress) }
        var work by remember(settings.workAddress) { mutableStateOf(settings.workAddress) }
        OutlinedTextField(home, { home = it }, label = { Text("Dom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(work, { work = it }, label = { Text("Praca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (home != settings.homeAddress || work != settings.workAddress) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { actions.onChange { it.copy(homeAddress = home.trim(), workAddress = work.trim()) } }) { Text("Zapisz adresy") }
        }

        Divider()
        Title("Przyciski na kierownicy")
        Text(
            "Przypisz przyciski z kierownicy (albo pilota) do akcji. Przyciski multimediów bez przypisania działają jak zwykle – " +
                "sterują tym, co akurat gra.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        settings.keyBindings.entries.sortedBy { it.key }.forEach { (code, action) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(KeyBindings.keyName(code), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(action.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { actions.onChange { it.copy(keyBindings = it.keyBindings - code) } }) {
                    Icon(Icons.Rounded.Delete, "Usuń przypisanie", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (learning) {
            // Inline (not a dialog): key presses must reach the launcher window to be learned.
            LearnKeyPanel(
                onDismiss = { learning = false },
                onBind = { code, action ->
                    actions.onChange { it.copy(keyBindings = it.keyBindings + (code to action)) }
                    learning = false
                },
            )
            Spacer(Modifier.height(8.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!learning) Button(onClick = { learning = true }) { Text("Naucz przycisk") }
            OutlinedButton(onClick = actions.onOpenAccessibility) {
                Text(if (permissions.keysEverywhere) "Działa też nad innymi apkami ✓" else "Włącz działanie nad innymi apkami")
            }
        }
        if (!permissions.keysEverywhere) {
            Text(
                "Bez tego przyciski działają, gdy Opentaiment jest na ekranie. Włącz „Opentaiment – przyciski na kierownicy” w ustawieniach dostępności.",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearnKeyPanel(onDismiss: () -> Unit, onBind: (Int, KeyAction) -> Unit) {
    var code by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        KeyLearning.start()
        onDispose { KeyLearning.stop() }
    }
    LaunchedEffect(Unit) { KeyLearning.learned.collect { code = it } }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(16.dp),
    ) {
        val c = code
        Text(
            c?.let { "Przycisk: ${KeyBindings.keyName(it)} – co ma robić?" } ?: "Naciśnij teraz przycisk na kierownicy…",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        if (c == null) {
            Text(
                "Jeśli nic się nie dzieje, radio wysyła ten przycisk po swojemu (część chińskich radyj) – " +
                    "przypisz go wtedy w ustawieniach producenta do przycisku multimediów.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyAction.entries.forEach { action -> OutlinedButton(onClick = { onBind(c, action) }) { Text(action.label) } }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) { Text("Anuluj") }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Grant(text: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onClick) { Text("Zezwól") }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) Text(description, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    Spacer(Modifier.height(4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            if (o == selected) Button(onClick = {}) { Text(label(o)) }
            else OutlinedButton(onClick = { onPick(o) }) { Text(label(o)) }
        }
    }
}
