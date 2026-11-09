package app.opentaiment.feature.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherRepository(context: Context) {

    private val appContext = context.applicationContext

    suspend fun fetch(): Result<Weather> = withContext(Dispatchers.IO) {
        runCatching {
            val location = lastKnownLocation()
            val lat = location?.latitude ?: DEFAULT_LAT
            val lon = location?.longitude ?: DEFAULT_LON
            val place = if (location != null) placeName(lat, lon) else DEFAULT_PLACE

            val connection = URL(OpenMeteo.url(lat, lon)).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                OpenMeteo.parse(connection.inputStream.bufferedReader().use { it.readText() }, place)
            } finally {
                connection.disconnect()
            }
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return null
        return try {
            lm.getProviders(true)
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
    }

    @Suppress("DEPRECATION") // async variant is API 33+, we're already on IO
    private fun placeName(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            Geocoder(appContext, Locale("pl")).getFromLocation(lat, lon, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val DEFAULT_LAT = 52.2297
        const val DEFAULT_LON = 21.0122
        const val DEFAULT_PLACE = "Warszawa"
    }
}
