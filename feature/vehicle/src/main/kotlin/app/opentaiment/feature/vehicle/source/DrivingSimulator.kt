package app.opentaiment.feature.vehicle.source

import app.opentaiment.feature.vehicle.data.ObdSample
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * A fake car for demo mode and screenshots: repeats a ~65 s loop of launch, motorway cruise,
 * slowdown, overtaking and braking, producing OBD-like values (speed in whole km/h, like a real ECU).
 * Deterministic for a given seed.
 */
class DrivingSimulator(seed: Long = 7L) {

    private data class Phase(val durationS: Double, val targetKmh: Double, val accel: Double)

    private val phases = listOf(
        Phase(4.0, 0.0, 3.0),     // waiting at the lights (finishes braking to a full stop)
        Phase(15.0, 110.0, 3.0),  // full throttle launch
        Phase(8.0, 110.0, 0.0),   // cruise
        Phase(6.0, 60.0, 1.8),    // slow down
        Phase(8.0, 60.0, 0.0),
        Phase(7.0, 100.0, 1.7),   // overtake
        Phase(8.0, 100.0, 0.0),
        Phase(9.0, 0.0, 3.4),     // brake to a stop
    )

    private val random = Random(seed)
    private var timeMs = 0L
    private var speedMs = 0.0
    private var phaseIndex = 0
    private var phaseTime = 0.0
    private var coolant = 48.0
    private var fuelLevel = 63.0

    fun next(dtMs: Long): ObdSample {
        timeMs += dtMs
        val dt = dtMs / 1000.0
        val phase = phases[phaseIndex]
        val target = phase.targetKmh / 3.6

        val diff = target - speedMs
        val accel = if (phase.accel == 0.0) {
            random.nextGaussian() * 0.15
        } else {
            val available = if (diff > 0) phase.accel * max(0.45, 1.0 - speedMs / 60.0) else phase.accel
            sign(diff) * min(available, abs(diff) / dt)
        }
        speedMs = max(0.0, speedMs + accel * dt)
        if (phase.targetKmh == 0.0 && speedMs < 0.3) speedMs = 0.0

        phaseTime += dt
        if (phaseTime >= phase.durationS) {
            phaseTime = 0.0
            phaseIndex = (phaseIndex + 1) % phases.size
        }

        val kmh = speedMs * 3.6
        val rpmPerKmh = when {
            kmh < 20 -> 120.0
            kmh < 40 -> 70.0
            kmh < 60 -> 48.0
            kmh < 85 -> 36.0
            else -> 28.0
        }
        val rpm = max(780.0, kmh * rpmPerKmh) + random.nextGaussian() * 12
        val throttle = when {
            accel > 0.2 -> 35 + accel * 18
            accel < -0.2 -> 0.0
            kmh < 1 -> 0.0
            else -> 14 + kmh * 0.08
        }.coerceIn(0.0, 100.0)
        val maf = 2.6 + rpm / 1000 * (throttle / 100) * 20 + rpm / 1000 * 1.2
        coolant = min(90.0, coolant + dt * 0.4)
        fuelLevel -= dt * 0.002

        return ObdSample(
            timeMs = timeMs,
            speedKmh = kmh.roundToInt().toDouble(),
            rpm = rpm,
            mafGs = maf,
            mapKpa = 30 + throttle * 0.7,
            intakeTempC = 24.0,
            coolantTempC = coolant,
            oilTempC = coolant - 7,
            ambientTempC = 8.0,
            throttlePct = throttle,
            engineLoadPct = (throttle * 0.9 + 12).coerceIn(0.0, 100.0),
            fuelLevelPct = fuelLevel,
            voltage = 14.1 + random.nextGaussian() * 0.03,
        )
    }
}
