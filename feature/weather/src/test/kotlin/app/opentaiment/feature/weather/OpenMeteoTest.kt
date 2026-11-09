package app.opentaiment.feature.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoTest {

    @Test
    fun parsesCurrentAndDaily() {
        val json = """
            {"current":{"temperature_2m":7.4,"weather_code":61,"wind_speed_10m":18.2,"is_day":0},
             "daily":{"temperature_2m_max":[9.1],"temperature_2m_min":[2.3]}}
        """.trimIndent()

        val w = OpenMeteo.parse(json, "Kraków")

        assertEquals(7.4, w.temperatureC, 0.001)
        assertEquals(61, w.code)
        assertEquals(18.2, w.windKmh, 0.001)
        assertEquals(2.3, w.minC!!, 0.001)
        assertEquals(9.1, w.maxC!!, 0.001)
        assertFalse(w.isDay)
        assertEquals("Kraków", w.place)
    }

    @Test
    fun missingDailyGivesNullMinMax() {
        val w = OpenMeteo.parse("""{"current":{"temperature_2m":20,"weather_code":0}}""", null)
        assertNull(w.minC)
        assertNull(w.maxC)
        assertTrue(w.isDay)
    }

    @Test
    fun urlUsesDotDecimalSeparator() {
        val url = OpenMeteo.url(50.0647, 19.945)
        assertTrue(url, url.contains("latitude=50.0647&longitude=19.9450"))
    }

    @Test
    fun weatherCodes() {
        assertEquals("Burza", WeatherCodes.describe(95))
        assertEquals("🌙", WeatherCodes.emoji(0, isDay = false))
        assertTrue(WeatherCodes.isHazardous(71))
        assertFalse(WeatherCodes.isHazardous(0))
    }
}
