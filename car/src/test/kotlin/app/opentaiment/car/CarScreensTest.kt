package app.opentaiment.car

import androidx.car.app.HandshakeInfo
import androidx.car.app.Screen
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.testing.TestCarContext
import androidx.car.app.versioning.CarAppApiLevels
import app.cash.paparazzi.Paparazzi
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.VehicleHub
import app.opentaiment.feature.vehicle.VehicleRepository
import app.opentaiment.feature.vehicle.source.DrivingSimulator
import app.opentaiment.feature.vehicle.source.ObdRequest
import app.opentaiment.feature.vehicle.source.DEMO_DTCS
import app.opentaiment.feature.vehicle.source.VehicleDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Builds every Android Auto screen in different states. Template builders throw on constraint
 * violations (missing titles, too many actions, empty rows…), which would otherwise only show
 * up in the car.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CarScreensTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private lateinit var carContext: TestCarContext
    private lateinit var vehicle: VehicleRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        carContext = TestCarContext.createCarContext(paparazzi.context)
        // A real host sets this during the handshake. Level 1 = oldest Android Auto, fallback content limits.
        carContext.updateHandshakeInfo(HandshakeInfo("com.google.android.projection.gearhead", CarAppApiLevels.LEVEL_1))
        vehicle = VehicleHub.get(carContext)
        vehicle.disconnect()
    }

    @After
    fun tearDown() {
        vehicle.disconnect()
        Dispatchers.resetMain()
    }

    /** ~76 s of the demo drive delivered instantly, then the "car" stays connected and answers DTC requests. */
    private val simulatedCar = VehicleDataSource { requests ->
        flow {
            val sim = DrivingSimulator(seed = 3)
            repeat(380) { emit(sim.next(200)) }
            while (true) {
                when (val r = requests.receive()) {
                    is ObdRequest.ReadDtcs -> r.result.complete(DEMO_DTCS)
                    is ObdRequest.ClearDtcs -> r.result.complete(Unit)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            awaitCancellation()
        }
    }

    private fun allScreens(): List<Screen> = listOf(
        HomeCarScreen(carContext), DriveCarScreen(carContext), StatsCarScreen(carContext),
        DiagnosticsCarScreen(carContext), ConfirmClearCarScreen(carContext), TripsCarScreen(carContext),
        RaceCarScreen(carContext), ConnectionCarScreen(carContext),
    )

    private fun rows(t: Template): List<Row> = when (t) {
        is ListTemplate -> t.singleList!!.items.map { it as Row }
        is PaneTemplate -> t.pane.rows
        else -> emptyList()
    }

    @Test
    fun everyScreenBuildsWhenDisconnected() {
        val t = allScreens().map { it.onGetTemplate() }
        assertTrue(t[0] is GridTemplate)
        assertEquals(6, (t[0] as GridTemplate).singleList!!.items.size)
        // Live-data screens explain that there is no car instead of showing empty numbers.
        assertTrue(t[1] is MessageTemplate)
        assertTrue(t[2] is MessageTemplate)
        assertTrue(t[3] is MessageTemplate)
        assertTrue(t[4] is MessageTemplate)
        assertTrue(t[5] is ListTemplate)
        assertTrue(t[6] is PaneTemplate)
        assertTrue(t[7] is ListTemplate)
    }

    @Test
    fun everyScreenBuildsWhileDriving() {
        vehicle.start("Test car", demo = false, source = simulatedCar)

        assertTrue(vehicle.connection.value is ConnectionState.Connected)
        assertTrue(vehicle.stats.value.hasData)
        assertTrue("diagnostics: ${vehicle.diagnostics.value}", vehicle.diagnostics.value is DiagnosticsState.Ready)

        val t = allScreens().map { it.onGetTemplate() }
        val drive = t[1] as PaneTemplate
        assertTrue(rows(drive).first().title.toString().endsWith("km/h"))
        assertTrue(t[2] is ListTemplate)
        val diagnostics = rows(t[3])
        assertEquals("Check Engine świeci", diagnostics.first().title.toString())
        assertTrue(diagnostics.any { it.title.toString().startsWith("P0420") })
        assertTrue(t[6] is PaneTemplate)
        // Home tiles show live values.
        val home = (t[0] as GridTemplate).singleList!!.items
        assertTrue(home.isNotEmpty())
    }

    @Test
    fun newestHostWithConstraintManager() {
        carContext.updateHandshakeInfo(HandshakeInfo("com.google.android.projection.gearhead", CarAppApiLevels.getLatest()))
        vehicle.start("Test car", demo = false, source = simulatedCar)
        allScreens().forEach { it.onGetTemplate() }
    }

    @Test
    fun alertIsTheFirstThingOnTheDriveScreen() {
        val overheating = VehicleDataSource { _ ->
            flow {
                repeat(5) { i ->
                    emit(app.opentaiment.feature.vehicle.data.ObdSample(i * 500L, speedKmh = 50.0, rpm = 2000.0, coolantTempC = 115.0))
                }
                awaitCancellation()
            }
        }
        vehicle.start("Test car", demo = false, source = overheating)
        val first = rows(DriveCarScreen(carContext).onGetTemplate()).first()
        assertEquals("⚠ Silnik się przegrzewa", first.title.toString())
    }

    @Test
    fun finishedTripLandsInHistoryAndOnTheTripsScreen() {
        val before = vehicle.trips.value.size
        vehicle.start("Test car", demo = false, source = simulatedCar)
        vehicle.disconnect()
        assertEquals(before + 1, vehicle.trips.value.size)
        assertTrue(rows(TripsCarScreen(carContext).onGetTemplate()).size >= 3)
    }
}
