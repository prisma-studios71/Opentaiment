package app.opentaiment.headunit.tiles

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.core.ui.TileIcon
import app.opentaiment.core.ui.TileIconView

/** A tile as shown in the editor (no click action, just identity + looks). */
data class TileOption(val id: String, val label: String, val icon: TileIcon, val isApp: Boolean)

class TileEditorActions(
    val onAdd: (String) -> Unit,
    val onRemove: (String) -> Unit,
    val onMove: (String, Int) -> Unit,
    val onReset: () -> Unit,
    val onDone: () -> Unit,
)

@Composable
fun TileEditorScreen(
    current: List<TileOption>,
    available: List<TileOption>,
    actions: TileEditorActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onDone, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Wróć", Modifier.size(40.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Kafelki ekranu głównego", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
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
            val currentPanel: @Composable (Modifier) -> Unit = { m -> CurrentTiles(current, actions, m) }
            val availablePanel: @Composable (Modifier) -> Unit = { m ->
                AvailableTiles(available, full = current.size >= TileList.MAX_TILES, onAdd = actions.onAdd, modifier = m)
            }
            if (maxWidth >= maxHeight) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    currentPanel(Modifier.weight(1f).fillMaxHeight())
                    availablePanel(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    currentPanel(Modifier.weight(1f).fillMaxWidth())
                    availablePanel(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CurrentTiles(tiles: List<TileOption>, actions: TileEditorActions, modifier: Modifier) {
    OtCard(modifier) {
        Text(
            "Na ekranie głównym (${tiles.size}/${TileList.MAX_TILES})",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Kolejność jak na ekranie: od lewej do prawej, z góry na dół.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (tiles.isEmpty()) {
            Text("Pusto! Dodaj coś z listy obok.", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            itemsIndexed(tiles, key = { _, t -> t.id }) { index, tile ->
                TileRow(tile) {
                    IconButton(onClick = { actions.onMove(tile.id, -1) }, enabled = index > 0, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "W górę", Modifier.size(32.dp))
                    }
                    IconButton(
                        onClick = { actions.onMove(tile.id, 1) },
                        enabled = index < tiles.lastIndex,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "W dół", Modifier.size(32.dp))
                    }
                    IconButton(onClick = { actions.onRemove(tile.id) }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Rounded.Close, contentDescription = "Usuń", Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableTiles(tiles: List<TileOption>, full: Boolean, onAdd: (String) -> Unit, modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val builtins = tiles.filter { !it.isApp }
    val apps = remember(tiles, query) {
        tiles.filter { it.isApp && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) }
    }
    OtCard(modifier) {
        Text("Dodaj kafelek", style = MaterialTheme.typography.titleLarge)
        Text(
            if (full) "Masz już ${TileList.MAX_TILES} kafelków – usuń któryś, żeby dodać nowy." else "Funkcje Opentaiment albo dowolna aplikacja.",
            style = MaterialTheme.typography.bodyLarge,
            color = if (full) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(builtins, key = { it.id }) { tile -> AddRow(tile, enabled = !full, onAdd = onAdd) }
            item(key = "apps-header") {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("Aplikacje", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Szukaj aplikacji") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(apps, key = { it.id }) { tile -> AddRow(tile, enabled = !full, onAdd = onAdd) }
        }
    }
}

@Composable
private fun AddRow(tile: TileOption, enabled: Boolean, onAdd: (String) -> Unit) {
    TileRow(tile, onClick = if (enabled) ({ onAdd(tile.id) }) else null) {
        Icon(
            Icons.Rounded.Add, contentDescription = "Dodaj",
            modifier = Modifier.padding(horizontal = 12.dp).size(32.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun TileRow(
    tile: TileOption,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TileIconView(tile.icon, 36.dp)
            Spacer(Modifier.width(16.dp))
            Text(
                tile.label, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            trailing()
        }
    }
}
