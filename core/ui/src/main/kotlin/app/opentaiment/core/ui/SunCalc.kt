package app.opentaiment.core.ui

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise / sunset (the classic "Almanac for Computers" algorithm, ~1–2 min accuracy),
 * used for the automatic night mode. Pure Kotlin.
 */
object SunCalc {

    sealed interface SunTimes {
        data class Normal(val sunriseUtcHours: Double, val sunsetUtcHours: Double) : SunTimes
        data object PolarDay : SunTimes
        data object PolarNight : SunTimes
    }

    fun sunTimes(date: LocalDate, lat: Double, lon: Double): SunTimes {
        val rise = eventUtcHours(date, lat, lon, rising = true)
        val set = eventUtcHours(date, lat, lon, rising = false)
        return when {
            rise is Double && set is Double -> SunTimes.Normal(rise, set)
            rise == POLAR_DAY || set == POLAR_DAY -> SunTimes.PolarDay
            else -> SunTimes.PolarNight
        }
    }

    /** True between sunset and sunrise at the given place. */
    fun isNight(now: ZonedDateTime, lat: Double, lon: Double): Boolean {
        val utc = now.withZoneSameInstant(ZoneOffset.UTC)
        val hours = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
        return when (val t = sunTimes(utc.toLocalDate(), lat, lon)) {
            SunTimes.PolarDay -> false
            SunTimes.PolarNight -> true
            is SunTimes.Normal -> if (t.sunriseUtcHours < t.sunsetUtcHours) {
                hours < t.sunriseUtcHours || hours >= t.sunsetUtcHours
            } else {
                // Far east/west longitudes: in UTC the sunset can come "before" the sunrise.
                hours >= t.sunsetUtcHours && hours < t.sunriseUtcHours
            }
        }
    }

    private const val POLAR_DAY = "day"
    private const val POLAR_NIGHT = "night"

    /** UTC hour of sunrise/sunset as Double, or POLAR_DAY / POLAR_NIGHT. */
    private fun eventUtcHours(date: LocalDate, lat: Double, lon: Double, rising: Boolean): Any {
        val zenith = 90.833
        val n = date.dayOfYear
        val lngHour = lon / 15
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        val l = norm360(m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634)
        var ra = norm360(atanD(0.91764 * tanD(l)))
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(zenith) - sinDec * sinD(lat)) / (cosDec * cosD(lat))
        if (cosH > 1) return POLAR_NIGHT
        if (cosH < -1) return POLAR_DAY
        val h = (if (rising) 360 - acosD(cosH) else acosD(cosH)) / 15
        val localMean = h + ra - 0.06571 * t - 6.622
        return ((localMean - lngHour) % 24 + 24) % 24
    }

    private fun norm360(x: Double) = (x % 360 + 360) % 360
    private fun sinD(x: Double) = sin(x * PI / 180)
    private fun cosD(x: Double) = cos(x * PI / 180)
    private fun tanD(x: Double) = tan(x * PI / 180)
    private fun atanD(x: Double) = atan(x) * 180 / PI
    private fun acosD(x: Double) = acos(x) * 180 / PI
}
