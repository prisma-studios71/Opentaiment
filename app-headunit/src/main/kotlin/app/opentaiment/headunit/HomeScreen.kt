package app.opentaiment.headunit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.ActionTile
import app.opentaiment.core.ui.OtCard
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
    val onNavigation: () -> Unit,
    val onPhone: () -> Unit,
    val onApps: () -> Unit,
    val onBluetooth: () -> Unit,
    val onSettings: () -> Unit,
)

private val gap = 20.dp

@Composable
fun HomeScreen(
    now: LocalDateTime,
    weather: WeatherState,
    nowPlaying: NowPlaying?,
    hasMediaAccess: Boolean,
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
                    ClockCard(now, Modifier.weight(1f).fillMaxWidth())
                    WeatherCard(weather, actions.onRefreshWeather, Modifier.weight(1.2f).fillMaxWidth())
                }
                media(Modifier.weight(1.5f).fillMaxHeight())
                QuickActions(actions, columns = 2, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    ClockCard(now, Modifier.weight(1f).fillMaxHeight())
                    WeatherCard(weather, actions.onRefreshWeather, Modifier.weight(1f).fillMaxHeight())
                }
                media(Modifier.weight(1.4f).fillMaxWidth())
                QuickActions(actions, columns = 3, modifier = Modifier.weight(0.9f).fillMaxWidth())
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("pl"))

@Composable
private fun ClockCard(now: LocalDateTime, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(now.format(timeFormat), style = MaterialTheme.typography.displayLarge, maxLines = 1)
            Text(
                now.format(dateFormat).replaceFirstChar { it.titlecase(Locale("pl")) },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private data class Shortcut(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun QuickActions(actions: HomeActions, columns: Int, modifier: Modifier = Modifier) {
    val shortcuts = listOf(
        Shortcut(Icons.Rounded.Navigation, "Nawigacja", actions.onNavigation),
        Shortcut(Icons.Rounded.MusicNote, "Muzyka", actions.onOpenPlayer),
        Shortcut(Icons.Rounded.Phone, "Telefon", actions.onPhone),
        Shortcut(Icons.Rounded.Apps, "Aplikacje", actions.onApps),
        Shortcut(Icons.Rounded.Bluetooth, "Bluetooth", actions.onBluetooth),
        Shortcut(Icons.Rounded.Settings, "Ustawienia", actions.onSettings),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        shortcuts.chunked(columns).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { s ->
                    ActionTile(s.icon, s.label, s.onClick, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}
