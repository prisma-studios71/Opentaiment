package app.opentaiment.feature.weather

data class Weather(
    val temperatureC: Double,
    val code: Int,
    val windKmh: Double,
    val minC: Double?,
    val maxC: Double?,
    val isDay: Boolean,
    val place: String?,
)

sealed interface WeatherState {
    data object Loading : WeatherState
    data class Ready(val weather: Weather) : WeatherState
    data object Error : WeatherState
}

/** WMO weather interpretation codes used by Open-Meteo. */
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "Bezchmurnie"
        1 -> "Prawie bezchmurnie"
        2 -> "Częściowe zachmurzenie"
        3 -> "Pochmurno"
        45, 48 -> "Mgła"
        51, 53, 55 -> "Mżawka"
        56, 57 -> "Marznąca mżawka"
        61, 63, 65 -> "Deszcz"
        66, 67 -> "Marznący deszcz"
        71, 73, 75, 77 -> "Śnieg"
        80, 81, 82 -> "Przelotne opady"
        85, 86 -> "Przelotny śnieg"
        95 -> "Burza"
        96, 99 -> "Burza z gradem"
        else -> "Nieznana pogoda"
    }

    fun emoji(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1, 2 -> if (isDay) "🌤️" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    /** Conditions worth warning a driver about (slippery road / poor visibility). */
    fun isHazardous(code: Int): Boolean = code in setOf(45, 48, 56, 57, 66, 67, 71, 73, 75, 77, 85, 86, 95, 96, 99)
}
