package app.opentaiment.headunit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.opentaiment.feature.apps.AppEntry
import app.opentaiment.feature.apps.AppsRepository
import app.opentaiment.feature.media.MediaRepository
import app.opentaiment.feature.media.NowPlaying
import app.opentaiment.feature.weather.WeatherRepository
import app.opentaiment.feature.weather.WeatherState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val media = MediaRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val appsRepo = AppsRepository(app)

    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    val nowPlaying: StateFlow<NowPlaying?> = media.nowPlaying
    val hasMediaAccess: StateFlow<Boolean> = media.hasAccess

    private val _weather = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private var weatherJob: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                loadWeather()
                delay(30.minutes)
            }
        }
        reloadApps()
    }

    fun onResume() {
        media.start()
        reloadApps() // apps may have been installed/removed meanwhile
    }

    fun hasLocationPermission(): Boolean = weatherRepo.hasLocationPermission()

    fun refreshWeather() {
        if (weatherJob?.isActive == true) return
        weatherJob = viewModelScope.launch { loadWeather() }
    }

    private suspend fun loadWeather() {
        // Keep showing stale data while refreshing; only show a spinner on first load.
        if (_weather.value is WeatherState.Error) _weather.value = WeatherState.Loading
        weatherRepo.fetch()
            .onSuccess { _weather.value = WeatherState.Ready(it) }
            .onFailure { if (_weather.value !is WeatherState.Ready) _weather.value = WeatherState.Error }
    }

    private fun reloadApps() {
        viewModelScope.launch { _apps.value = appsRepo.loadApps() }
    }

    // --- media ---
    fun playPause() = media.playPause()
    fun next() = media.next()
    fun previous() = media.previous()
    fun openPlayer() {
        if (!media.openPlayer()) appsRepo.openMusic()
    }
    fun grantMediaAccess() {
        getApplication<Application>().startActivity(media.accessSettingsIntent())
    }

    // --- shortcuts ---
    fun launch(app: AppEntry) = appsRepo.launch(app.packageName)
    fun openNavigation() = appsRepo.openNavigation()
    fun openDialer() = appsRepo.openDialer()
    fun openBluetooth() = appsRepo.openBluetoothSettings()
    fun openSettings() = appsRepo.openSystemSettings()

    override fun onCleared() {
        media.stop()
    }
}
