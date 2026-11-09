package app.opentaiment.headunit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.data.DtcReport
import app.opentaiment.feature.vehicle.data.TripCalculator
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.source.DEMO_DTCS
import app.opentaiment.feature.vehicle.source.DrivingSimulator
import app.opentaiment.feature.vehicle.ui.DiagnosticsActions
import app.opentaiment.feature.vehicle.ui.DiagnosticsScreen
import app.opentaiment.feature.vehicle.ui.StatsActions
import app.opentaiment.feature.vehicle.ui.StatsScreen
import app.opentaiment.feature.weather.Weather
import app.opentaiment.feature.weather.WeatherState
import app.opentaiment.headunit.tiles.TileEditorActions
import app.opentaiment.headunit.tiles.TileEditorScreen
import app.opentaiment.headunit.tiles.TileIds
import app.opentaiment.headunit.tiles.HomeWidget
import app.opentaiment.headunit.tiles.WidgetEditorActions
import app.opentaiment.headunit.tiles.WidgetEditorScreen
import app.opentaiment.headunit.tiles.WidgetSlots
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/** Stats, diagnostics and tile editor screens. Record: ./gradlew :app-headunit:recordPaparazziDebug */
class StatsScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.LANDSCAPE),
        showSystemUi = false,
    )

    private val pad = Modifier.fillMaxSize().padding(20.dp)
    private val demo = ConnectionState.Connected("Tryb demo", demo = true)

    private val statsActions = StatsActions(
        onBack = {}, connect = Fixtures.connectActions, onDisconnect = {}, onResetTrip = {}, onProfileChange = {},
    )
    private val diagActions = DiagnosticsActions(
        onBack = {}, connect = Fixtures.connectActions, onRead = {}, onClear = {}, onSearchCode = {},
    )

    /** ~1 lap of the demo drive, then 7 s into the next full-throttle launch (0–100 timer running). */
    private fun drivenStats(): VehicleStats {
        val sim = DrivingSimulator(seed = 3)
        val calc = TripCalculator()
        repeat(380) { calc.add(sim.next(200)) }
        return calc.current
    }

    @Test
    fun statsDemoDriving() = paparazzi.render {
        StatsScreen(demo, drivenStats(), VehicleProfile(), statsActions, pad)
    }

    @Test
    fun statsPortraitDemoDriving() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.PORTRAIT))
        paparazzi.render {
            StatsScreen(ConnectionState.Connected("OBDII", demo = false), drivenStats(), VehicleProfile(), statsActions, pad)
        }
    }

    @Test
    fun statsNotConnected() = paparazzi.render {
        StatsScreen(ConnectionState.Disconnected, VehicleStats(), VehicleProfile(), statsActions, pad)
    }

    @Test
    fun statsConnectionFailed() = paparazzi.render {
        StatsScreen(
            ConnectionState.Failed("Auto nie odpowiada (NO DATA). Czy zapłon jest włączony?"),
            VehicleStats(), VehicleProfile(), statsActions, pad,
        )
    }

    @Test
    fun diagnosticsWithCodes() = paparazzi.render {
        DiagnosticsScreen(demo, DiagnosticsState.Ready(DEMO_DTCS), diagActions, pad)
    }

    @Test
    fun diagnosticsClean() = paparazzi.render {
        DiagnosticsScreen(demo, DiagnosticsState.Ready(DtcReport(false, emptyList(), emptyList(), emptyList())), diagActions, pad)
    }

    @Test
    fun diagnosticsReading() = paparazzi.render {
        DiagnosticsScreen(demo, DiagnosticsState.Reading, diagActions, pad)
    }

    @Test
    fun diagnosticsPortraitWithCodes() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.PORTRAIT))
        paparazzi.render { DiagnosticsScreen(demo, DiagnosticsState.Ready(DEMO_DTCS), diagActions, pad) }
    }

    @Test
    fun homeWithLiveObdAndCustomTiles() = paparazzi.render {
        val apps = Fixtures.fakeApps()
        HomeScreen(
            widgets = WidgetSlots.DEFAULT,
            data = WidgetData(
                LocalDateTime.of(2026, 11, 9, 18, 42),
                WeatherState.Ready(Weather(2.4, 3, 14.0, -1.0, 5.0, true, "Warszawa")),
                nowPlaying = null,
                hasMediaAccess = true,
            ),
            tiles = Fixtures.tiles(
                ids = listOf("nav", "music", "stats", "diag", TileIds.app("pkg.waze"), TileIds.app("pkg.spotify"), "apps"),
                apps = apps,
                connection = demo,
                stats = drivenStats(),
                diagnostics = DiagnosticsState.Ready(DEMO_DTCS),
            ),
            actions = Fixtures.homeActions,
            modifier = pad,
        )
    }

    @Test
    fun tileEditor() = paparazzi.render {
        val ids = listOf("nav", "music", "stats", "diag", TileIds.app("pkg.spotify"), "apps")
        val (current, available) = tileOptions(ids, Fixtures.fakeApps())
        TileEditorScreen(current, available, TileEditorActions({}, {}, { _, _ -> }, {}, {}), pad)
    }

    @Test
    fun homeWithCarWidgets() = paparazzi.render {
        val stats = drivenStats()
        HomeScreen(
            widgets = listOf(HomeWidget.SPEED, HomeWidget.ECO, HomeWidget.CONSUMPTION),
            data = WidgetData(
                LocalDateTime.of(2026, 11, 9, 18, 42),
                WeatherState.Ready(Weather(2.4, 3, 14.0, -1.0, 5.0, true, "Warszawa")),
                nowPlaying = null,
                hasMediaAccess = true,
                connection = demo,
                stats = stats,
                diagnostics = DiagnosticsState.Ready(DEMO_DTCS),
            ),
            tiles = Fixtures.tiles(connection = demo, stats = stats, diagnostics = DiagnosticsState.Ready(DEMO_DTCS)),
            actions = Fixtures.homeActions,
            modifier = pad,
        )
    }

    @Test
    fun homeWithTripAndDiagnosticWidgets() = paparazzi.render {
        val stats = drivenStats()
        HomeScreen(
            widgets = listOf(HomeWidget.CLOCK, HomeWidget.DIAGNOSTICS, HomeWidget.TRIP),
            data = WidgetData(
                LocalDateTime.of(2026, 11, 9, 18, 42),
                WeatherState.Loading,
                nowPlaying = null,
                hasMediaAccess = true,
                connection = demo,
                stats = stats,
                diagnostics = DiagnosticsState.Ready(DEMO_DTCS),
            ),
            tiles = Fixtures.tiles(connection = demo, stats = stats),
            actions = Fixtures.homeActions,
            modifier = pad,
        )
    }

    @Test
    fun widgetEditor() = paparazzi.render {
        WidgetEditorScreen(
            widgets = listOf(HomeWidget.CLOCK, HomeWidget.ECO, HomeWidget.MUSIC),
            selectedSlot = 1,
            actions = WidgetEditorActions({}, {}, {}, {}, {}),
            modifier = pad,
        )
    }
}
