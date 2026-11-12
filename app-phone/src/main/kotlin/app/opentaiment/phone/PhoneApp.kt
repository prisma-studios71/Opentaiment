package app.opentaiment.phone

import androidx.compose.ui.graphics.Color
import app.opentaiment.core.ui.OtTheme
import app.opentaiment.core.ui.OtBackground
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opentaiment.core.ui.AppearanceController
import app.opentaiment.core.ui.AppearanceSettings
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.VehicleHub
import app.opentaiment.feature.vehicle.data.TripRecord
import app.opentaiment.feature.vehicle.data.TripSharing
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.race.RaceState
import app.opentaiment.feature.vehicle.ui.DiagnosticsActions
import app.opentaiment.feature.vehicle.ui.DiagnosticsScreen
import app.opentaiment.feature.vehicle.ui.DriveScreen
import app.opentaiment.feature.vehicle.ui.ObdConnectActions
import app.opentaiment.feature.vehicle.ui.RaceActions
import app.opentaiment.feature.vehicle.ui.RaceScreen
import app.opentaiment.feature.vehicle.ui.SettingsScreen
import app.opentaiment.feature.vehicle.ui.StatsActions
import app.opentaiment.feature.vehicle.ui.StatsScreen
import app.opentaiment.feature.vehicle.ui.TripsActions
import app.opentaiment.feature.vehicle.ui.TripsScreen
import app.opentaiment.feature.vehicle.ui.rememberObdConnector

enum class Tab(val label: String, val icon: ImageVector) {
    DRIVE("Jazda", Icons.Rounded.Speed),
    STATS("Statystyki", Icons.Rounded.Timeline),
    DIAGNOSTICS("Błędy", Icons.Rounded.Build),
    TRIPS("Trasy", Icons.Rounded.List),
    MORE("Więcej", Icons.Rounded.MoreHoriz),
}

enum class MorePage { MENU, RACE, SETTINGS }

/** Everything the phone UI shows, as plain values – keeps [PhoneContent] previewable and testable. */
class PhoneState(
    val connection: ConnectionState,
    val stats: VehicleStats,
    val profile: VehicleProfile,
    val diagnostics: DiagnosticsState,
    val trips: List<TripRecord>,
    val race: RaceState,
    val appearance: AppearanceSettings,
)

class PhoneActions(
    val connect: ObdConnectActions,
    val onDisconnect: () -> Unit,
    val onResetTrip: () -> Unit,
    val onProfileChange: (VehicleProfile) -> Unit,
    val onReadDtcs: () -> Unit,
    val onClearDtcs: () -> Unit,
    val onSearchCode: (String) -> Unit,
    val onDeleteTrip: (Long) -> Unit,
    val onClearTrips: () -> Unit,
    val onExportTrips: (() -> Unit)? = null,
    val race: (onBack: () -> Unit) -> RaceActions,
    val onAppearanceChange: (AppearanceSettings) -> Unit,
)

@Composable
fun PhoneApp(appearance: AppearanceController) {
    val context = LocalContext.current
    val vehicle = remember { VehicleHub.get(context) }
    val connection by vehicle.connection.collectAsStateWithLifecycle()
    val stats by vehicle.stats.collectAsStateWithLifecycle()
    val profile by vehicle.profile.collectAsStateWithLifecycle()
    val diagnostics by vehicle.diagnostics.collectAsStateWithLifecycle()
    val trips by vehicle.trips.collectAsStateWithLifecycle()
    val race by vehicle.race.collectAsStateWithLifecycle()
    val look by appearance.state.collectAsStateWithLifecycle()

    val actions = remember(vehicle) {
        PhoneActions(
            connect = ObdConnectActions(
                pairedDevices = vehicle::pairedDevices,
                hasBluetoothPermission = vehicle::hasBluetoothPermission,
                onConnect = vehicle::connect,
                onDemo = vehicle::startDemo,
                onOpenBluetoothSettings = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                scanBle = vehicle::scanBleAdapters,
                hasBleScanPermission = vehicle::hasBleScanPermission,
            ),
            onExportTrips = { TripSharing.shareCsv(context, vehicle.trips.value) },
            onDisconnect = vehicle::disconnect,
            onResetTrip = vehicle::resetTrip,
            onProfileChange = vehicle::updateProfile,
            onReadDtcs = vehicle::readDtcs,
            onClearDtcs = vehicle::clearDtcs,
            onSearchCode = { code ->
                val uri = android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode("$code kod błędu OBD"))
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            onDeleteTrip = vehicle::deleteTrip,
            onClearTrips = vehicle::clearTrips,
            race = { back ->
                RaceActions(
                    onBack = back,
                    hasGpsPermission = vehicle::hasGpsPermission,
                    onStartGps = { vehicle.startRaceGps() },
                    onStopGps = vehicle::stopRaceGps,
                    onSetFinishLine = { vehicle.setFinishLineHere() },
                    onReset = vehicle::resetRace,
                )
            },
            onAppearanceChange = appearance::update,
        )
    }

    PhoneContent(
        PhoneState(connection, stats, profile, diagnostics, trips, race, look.settings),
        actions,
    )
}

