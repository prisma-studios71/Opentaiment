package app.opentaiment.feature.vehicle

import app.opentaiment.feature.vehicle.data.FuelSource
import app.opentaiment.feature.vehicle.data.FuelType
import app.opentaiment.feature.vehicle.data.ObdSample
import app.opentaiment.feature.vehicle.data.SprintTimer
import app.opentaiment.feature.vehicle.data.TripCalculator
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.source.DrivingSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCalculatorTest {

    @Test
    fun fuelFromMafForPetrol() {
        val calc = TripCalculator(VehicleProfile(fuelType = FuelType.PETROL))
        val (lh, source) = calc.fuelRate(ObdSample(0, mafGs = 10.0))!!
        assertEquals(10 / 14.7 / 745 * 3600, lh, 1e-6) // ≈ 3.29 l/h
        assertEquals(FuelSource.MAF, source)
    }

    @Test
    fun fuelRatePidWinsOverMaf() {
        val (lh, source) = TripCalculator().fuelRate(ObdSample(0, mafGs = 10.0, fuelRateLh = 4.2))!!
        assertEquals(4.2, lh, 1e-9)
        assertEquals(FuelSource.FUEL_RATE, source)
    }

    @Test
    fun speedDensityGivesRealisticIdleAirflow() {
        val calc = TripCalculator(VehicleProfile(displacementL = 1.6))
        val maf = calc.speedDensityMafGs(ObdSample(0, mapKpa = 30.0, intakeTempC = 20.0, rpm = 800.0))!!
        assertEquals(3.2, maf, 0.2) // a 1.6 at idle breathes ~3 g/s
    }

    @Test
    fun overrunFuelCutMeansZeroConsumption() {
        val (lh, _) = TripCalculator().fuelRate(ObdSample(0, mafGs = 15.0, throttlePct = 0.0, rpm = 2500.0))!!
        assertEquals(0.0, lh, 0.0)
    }

    @Test
    fun instantConsumptionPer100Km() {
        val calc = TripCalculator()
        val stats = calc.add(ObdSample(0, speedKmh = 100.0, fuelRateLh = 6.0))
        assertEquals(6.0, stats.instantLPer100Km!!, 1e-9)
    }

    @Test
    fun noPer100KmWhenStandingStill() {
        val stats = TripCalculator().add(ObdSample(0, speedKmh = 0.0, fuelRateLh = 0.8))
        assertNull(stats.instantLPer100Km)
        assertEquals(0.8, stats.fuelRateLh!!, 1e-9)
    }

    @Test
    fun distanceAndAverageConsumption() {
        val calc = TripCalculator()
        // 36 km/h for 100 s = 1 km, burning 3.6 l/h = 0.1 l => 10 l/100 km
        var stats = calc.add(ObdSample(0, speedKmh = 36.0, fuelRateLh = 3.6))
        for (i in 1..100) stats = calc.add(ObdSample(i * 1000L, speedKmh = 36.0, fuelRateLh = 3.6))
        assertEquals(1.0, stats.trip.distanceKm, 1e-6)
        assertEquals(0.1, stats.trip.fuelUsedL, 1e-6)
        assertEquals(10.0, stats.avgLPer100Km!!, 1e-6)
        assertEquals(36.0, stats.trip.avgSpeedKmh!!, 1e-6)
    }

    @Test
    fun accelerationFromLinearSpeedRamp() {
        val calc = TripCalculator()
        var stats = calc.add(ObdSample(0, speedKmh = 0.0))
        // +3.6 km/h per second == 1 m/s²
        for (i in 1..20) stats = calc.add(ObdSample(i * 100L, speedKmh = i * 0.36))
        assertEquals(1.0, stats.accelMs2!!, 0.01)
    }

    @Test
    fun powerEstimateMatchesPhysics() {
        val calc = TripCalculator(VehicleProfile(massKg = 1400.0, dragAreaM2 = 0.7, rollingResistance = 0.012, drivetrainEfficiency = 0.85))
        val w = calc.enginePowerW(vMs = 20.0, accelMs2 = 1.0)
        val expected = 20 * (1400 * 1.0 + 1400 * 9.81 * 0.012 + 0.5 * 1.2 * 0.7 * 400) / 0.85
        assertEquals(expected, w, 1e-6)
        assertEquals(0.0, calc.enginePowerW(vMs = 20.0, accelMs2 = -5.0), 0.0) // braking ≠ negative power
    }

    @Test
    fun zeroToHundredTimer() {
        val timer = SprintTimer(0, 100)
        for (t in 0..1000L step 100) timer.add(t, 0.0)
        for (i in 1..100) timer.add(1000L + i * 100, i.toDouble()) // 10 km/h per second
        assertEquals(10.0, timer.lastSec!!, 0.01)
        assertEquals(10.0, timer.bestSec!!, 0.01)
    }

    @Test
    fun sprintCancelledWhenDriverLiftsOff() {
        val timer = SprintTimer(0, 100)
        timer.add(0, 0.0)
        timer.add(100, 10.0)
        timer.add(200, 50.0)
        timer.add(300, 40.0) // backed off
        timer.add(400, 100.0)
        assertNull(timer.lastSec)
    }

    @Test
    fun eightyToOneTwentyIsInterpolated() {
        val timer = SprintTimer(80, 120)
        timer.add(0, 78.0)
        timer.add(1000, 82.0) // crosses 80 at t=500
        timer.add(5000, 118.0)
        timer.add(6000, 122.0) // crosses 120 at t=5500
        assertEquals(5.0, timer.lastSec!!, 0.001)
    }

    @Test
    fun simulatedDriveProducesSaneNumbers() {
        val sim = DrivingSimulator(seed = 1)
        val calc = TripCalculator()
        repeat(330) { calc.add(sim.next(200)) } // one full ~65 s loop
        val s = calc.current
        assertNotNull(s.sprints[0].bestSec)
        assertTrue("0-100 ${s.sprints[0].bestSec}", s.sprints[0].bestSec!! in 7.0..16.0)
        assertTrue("dist ${s.trip.distanceKm}", s.trip.distanceKm in 0.8..2.0)
        assertTrue("avg ${s.avgLPer100Km}", s.avgLPer100Km!! in 4.0..15.0)
        assertTrue("peak ${s.peakPowerKw}", s.peakPowerKw in 40.0..150.0)
    }
}
