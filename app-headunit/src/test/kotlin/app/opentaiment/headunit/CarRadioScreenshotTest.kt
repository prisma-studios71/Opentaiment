package app.opentaiment.headunit

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.opentaiment.core.ui.Accent
import app.opentaiment.core.ui.OpentaimentTheme
import app.opentaiment.core.ui.OtBackground
import app.opentaiment.core.ui.UiStyle
import app.opentaiment.feature.player.LocalTrack
import app.opentaiment.feature.player.PlaybackKind
import app.opentaiment.feature.player.PlayerActions
import app.opentaiment.feature.player.PlayerScreen
import app.opentaiment.feature.player.PlayerScreenData
import app.opentaiment.feature.player.PlayerState
import app.opentaiment.feature.player.RadioStation
import app.opentaiment.feature.player.StationsState
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.data.ServiceReminder
import app.opentaiment.feature.vehicle.data.ServiceReminders
import app.opentaiment.feature.vehicle.data.TrackPoint
import app.opentaiment.feature.vehicle.data.TripCalculator
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.source.DrivingSimulator
import app.opentaiment.feature.vehicle.tpms.TpmsLimits
import app.opentaiment.feature.vehicle.tpms.TpmsParser
import app.opentaiment.feature.vehicle.tpms.TpmsReading
import app.opentaiment.feature.vehicle.tpms.TpmsState
import app.opentaiment.feature.vehicle.tpms.Wheel
import app.opentaiment.feature.vehicle.tpms.WheelStatus
import app.opentaiment.feature.vehicle.ui.GaugesScreen
import app.opentaiment.feature.vehicle.ui.RouteMap
import app.opentaiment.feature.vehicle.ui.ServiceActions
import app.opentaiment.feature.vehicle.ui.ServiceScreen
import app.opentaiment.feature.vehicle.ui.TpmsActions
import app.opentaiment.feature.vehicle.ui.TpmsScreen
import app.opentaiment.feature.weather.Weather
import app.opentaiment.feature.weather.WeatherState
import app.opentaiment.headunit.car.CarPermissions
import app.opentaiment.headunit.car.CarSettings
import app.opentaiment.headunit.car.CarSettingsActions
import app.opentaiment.headunit.car.CarSettingsSection
import app.opentaiment.headunit.car.KeyAction
import app.opentaiment.headunit.car.SpeedVolumeLevel
import app.opentaiment.headunit.tiles.HomeWidget
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin

