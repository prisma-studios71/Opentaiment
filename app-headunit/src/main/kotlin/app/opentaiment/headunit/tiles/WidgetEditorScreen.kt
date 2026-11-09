package app.opentaiment.headunit.tiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.OtCard

class WidgetEditorActions(
    val onSelectSlot: (Int) -> Unit,
    val onPick: (HomeWidget) -> Unit,
    val onReset: () -> Unit,
    val onEditTiles: () -> Unit,
    val onDone: () -> Unit,
)

/** Pick a slot on the mini home-screen map, then what it should show. */
@Composable
fun WidgetEditorScreen(
    widgets: List<HomeWidget>,
    selectedSlot: Int,
    actions: WidgetEditorActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onDone, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Wróć", Modifier.size(40.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Widżety ekranu głównego", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = actions.onEditTiles, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Rounded.Apps, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Kafelki", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = actions.onReset, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Domyślne", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = actions.onDone, modifier = Modifier.height(56.dp)) {
                Text("Gotowe", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val map: @Composable (Modifier) -> Unit = { m -> SlotMap(widgets, selectedSlot, actions.onSelectSlot, m) }
            val list: @Composable (Modifier) -> Unit = { m -> WidgetList(widgets, selectedSlot, actions.onPick, m) }
            if (maxWidth >= maxHeight) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    map(Modifier.weight(1f).fillMaxHeight())
                    list(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    map(Modifier.fillMaxWidth().weight(0.8f))
                    list(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

/** Miniature of the home screen layout; tap a slot to change it. */
@Composable
private fun SlotMap(widgets: List<HomeWidget>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    OtCard(modifier) {
        Text("1. Wybierz miejsce", style = MaterialTheme.typography.titleLarge)
        Text(
            "Tak wygląda ekran główny. Stuknij miejsce, które chcesz zmienić.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SlotBox(0, widgets[0], selected == 0, onSelect, Modifier.weight(1f).fillMaxWidth())
                SlotBox(1, widgets[1], selected == 1, onSelect, Modifier.weight(1.2f).fillMaxWidth())
            }
            SlotBox(2, widgets[2], selected == 2, onSelect, Modifier.weight(1.5f).fillMaxHeight())
            Card(
                Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    Text("kafelki", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun SlotBox(index: Int, widget: HomeWidget, selected: Boolean, onSelect: (Int) -> Unit, modifier: Modifier) {
    Card(
        onClick = { onSelect(index) },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(widget.icon, contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(widget.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
            Text(
                WidgetSlots.NAMES[index], style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1,
            )
        }
    }
}

@Composable
private fun WidgetList(widgets: List<HomeWidget>, selected: Int, onPick: (HomeWidget) -> Unit, modifier: Modifier) {
    OtCard(modifier) {
        Text("2. Co ma tam być?", style = MaterialTheme.typography.titleLarge)
        Text(
            "Miejsce: ${WidgetSlots.NAMES[selected].lowercase()}. Widżet z innego miejsca zamieni się miejscami.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(HomeWidget.entries, key = { it.id }) { widget ->
                val current = widgets[selected] == widget
                val elsewhere = widgets.indexOf(widget).takeIf { it >= 0 && it != selected }
                Card(
                    onClick = { onPick(widget) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(widget.icon, contentDescription = null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(widget.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                widget.description + (elsewhere?.let { " · teraz: ${WidgetSlots.NAMES[it].lowercase()}" } ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (current) Icon(Icons.Rounded.CheckCircle, contentDescription = "Wybrane", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

