package app.opentaiment.feature.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PlayerHub {
    @Volatile
    private var instance: MusicPlayer? = null

    fun get(context: Context): MusicPlayer = instance ?: synchronized(this) {
        instance ?: MusicPlayer(context.applicationContext, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
            .also { instance = it }
    }
}

sealed interface StationsState {
    data object Loading : StationsState
    data class Ready(val stations: List<RadioStation>) : StationsState
    data class Failed(val message: String) : StationsState
}

/**
 * The app's own radio + music player: station catalogue, favourites, local files and a
 * connection to [PlayerService]. One per process, shared by all screens.
 */
class MusicPlayer(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("radio", Context.MODE_PRIVATE)
    private val browser = RadioBrowser()
    private val local = LocalMusic(appContext)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _favorites = MutableStateFlow(StationJson.decode(prefs.getString(KEY_FAVORITES, null)))
    val favorites: StateFlow<List<RadioStation>> = _favorites.asStateFlow()

    private val _popular = MutableStateFlow<StationsState>(StationsState.Loading)
    val popular: StateFlow<StationsState> = _popular.asStateFlow()

    private val _searchResults = MutableStateFlow<StationsState?>(null)
    val searchResults: StateFlow<StationsState?> = _searchResults.asStateFlow()

    private val _tracks = MutableStateFlow<List<LocalTrack>?>(null)
    /** null until loaded (or without the storage permission). */
    val tracks: StateFlow<List<LocalTrack>?> = _tracks.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var ticker: Job? = null
    private var searchJob: Job? = null
    private var lastResumeAt = -RESUME_DEBOUNCE_MS

    /** Last radio station played, to resume when the car starts. */
    val lastStation: RadioStation? get() = StationJson.decode(prefs.getString(KEY_LAST, null)).firstOrNull()

    // ---------- catalogue ----------

    fun loadPopular(force: Boolean = false) {
        if (!force && _popular.value is StationsState.Ready) return
        _popular.value = StationsState.Loading
        scope.launch {
            _popular.value = try {
                StationsState.Ready(browser.popular())
            } catch (e: Exception) {
                StationsState.Failed("Brak internetu albo katalog stacji nie odpowiada")
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = null
            return
        }
        _searchResults.value = StationsState.Loading
        searchJob = scope.launch {
            delay(400) // wait until the user stops typing
            _searchResults.value = try {
                StationsState.Ready(browser.search(query))
            } catch (e: Exception) {
                StationsState.Failed("Nie udało się wyszukać – sprawdź internet")
            }
        }
    }

    fun isFavorite(station: RadioStation) = _favorites.value.any { it.id == station.id }

    fun toggleFavorite(station: RadioStation) {
        val list = _favorites.value
        saveFavorites(if (list.any { it.id == station.id }) list.filterNot { it.id == station.id } else list + station)
    }

    fun moveFavorite(station: RadioStation, delta: Int) {
        val list = _favorites.value.toMutableList()
        val from = list.indexOfFirst { it.id == station.id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.add(to, list.removeAt(from))
        saveFavorites(list)
    }

    /** Adds a stream URL by hand (e.g. a local station missing from the catalogue). */
    fun addCustomStation(name: String, url: String) {
        val station = RadioStation(id = "custom-" + url.hashCode(), name = name.ifBlank { url }, streamUrl = url.trim())
        if (!isFavorite(station)) saveFavorites(_favorites.value + station)
    }

    private fun saveFavorites(list: List<RadioStation>) {
        _favorites.value = list
        prefs.edit { putString(KEY_FAVORITES, StationJson.encode(list)) }
    }

    // ---------- local files ----------

    fun storagePermission(): String = local.permission()
    fun hasStoragePermission(): Boolean = local.hasPermission()

    fun loadTracks() {
        scope.launch { _tracks.value = if (local.hasPermission()) local.load() else null }
    }

    // ---------- playback ----------

    /**
     * Plays [station]; the rest of [list] becomes the queue, so "next"/"previous" (also on the
     * steering wheel) switch to the neighbouring station.
     */
    fun playStation(station: RadioStation, list: List<RadioStation> = listOf(station)) {
        val queue = if (list.any { it.id == station.id }) list else listOf(station)
        val index = queue.indexOfFirst { it.id == station.id }
        prefs.edit { putString(KEY_LAST, StationJson.encode(listOf(station))) }
        scope.launch { browser.countClick(station.id) }
        withController { c ->
            c.shuffleModeEnabled = false
            c.repeatMode = Player.REPEAT_MODE_ALL
            c.setMediaItems(queue.map(::stationItem), index, 0)
            c.prepare()
            c.play()
        }
    }

    fun playTracks(tracks: List<LocalTrack>, index: Int, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        withController { c ->
            c.setMediaItems(tracks.map(::trackItem), index.coerceIn(0, tracks.lastIndex), 0)
            c.repeatMode = Player.REPEAT_MODE_ALL
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
        }
    }

    /** Car started: carry on with the last radio station. */
    fun resumeLastStation() {
        val station = lastStation ?: return
        if (_state.value.isPlaying) return
        // Boot, the launcher and USB power can all say "car started" within a few seconds.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastResumeAt < RESUME_DEBOUNCE_MS) return
        lastResumeAt = now
        playStation(station, _favorites.value.takeIf { list -> list.any { it.id == station.id } } ?: listOf(station))
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause()
        else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun pause() = withController { it.pause() }
    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = withController { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0) }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }
    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
    }

    // ---------- controller plumbing ----------

    /** Connects to the playback service (starts it on first use). */
    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(appContext, ComponentName(appContext, PlayerService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            connecting = false
            val c = try { future.get() } catch (e: Exception) { null } ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()
            })
            publish()
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending += block
            connect()
        }
    }

    private fun publish() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val id = item?.mediaId
        val md = c.mediaMetadata
        val kind = when {
            id == null -> PlaybackKind.NONE
            id.startsWith(RADIO_PREFIX) -> PlaybackKind.RADIO
            else -> PlaybackKind.LOCAL
        }
        val stationName = md.station?.toString() ?: item?.mediaMetadata?.station?.toString()
        val title = md.title?.toString()
        _state.value = PlayerState(
            connected = true,
            kind = kind,
            title = when {
                kind == PlaybackKind.RADIO && (title == null || title == stationName) -> stationName
                else -> title
            },
            subtitle = when (kind) {
                PlaybackKind.RADIO -> if (title != null && title != stationName) stationName else "Radio na żywo"
                PlaybackKind.LOCAL -> listOfNotNull(md.artist?.toString(), md.albumTitle?.toString()).joinToString(" · ").ifEmpty { null }
                PlaybackKind.NONE -> null
            },
            artworkUri = (md.artworkUri ?: item?.mediaMetadata?.artworkUri)?.toString(),
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            error = c.playerError?.let { if (kind == PlaybackKind.RADIO) "Stacja nie odpowiada – próbuję ponownie" else "Nie da się odtworzyć pliku" },
            currentId = id,
            shuffle = c.shuffleModeEnabled,
            durationMs = if (kind == PlaybackKind.LOCAL) c.duration.coerceAtLeast(0) else 0,
            positionMs = if (kind == PlaybackKind.LOCAL) c.currentPosition.coerceAtLeast(0) else 0,
        )
        if (kind == PlaybackKind.LOCAL && c.isPlaying) startTicker() else ticker?.cancel()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                val c = controller ?: break
                _state.value = _state.value.copy(positionMs = c.currentPosition.coerceAtLeast(0))
            }
        }
    }

    private fun stationItem(s: RadioStation) = MediaItem.Builder()
        .setMediaId(RADIO_PREFIX + s.id)
        .setUri(s.streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.name)
                .setStation(s.name)
                .setArtist(s.name)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .apply { s.faviconUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .build(),
        )
        .build()

    private fun trackItem(t: LocalTrack) = MediaItem.Builder()
        .setMediaId(FILE_PREFIX + t.id)
        .setUri(t.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()

    companion object {
        const val RADIO_PREFIX = "radio:"
        const val FILE_PREFIX = "file:"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_LAST = "last"
        private const val RESUME_DEBOUNCE_MS = 15_000L
    }
}
