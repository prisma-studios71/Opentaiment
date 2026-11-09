package app.opentaiment.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SunCalcTest {
    private val warsawLat = 52.23
    private val warsawLon = 21.01
    private val zone = ZoneId.of("Europe/Warsaw")

    private fun normal(date: LocalDate) = SunCalc.sunTimes(date, warsawLat, warsawLon) as SunCalc.SunTimes.Normal

    @Test
    fun warsawMidsummer() {
        val t = normal(LocalDate.of(2026, 6, 21))
        // Reference values from the NOAA solar calculator.
        assertEquals(2.24, t.sunriseUtcHours, 0.05)  // 04:14 CEST
        assertEquals(19.02, t.sunsetUtcHours, 0.05)  // 21:01 CEST
    }

    @Test
    fun warsawNovember() {
        val t = normal(LocalDate.of(2026, 11, 9))
        assertEquals(5.76, t.sunriseUtcHours, 0.05)  // 06:46 CET
        assertEquals(14.89, t.sunsetUtcHours, 0.05)  // 15:53 CET
    }

    @Test
    fun nightDetection() {
        fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 9, h, m, 0, 0, zone)
        assertTrue(SunCalc.isNight(at(5, 30), warsawLat, warsawLon))
        assertFalse(SunCalc.isNight(at(12, 0), warsawLat, warsawLon))
        assertTrue(SunCalc.isNight(at(17, 0), warsawLat, warsawLon))
    }

    @Test
    fun polarCases() {
        assertEquals(SunCalc.SunTimes.PolarDay, SunCalc.sunTimes(LocalDate.of(2026, 6, 21), 78.2, 15.6)) // Svalbard
        assertEquals(SunCalc.SunTimes.PolarNight, SunCalc.sunTimes(LocalDate.of(2026, 12, 21), 78.2, 15.6))
    }
}
