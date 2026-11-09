package app.opentaiment.feature.vehicle.source

import android.os.SystemClock
import app.opentaiment.feature.vehicle.data.DtcReport
import app.opentaiment.feature.vehicle.data.FreezeFrame
import app.opentaiment.feature.vehicle.data.Readiness
import app.opentaiment.feature.vehicle.data.ObdSample
import app.opentaiment.feature.vehicle.obd.Elm327
import app.opentaiment.feature.vehicle.obd.ObdPid
import app.opentaiment.feature.vehicle.obd.ObdTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

fun interface VehicleDataSource {
    /**
     * Cold flow: collecting it connects, cancelling it disconnects.
     * [requests] carries one-off commands (read/clear trouble codes) to run on the same link.
     */
    fun samples(requests: ReceiveChannel<ObdRequest>): Flow<ObdSample>
}

/** Runs [request] with [read]/[clear] and completes its deferred, never throwing except on cancel. */
internal suspend fun handleRequest(
    request: ObdRequest,
    read: suspend () -> DtcReport,
    clear: suspend () -> Unit,
) {
    try {
        when (request) {
            is ObdRequest.ReadDtcs -> request.result.complete(read())
            is ObdRequest.ClearDtcs -> request.result.complete(clear())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        when (request) {
            is ObdRequest.ReadDtcs -> request.result.completeExceptionally(e)
            is ObdRequest.ClearDtcs -> request.result.completeExceptionally(e)
        }
    }
}

/** Live data from a real car through an ELM327 adapter. */
class ObdDataSource(
    private val openTransport: suspend () -> ObdTransport,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : VehicleDataSource {

    override fun samples(requests: ReceiveChannel<ObdRequest>): Flow<ObdSample> = flow {
        val transport = openTransport()
        try {
            val elm = Elm327(transport)
            elm.initialize()
            val supported = elm.supportedPids()
            fun supports(pid: ObdPid) = pid.code in supported

            // Speed and RPM are read every cycle (acceleration needs them), cheap extras next,
            // slowly changing temperatures/levels only every few cycles.
            val fast = listOf(ObdPid.MAF, ObdPid.INTAKE_MAP, ObdPid.FUEL_RATE, ObdPid.THROTTLE, ObdPid.ENGINE_LOAD)
                .filter(::supports)
            val slow = listOf(ObdPid.COOLANT_TEMP, ObdPid.INTAKE_TEMP, ObdPid.OIL_TEMP, ObdPid.AMBIENT_TEMP, ObdPid.FUEL_LEVEL)
                .filter(::supports)

            var cycle = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                requests.tryReceive().getOrNull()?.let { request ->
                    handleRequest(request, read = { elm.readDtcs() }, clear = { elm.clearDtcs() })
                }
                val t = clock()
                val speed = elm.read(ObdPid.SPEED)
                val rpm = elm.read(ObdPid.RPM)
                val values = HashMap<ObdPid, Double>()
                for (pid in fast) elm.read(pid)?.let { values[pid] = it }
                var voltage: Double? = null
                if (cycle % SLOW_EVERY_CYCLES == 0L) {
                    for (pid in slow) elm.read(pid)?.let { values[pid] = it }
                    voltage = elm.readVoltage()
                }
                emit(
                    ObdSample(
                        timeMs = t,
                        speedKmh = speed,
                        rpm = rpm,
                        mafGs = values[ObdPid.MAF],
                        mapKpa = values[ObdPid.INTAKE_MAP],
                        fuelRateLh = values[ObdPid.FUEL_RATE],
                        throttlePct = values[ObdPid.THROTTLE],
                        engineLoadPct = values[ObdPid.ENGINE_LOAD],
                        coolantTempC = values[ObdPid.COOLANT_TEMP],
                        intakeTempC = values[ObdPid.INTAKE_TEMP],
                        oilTempC = values[ObdPid.OIL_TEMP],
                        ambientTempC = values[ObdPid.AMBIENT_TEMP],
                        fuelLevelPct = values[ObdPid.FUEL_LEVEL],
                        voltage = voltage,
                    ),
                )
                cycle++
            }
        } finally {
            transport.close()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val SLOW_EVERY_CYCLES = 10L
    }
}

/** Demo mode: a simulated drive with a couple of fake trouble codes, so everything can be tried without a car. */
class SimulatedDataSource(private val stepMs: Long = 200) : VehicleDataSource {
    override fun samples(requests: ReceiveChannel<ObdRequest>): Flow<ObdSample> = flow {
        val simulator = DrivingSimulator(seed = System.currentTimeMillis())
        val base = SystemClock.elapsedRealtime()
        var codes = DEMO_DTCS
        while (true) {
            delay(stepMs)
            requests.tryReceive().getOrNull()?.let { request ->
                handleRequest(
                    request,
                    read = { delay(800); codes }, // feels like talking to a real ECU
                    clear = {
                        delay(800)
                        // Like a real car: codes gone, but self-tests reset to "not ready".
                        codes = DtcReport(
                            milOn = false, emptyList(), emptyList(), emptyList(),
                            readiness = Readiness.decode(intArrayOf(0x00, 0x07, 0xE5, 0xE5)),
                        )
                    },
                )
            }
            val s = simulator.next(stepMs)
            emit(s.copy(timeMs = base + s.timeMs))
        }
    }
}

val DEMO_DTCS = DtcReport(
    milOn = true,
    stored = listOf("P0420", "P0301"),
    pending = listOf("P0171"),
    permanent = listOf("P0420"),
    freezeFrame = FreezeFrame(
        dtc = "P0301",
        values = linkedMapOf(
            ObdPid.RPM to 2350.0, ObdPid.SPEED to 54.0, ObdPid.COOLANT_TEMP to 88.0,
            ObdPid.ENGINE_LOAD to 62.0, ObdPid.THROTTLE to 31.0, ObdPid.SHORT_FUEL_TRIM to 4.7,
            ObdPid.LONG_FUEL_TRIM to 9.4, ObdPid.INTAKE_MAP to 68.0, ObdPid.INTAKE_TEMP to 21.0,
        ),
    ),
    readiness = Readiness.decode(intArrayOf(0x83, 0x07, 0xE5, 0x05)),
)
