package app.opentaiment.feature.vehicle.race

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS reading. [bearingDeg] / [speedMs] may be missing when the receiver doesn't report them. */
data class GpsFix(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val speedMs: Double?,
    val bearingDeg: Double?,
    val accuracyM: Double? = null,
)

data class GPoint(val lateral: Double, val longitudinal: Double)

data class RaceState(
    val gpsActive: Boolean = false,
    val hasFix: Boolean = false,
    val hasFinishLine: Boolean = false,
    val laps: List<Double> = emptyList(),
    val bestLapSec: Double? = null,
    val currentLapSec: Double? = null,
    val g: GPoint = GPoint(0.0, 0.0),
    val maxLateralG: Double = 0.0,
    val maxAccelG: Double = 0.0,
    val maxBrakeG: Double = 0.0,
    /** Recent g readings for the "friction circle" trail, oldest first. */
    val trail: List<GPoint> = emptyList(),
)

/**
 * GPS lap timer + g-meter.
 *
 * Laps: the driver marks the start/finish line at the current position; a lap is counted every
 * time the car comes back within [lineRadiusM] after having been far enough away.
 *
 * G-meter: lateral g = speed × yaw rate (from the change of GPS heading); longitudinal g comes
 * from OBD acceleration when available (much smoother), otherwise from GPS speed.
 */
class RaceTracker(
    private val lineRadiusM: Double = 25.0,
    private val armDistanceM: Double = 80.0,
    private val minLapMs: Long = 15_000,
) {
    private var line: Pair<Double, Double>? = null
    private var armed = false
    private var lapStart: Long? = null
    private var closestApproach: Pair<Double, Long>? = null
    private val laps = mutableListOf<Double>()

    private var prevFix: GpsFix? = null
    private var lateral = 0.0
    private var longitudinal = 0.0
    private var maxLat = 0.0
    private var maxAccel = 0.0
    private var maxBrake = 0.0
    private val trail = ArrayDeque<GPoint>()
    private var lastFixTime: Long? = null

    var state = RaceState()
        private set

    fun setFinishLine(lat: Double, lon: Double) {
        line = lat to lon
        armed = false
        lapStart = null
        closestApproach = null
        laps.clear()
        publish(gpsActive = state.gpsActive)
    }

    /** Uses the latest GPS position as the finish line. Returns false without a fix. */
    fun setFinishLineHere(): Boolean {
        val fix = prevFix ?: return false
        setFinishLine(fix.lat, fix.lon)
        return true
    }

    fun resetLaps() {
        laps.clear()
        lapStart = null
        closestApproach = null
        armed = false
        maxLat = 0.0; maxAccel = 0.0; maxBrake = 0.0
        trail.clear()
        publish(gpsActive = state.gpsActive)
    }

    fun setGpsActive(active: Boolean) {
        if (!active) prevFix = null
        publish(gpsActive = active)
    }

    /** Longitudinal acceleration from OBD (m/s²) – preferred over GPS-derived values. */
    fun addObdAcceleration(accelMs2: Double) {
        longitudinal = accelMs2 / G
        updateMaxima()
        publish(gpsActive = state.gpsActive)
    }

    fun addFix(fix: GpsFix, obdAccelAvailable: Boolean) {
        val prev = prevFix
        prevFix = fix
        lastFixTime = fix.timeMs
        if (prev != null && fix.timeMs > prev.timeMs) {
            val dt = (fix.timeMs - prev.timeMs) / 1000.0
            val v = fix.speedMs ?: (distanceM(prev.lat, prev.lon, fix.lat, fix.lon) / dt)
            // Heading is meaningless when crawling: GPS bearing jumps around.
            val b0 = prev.bearingDeg
            val b1 = fix.bearingDeg
            val rawLateral = if (b0 != null && b1 != null && v > MIN_SPEED_FOR_HEADING) {
                v * (angleDiffDeg(b0, b1) * PI / 180 / dt) / G
            } else {
                0.0
            }
            lateral = smooth(lateral, rawLateral)
            if (!obdAccelAvailable) {
                val pv = prev.speedMs
                if (pv != null && fix.speedMs != null) longitudinal = smooth(longitudinal, (fix.speedMs - pv) / dt / G)
            }
            updateMaxima()
            trail.addLast(GPoint(lateral, longitudinal))
            while (trail.size > TRAIL_SIZE) trail.removeFirst()
        }
        trackLap(fix)
        publish(gpsActive = true)
    }

    /** Call regularly (e.g. once a second) so the running lap clock moves between GPS fixes. */
    fun tick(nowMs: Long) {
        if (lapStart != null) publish(gpsActive = state.gpsActive, now = nowMs)
    }

    /**
     * The line is "crossed" at the closest approach to it while passing within [lineRadiusM] –
     * not when entering the radius – so every lap is measured at the same point.
     */
    private fun trackLap(fix: GpsFix) {
        val (lat, lon) = line ?: return
        val d = distanceM(lat, lon, fix.lat, fix.lon)
        if (d > armDistanceM) armed = true
        val best = closestApproach
        if (d <= lineRadiusM && (armed || lapStart == null)) {
            if (best == null || d < best.first) {
                closestApproach = d to fix.timeMs
            } else {
                crossLine(best.second) // getting further away: the closest point is behind us
            }
        } else if (best != null) {
            crossLine(best.second)
        }
    }

    private fun crossLine(timeMs: Long) {
        closestApproach = null
        val start = lapStart
        if (start == null) {
            lapStart = timeMs // first pass over the line starts lap 1
            armed = false
        } else if (timeMs - start >= minLapMs) {
            laps += (timeMs - start) / 1000.0
            lapStart = timeMs
            armed = false
        }
    }

    private fun updateMaxima() {
        maxLat = max(maxLat, abs(lateral))
        maxAccel = max(maxAccel, longitudinal)
        maxBrake = max(maxBrake, -longitudinal)
    }

    private fun publish(gpsActive: Boolean, now: Long? = null) {
        val clock = now ?: lastFixTime
        state = RaceState(
            gpsActive = gpsActive,
            hasFix = prevFix != null,
            hasFinishLine = line != null,
            laps = laps.toList(),
            bestLapSec = laps.minOrNull(),
            currentLapSec = lapStart?.let { s -> clock?.let { (it - s) / 1000.0 } },
            g = GPoint(lateral, longitudinal),
            maxLateralG = maxLat,
            maxAccelG = maxAccel,
            maxBrakeG = maxBrake,
            trail = trail.toList(),
        )
    }

    private fun smooth(old: Double, new: Double) = old + SMOOTHING * (new - old)

    companion object {
        const val G = 9.81
        private const val SMOOTHING = 0.5
        private const val MIN_SPEED_FOR_HEADING = 4.0
        private const val TRAIL_SIZE = 40
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Signed smallest difference b1 - b0 in degrees, in (-180, 180]. */
        fun angleDiffDeg(b0: Double, b1: Double): Double {
            var d = (b1 - b0) % 360.0
            if (d > 180) d -= 360
            if (d <= -180) d += 360
            return d
        }

        /** Haversine distance. */
        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = lat1 * PI / 180
            val p2 = lat2 * PI / 180
            val dp = p2 - p1
            val dl = (lon2 - lon1) * PI / 180
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
        }
    }
}
