package app.opentaiment.feature.vehicle.data

import kotlin.math.PI
import kotlin.math.max

/**
 * Turns a stream of raw [ObdSample]s into everything shown on the stats screen:
 * fuel consumption, acceleration, estimated power/torque, sprint times and trip totals.
 *
 * Pure Kotlin (no Android), so it is unit tested directly.
 */
class TripCalculator(var profile: VehicleProfile = VehicleProfile()) {

    private var merged = ObdSample(timeMs = 0)
    private var prevTime: Long? = null
    private var prevSpeedMs: Double? = null
    private val speedWindow = ArrayDeque<Pair<Long, Double>>()
    private val history = ArrayDeque<HistoryPoint>()
    private val sprints = listOf(SprintTimer(0, 100), SprintTimer(80, 120))
    private val quarterMile = QuarterMileTimer()
    private val eco = EcoScorer()
    private val alerts = AlertMonitor()

    private var distanceM = 0.0
    private var fuelL = 0.0
    private var durationMs = 0L
    private var movingMs = 0L
    private var maxSpeed = 0.0
    private var maxRpm = 0.0
    private var peakPowerKw = 0.0
    private var maxAccel = 0.0

    var current: VehicleStats = VehicleStats()
        private set

    fun add(sample: ObdSample): VehicleStats {
        merged = merged.mergedWith(sample)
        val t = sample.timeMs
        val dt = prevTime?.let { (t - it).coerceIn(0, MAX_GAP_MS) } ?: 0L
        val speedKmh = sample.speedKmh
        val v = speedKmh?.div(3.6)

        // Acceleration: slope of a linear fit over the last ~1.5 s. OBD speed is whole km/h,
        // so differentiating two neighbouring samples would be far too noisy.
        if (v != null) {
            speedWindow.addLast(t to v)
            while (speedWindow.size > 2 && t - speedWindow.first().first > ACCEL_WINDOW_MS) speedWindow.removeFirst()
        }
        val accel = if (v != null) slope(speedWindow) else null

        val fuel = fuelRate(merged)
        if (dt > 0) {
            durationMs += dt
            val pv = prevSpeedMs
            if (v != null && pv != null) distanceM += (v + pv) / 2 * dt / 1000.0
            if (v != null && v > 0.5) movingMs += dt
            if (fuel != null) fuelL += fuel.first * dt / 3_600_000.0
        }

        val powerKw = if (v != null && accel != null) enginePowerW(v, accel) / 1000.0 else null
        val rpm = merged.rpm
        val torque = if (powerKw != null && rpm != null && rpm > MIN_RPM_FOR_TORQUE) {
            powerKw * 1000 / (rpm * 2 * PI / 60)
        } else {
            null
        }

        if (speedKmh != null) {
            sprints.forEach { it.add(t, speedKmh) }
            quarterMile.add(t, speedKmh)
            maxSpeed = max(maxSpeed, speedKmh)
        }
        eco.add(dt, merged.speedKmh, merged.rpm, accel, profile.highRpmLimit)
        val activeAlerts = alerts.update(merged)
        sample.rpm?.let { maxRpm = max(maxRpm, it) }
        powerKw?.let { peakPowerKw = max(peakPowerKw, it) }
        accel?.let { maxAccel = max(maxAccel, it) }

        val currentSpeed = merged.speedKmh
        val instant = if (fuel != null && currentSpeed != null && currentSpeed >= MIN_SPEED_FOR_L100) {
            fuel.first / currentSpeed * 100
        } else {
            null
        }
        val avg = if (distanceM >= MIN_DISTANCE_FOR_AVG_M && fuelL > 0) fuelL / (distanceM / 1000) * 100 else null

        history.addLast(HistoryPoint(t, currentSpeed ?: 0.0, powerKw ?: 0.0))
        while (t - history.first().timeMs > HISTORY_MS) history.removeFirst()

        prevTime = t
        if (v != null) prevSpeedMs = v

        current = VehicleStats(
            hasData = true,
            speedKmh = currentSpeed,
            rpm = rpm,
            throttlePct = merged.throttlePct,
            engineLoadPct = merged.engineLoadPct,
            coolantTempC = merged.coolantTempC,
            intakeTempC = merged.intakeTempC,
            oilTempC = merged.oilTempC,
            ambientTempC = merged.ambientTempC,
            fuelLevelPct = merged.fuelLevelPct,
            voltage = merged.voltage,
            fuelRateLh = fuel?.first,
            fuelSource = fuel?.second ?: FuelSource.NONE,
            instantLPer100Km = instant,
            avgLPer100Km = avg,
            accelMs2 = accel,
            maxAccelMs2 = maxAccel,
            powerKw = powerKw,
            peakPowerKw = peakPowerKw,
            torqueNm = torque,
            sprints = sprints.map { it.state() },
            quarterMile = quarterMile.state(),
            eco = eco.result(),
            alerts = activeAlerts,
            trip = TripStats(
                distanceKm = distanceM / 1000,
                durationMs = durationMs,
                movingMs = movingMs,
                maxSpeedKmh = maxSpeed,
                maxRpm = maxRpm,
                fuelUsedL = fuelL,
                fuelCost = fuelL * profile.fuelPricePerL,
            ),
            history = history.toList(),
        )
        return current
    }

