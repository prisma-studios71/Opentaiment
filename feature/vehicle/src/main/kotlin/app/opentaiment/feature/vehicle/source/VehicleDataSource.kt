package app.opentaiment.feature.vehicle.source

import android.os.SystemClock
import app.opentaiment.feature.vehicle.data.ObdSample
import app.opentaiment.feature.vehicle.obd.Elm327
import app.opentaiment.feature.vehicle.obd.ObdPid
import app.opentaiment.feature.vehicle.obd.ObdTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

fun interface VehicleDataSource {
    /** Cold flow: collecting it connects, cancelling it disconnects. */
    fun samples(): Flow<ObdSample>
}

/** Live data from a real car through an ELM327 adapter. */
class ObdDataSource(
    private val openTransport: suspend () -> ObdTransport,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : VehicleDataSource {

    override fun samples(): Flow<ObdSample> = flow {
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

/** Demo mode: a simulated drive, so the stats screen can be tried without a car. */
class SimulatedDataSource(private val stepMs: Long = 200) : VehicleDataSource {
    override fun samples(): Flow<ObdSample> = flow {
        val simulator = DrivingSimulator(seed = System.currentTimeMillis())
        val base = SystemClock.elapsedRealtime()
        while (true) {
            delay(stepMs)
            val s = simulator.next(stepMs)
            emit(s.copy(timeMs = base + s.timeMs))
        }
    }
}
