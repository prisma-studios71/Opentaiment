package app.opentaiment.phone

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.opentaiment.core.ui.Accent
import app.opentaiment.core.ui.AppearanceSettings
import app.opentaiment.core.ui.OpentaimentTheme
import app.opentaiment.core.ui.UiStyle
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.data.TripCalculator
import app.opentaiment.feature.vehicle.data.TripRecord
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.race.GPoint
import app.opentaiment.feature.vehicle.race.RaceState
import app.opentaiment.feature.vehicle.source.DEMO_DTCS
import app.opentaiment.feature.vehicle.source.DrivingSimulator
import app.opentaiment.feature.vehicle.ui.ObdConnectActions
import app.opentaiment.feature.vehicle.ui.RaceActions
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/** Phone UI fed with a simulated drive. Record: ./gradlew :app-phone:recordPaparazziDebug */
class PhoneScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val demo = ConnectionState.Connected("Tryb demo", demo = true)

    private fun drivenStats(steps: Int = 380): VehicleStats {
        val sim = DrivingSimulator(seed = 3)
        val calc = TripCalculator(VehicleProfile(fuelPricePerL = 6.49))
        repeat(steps) { calc.add(sim.next(200)) }
        return calc.current
    }

    private fun sampleTrips(): List<TripRecord> {
        val zone = ZoneId.systemDefault()
        fun at(d: Int, h: Int) = LocalDateTime.of(2026, 11, d, h, 15).atZone(zone).toInstant().toEpochMilli()
        fun t(start: Long, km: Double, min: Int, fuel: Double, eco: Int, s100: Double? = null) = TripRecord(
            start, start + min * 60_000L, km, min * 60_000L, (min * 0.9 * 60_000).toLong(), fuel, 6.49,
            minOf(135.0, 60 + km), eco, s100, null, demo = false,
        )
        return listOf(
            t(at(9, 17), 23.4, 31, 1.62, 84),
            t(at(9, 7), 21.9, 38, 1.83, 71),
            t(at(8, 14), 148.2, 92, 9.10, 88, 10.9),
            t(at(6, 18), 6.1, 14, 0.71, 52),
        )
    }

    private fun sampleRace(): RaceState {
        val trail = (0 until 40).map { i -> GPoint(0.55 * sin(i / 6.0), 0.35 * cos(i / 5.0)) }
        return RaceState(
            gpsActive = true, hasFix = true, hasFinishLine = true,
            laps = listOf(84.512, 82.904, 81.733), bestLapSec = 81.733, currentLapSec = 37.2,
            g = trail.last(), maxLateralG = 0.71, maxAccelG = 0.36, maxBrakeG = 0.84, trail = trail,
        )
    }

    private fun state(stats: VehicleStats = drivenStats(), appearance: AppearanceSettings = AppearanceSettings()) = PhoneState(
        connection = demo,
        stats = stats,
        profile = VehicleProfile(fuelPricePerL = 6.49),
        diagnostics = DiagnosticsState.Ready(DEMO_DTCS),
        trips = sampleTrips(),
        race = sampleRace(),
        appearance = appearance,
    )

    private val actions = PhoneActions(
        connect = ObdConnectActions({ emptyList() }, { true }, {}, {}, {}),
        onDisconnect = {}, onResetTrip = {}, onProfileChange = {}, onReadDtcs = {}, onClearDtcs = {},
        onSearchCode = {}, onDeleteTrip = {}, onClearTrips = {},
        race = { back -> RaceActions(back, { true }, {}, {}, {}, {}) },
        onAppearanceChange = {},
    )

    private fun snap(
        tab: Tab,
        more: MorePage = MorePage.MENU,
        drivePage: Int = 0,
        night: Boolean = false,
        state: PhoneState = state(),
    ) {
        paparazzi.snapshot {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoOpRegistryOwner) {
                OpentaimentTheme(accent = state.appearance.accent, night = night, style = state.appearance.style) {
                    PhoneContent(state, actions, initialTab = tab, initialMorePage = more, initialDrivePage = drivePage)
                }
            }
        }
    }

    @Test fun drive() = snap(Tab.DRIVE)
    @Test fun driveEco() = snap(Tab.DRIVE, drivePage = 1)
    @Test fun driveNight() = snap(Tab.DRIVE, night = true)
    @Test fun driveWithAlert() = snap(
        Tab.DRIVE,
        state = state(
            drivenStats().let {
                it.copy(
                    alerts = listOf(
                        app.opentaiment.feature.vehicle.data.VehicleAlert(
                            app.opentaiment.feature.vehicle.data.AlertType.OVERHEAT,
                            "Płyn chłodniczy 112 °C. Zjedź w bezpieczne miejsce i zgaś silnik.",
                        ),
                    ),
                )
            },
        ),
    )
    @Test fun stats() = snap(Tab.STATS)
    @Test fun diagnostics() = snap(Tab.DIAGNOSTICS)
    @Test fun trips() = snap(Tab.TRIPS)
    @Test fun more() = snap(Tab.MORE)
    @Test fun race() = snap(Tab.MORE, more = MorePage.RACE)
    @Test fun settingsGreen() = snap(
        Tab.MORE, more = MorePage.SETTINGS,
        state = state(appearance = AppearanceSettings(accent = Accent.GREEN)),
    )

    private fun styled(style: UiStyle, accent: Accent = Accent.BLUE) = state(appearance = AppearanceSettings(style = style, accent = accent))

    @Test fun driveCockpit() = snap(Tab.DRIVE, state = styled(UiStyle.COCKPIT))
    @Test fun statsCockpit() = snap(Tab.STATS, state = styled(UiStyle.COCKPIT))
    @Test fun driveGlass() = snap(Tab.DRIVE, state = styled(UiStyle.GLASS, Accent.PURPLE))
    @Test fun statsGlass() = snap(Tab.STATS, state = styled(UiStyle.GLASS, Accent.PURPLE))
    @Test fun driveBigType() = snap(Tab.DRIVE, state = styled(UiStyle.BIG_TYPE, Accent.YELLOW))
    @Test fun statsBigType() = snap(Tab.STATS, state = styled(UiStyle.BIG_TYPE, Accent.YELLOW))
    @Test fun driveRetro() = snap(Tab.DRIVE, state = styled(UiStyle.RETRO, Accent.GREEN))
    @Test fun statsRetro() = snap(Tab.STATS, state = styled(UiStyle.RETRO, Accent.GREEN))
    @Test fun settingsStyles() = snap(Tab.MORE, more = MorePage.SETTINGS, state = styled(UiStyle.COCKPIT))

    private object NoOpRegistryOwner : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
        }
    }
}
