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

private enum class Screen { Home, Apps, Drive, Stats, Diagnostics, Trips, Race, Settings, EditTiles, EditWidgets }

@Composable
fun OpentaimentApp(viewModel: HomeViewModel, isDefaultHome: () -> Boolean) {
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
    BackHandler(enabled = screen == Screen.Home && isDefaultHome()) { }

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

    val connectActions = remember(viewModel) {
        ObdConnectActions(
            pairedDevices = viewModel::pairedObdDevices,
            hasBluetoothPermission = viewModel::hasBluetoothPermission,
            onConnect = viewModel::connectObd,
            onDemo = viewModel::startVehicleDemo,
            onOpenBluetoothSettings = { viewModel.openBluetooth() },
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
                data = WidgetData(now, weather, nowPlaying, hasMediaAccess, vehicleConnection, vehicleStats, diagnostics, trips),
                tiles = homeTiles(
                    ids = tileIds,
                    apps = apps,
                    connection = vehicleConnection,
                    stats = vehicleStats,
                    diagnostics = diagnostics,
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
                TripsActions({ screen = Screen.Home }, viewModel::deleteTrip, viewModel::clearTrips),
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
                OtCard(Modifier.fillMaxWidth()) {
                    Text("Tablet", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
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

/** Live one-liners under some tiles. */
internal fun tileSubtitle(tile: BuiltinTile, connection: ConnectionState, stats: VehicleStats, diagnostics: DiagnosticsState): String? =
    when (tile) {
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
                subtitle = tileSubtitle(tile, connection, stats, diagnostics),
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
