package app.opentaiment.headunit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivityResultRegistryOwner
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.opentaiment.core.ui.Accent
import app.opentaiment.core.ui.AppearanceSettings
import app.opentaiment.core.ui.OpentaimentTheme
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.data.AlertType
import app.opentaiment.feature.vehicle.data.TripCalculator
import app.opentaiment.feature.vehicle.data.TripRecord
import app.opentaiment.feature.vehicle.data.VehicleAlert
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.race.GPoint
import app.opentaiment.feature.vehicle.race.RaceState
import app.opentaiment.feature.vehicle.source.DEMO_DTCS
import app.opentaiment.feature.vehicle.source.DrivingSimulator
import app.opentaiment.feature.vehicle.ui.AlertBanner
import app.opentaiment.feature.vehicle.ui.DriveScreen
import app.opentaiment.feature.vehicle.ui.RaceActions
import app.opentaiment.feature.vehicle.ui.RaceScreen
import app.opentaiment.feature.vehicle.ui.SettingsScreen
import app.opentaiment.feature.vehicle.ui.TripsActions
import app.opentaiment.feature.vehicle.ui.TripsScreen
import app.opentaiment.feature.weather.Weather
import app.opentaiment.feature.weather.WeatherState
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/** Drive / trips / race / settings on the tablet, plus night mode. */
class TabletExtrasScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.LANDSCAPE),
        showSystemUi = false,
    )

    private val pad = Modifier.fillMaxSize().padding(20.dp)
    private val demo = ConnectionState.Connected("Tryb demo", demo = true)

    private fun stats(): VehicleStats {
        val sim = DrivingSimulator(seed = 3)
        val calc = TripCalculator(VehicleProfile(fuelPricePerL = 6.49))
        repeat(380) { calc.add(sim.next(200)) }
        return calc.current
    }

    private fun snap(night: Boolean = false, accent: Accent = Accent.BLUE, content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.snapshot {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoOpRegistry) {
                OpentaimentTheme(accent = accent, night = night) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    @Test
    fun drive() = snap { DriveScreen(demo, stats(), {}, {}, pad) }

    @Test
    fun trips() = snap {
        val zone = ZoneId.systemDefault()
        fun at(d: Int, h: Int) = LocalDateTime.of(2026, 11, d, h, 15).atZone(zone).toInstant().toEpochMilli()
        fun t(start: Long, km: Double, min: Int, fuel: Double, eco: Int, s100: Double? = null, qm: Double? = null) = TripRecord(
            start, start + min * 60_000L, km, min * 60_000L, (min * 0.9 * 60_000).toLong(), fuel, 6.49,
            minOf(135.0, 60 + km), eco, s100, qm, demo = false,
        )
        TripsScreen(
            listOf(
                t(at(9, 17), 23.4, 31, 1.62, 84),
                t(at(9, 7), 21.9, 38, 1.83, 71),
                t(at(8, 14), 148.2, 92, 9.10, 88, 10.9, 17.42),
                t(at(6, 18), 6.1, 14, 0.71, 52),
            ),
            TripsActions({}, {}, {}),
            pad,
            now = at(9, 20),
        )
    }

    @Test
    fun race() = snap {
        val trail = (0 until 40).map { i -> GPoint(0.55 * sin(i / 6.0), 0.35 * cos(i / 5.0)) }
        RaceScreen(
            RaceState(
                gpsActive = true, hasFix = true, hasFinishLine = true,
                laps = listOf(84.512, 82.904, 81.733), bestLapSec = 81.733, currentLapSec = 37.2,
                g = trail.last(), maxLateralG = 0.71, maxAccelG = 0.36, maxBrakeG = 0.84, trail = trail,
            ),
            stats(),
            RaceActions({}, { true }, {}, {}, {}, {}),
            pad,
        )
    }

    @Test
    fun settings() = snap(accent = Accent.PURPLE) {
        SettingsScreen(AppearanceSettings(accent = Accent.PURPLE), {}, VehicleProfile(fuelPricePerL = 6.49), {}, onBack = {}, modifier = pad)
    }

    @Test
    fun homeNightWithAlert() = snap(night = true) {
        val s = stats().copy(
            alerts = listOf(VehicleAlert(AlertType.LOW_FUEL, "Zostało 8 % paliwa – czas na stację.")),
        )
        Column(pad) {
            AlertBanner(s.alerts, Modifier.padding(bottom = 16.dp))
            HomeScreen(
                now = LocalDateTime.of(2026, 11, 9, 22, 14),
                weather = WeatherState.Ready(Weather(1.2, 0, 6.0, -2.0, 5.0, false, "Warszawa")),
                nowPlaying = null,
                hasMediaAccess = true,
                tiles = Fixtures.tiles(connection = demo, stats = s, diagnostics = DiagnosticsState.Ready(DEMO_DTCS)),
                actions = Fixtures.homeActions,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    private object NoOpRegistry : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                input: I,
                options: androidx.core.app.ActivityOptionsCompat?,
            ) = Unit
        }
    }
}
