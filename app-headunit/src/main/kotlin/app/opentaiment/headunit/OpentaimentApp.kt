package app.opentaiment.headunit

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
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
import app.opentaiment.feature.vehicle.ui.dtcCountLabel
import app.opentaiment.feature.vehicle.ui.shortSummary
import app.opentaiment.headunit.tiles.BuiltinTile
import app.opentaiment.headunit.tiles.TileEditorActions
import app.opentaiment.headunit.tiles.TileEditorScreen
import app.opentaiment.headunit.tiles.TileIds
import app.opentaiment.headunit.tiles.TileOption

private enum class Screen { Home, Apps, Stats, Diagnostics, EditTiles }

@Composable
fun OpentaimentApp(viewModel: HomeViewModel, isDefaultHome: () -> Boolean) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

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
            Screen.Home -> HomeScreen(
                now = now,
                weather = weather,
                nowPlaying = nowPlaying,
                hasMediaAccess = hasMediaAccess,
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
                            BuiltinTile.STATS -> screen = Screen.Stats
                            BuiltinTile.DIAGNOSTICS -> screen = Screen.Diagnostics
                            BuiltinTile.APPS -> screen = Screen.Apps
                            BuiltinTile.BLUETOOTH -> viewModel.openBluetooth()
                            BuiltinTile.SETTINGS -> viewModel.openSettings()
                            BuiltinTile.PHONE -> viewModel.openDialer()
                        }
                    },
                    onApp = { viewModel.launch(it) },
                ),
                actions = HomeActions(
                    onRefreshWeather = viewModel::refreshWeather,
                    onGrantMediaAccess = viewModel::grantMediaAccess,
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onOpenPlayer = viewModel::openPlayer,
                    onEditTiles = { screen = Screen.EditTiles },
                ),
                modifier = content,
            )
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
                    ),
                    modifier = content,
                )
            }
        }
    }
}

/** Live one-liners under some tiles. */
internal fun tileSubtitle(tile: BuiltinTile, connection: ConnectionState, stats: VehicleStats, diagnostics: DiagnosticsState): String? =
    when (tile) {
        BuiltinTile.STATS -> if (connection is ConnectionState.Connected) stats.shortSummary() else null
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