    /** Clears trip totals and sprint records but keeps live values. */
    fun resetTrip() {
        distanceM = 0.0
        fuelL = 0.0
        durationMs = 0
        movingMs = 0
        maxSpeed = 0.0
        maxRpm = 0.0
        peakPowerKw = 0.0
        maxAccel = 0.0
        sprints.forEach { it.reset() }
        quarterMile.reset()
        eco.reset()
        current = current.copy(
            avgLPer100Km = null,
            maxAccelMs2 = 0.0,
            peakPowerKw = 0.0,
            sprints = sprints.map { it.state() },
            quarterMile = quarterMile.state(),
            eco = null,
            trip = TripStats(),
        )
    }

    /** Fuel flow in l/h and where it came from, best source first. */
    internal fun fuelRate(s: ObdSample): Pair<Double, FuelSource>? {
        s.fuelRateLh?.let { return it to FuelSource.FUEL_RATE }
        val (mafGs, source) = s.mafGs?.let { it to FuelSource.MAF }
            ?: speedDensityMafGs(s)?.let { it to FuelSource.SPEED_DENSITY }
            ?: return null
        // Overrun fuel cut: throttle closed at higher revs means injectors are off,
        // even though air still flows through the engine.
        val throttle = s.throttlePct
        val rpm = s.rpm
        if (throttle != null && rpm != null && throttle < FUEL_CUT_THROTTLE_PCT && rpm > FUEL_CUT_MIN_RPM) {
            return 0.0 to source
        }
        val fuel = profile.fuelType
        return mafGs / fuel.stoichAfr / fuel.densityGPerL * 3600 to source
    }

    /** Estimates air mass flow from manifold pressure (ideal gas law) for cars without a MAF sensor. */
    internal fun speedDensityMafGs(s: ObdSample): Double? {
        val map = s.mapKpa ?: return null
        val iat = s.intakeTempC ?: return null
        val rpm = s.rpm ?: return null
        val airFlowM3s = profile.displacementL / 1000 * rpm / 120 * profile.volumetricEfficiency
        val molPerS = map * 1000 * airFlowM3s / (GAS_CONSTANT * (iat + 273.15))
        return molPerS * AIR_MOLAR_MASS_KG * 1000
    }

    /** Power needed at the wheels for this speed and acceleration, scaled up by drivetrain losses. */
    internal fun enginePowerW(vMs: Double, accelMs2: Double): Double {
        val p = profile
        val force = p.massKg * accelMs2 +
            p.massKg * GRAVITY * p.rollingResistance +
            0.5 * AIR_DENSITY * p.dragAreaM2 * vMs * vMs
        return (vMs * force).coerceAtLeast(0.0) / p.drivetrainEfficiency
    }

    private fun slope(points: Collection<Pair<Long, Double>>): Double? {
        if (points.size < 2) return null
        val t0 = points.first().first
        val n = points.size
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for ((t, v) in points) {
            val x = (t - t0) / 1000.0
            sx += x; sy += v; sxx += x * x; sxy += x * v
        }
        val denom = n * sxx - sx * sx
        return if (denom == 0.0) null else (n * sxy - sx * sy) / denom
    }

    companion object {
        const val GRAVITY = 9.81
        const val AIR_DENSITY = 1.2
        const val GAS_CONSTANT = 8.314
        const val AIR_MOLAR_MASS_KG = 0.02897
        const val ACCEL_WINDOW_MS = 1_500L
        const val HISTORY_MS = 60_000L
        const val MAX_GAP_MS = 5_000L
        const val MIN_SPEED_FOR_L100 = 5.0
        const val MIN_DISTANCE_FOR_AVG_M = 100.0
        const val MIN_RPM_FOR_TORQUE = 900.0
        const val FUEL_CUT_THROTTLE_PCT = 1.0
        const val FUEL_CUT_MIN_RPM = 1_300.0
    }
}
