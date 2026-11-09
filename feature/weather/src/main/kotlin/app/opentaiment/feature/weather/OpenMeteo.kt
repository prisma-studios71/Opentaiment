package app.opentaiment.feature.weather

import org.json.JSONObject
import java.util.Locale

internal object OpenMeteo {

    fun url(lat: Double, lon: Double): String =
        String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,wind_speed_10m,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto",
            lat, lon,
        )

    fun parse(json: String, place: String?): Weather {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.optJSONObject("daily")
        return Weather(
            temperatureC = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            windKmh = current.optDouble("wind_speed_10m", 0.0),
            minC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeUnless { it.isNaN() },
            maxC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeUnless { it.isNaN() },
            isDay = current.optInt("is_day", 1) == 1,
            place = place,
        )
    }
}
