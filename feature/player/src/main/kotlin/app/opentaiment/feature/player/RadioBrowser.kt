package app.opentaiment.feature.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client for radio-browser.info – a free, community-run catalogue of ~50 000 internet radio
 * stations (no account, no API key). Several mirrors; if one is down the next is tried.
 */
class RadioBrowser(private val servers: List<String> = SERVERS) {

    suspend fun popular(countryCode: String = "PL", limit: Int = 60): List<RadioStation> =
        stations("/json/stations/search?countrycode=$countryCode&order=clickcount&reverse=true&hidebroken=true&limit=$limit")

    suspend fun search(query: String, limit: Int = 50): List<RadioStation> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return stations("/json/stations/search?name=$q&order=clickcount&reverse=true&hidebroken=true&limit=$limit")
    }

    /** Lets the catalogue know a station was played (keeps its popularity ranking honest). */
    suspend fun countClick(stationId: String) {
        try {
            get("/json/url/" + URLEncoder.encode(stationId, "UTF-8"))
        } catch (_: IOException) {
        }
    }

    private suspend fun stations(path: String): List<RadioStation> = StationJson.parseRadioBrowser(get(path))

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        var last: IOException? = null
        for (server in servers) {
            try {
                val connection = URL(server + path).openConnection() as HttpURLConnection
                connection.connectTimeout = 6_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                try {
                    if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
                    return@withContext connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("Brak serwerów katalogu radia")
    }

    companion object {
        const val USER_AGENT = "Opentaiment/0.4"
        val SERVERS = listOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://fi1.api.radio-browser.info",
        )
    }
}
