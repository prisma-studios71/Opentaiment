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
import app.opentaiment.headunit.tiles.HomeWidget
import app.opentaiment.headunit.tiles.TilesStore
import app.opentaiment.headunit.tiles.WidgetsStore
import app.opentaiment.feature.weather.WeatherState
import android.content.Intent
import android.provider.Settings
import app.opentaiment.feature.player.LocalTrack
import app.opentaiment.feature.player.PlayerHub
import app.opentaiment.feature.player.PlayerState
import app.opentaiment.feature.player.RadioStation
import app.opentaiment.feature.player.StationsState
import app.opentaiment.feature.vehicle.data.ReminderStatus
import app.opentaiment.feature.vehicle.data.ServiceReminder
import app.opentaiment.feature.vehicle.data.TrackPoint
import app.opentaiment.feature.vehicle.data.TripSharing
import app.opentaiment.feature.vehicle.tpms.TpmsHub
import app.opentaiment.feature.vehicle.tpms.TpmsLimits
import app.opentaiment.feature.vehicle.tpms.TpmsState
import app.opentaiment.feature.vehicle.tpms.Wheel
import app.opentaiment.headunit.car.Brightness
import app.opentaiment.headunit.car.CarPermissions
import app.opentaiment.headunit.car.CarSettings
import app.opentaiment.headunit.car.CarSettingsStore
import app.opentaiment.headunit.car.KeyListenerService
import app.opentaiment.headunit.car.OverlayBubble
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
    private val widgetsStore = WidgetsStore(app)
    private val player = PlayerHub.get(app)
    private val tpmsRepo = TpmsHub.get(app)
    private val carSettingsStore = CarSettingsStore.get(app)

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

    val playerState: StateFlow<PlayerState> = player.state
    val favorites: StateFlow<List<RadioStation>> = player.favorites
    val popularStations: StateFlow<StationsState> = player.popular
    val stationSearch: StateFlow<StationsState?> = player.searchResults
    val localTracks: StateFlow<List<LocalTrack>?> = player.tracks
    val tpms: StateFlow<TpmsState> = tpmsRepo.state
    val reminders: StateFlow<List<ReminderStatus>> = vehicle.reminderStatuses
    val odometerKm: StateFlow<Double?> = vehicle.odometerKm
    val carSettings: StateFlow<CarSettings> = carSettingsStore.settings

    private val _permissions = MutableStateFlow(CarPermissions())
    /** Special permissions (brightness, overlay, steering keys everywhere) – re-checked on resume. */
    val carPermissions: StateFlow<CarPermissions> = _permissions.asStateFlow()

    private val _tpmsEnabled = MutableStateFlow(tpmsRepo.enabled)
    val tpmsEnabled: StateFlow<Boolean> = _tpmsEnabled.asStateFlow()

    private val _autoReconnect = MutableStateFlow(vehicle.autoReconnectEnabled)
    val autoReconnect: StateFlow<Boolean> = _autoReconnect.asStateFlow()

    private val _requestedScreen = MutableStateFlow<String?>(null)
    /** A screen asked for from outside (steering wheel button, notification). */
    val requestedScreen: StateFlow<String?> = _requestedScreen.asStateFlow()

    /** Ids of tiles on the home screen, in display order. */
    val tiles: StateFlow<List<String>> = tilesStore.tiles

    /** Widgets in the three home screen slots. */
    val widgets: StateFlow<List<HomeWidget>> = widgetsStore.widgets

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
        player.connect()
        refreshPermissions()
    }

    fun refreshPermissions() {
        val app = getApplication<Application>()
        _permissions.value = CarPermissions(
            brightness = Brightness.canWrite(app),
            overlay = OverlayBubble.canDraw(app),
            keysEverywhere = KeyListenerService.isEnabled(app),
            preciseLocation = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }

    fun requestScreen(screen: String?) { _requestedScreen.value = screen }
    fun consumeRequestedScreen() { _requestedScreen.value = null }

    // --- radio & music ---
    fun playStation(station: RadioStation, list: List<RadioStation>) = player.playStation(station, list)
    fun toggleFavorite(station: RadioStation) = player.toggleFavorite(station)
    fun addCustomStation(name: String, url: String) = player.addCustomStation(name, url)
    fun searchStations(query: String) = player.search(query)
    fun loadPopularStations() = player.loadPopular()
    fun playTracks(tracks: List<LocalTrack>, index: Int, shuffle: Boolean) = player.playTracks(tracks, index, shuffle)
    fun loadTracks() = player.loadTracks()
    fun storagePermission() = player.storagePermission()
    fun hasStoragePermission() = player.hasStoragePermission()
    fun radioToggle() = player.togglePlay()
    fun radioNext() = player.next()
    fun radioPrevious() = player.previous()

    // --- tyres ---
    fun hasTpmsPermission() = tpmsRepo.hasPermission()
    fun setTpmsEnabled(on: Boolean) {
        tpmsRepo.setEnabled(on)
        _tpmsEnabled.value = on
    }
    fun assignTpms(sensorId: String, wheel: Wheel) = tpmsRepo.assign(sensorId, wheel)
    fun forgetTpms(wheel: Wheel) = tpmsRepo.forgetWheel(wheel)
    fun setTpmsLimits(limits: TpmsLimits) = tpmsRepo.setLimits(limits)

    // --- service reminders ---
    fun setOdometer(km: Double) = vehicle.setOdometer(km)
    fun markReminderDone(id: String) = vehicle.markReminderDone(id)
    fun saveReminder(reminder: ServiceReminder) = vehicle.updateReminder(reminder)
    fun deleteReminder(id: String) = vehicle.deleteReminder(id)

    // --- trips export ---
    fun exportTripsCsv() { TripSharing.shareCsv(getApplication(), trips.value) }
    fun trackOf(id: Long): List<TrackPoint> = vehicle.trackOf(id)
    fun shareGpx(trip: TripRecord) { TripSharing.shareGpx(getApplication(), trip, vehicle.trackOf(trip.id)) }

    // --- car radio settings ---
    fun updateCarSettings(transform: (CarSettings) -> CarSettings) = carSettingsStore.update(transform)
    fun setAutoReconnect(on: Boolean) {
        vehicle.autoReconnectEnabled = on
        _autoReconnect.value = on
    }
    fun openBrightnessPermission() = startIntent(Brightness.permissionIntent(getApplication()))
    fun openOverlayPermission() = startIntent(OverlayBubble.permissionIntent(getApplication()))
    fun openAccessibilitySettings() = startIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun navigateHome() = carSettings.value.homeAddress.let { if (it.isBlank()) appsRepo.openNavigation() else appsRepo.navigateTo(it) }
    fun navigateWork() = carSettings.value.workAddress.let { if (it.isBlank()) appsRepo.openNavigation() else appsRepo.navigateTo(it) }
    fun searchNearby(what: String) = appsRepo.searchNearby(what)

    // --- OBD over BLE ---
    suspend fun scanBleAdapters() = vehicle.scanBleAdapters()
    fun hasBleScanPermission() = vehicle.hasBleScanPermission()

    private fun startIntent(intent: Intent) {
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
        }
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
    fun setWidget(slot: Int, widget: HomeWidget) = widgetsStore.set(slot, widget)
    fun resetWidgets() = widgetsStore.reset()

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
