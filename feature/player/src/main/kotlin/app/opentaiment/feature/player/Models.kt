package app.opentaiment.feature.player

import org.json.JSONArray
import org.json.JSONObject

/** An internet radio station (from the Radio Browser catalogue or added by hand). */
data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String? = null,
    val country: String? = null,
    val tags: List<String> = emptyList(),
    val bitrateKbps: Int? = null,
    val codec: String? = null,
) {
    /** "MP3 · 128 kbps · pop, rock" */
    val details: String
        get() = listOfNotNull(
            codec?.takeIf { it.isNotBlank() && it != "UNKNOWN" },
            bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" },
            tags.take(3).joinToString(", ").ifEmpty { null },
        ).joinToString(" · ")
}

/** A song found on the device, SD card or USB stick. */
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val uri: String,
    /** Folder name – head units usually have music sorted in folders on a stick. */
    val folder: String,
)

enum class PlaybackKind { NONE, RADIO, LOCAL }

data class PlayerState(
    val connected: Boolean = false,
    val kind: PlaybackKind = PlaybackKind.NONE,
    val title: String? = null,
    /** Station name for radio (title is the song from ICY metadata), artist for files. */
    val subtitle: String? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    val currentId: String? = null,
    val shuffle: Boolean = false,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
)

object StationJson {
    /** Parses a Radio Browser `/json/stations/...` response. Broken entries are skipped. */
    fun parseRadioBrowser(json: String): List<RadioStation> {
        val array = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            val id = o.optString("stationuuid")
            val name = o.optString("name").trim()
            if (url.isBlank() || id.isBlank() || name.isBlank()) return@mapNotNull null
            if (o.optInt("lastcheckok", 1) == 0) return@mapNotNull null
            RadioStation(
                id = id,
                name = name,
                streamUrl = url,
                faviconUrl = o.optString("favicon").takeIf { it.startsWith("http") },
                country = o.optString("countrycode").ifBlank { null },
                tags = o.optString("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() },
                bitrateKbps = o.optInt("bitrate", 0).takeIf { it > 0 },
                codec = o.optString("codec").ifBlank { null },
            )
        }.distinctBy { it.streamUrl }
    }

    fun encode(stations: List<RadioStation>): String = JSONArray().apply {
        stations.forEach { s ->
            put(
                JSONObject().put("id", s.id).put("name", s.name).put("url", s.streamUrl)
                    .putOpt("favicon", s.faviconUrl).putOpt("country", s.country)
                    .put("tags", s.tags.joinToString(","))
                    .putOpt("bitrate", s.bitrateKbps).putOpt("codec", s.codec),
            )
        }
    }.toString()

    fun decode(json: String?): List<RadioStation> {
        if (json.isNullOrBlank()) return emptyList()
        val array = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            RadioStation(
                id = o.optString("id").ifBlank { return@mapNotNull null },
                name = o.optString("name"),
                streamUrl = o.optString("url").ifBlank { return@mapNotNull null },
                faviconUrl = o.optString("favicon").ifBlank { null },
                country = o.optString("country").ifBlank { null },
                tags = o.optString("tags").split(',').filter { it.isNotBlank() },
                bitrateKbps = if (o.has("bitrate")) o.optInt("bitrate") else null,
                codec = o.optString("codec").ifBlank { null },
            )
        }
    }
}
