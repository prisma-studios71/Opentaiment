package app.opentaiment.feature.vehicle

import app.opentaiment.feature.vehicle.data.AlertMonitor
import app.opentaiment.feature.vehicle.data.AlertType
import app.opentaiment.feature.vehicle.data.EcoScorer
import app.opentaiment.feature.vehicle.data.ObdSample
import app.opentaiment.feature.vehicle.data.QuarterMileTimer
import app.opentaiment.feature.vehicle.data.Readiness
import app.opentaiment.feature.vehicle.data.TripCalculator
import app.opentaiment.feature.vehicle.data.TripJson
import app.opentaiment.feature.vehicle.data.TripRecord
import app.opentaiment.feature.vehicle.data.TripSummaries
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.obd.Elm327Parser
import app.opentaiment.feature.vehicle.obd.ObdPid
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DrivingInsightsTest {

    // ---------- eco score ----------

    @Test
    fun smoothDrivingScoresHigh() {
        val eco = EcoScorer()
        repeat(600) { eco.add(1000, speedKmh = 70.0, rpm = 2000.0, accelMs2 = 0.3, highRpmLimit = 3500.0) }
        val r = eco.result()!!
        assertEquals(100, r.score)
        assertTrue(r.tips.single().contains("ekonomicznie"))
    }

    @Test
    fun aggressiveDrivingScoresLowWithTips() {
        val eco = EcoScorer()
        repeat(600) { i ->
            // a hard launch and a hard stop every 20 s, revs high most of the time
            val a = when (i % 20) { 0, 1 -> 4.0; 10, 11 -> -5.0; else -> 0.5 }
            eco.add(1000, speedKmh = 60.0, rpm = 4200.0, accelMs2 = a, highRpmLimit = 3500.0)
        }
        val r = eco.result()!!
        assertEquals(30, r.harshAccelerations)
        assertEquals(30, r.harshBrakings)
        assertTrue("score ${r.score}", r.score <= 30)
        assertEquals(3, r.tips.size)
    }

    @Test
    fun ecoNeedsAMinuteOfData() {
        val eco = EcoScorer()
        repeat(30) { eco.add(1000, 50.0, 2000.0, 0.0, 3500.0) }
        assertNull(eco.result())
    }

    @Test
    fun idlingIsPenalised() {
        val eco = EcoScorer()
        repeat(300) { eco.add(1000, 0.0, 800.0, 0.0, 3500.0) }  // 5 min idle
        repeat(300) { eco.add(1000, 50.0, 2000.0, 0.0, 3500.0) } // 5 min driving
        val r = eco.result()!!
        assertEquals(0.5, r.idleShare, 0.01)
        assertTrue(r.tips.first().contains("zgaś silnik"))
    }

    // ---------- alerts ----------

    @Test
    fun overheatNeedsConfirmationAndHasHysteresis() {
        val m = AlertMonitor(confirmSamples = 3)
        fun s(c: Double) = ObdSample(0, coolantTempC = c, rpm = 900.0, voltage = 14.0)
        assertTrue(m.update(s(112.0)).isEmpty())
        assertTrue(m.update(s(112.0)).isEmpty())
        assertEquals(AlertType.OVERHEAT, m.update(s(113.0)).single().type)
        assertEquals(1, m.update(s(107.0)).size) // still above clear threshold
        assertTrue(m.update(s(100.0)).isEmpty())
    }

    @Test
    fun noChargingOnlyWhileEngineRuns() {
        val m = AlertMonitor(confirmSamples = 1)
        assertEquals(AlertType.NOT_CHARGING, m.update(ObdSample(0, rpm = 850.0, voltage = 12.1)).single().type)
        // engine off at 12.1 V is a healthy-ish battery, not a charging fault
        assertTrue(m.update(ObdSample(0, rpm = 0.0, voltage = 12.1)).isEmpty())
        assertEquals(AlertType.WEAK_BATTERY, m.update(ObdSample(0, rpm = 0.0, voltage = 11.6)).single().type)
    }

    @Test
    fun lowFuel() {
        val m = AlertMonitor(confirmSamples = 1)
        val alert = m.update(ObdSample(0, fuelLevelPct = 8.0)).single()
        assertEquals(AlertType.LOW_FUEL, alert.type)
        assertTrue(alert.message.contains("8 %"))
    }

    // ---------- quarter mile ----------

    @Test
    fun quarterMileAtConstantAcceleration() {
        // a = 4 m/s² => 402.336 m after sqrt(2*402.336/4) = 14.18 s at 56.7 m/s = 204 km/h
        val q = QuarterMileTimer(timeoutMs = 60_000)
        q.add(0, 0.0)
        for (i in 1..200) {
            val t = i * 100L
            q.add(t, 4.0 * t / 1000.0 * 3.6)
        }
        val s = q.state()
        assertEquals(14.18, s.lastSec!!, 0.1)
        assertEquals(204.3, s.lastTrapKmh!!, 1.5)
    }

    @Test
    fun quarterMileAbortedOnLiftOff() {
        val q = QuarterMileTimer()
        q.add(0, 0.0)
        q.add(1000, 30.0)
        q.add(2000, 50.0)
        q.add(3000, 40.0)
        for (i in 4..60) q.add(i * 1000L, 200.0)
        assertNull(q.state().lastSec)
    }

    // ---------- fuel cost ----------

    @Test
    fun tripCostUsesFuelPrice() {
        val calc = TripCalculator(VehicleProfile(fuelPricePerL = 7.0))
        var stats = calc.add(ObdSample(0, speedKmh = 36.0, fuelRateLh = 3.6))
        for (i in 1..100) stats = calc.add(ObdSample(i * 1000L, speedKmh = 36.0, fuelRateLh = 3.6))
        assertEquals(0.7, stats.trip.fuelCost, 1e-6) // 0.1 l * 7 zł
    }

    // ---------- trip history ----------

    private fun trip(start: Long, km: Double, fuel: Double, eco: Int?, demo: Boolean = false) = TripRecord(
        startEpochMs = start, endEpochMs = start + 600_000, distanceKm = km, durationMs = 600_000,
        movingMs = 540_000, fuelUsedL = fuel, fuelPricePerL = 6.5, maxSpeedKmh = 120.0,
        ecoScore = eco, best0to100Sec = 11.2, quarterMileSec = null, demo = demo,
    )

    @Test
    fun jsonRoundTrip() {
        val trips = listOf(trip(1_000, 12.5, 0.9, 81), trip(2_000, 3.0, 0.3, null, demo = true))
        assertEquals(trips, TripJson.decode(TripJson.encode(trips)))
        assertEquals(emptyList<TripRecord>(), TripJson.decode("garbage"))
    }

    @Test
    fun summariesSkipDemoAndWeightEcoByDistance() {
        val zone = ZoneId.of("Europe/Warsaw")
        fun at(d: Int) = LocalDateTime.of(2026, 11, d, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val trips = listOf(
            trip(at(9), 30.0, 2.0, 90),
            trip(at(10), 10.0, 1.0, 50),
            trip(at(10), 100.0, 9.0, 10, demo = true),
            trip(at(2), 50.0, 4.0, 70), // previous week, same month
        )
        val week = TripSummaries.thisWeek(trips, now = at(11), zone = zone)
        assertEquals(2, week.trips)
        assertEquals(40.0, week.distanceKm, 1e-9)
        assertEquals(7.5, week.avgLPer100Km!!, 1e-9)
        assertEquals(19.5, week.cost, 1e-9)
        assertEquals(80, week.avgEcoScore) // (90*30 + 50*10) / 40

        val month = TripSummaries.thisMonth(trips, now = at(11), zone = zone)
        assertEquals(3, month.trips)
        assertEquals(90.0, month.distanceKm, 1e-9)
    }

    // ---------- readiness & freeze frame ----------

    @Test
    fun readinessForPetrolCar() {
        // MIL on, 3 codes; spark engine; continuous ok; catalyst/EVAP/O2/O2 heater available; EVAP incomplete
        val r = Readiness.decode(intArrayOf(0x83, 0x07, 0x65, 0x04))!!
        assertFalse(r.diesel)
        assertEquals(7, r.monitors.size)
        assertEquals(listOf("Układ EVAP (opary paliwa)"), r.incomplete.map { it.name })
        assertFalse(r.ready)
    }

    @Test
    fun readinessForDiesel() {
        val r = Readiness.decode(intArrayOf(0x00, 0x0F, 0x40, 0x00))!!
        assertTrue(r.diesel)
        assertTrue(r.monitors.any { it.name.contains("DPF") })
        assertTrue(r.ready)
    }

    @Test
    fun freezeFrameParsing() {
        assertArrayEquals(intArrayOf(0x1A, 0xF8), Elm327Parser.mode02Data("42 0C 00 1A F8\r", 0x0C))
        assertArrayEquals(intArrayOf(0x03, 0x01), Elm327Parser.mode02Data("4202000301\r", 0x02))
        assertNull(Elm327Parser.mode02Data("NO DATA", 0x0C))
        assertEquals(9.375, ObdPid.LONG_FUEL_TRIM.decodeOrNull(intArrayOf(140))!!, 1e-9)
    }
}
