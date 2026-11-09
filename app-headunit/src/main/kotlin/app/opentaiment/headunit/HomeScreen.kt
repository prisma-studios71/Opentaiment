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
import app.opentaiment.core.ui.OtCard
import app.opentaiment.core.ui.TileIcon
import app.opentaiment.feature.media.MediaPanel
import app.opentaiment.feature.media.NowPlaying
import app.opentaiment.feature.weather.WeatherCard
import app.opentaiment.feature.weather.WeatherState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeActions(
    val onRefreshWeather: () -> Unit,
    val onGrantMediaAccess: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onOpenPlayer: () -> Unit,
    val onEditTiles: () -> Unit,
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

@Composable
fun HomeScreen(
    now: LocalDateTime,
    weather: WeatherState,
    nowPlaying: NowPlaying?,
    hasMediaAccess: Boolean,
    tiles: List<HomeTile>,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val media: @Composable (Modifier) -> Unit = { m ->
        MediaPanel(
            hasAccess = hasMediaAccess,
            nowPlaying = nowPlaying,
            onGrantAccess = actions.onGrantMediaAccess,
            onPlayPause = actions.onPlayPause,
            onNext = actions.onNext,
            onPrevious = actions.onPrevious,
            onOpenPlayer = actions.onOpenPlayer,
            modifier = m,
        )
    }

    BoxWithConstraints(modifier) {
        if (maxWidth >= maxHeight) {
            // Landscape – typical tablet on a dashboard mount.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    ClockCard(now, actions.onEditTiles, Modifier.weight(1f).fillMaxWidth())
                    WeatherCard(weather, actions.onRefreshWeather, Modifier.weight(1.2f).fillMaxWidth())
                }
                media(Modifier.weight(1.5f).fillMaxHeight())
                TileGrid(tiles, columns = 2, onEdit = actions.onEditTiles, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    ClockCard(now, actions.onEditTiles, Modifier.weight(1f).fillMaxHeight())
                    WeatherCard(weather, actions.onRefreshWeather, Modifier.weight(1f).fillMaxHeight())
                }
                media(Modifier.weight(1.4f).fillMaxWidth())
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

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("pl"))

@Composable
private fun ClockCard(now: LocalDateTime, onEditTiles: () -> Unit, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(now.format(timeFormat), style = MaterialTheme.typography.displayLarge, maxLines = 1)
                Text(
                    now.format(dateFormat).replaceFirstChar { it.titlecase(Locale("pl")) },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onEditTiles, modifier = Modifier.align(Alignment.TopEnd).size(56.dp)) {
                Icon(
                    Icons.Rounded.Edit, contentDescription = "Edytuj kafelki",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
