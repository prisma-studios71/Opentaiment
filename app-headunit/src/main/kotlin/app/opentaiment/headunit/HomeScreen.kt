package app.opentaiment.headunit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.ActionTile
import app.opentaiment.core.ui.TileIcon
import app.opentaiment.headunit.tiles.HomeWidget
import app.opentaiment.headunit.tiles.WidgetSlots
import androidx.compose.foundation.layout.padding

class HomeActions(
    val widgets: WidgetActions,
    val onEditTiles: () -> Unit,
    /** Opens the widget editor with the given slot selected. */
    val onEditWidgets: (slot: Int) -> Unit,
)

/** A tile ready to draw on the home screen. */
class HomeTile(
    val id: String,
    val label: String,
    val icon: TileIcon,
    val subtitle: String? = null,
    val onClick: () -> Unit,
)

private val gap = 20.dp

/**
 * Home screen: three widget slots (two small on the left, one large in the middle – see
 * [WidgetSlots]) and the tile grid.
 */
@Composable
fun HomeScreen(
    widgets: List<HomeWidget>,
    data: WidgetData,
    tiles: List<HomeTile>,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val slot: @Composable (Int, Modifier) -> Unit = { index, m ->
        Box(m) {
            WidgetView(widgets.getOrElse(index) { WidgetSlots.DEFAULT[index] }, data, actions.widgets, Modifier.fillMaxSize())
            // The first slot always carries the edit button, whatever widget sits there.
            if (index == 0) {
                IconButton(onClick = { actions.onEditWidgets(0) }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(56.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edytuj ekran główny", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    BoxWithConstraints(modifier) {
        if (maxWidth >= maxHeight) {
            // Landscape – typical tablet on a dashboard mount.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    slot(0, Modifier.weight(1f).fillMaxWidth())
                    slot(1, Modifier.weight(1.2f).fillMaxWidth())
                }
                slot(2, Modifier.weight(1.5f).fillMaxHeight())
                TileGrid(tiles, columns = 2, onEdit = actions.onEditTiles, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    slot(0, Modifier.weight(1f).fillMaxHeight())
                    slot(1, Modifier.weight(1f).fillMaxHeight())
                }
                slot(2, Modifier.weight(1.4f).fillMaxWidth())
                TileGrid(
                    tiles,
                    columns = if (tiles.size <= 6) 3 else 4,
                    onEdit = actions.onEditTiles,
                    modifier = Modifier.weight(0.9f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TileGrid(tiles: List<HomeTile>, columns: Int, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    if (tiles.isEmpty()) {
        ActionTile(TileIcon.Vector(Icons.Rounded.Add), "Dodaj kafelki", onEdit, modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { tile ->
                    ActionTile(
                        icon = tile.icon,
                        label = tile.label,
                        onClick = tile.onClick,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        subtitle = tile.subtitle,
                        onLongClick = onEdit,
                    )
                }
                // Keep tiles in a short last row the same size as the others.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