/** The car-radio additions: radio, gauges, tyres, service, settings and the new widgets. */
class CarRadioScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.LANDSCAPE),
        showSystemUi = false,
    )

    private val pad = Modifier.fillMaxSize().padding(20.dp)
    private val demo = ConnectionState.Connected("Tryb demo", demo = true)

    private fun snap(
        night: Boolean = false,
        style: UiStyle = UiStyle.CLASSIC,
        accent: Accent = Accent.BLUE,
        content: @Composable () -> Unit,
    ) {
        paparazzi.snapshot {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoOpRegistry) {
                OpentaimentTheme(accent = accent, night = night, style = style) {
                    OtBackground(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }

    private fun stats(): VehicleStats {
        val sim = DrivingSimulator(seed = 7)
        val calc = TripCalculator(VehicleProfile())
        repeat(420) { calc.add(sim.next(200)) }
        return calc.current.copy(oilTempC = 96.0, boostKpa = 64.0)
    }

    private val stations = listOf(
        RadioStation("1", "RMF FM", "https://x/1", tags = listOf("pop", "hity"), bitrateKbps = 128, codec = "MP3"),
        RadioStation("2", "Radio ZET", "https://x/2", tags = listOf("pop"), bitrateKbps = 128, codec = "MP3"),
        RadioStation("3", "Polskie Radio Trójka", "https://x/3", tags = listOf("rock", "publiczne"), bitrateKbps = 96, codec = "AAC"),
        RadioStation("4", "Radio 357", "https://x/4", tags = listOf("muzyka", "rozmowy"), bitrateKbps = 128, codec = "AAC"),
        RadioStation("5", "Open FM - 500 Party", "https://x/5", tags = listOf("dance"), bitrateKbps = 128, codec = "MP3"),
        RadioStation("6", "Radio Nowy Świat", "https://x/6", tags = listOf("muzyka"), bitrateKbps = 192, codec = "MP3"),
        RadioStation("7", "Chillizet", "https://x/7", tags = listOf("chillout"), bitrateKbps = 128, codec = "AAC"),
    )
    private val playing = PlayerState(
        connected = true, kind = PlaybackKind.RADIO, title = "Dawid Podsiadło – Małomiasteczkowy", subtitle = "Radio 357",
        isPlaying = true, currentId = "radio:4",
    )

    private val playerActions = PlayerActions(
        {}, { _, _ -> }, {}, { _, _ -> }, {}, {}, { _, _, _ -> }, {}, "", {}, {}, {},
    )

    @Test
    fun radio() = snap {
        PlayerScreen(
            PlayerScreenData(playing, stations.take(4), StationsState.Ready(stations), null, null, hasStoragePermission = true),
            playerActions, pad,
        )
    }

    @Test
    fun musicFiles() = snap {
        fun t(id: Long, title: String, artist: String, folder: String, sec: Long) = LocalTrack(id, title, artist, null, sec * 1000, "u$id", folder)
        val tracks = listOf(
            t(1, "Kwiat jabłoni – Dziś późno pójdę spać", "Kwiat Jabłoni", "Na trasę", 215),
            t(2, "Sanah – Szampan", "Sanah", "Na trasę", 189),
            t(3, "Męskie Granie Orkiestra – Wszystko, czego dziś chcę", "Męskie Granie", "Na trasę", 252),
            t(4, "Daft Punk – Contact", "Daft Punk", "Elektronika", 381),
            t(5, "Justice – D.A.N.C.E.", "Justice", "Elektronika", 242),
        )
        PlayerScreen(
            PlayerScreenData(
                PlayerState(true, PlaybackKind.LOCAL, "Sanah – Szampan", "Sanah", isPlaying = true, currentId = "file:2", durationMs = 189_000, positionMs = 71_000),
                emptyList(), StationsState.Loading, null, tracks, hasStoragePermission = true,
            ),
            playerActions, pad, initialTab = 1,
        )
    }

    @Test
    fun gauges() = snap { GaugesScreen(demo, stats(), VehicleProfile(), {}, {}, {}, pad) }

    private fun reading(bar: Double, temp: Double, battery: Int = 88) = TpmsReading("80:00:00:00:00:01", bar, temp, battery, null, false, timeMs = 1_000)

    private val tpmsState = TpmsState(
        scanning = true,
        wheels = listOf(
            WheelStatus(Wheel.FRONT_LEFT, reading(2.3, 21.0), emptyList()),
            WheelStatus(Wheel.FRONT_RIGHT, reading(2.2, 22.0), emptyList()),
            WheelStatus(Wheel.REAR_LEFT, reading(1.7, 20.0), TpmsParser.problems(reading(1.7, 20.0), TpmsLimits(), 1_000)),
            WheelStatus(Wheel.REAR_RIGHT, reading(2.3, 21.0, battery = 9), TpmsParser.problems(reading(2.3, 21.0, battery = 9), TpmsLimits(), 1_000)),
        ),
    )

    @Test
    fun tyres() = snap {
        TpmsScreen(tpmsState, TpmsActions({}, enabled = true, { true }, {}, { _, _ -> }, {}, {}), pad)
    }

    private val today = LocalDate.of(2026, 11, 9)
    private val reminders = ServiceReminders.statuses(
        listOf(
            ServiceReminder("oil", "Wymiana oleju", 15_000, 365, 171_200.0, today.minusDays(200).toEpochDay()),
            ServiceReminder("inspection", "Przegląd techniczny", null, 365, null, today.minusDays(352).toEpochDay()),
            ServiceReminder("insurance", "OC", null, 365, null, today.minusDays(390).toEpochDay()),
            ServiceReminder("timing", "Rozrząd", 120_000, 365 * 5, 98_000.0, today.minusDays(900).toEpochDay()),
        ),
        odometerKm = 185_640.0, today = today,
    )

    @Test
    fun service() = snap {
        ServiceScreen(reminders, 185_640.0, odometerFromCar = false, ServiceActions({}, {}, {}, {}, {}), pad)
    }

    @Test
    fun carSettings() = snap {
        Column(pad.verticalScroll(rememberScrollState())) {
            CarSettingsSection(
                CarSettings(
                    speedVolume = SpeedVolumeLevel.MEDIUM, brightnessControl = true, overlay = true,
                    homeAddress = "Marszałkowska 1, Warszawa",
                    keyBindings = mapOf(87 to KeyAction.NEXT, 88 to KeyAction.PREVIOUS, 5 to KeyAction.NAVIGATE_HOME),
                ),
                CarPermissions(brightness = true, overlay = true, keysEverywhere = false, preciseLocation = true),
                CarSettingsActions({}, {}, {}, {}, {}, {}, autoReconnect = true),
            )
        }
    }

    @Test
    fun routeMap() = snap {
        val pts = (0 until 120).map { i ->
            val a = i / 120.0 * 2 * Math.PI
            TrackPoint(i * 5_000L, 52.23 + 0.02 * sin(a) + 0.004 * sin(5 * a), 21.01 + 0.035 * cos(a), 30 + 80 * (0.5 + 0.5 * sin(3 * a)))
        }
        RouteMap(pts, Modifier.padding(40.dp).width(900.dp).fillMaxSize())
    }

    @Test
    fun homeCarRadio() = snap { homeRadio() }

    @Test
    fun homeCockpit() = snap(style = UiStyle.COCKPIT) { homeDriving() }

    @Test
    fun homeGlass() = snap(style = UiStyle.GLASS, accent = Accent.PURPLE) { homeRadio() }

    @Test
    fun homeBigType() = snap(style = UiStyle.BIG_TYPE, accent = Accent.YELLOW) { homeDriving() }

    @Test
    fun homeRetro() = snap(style = UiStyle.RETRO, accent = Accent.GREEN) { homeDriving() }

    @Test
    fun homePortraitCockpit() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.PORTRAIT))
        snap(style = UiStyle.COCKPIT) { homeDriving() }
    }

    @Test
    fun homePortraitRetro() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.PORTRAIT))
        snap(style = UiStyle.RETRO, accent = Accent.ORANGE) { homeDriving() }
    }

    @Test
    fun homeGlassNoCar() = snap(style = UiStyle.GLASS, accent = Accent.BLUE) {
        HomeScreen(
            widgets = listOf(HomeWidget.CLOCK, HomeWidget.WEATHER, HomeWidget.RADIO),
            data = WidgetData(
                LocalDateTime.of(2026, 11, 9, 21, 40), WeatherState.Loading, null, true,
                favorites = stations.take(6),
            ),
            tiles = Fixtures.tiles(),
            actions = Fixtures.homeActions,
            modifier = pad,
        )
    }

    @Test
    fun gaugesRetro() = snap(style = UiStyle.RETRO, accent = Accent.GREEN) { GaugesScreen(demo, stats(), VehicleProfile(), {}, {}, {}, pad) }

    @Composable
    private fun homeDriving() = HomeScreen(
        widgets = listOf(HomeWidget.CLOCK, HomeWidget.CONSUMPTION, HomeWidget.SPEED),
        data = WidgetData(
            LocalDateTime.of(2026, 11, 9, 8, 5),
            WeatherState.Ready(Weather(4.0, 2, 8.0, 1.0, 12.0, false, "Warszawa")),
            nowPlaying = null, hasMediaAccess = true, connection = demo, stats = stats(),
            player = playing, favorites = stations.take(6), tpms = tpmsState, tpmsEnabled = true,
        ),
        tiles = Fixtures.tiles(),
        actions = Fixtures.homeActions,
        modifier = pad,
    )

    @Composable
    private fun homeRadio() {
        HomeScreen(
            widgets = listOf(HomeWidget.NAVIGATION, HomeWidget.TPMS, HomeWidget.RADIO),
            data = WidgetData(
                LocalDateTime.of(2026, 11, 9, 8, 5),
                WeatherState.Ready(Weather(4.0, 2, 8.0, 1.0, 12.0, false, "Warszawa")),
                nowPlaying = null, hasMediaAccess = true,
                player = playing, favorites = stations.take(6), tpms = tpmsState, tpmsEnabled = true,
                homeAddress = "Marszałkowska 1, Warszawa", workAddress = "Prosta 20, Warszawa",
            ),
            tiles = Fixtures.tiles(),
            actions = Fixtures.homeActions,
            modifier = pad,
        )
    }

    @Test
    fun homeServiceWidget() = snap(night = true) {
        HomeScreen(
            widgets = listOf(HomeWidget.CLOCK, HomeWidget.SERVICE, HomeWidget.RADIO),
            data = WidgetData(
                LocalDateTime.of(2026, 11, 9, 22, 40), WeatherState.Loading, null, true,
                player = playing, favorites = stations.take(4), reminders = reminders,
            ),
            tiles = Fixtures.tiles(),
            actions = Fixtures.homeActions,
            modifier = pad,
        )
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
