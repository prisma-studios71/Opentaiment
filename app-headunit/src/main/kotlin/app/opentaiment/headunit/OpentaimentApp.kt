package app.opentaiment.headunit

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import app.opentaiment.headunit.car.DefaultHome
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.ui.AlertBanner
import app.opentaiment.feature.vehicle.ui.DriveScreen
import app.opentaiment.feature.vehicle.ui.RaceActions
import app.opentaiment.feature.vehicle.ui.RaceScreen
import app.opentaiment.feature.vehicle.ui.SettingsScreen
import app.opentaiment.feature.vehicle.ui.TripsActions
import app.opentaiment.feature.vehicle.ui.TripsScreen
import app.opentaiment.feature.vehicle.ui.rememberObdConnector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opentaiment.core.ui.TileIcon
import app.opentaiment.feature.apps.AppDrawer
import app.opentaiment.feature.apps.AppEntry
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.ui.DiagnosticsActions
import app.opentaiment.feature.vehicle.ui.DiagnosticsScreen
import app.opentaiment.feature.vehicle.ui.ObdConnectActions
import app.opentaiment.feature.vehicle.ui.StatsActions
import app.opentaiment.feature.vehicle.ui.StatsScreen
import app.opentaiment.feature.vehicle.data.dtcCountLabel
import app.opentaiment.feature.vehicle.ui.shortSummary
import app.opentaiment.headunit.tiles.BuiltinTile
import app.opentaiment.headunit.tiles.TileEditorActions
import app.opentaiment.headunit.tiles.TileEditorScreen
import app.opentaiment.headunit.tiles.TileIds
import app.opentaiment.headunit.tiles.TileOption
import app.opentaiment.headunit.tiles.WidgetEditorActions
import app.opentaiment.headunit.tiles.WidgetEditorScreen
import app.opentaiment.feature.player.PlayerActions
import app.opentaiment.feature.player.PlayerScreen
import app.opentaiment.feature.player.PlayerScreenData
import app.opentaiment.feature.player.PlayerState
import app.opentaiment.feature.vehicle.data.ReminderLevel
import app.opentaiment.feature.vehicle.data.ReminderStatus
import app.opentaiment.feature.vehicle.tpms.TpmsState
import app.opentaiment.feature.vehicle.ui.GaugesScreen
import app.opentaiment.feature.vehicle.ui.ServiceActions
import app.opentaiment.feature.vehicle.ui.ServiceScreen
import app.opentaiment.feature.vehicle.ui.TpmsActions
import app.opentaiment.feature.vehicle.ui.TpmsScreen
import app.opentaiment.headunit.car.CarSettingsActions
import app.opentaiment.headunit.car.CarSettingsSection

private enum class Screen { Home, Apps, Drive, Stats, Diagnostics, Trips, Race, Settings, EditTiles, EditWidgets, Player, Gauges, Tpms, Service }