@Composable
fun PhoneContent(
    state: PhoneState,
    actions: PhoneActions,
    initialTab: Tab = Tab.DRIVE,
    initialMorePage: MorePage = MorePage.MENU,
    initialDrivePage: Int = 0,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var more by rememberSaveable { mutableStateOf(initialMorePage) }
    BackHandler(enabled = tab == Tab.MORE && more != MorePage.MENU) { more = MorePage.MENU }

    OtBackground(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            NavigationBar(containerColor = OtTheme.style.barColor) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = {
                            tab = t
                            if (t == Tab.MORE) more = MorePage.MENU
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        val connect = rememberObdConnector(actions.connect)
        when (tab) {
            Tab.DRIVE -> DriveScreen(state.connection, state.stats, connect, actions.connect.onDemo, content, initialDrivePage)
            Tab.STATS -> StatsScreen(
                state.connection, state.stats, state.profile,
                StatsActions(null, actions.connect, actions.onDisconnect, actions.onResetTrip, actions.onProfileChange),
                content,
            )
            Tab.DIAGNOSTICS -> DiagnosticsScreen(
                state.connection, state.diagnostics,
                DiagnosticsActions(null, actions.connect, actions.onReadDtcs, actions.onClearDtcs, actions.onSearchCode),
                content,
            )
            Tab.TRIPS -> TripsScreen(state.trips, TripsActions(null, actions.onDeleteTrip, actions.onClearTrips, onExportCsv = actions.onExportTrips), content)
            Tab.MORE -> when (more) {
                MorePage.MENU -> MoreMenu(onRace = { more = MorePage.RACE }, onSettings = { more = MorePage.SETTINGS }, modifier = content)
                MorePage.RACE -> RaceScreen(state.race, state.stats, actions.race { more = MorePage.MENU }, content)
                MorePage.SETTINGS -> SettingsScreen(
                    state.appearance, actions.onAppearanceChange, state.profile, actions.onProfileChange,
                    onBack = { more = MorePage.MENU }, modifier = content,
                )
            }
        }
    }
    }
}

@Composable
private fun MoreMenu(onRace: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Więcej", style = MaterialTheme.typography.headlineMedium)
        MenuItem(Icons.Rounded.Flag, "Wyścig", "G-meter, okrążenia GPS, 0–100, ¼ mili", onRace)
        MenuItem(Icons.Rounded.Settings, "Ustawienia", "Wygląd, tryb nocny, cena paliwa, dane auta", onSettings)
        Spacer(Modifier.height(8.dp))
        OtCard(Modifier.fillMaxWidth()) {
            Text("Android Auto", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Opentaiment ma swoją ikonę w menu aplikacji Android Auto: jazda, statystyki, błędy, trasy i wyścig na ekranie auta. " +
                    "Muzyka i nawigacja zostają w Android Auto jak zawsze.\n\n" +
                    "Apka spoza Sklepu Play: w Android Auto otwórz Ustawienia → stuknij 10× w „Wersja” → menu ⋮ → " +
                    "Ustawienia dla programistów → włącz „Nieznane źródła”. Potem Opentaiment pojawi się w aucie.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    OtCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}
