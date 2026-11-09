package app.opentaiment.headunit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.opentaiment.feature.apps.AppEntry
import app.opentaiment.feature.apps.AppsRepository
import app.opentaiment.feature.media.MediaRepository
import app.opentaiment.feature.media.NowPlaying
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.ObdDevice
import app.opentaiment.core.ui.AppearanceController
import app.opentaiment.core.ui.AppearanceSettings
import app.opentaiment.core.ui.AppearanceState
import app.opentaiment.feature.vehicle.VehicleHub
import app.opentaiment.feature.vehicle.data.TripRecord
import app.opentaiment.feature.vehicle.race.RaceState
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.weather.WeatherRepository
import app.opentaiment.headunit.tiles.TilesStore
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
    private val vehicle = VehicleHub.get(app)
    private val appearanceController = AppearanceController(app, viewModelScope)
    private val tilesStore = TilesStore(app)

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

    val vehicleConnection: StateFlow<ConnectionState> = vehicle.connection
    val vehicleStats: StateFlow<VehicleStats> = vehicle.stats
    val vehicleProfile: StateFlow<VehicleProfile> = vehicle.profile
    val diagnostics: StateFlow<DiagnosticsState> = vehicle.diagnostics
    val trips: StateFlow<List<TripRecord>> = vehicle.trips
    val race: StateFlow<RaceState> = vehicle.race
    val appearance: StateFlow<AppearanceState> = appearanceController.state

    /** Ids of tiles on the home screen, in display order. */
    val tiles: StateFlow<List<String>> = tilesStore.tiles

    private var weatherJob: Job? = null

    init {
        vehicle.autoConnect()
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
        appearanceController.refresh()
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

    // --- vehicle / OBD ---
    fun pairedObdDevices(): List<ObdDevice> = vehicle.pairedDevices()
    fun hasBluetoothPermission(): Boolean = vehicle.hasBluetoothPermission()
    fun connectObd(device: ObdDevice) = vehicle.connect(device)
    fun startVehicleDemo() = vehicle.startDemo()
    fun disconnectObd() = vehicle.disconnect()
    fun resetTrip() = vehicle.resetTrip()
    fun updateVehicleProfile(profile: VehicleProfile) = vehicle.updateProfile(profile)
    fun readDtcs() = vehicle.readDtcs()
    fun clearDtcs() = vehicle.clearDtcs()
    fun searchDtc(code: String) = appsRepo.openWebSearch("$code kod błędu OBD")
    fun deleteTrip(id: Long) = vehicle.deleteTrip(id)
    fun clearTrips() = vehicle.clearTrips()
    fun hasGpsPermission() = vehicle.hasGpsPermission()
    fun startRaceGps() { vehicle.startRaceGps() }
    fun stopRaceGps() = vehicle.stopRaceGps()
    fun setFinishLineHere() { vehicle.setFinishLineHere() }
    fun resetRace() = vehicle.resetRace()
    fun updateAppearance(settings: AppearanceSettings) = appearanceController.update(settings)

    // --- home tiles ---
    fun addTile(id: String) = tilesStore.add(id)
    fun removeTile(id: String) = tilesStore.remove(id)
    fun moveTile(id: String, delta: Int) = tilesStore.move(id, delta)
    fun resetTiles() = tilesStore.reset()

    // --- shortcuts ---
    fun launch(app: AppEntry) = appsRepo.launch(app.packageName)
    fun launch(packageName: String) = appsRepo.launch(packageName)
    fun openNavigation() = appsRepo.openNavigation()
    fun openDialer() = appsRepo.openDialer()
    fun openBluetooth() = appsRepo.openBluetoothSettings()
    fun openSettings() = appsRepo.openSystemSettings()

    override fun onCleared() {
        media.stop()
        // The OBD connection lives in VehicleHub and outlives this screen on purpose.
    }
}
