package app.opentaiment.feature.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.OtCard

/** Everything the player screen shows, as plain values (easy to screenshot-test). */
class PlayerScreenData(
    val state: PlayerState,
    val favorites: List<RadioStation>,
    val popular: StationsState,
    val searchResults: StationsState?,
    val tracks: List<LocalTrack>?,
    val hasStoragePermission: Boolean,
)

class PlayerActions(
    val onBack: (() -> Unit)?,
    val onPlayStation: (RadioStation, List<RadioStation>) -> Unit,
    val onToggleFavorite: (RadioStation) -> Unit,
    val onAddCustom: (name: String, url: String) -> Unit,
    val onSearch: (String) -> Unit,
    val onLoadPopular: () -> Unit,
    val onPlayTracks: (List<LocalTrack>, Int, Boolean) -> Unit,
    val onLoadTracks: () -> Unit,
    val storagePermission: String,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
)

/** "Radio i muzyka": internet radio and music files from the device / SD card / USB stick. */
@Composable
fun PlayerScreen(data: PlayerScreenData, actions: PlayerActions, modifier: Modifier = Modifier, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (actions.onBack != null) {
                IconButton(onClick = actions.onBack, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Wróć", Modifier.size(36.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Text("Radio i muzyka", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Spacer(Modifier.width(24.dp))
            TabRow(selectedTabIndex = tab, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Radio internetowe", style = MaterialTheme.typography.titleMedium) }, icon = { Icon(Icons.Rounded.Radio, null) })
                Tab(tab == 1, { tab = 1 }, text = { Text("Pliki", style = MaterialTheme.typography.titleMedium) }, icon = { Icon(Icons.Rounded.Folder, null) })
            }
        }
        Spacer(Modifier.height(16.dp))
        NowPlayingBar(data.state, actions, Modifier.fillMaxWidth().height(112.dp))
        Spacer(Modifier.height(16.dp))
        when (tab) {
            0 -> RadioTab(data, actions, Modifier.weight(1f).fillMaxWidth())
            else -> FilesTab(data, actions, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
fun NowPlayingBar(state: PlayerState, actions: PlayerActions, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            StationBadge(state.title ?: "♪", Modifier.size(64.dp), radio = state.kind != PlaybackKind.LOCAL)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.title ?: "Nic nie gra – wybierz stację albo utwór",
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.error ?: state.subtitle ?: "",
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.kind == PlaybackKind.LOCAL && state.durationMs > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = actions.onPrevious, modifier = Modifier.size(64.dp)) { Icon(Icons.Rounded.SkipPrevious, "Poprzednia", Modifier.size(40.dp)) }
            FilledIconButton(onClick = actions.onTogglePlay, modifier = Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors()) {
                if (state.buffering) CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pauza" else "Graj", Modifier.size(44.dp))
            }
            IconButton(onClick = actions.onNext, modifier = Modifier.size(64.dp)) { Icon(Icons.Rounded.SkipNext, "Następna", Modifier.size(40.dp)) }
        }
    }
}

/** Round badge with the station's first letter – no image downloads, readable at a glance. */
@Composable
internal fun StationBadge(name: String, modifier: Modifier = Modifier, radio: Boolean = true) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase()
        if (letter != null && radio) {
            Text(letter, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        } else {
            Icon(if (radio) Icons.Rounded.Radio else Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RadioTab(data: PlayerScreenData, actions: PlayerActions, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var addCustom by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { actions.onLoadPopular() }
    BoxWithConstraints(modifier) {
        val narrow = maxWidth < 700.dp
        val favorites: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ulubione", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addCustom = true }) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Własny adres")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (data.favorites.isEmpty()) {
                    Text(
                        "Stuknij ♡ przy stacji, żeby mieć ją tutaj. Przyciski następny/poprzedni (też na kierownicy) przełączają między ulubionymi.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyVerticalGrid(
                        GridCells.Adaptive(160.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(data.favorites, key = { it.id }) { station ->
                            FavoriteTile(
                                station,
                                playing = data.state.currentId == MusicPlayer.RADIO_PREFIX + station.id,
                                onClick = { actions.onPlayStation(station, data.favorites) },
                            )
                        }
                    }
                }
            }
        }
        val catalogue: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        actions.onSearch(it)
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Szukaj stacji: RMF, Trójka, jazz…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = if (query.isBlank()) data.popular else data.searchResults ?: data.popular
                Text(
                    if (query.isBlank()) "Popularne w Polsce" else "Wyniki",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                when (list) {
                    StationsState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    is StationsState.Failed -> Column {
                        Text(list.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = actions.onLoadPopular) { Text("Spróbuj ponownie") }
                    }
                    is StationsState.Ready -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(list.stations, key = { it.id }) { station ->
                            StationRow(
                                station,
                                favorite = data.favorites.any { it.id == station.id },
                                playing = data.state.currentId == MusicPlayer.RADIO_PREFIX + station.id,
                                onPlay = { actions.onPlayStation(station, list.stations) },
                                onFavorite = { actions.onToggleFavorite(station) },
                            )
                        }
                    }
                }
            }
        }
        if (narrow) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                favorites(Modifier.weight(0.8f).fillMaxWidth())
                catalogue(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                favorites(Modifier.weight(1f).fillMaxHeight())
                catalogue(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
    if (addCustom) CustomStationDialog(onDismiss = { addCustom = false }, onAdd = { n, u ->
        actions.onAddCustom(n, u)
        addCustom = false
    })
}

@Composable
private fun FavoriteTile(station: RadioStation, playing: Boolean, onClick: () -> Unit) {
    val bg = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (playing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.height(112.dp).clip(RoundedCornerShape(24.dp)).background(bg).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(station.name, style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (playing) Text("Gra teraz", style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun StationRow(station: RadioStation, favorite: Boolean, playing: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onPlay)
            .background(if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationBadge(station.name, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (station.details.isNotEmpty()) {
                Text(station.details, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(56.dp)) {
            Icon(
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (favorite) "Usuń z ulubionych" else "Dodaj do ulubionych",
                tint = if (favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CustomStationDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    val valid = url.startsWith("http") && url.length > 10
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj stację po adresie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true)
                OutlinedTextField(url, { url = it.trim() }, label = { Text("Adres strumienia (MP3, AAC, HLS)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onAdd(name, url) }) { Text("Dodaj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun FilesTab(data: PlayerScreenData, actions: PlayerActions, modifier: Modifier = Modifier) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) actions.onLoadTracks()
    }
    LaunchedEffect(data.hasStoragePermission) { if (data.hasStoragePermission) actions.onLoadTracks() }
    Column(modifier) {
        if (!data.hasStoragePermission) {
            OtCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Folder, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Muzyka z pendrive'a i karty", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Opentaiment potrzebuje dostępu do plików audio, żeby pokazać utwory z pamięci, karty SD i USB.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { permission.launch(actions.storagePermission) }, modifier = Modifier.height(64.dp)) {
                        Text("Pozwól", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            return
        }
        val tracks = data.tracks
        when {
            tracks == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            tracks.isEmpty() -> Text(
                "Nie znalazłem muzyki. Włóż pendrive'a albo kartę z plikami MP3/FLAC/M4A – Android potrzebuje chwili, żeby je zindeksować.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${tracks.size} utworów", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { actions.onPlayTracks(tracks, 0, true) }, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Losowo wszystko")
                    }
                }
                Spacer(Modifier.height(8.dp))
                val folders = remember(tracks) { tracks.groupBy { it.folder } }
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    folders.forEach { (folder, inFolder) ->
                        item(key = "f:$folder") {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).clip(RoundedCornerShape(12.dp))
                                    .clickable { actions.onPlayTracks(inFolder, 0, false) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(folder, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                Text("▶ graj folder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(inFolder, key = { it.id }) { track ->
                            val playing = data.state.currentId == MusicPlayer.FILE_PREFIX + track.id
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { actions.onPlayTracks(inFolder, inFolder.indexOf(track), false) }
                                    .background(if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    track.artist?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                                }
                                Text(formatMs(track.durationMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
