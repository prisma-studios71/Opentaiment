package app.opentaiment.feature.vehicle

import app.opentaiment.feature.vehicle.race.GpsFix
import app.opentaiment.feature.vehicle.race.RaceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RaceTrackerTest {

    private val lat0 = 52.0
    private val lon0 = 21.0
    private val mPerDegLat = 111_195.0
    private val mPerDegLon = 111_195.0 * cos(lat0 * PI / 180)

    /** Point on a circle of [r] metres around (lat0, lon0) at [angle] radians, heading tangent (CCW). */
    private fun circleFix(t: Long, r: Double, angle: Double, v: Double): GpsFix {
        val x = r * cos(angle)
        val y = r * sin(angle)
        // CCW motion: heading (compass, clockwise from north) = 360 - angle(deg) roughly; tangent direction
        val headingRad = angle + PI / 2 // math angle of the velocity
        val bearing = ((90 - headingRad * 180 / PI) % 360 + 360) % 360
        return GpsFix(t, lat0 + y / mPerDegLat, lon0 + x / mPerDegLon, v, bearing)
    }

    @Test
    fun distanceAndAngles() {
        assertEquals(111_195.0, RaceTracker.distanceM(52.0, 21.0, 53.0, 21.0), 50.0)
        assertEquals(20.0, RaceTracker.angleDiffDeg(350.0, 10.0), 1e-9)
        assertEquals(-20.0, RaceTracker.angleDiffDeg(10.0, 350.0), 1e-9)
    }

    @Test
    fun countsLapsOnACircleAndMeasuresLateralG() {
        // r = 100 m, v = 20 m/s -> lap = 2πr/v = 31.4 s, lateral = v²/r = 4 m/s² = 0.41 g
        val r = 100.0
        val v = 20.0
        val tracker = RaceTracker(minLapMs = 10_000)
        val omega = v / r
        tracker.setFinishLine(lat0 + 0.0 / mPerDegLat, lon0 + r / mPerDegLon) // angle 0 point
        var t = 0L
        while (t <= 100_000) {
            tracker.addFix(circleFix(t, r, omega * t / 1000.0, v), obdAccelAvailable = false)
            t += 500
        }
        val s = tracker.state
        assertEquals(3, s.laps.size)
        s.laps.forEach { assertEquals(31.4, it, 0.6) }
        assertEquals(0.41, kotlin.math.abs(s.g.lateral), 0.03)
    }

    @Test
    fun noLapsWithoutFinishLine() {
        val tracker = RaceTracker()
        tracker.addFix(GpsFix(0, lat0, lon0, 10.0, 0.0), obdAccelAvailable = true)
        assertNull(tracker.state.currentLapSec)
        assertEquals(0, tracker.state.laps.size)
    }

    @Test
    fun obdAccelerationFeedsLongitudinalG() {
        val tracker = RaceTracker()
        tracker.addObdAcceleration(4.905)
        assertEquals(0.5, tracker.state.g.longitudinal, 1e-9)
        tracker.addObdAcceleration(-7.0)
        assertEquals(0.5, tracker.state.maxAccelG, 1e-9)
        assertEquals(7.0 / 9.81, tracker.state.maxBrakeG, 1e-9)
    }
}