@Composable
fun OpentaimentApp(viewModel: HomeViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var editedSlot by rememberSaveable { mutableIntStateOf(0) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshWeather() }

    LaunchedEffect(Unit) {
        if (!viewModel.hasLocationPermission()) {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // Back from any sub-screen returns home; on home a launcher swallows Back.
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    val isDefaultHome by viewModel.isDefaultHome.collectAsStateWithLifecycle()
    val offerDefaultHome by viewModel.offerDefaultHome.collectAsStateWithLifecycle()
    BackHandler(enabled = screen == Screen.Home && isDefaultHome) { }

    val context = LocalContext.current
    val homeSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshDefaultHome()
    }
    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshDefaultHome()
        // Some ROMs refuse the dialog (or the user said no before): offer the settings page instead.
        if (!viewModel.isDefaultHome.value) homeSettings.launch(DefaultHome.settingsIntent(context))
    }
    val makeDefaultHome: () -> Unit = {
        viewModel.dismissDefaultHomeOffer()
        val role = DefaultHome.roleRequestIntent(context)
        try {
            if (role != null) homeRole.launch(role) else homeSettings.launch(DefaultHome.settingsIntent(context))
        } catch (e: android.content.ActivityNotFoundException) {
            viewModel.openSettings()
        }
    }
    if (offerDefaultHome && screen == Screen.Home) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDefaultHomeOffer,
            title = { Text("Ustawić Opentaiment jako ekran główny?") },
            text = {
                Text(
                    "Wtedy po włączeniu radia i po wciśnięciu „Home” zawsze wita Cię Opentaiment, jak fabryczne radio. " +
                        "Zawsze możesz to zmienić w ustawieniach.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = { TextButton(onClick = makeDefaultHome) { Text("Ustaw") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDefaultHomeOffer) { Text("Nie teraz") } },
        )
    }

    val now by viewModel.now.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val hasMediaAccess by viewModel.hasMediaAccess.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val vehicleConnection by viewModel.vehicleConnection.collectAsStateWithLifecycle()
    val vehicleStats by viewModel.vehicleStats.collectAsStateWithLifecycle()
    val vehicleProfile by viewModel.vehicleProfile.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val tileIds by viewModel.tiles.collectAsStateWithLifecycle()
    val widgets by viewModel.widgets.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val race by viewModel.race.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val popularStations by viewModel.popularStations.collectAsStateWithLifecycle()
    val stationSearch by viewModel.stationSearch.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val tpms by viewModel.tpms.collectAsStateWithLifecycle()
    val tpmsEnabled by viewModel.tpmsEnabled.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val odometer by viewModel.odometerKm.collectAsStateWithLifecycle()
    val carSettings by viewModel.carSettings.collectAsStateWithLifecycle()
    val carPermissions by viewModel.carPermissions.collectAsStateWithLifecycle()
    val autoReconnect by viewModel.autoReconnect.collectAsStateWithLifecycle()
    val requested by viewModel.requestedScreen.collectAsStateWithLifecycle()

    LaunchedEffect(requested) {
        when (requested) {
            MainActivity.SCREEN_PLAYER -> screen = Screen.Player
            MainActivity.SCREEN_DRIVE -> screen = Screen.Drive
        }
        if (requested != null) viewModel.consumeRequestedScreen()
    }

    val finePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermissions() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val connectActions = remember(viewModel) {
        ObdConnectActions(
            pairedDevices = viewModel::pairedObdDevices,
            hasBluetoothPermission = viewModel::hasBluetoothPermission,
            onConnect = viewModel::connectObd,
            onDemo = viewModel::startVehicleDemo,
            onOpenBluetoothSettings = { viewModel.openBluetooth() },
            scanBle = viewModel::scanBleAdapters,
            hasBleScanPermission = viewModel::hasBleScanPermission,
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val content = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)

        when (screen) {
            Screen.Home -> Column(content) {
                AlertBanner(vehicleStats.alerts.takeIf { vehicleConnection is ConnectionState.Connected }.orEmpty(), Modifier.padding(bottom = 16.dp))
                HomeScreen(
                widgets = widgets,
                data = WidgetData(
                    now, weather, nowPlaying, hasMediaAccess, vehicleConnection, vehicleStats, diagnostics, trips,
                    player = playerState, favorites = favorites, tpms = tpms, tpmsEnabled = tpmsEnabled,
                    reminders = reminders, homeAddress = carSettings.homeAddress, workAddress = carSettings.workAddress,
                ),
                tiles = homeTiles(
                    ids = tileIds,
                    apps = apps,
                    connection = vehicleConnection,
                    stats = vehicleStats,
                    diagnostics = diagnostics,
                    extras = TileExtras(playerState, tpms.takeIf { tpmsEnabled }, reminders),
                    onBuiltin = { tile ->
                        when (tile) {
                            BuiltinTile.NAVIGATION -> viewModel.openNavigation()
                            BuiltinTile.MUSIC -> viewModel.openPlayer()
                            BuiltinTile.DRIVE -> screen = Screen.Drive
                            BuiltinTile.STATS -> screen = Screen.Stats
                            BuiltinTile.TRIPS -> screen = Screen.Trips
                            BuiltinTile.RACE -> screen = Screen.Race
                            BuiltinTile.DIAGNOSTICS -> screen = Screen.Diagnostics
                            BuiltinTile.APPS -> screen = Screen.Apps
                            BuiltinTile.BLUETOOTH -> viewModel.openBluetooth()
                            BuiltinTile.SETTINGS -> screen = Screen.Settings
                            BuiltinTile.ANDROID_SETTINGS -> viewModel.openSettings()
                            BuiltinTile.PHONE -> viewModel.openDialer()
                            BuiltinTile.RADIO -> screen = Screen.Player
                            BuiltinTile.GAUGES -> screen = Screen.Gauges
                            BuiltinTile.TPMS -> screen = Screen.Tpms
                            BuiltinTile.SERVICE -> screen = Screen.Service
                            BuiltinTile.NAV_HOME -> viewModel.navigateHome()
                        }
                    },
                    onApp = { viewModel.launch(it) },
                ),
                actions = HomeActions(
                    widgets = WidgetActions(
                        onRefreshWeather = viewModel::refreshWeather,
                        onGrantMediaAccess = viewModel::grantMediaAccess,
                        onPlayPause = viewModel::playPause,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onOpenPlayer = viewModel::openPlayer,
                        onOpenDrive = { screen = Screen.Drive },
                        onOpenStats = { screen = Screen.Stats },
                        onOpenDiagnostics = { screen = Screen.Diagnostics },
                        onOpenTrips = { screen = Screen.Trips },
                        onOpenRadio = { screen = Screen.Player },
                        onPlayStation = viewModel::playStation,
                        onRadioToggle = viewModel::radioToggle,
                        onRadioNext = viewModel::radioNext,
                        onRadioPrevious = viewModel::radioPrevious,
                        onOpenNavigation = { viewModel.openNavigation() },
                        onNavigateHome = { viewModel.navigateHome() },
                        onNavigateWork = { viewModel.navigateWork() },
                        onSearchNearby = { viewModel.searchNearby(it) },
                        onOpenTpms = { screen = Screen.Tpms },
                        onOpenService = { screen = Screen.Service },
                    ),
                    onEditTiles = { screen = Screen.EditTiles },
                    onEditWidgets = { slot ->
                        editedSlot = slot
                        screen = Screen.EditWidgets
                    },
                ),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            }
            Screen.Drive -> Column(content) {
                ScreenBackRow("Jazda") { screen = Screen.Home }
                DriveScreen(vehicleConnection, vehicleStats, rememberObdConnector(connectActions), connectActions.onDemo, Modifier.weight(1f))
            }
            Screen.Trips -> TripsScreen(
                trips,
                TripsActions(
                    { screen = Screen.Home }, viewModel::deleteTrip, viewModel::clearTrips,
                    onExportCsv = viewModel::exportTripsCsv,
                    trackOf = viewModel::trackOf,
                    onShareGpx = viewModel::shareGpx,
                ),
                content,
            )
            Screen.Race -> RaceScreen(
                race, vehicleStats,
                RaceActions(
                    onBack = { screen = Screen.Home },
                    hasGpsPermission = viewModel::hasGpsPermission,
                    onStartGps = viewModel::startRaceGps,
                    onStopGps = viewModel::stopRaceGps,
                    onSetFinishLine = viewModel::setFinishLineHere,
                    onReset = viewModel::resetRace,
                ),
                content,
            )
            Screen.Settings -> SettingsScreen(
                appearance.settings, viewModel::updateAppearance, vehicleProfile, viewModel::updateVehicleProfile,
                onBack = { screen = Screen.Home },
                modifier = content,
            ) {
                CarSettingsSection(
                    settings = carSettings,
                    permissions = carPermissions,
                    actions = CarSettingsActions(
                        onChange = viewModel::updateCarSettings,
                        onGrantBrightness = viewModel::openBrightnessPermission,
                        onGrantOverlay = viewModel::openOverlayPermission,
                        onOpenAccessibility = viewModel::openAccessibilitySettings,
                        onGrantLocation = { finePermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        onAutoReconnect = viewModel::setAutoReconnect,
                        autoReconnect = autoReconnect,
                    ),
                )
                OtCard(Modifier.fillMaxWidth()) {
                    Text("Tablet", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isDefaultHome) "Opentaiment jest ekranem głównym ✓"
                            else "Opentaiment nie jest ekranem głównym – po starcie radia pokaże się inny launcher.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isDefaultHome) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        if (isDefaultHome) OutlinedButton(onClick = { homeSettings.launch(DefaultHome.settingsIntent(context)) }) { Text("Zmień") }
                        else Button(onClick = makeDefaultHome) { Text("Ustaw jako ekran główny") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { screen = Screen.EditWidgets }) { Text("Edytuj widżety") }
                        OutlinedButton(onClick = { screen = Screen.EditTiles }) { Text("Edytuj kafelki") }
                        OutlinedButton(onClick = { viewModel.openSettings() }) { Text("Ustawienia Androida") }
                    }
                }
            }
            Screen.Apps -> AppDrawer(
                apps = apps,
                onLaunch = {
                    viewModel.launch(it)
                    screen = Screen.Home
                },
                onBack = { screen = Screen.Home },
                modifier = content,
            )
            Screen.Stats -> StatsScreen(
                connection = vehicleConnection,
                stats = vehicleStats,
                profile = vehicleProfile,
                actions = StatsActions(
                    onBack = { screen = Screen.Home },
                    connect = connectActions,
                    onDisconnect = viewModel::disconnectObd,
                    onResetTrip = viewModel::resetTrip,
                    onProfileChange = viewModel::updateVehicleProfile,
                ),
                modifier = content,
            )
            Screen.Diagnostics -> DiagnosticsScreen(
                connection = vehicleConnection,
                state = diagnostics,
                actions = DiagnosticsActions(
                    onBack = { screen = Screen.Home },
                    connect = connectActions,
                    onRead = viewModel::readDtcs,
                    onClear = viewModel::clearDtcs,
                    onSearchCode = { viewModel.searchDtc(it) },
                ),
                modifier = content,
            )
            Screen.EditTiles -> {
                val (current, available) = tileOptions(tileIds, apps)
                TileEditorScreen(
                    current = current,
                    available = available,
                    actions = TileEditorActions(
                        onAdd = viewModel::addTile,
                        onRemove = viewModel::removeTile,
                        onMove = viewModel::moveTile,
                        onReset = viewModel::resetTiles,
                        onDone = { screen = Screen.Home },
                        onEditWidgets = { screen = Screen.EditWidgets },
                    ),
                    modifier = content,
                )
            }
            Screen.Player -> PlayerScreen(
                data = PlayerScreenData(
                    playerState, favorites, popularStations, stationSearch, localTracks, viewModel.hasStoragePermission(),
                ),
                actions = PlayerActions(
                    onBack = { screen = Screen.Home },
                    onPlayStation = viewModel::playStation,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onAddCustom = viewModel::addCustomStation,
                    onSearch = viewModel::searchStations,
                    onLoadPopular = viewModel::loadPopularStations,
                    onPlayTracks = viewModel::playTracks,
                    onLoadTracks = viewModel::loadTracks,
                    storagePermission = viewModel.storagePermission(),
                    onTogglePlay = viewModel::radioToggle,
                    onNext = viewModel::radioNext,
                    onPrevious = viewModel::radioPrevious,
                ),
                modifier = content,
            )
            Screen.Gauges -> GaugesScreen(
                vehicleConnection, vehicleStats, vehicleProfile,
                onConnect = rememberObdConnector(connectActions), onDemo = connectActions.onDemo,
                onBack = { screen = Screen.Home }, modifier = content,
            )
            Screen.Tpms -> TpmsScreen(
                tpms,
                TpmsActions(
                    onBack = { screen = Screen.Home },
                    enabled = tpmsEnabled,
                    hasPermission = viewModel::hasTpmsPermission,
                    onEnable = viewModel::setTpmsEnabled,
                    onAssign = viewModel::assignTpms,
                    onForget = viewModel::forgetTpms,
                    onLimits = viewModel::setTpmsLimits,
                ),
                content,
            )
            Screen.Service -> ServiceScreen(
                reminders, odometer, odometerFromCar = vehicleStats.odometerKm != null,
                actions = ServiceActions(
                    onBack = { screen = Screen.Home },
                    onSetOdometer = viewModel::setOdometer,
                    onDone = viewModel::markReminderDone,
                    onSave = viewModel::saveReminder,
                    onDelete = viewModel::deleteReminder,
                ),
                modifier = content,
            )
            Screen.EditWidgets -> WidgetEditorScreen(
                widgets = widgets,
                selectedSlot = editedSlot,
                actions = WidgetEditorActions(
                    onSelectSlot = { editedSlot = it },
                    onPick = { viewModel.setWidget(editedSlot, it) },
                    onReset = viewModel::resetWidgets,
                    onEditTiles = { screen = Screen.EditTiles },
                    onDone = { screen = Screen.Home },
                ),
                modifier = content,
            )
        }
    }
}

private fun Double.fmtKmh() = String.format(java.util.Locale("pl"), "%.0f", this)
private fun Double.fmtBar() = String.format(java.util.Locale("pl"), "%.1f", this)

@Composable
private fun ScreenBackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(64.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Wróć", Modifier.size(36.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

/** Live data for the newer tiles (radio, tyres, service). */
internal class TileExtras(
    val player: PlayerState = PlayerState(),
    val tpms: TpmsState? = null,
    val reminders: List<ReminderStatus> = emptyList(),
)

/** Live one-liners under some tiles. */
internal fun tileSubtitle(
    tile: BuiltinTile,
    connection: ConnectionState,
    stats: VehicleStats,
    diagnostics: DiagnosticsState,
    extras: TileExtras = TileExtras(),
): String? =
    when (tile) {
        BuiltinTile.RADIO -> extras.player.takeIf { it.isPlaying }?.title
        BuiltinTile.TPMS -> extras.tpms?.let { t ->
            t.problems.firstOrNull()?.let { (w, p) -> "⚠️ ${w.short}: ${p.label.lowercase()}" }
                ?: t.wheels.mapNotNull { it.reading?.pressureBar }.takeIf { it.isNotEmpty() }
                    ?.let { list -> "${list.min().fmtBar()}–${list.max().fmtBar()} bar" }
        }
        BuiltinTile.SERVICE -> extras.reminders.firstOrNull { it.level != ReminderLevel.OK }?.let { "${it.reminder.name}: ${it.label}" }
        BuiltinTile.STATS -> if (connection is ConnectionState.Connected) stats.shortSummary() else null
        BuiltinTile.DRIVE -> if (connection is ConnectionState.Connected) stats.speedKmh?.let { "${it.fmtKmh()} km/h" } else null
        BuiltinTile.DIAGNOSTICS -> when (diagnostics) {
            is DiagnosticsState.Ready -> diagnostics.report.totalCount.let { if (it == 0) "Brak błędów" else "⚠️ ${dtcCountLabel(it)}" }
            DiagnosticsState.Reading -> "Sprawdzam…"
            else -> null
        }
        else -> null
    }

internal fun homeTiles(
    ids: List<String>,
    apps: List<AppEntry>,
    connection: ConnectionState,
    stats: VehicleStats,
    diagnostics: DiagnosticsState,
    onBuiltin: (BuiltinTile) -> Unit,
    onApp: (String) -> Unit,
    extras: TileExtras = TileExtras(),
): List<HomeTile> = ids.mapNotNull { id ->
    val pkg = TileIds.packageOf(id)
    if (pkg != null) {
        // Hidden until the app list is loaded, or if the app was uninstalled.
        apps.firstOrNull { it.packageName == pkg }?.let { app ->
            HomeTile(id, app.label, TileIcon.Bitmap(app.icon), onClick = { onApp(pkg) })
        }
    } else {
        BuiltinTile.byId(id)?.let { tile ->
            HomeTile(
                id = id,
                label = tile.label,
                icon = TileIcon.Vector(tile.icon),
                subtitle = tileSubtitle(tile, connection, stats, diagnostics, extras),
                onClick = { onBuiltin(tile) },
            )
        }
    }
}

internal fun tileOptions(ids: List<String>, apps: List<AppEntry>): Pair<List<TileOption>, List<TileOption>> {
    val builtinOptions = BuiltinTile.entries.map { TileOption(it.id, it.label, TileIcon.Vector(it.icon), isApp = false) }
    val appOptions = apps.map { TileOption(TileIds.app(it.packageName), it.label, TileIcon.Bitmap(it.icon), isApp = true) }
    val all = (builtinOptions + appOptions).associateBy { it.id }
    val current = ids.mapNotNull { all[it] }
    val available = (builtinOptions + appOptions).filter { it.id !in ids }
    return current to available
}
