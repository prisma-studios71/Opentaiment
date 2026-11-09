package app.opentaiment.headunit

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.opentaiment.core.ui.OpentaimentTheme
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.data.TripCalculator
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.source.DrivingSimulator
import app.opentaiment.feature.vehicle.ui.StatsActions
import app.opentaiment.feature.vehicle.ui.StatsScreen
import app.opentaiment.feature.vehicle.ui.shortSummary
import app.opentaiment.feature.weather.Weather
import app.opentaiment.feature.weather.WeatherState
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/** Stats screen fed by the demo simulator. Record: ./gradlew :app-headunit:recordPaparazziDebug */
class StatsScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.LANDSCAPE),
        showSystemUi = false,
    )

    private val actions = StatsActions(
        onBack = {}, pairedDevices = { emptyList() }, hasBluetoothPermission = { true },
        onConnect = {}, onDemo = {}, onDisconnect = {}, onResetTrip = {}, onProfileChange = {},
        onOpenBluetoothSettings = {},
    )

    /** ~1 lap of the demo drive, then 7 s into the next full-throttle launch (0–100 timer running). */
    private fun drivenStats(): VehicleStats {
        val sim = DrivingSimulator(seed = 3)
        val calc = TripCalculator()
        repeat(380) { calc.add(sim.next(200)) }
        return calc.current
    }

    @Test
    fun statsDemoDriving() = render {
        StatsScreen(
            connection = ConnectionState.Connected("Tryb demo", demo = true),
            stats = drivenStats(),
            profile = VehicleProfile(),
            actions = actions,
            modifier = Modifier.fillMaxSize().padding(20.dp),
        )
    }

    @Test
    fun statsPortraitDemoDriving() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.PORTRAIT))
        render {
            StatsScreen(
                connection = ConnectionState.Connected("OBDII", demo = false),
                stats = drivenStats(),
                profile = VehicleProfile(),
                actions = actions,
                modifier = Modifier.fillMaxSize().padding(20.dp),
            )
        }
    }

    @Test
    fun statsNotConnected() = render {
        StatsScreen(
            connection = ConnectionState.Disconnected,
            stats = VehicleStats(),
            profile = VehicleProfile(),
            actions = actions,
            modifier = Modifier.fillMaxSize().padding(20.dp),
        )
    }

    @Test
    fun statsConnectionFailed() = render {
        StatsScreen(
            connection = ConnectionState.Failed("Auto nie odpowiada (NO DATA). Czy zapłon jest włączony?"),
            stats = VehicleStats(),
            profile = VehicleProfile(),
            actions = actions,
            modifier = Modifier.fillMaxSize().padding(20.dp),
        )
    }

    @Test
    fun homeWithLiveObd() = render {
        HomeScreen(
            now = LocalDateTime.of(2026, 11, 9, 18, 42),
            weather = WeatherState.Ready(Weather(2.4, 3, 14.0, -1.0, 5.0, true, "Warszawa")),
            nowPlaying = null,
            hasMediaAccess = true,
            actions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
            vehicleSummary = drivenStats().shortSummary(),
            modifier = Modifier.fillMaxSize().padding(20.dp),
        )
    }

    private fun render(content: @Composable () -> Unit) {
        paparazzi.snapshot {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoOpRegistryOwner) {
                OpentaimentTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private object NoOpRegistryOwner : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) = Unit
        }
    }
}
