package app.opentaiment.feature.vehicle.data

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One point of a trip's GPS trace. */
data class TrackPoint(val epochMs: Long, val lat: Double, val lon: Double, val speedKmh: Double?)

/**
 * Keeps a GPS trace small: a new point only every [minDistanceM] metres or [maxIntervalMs],
 * whichever comes first – about 1 point per 25 m at speed and nothing while parked.
 */
class TrackRecorder(
    private val minDistanceM: Double = 25.0,
    private val maxIntervalMs: Long = 30_000,
    private val maxAccuracyM: Double = 50.0,
) {
    private val points = ArrayList<TrackPoint>()
    val size: Int get() = points.size

    fun add(point: TrackPoint, accuracyM: Double? = null) {
        if (accuracyM != null && accuracyM > maxAccuracyM) return
        val last = points.lastOrNull()
        if (last == null) {
            points += point
            return
        }
        val moved = distanceM(last.lat, last.lon, point.lat, point.lon)
        if (moved >= minDistanceM || (point.epochMs - last.epochMs >= maxIntervalMs && moved >= 5)) points += point
    }

    fun snapshot(): List<TrackPoint> = points.toList()
    fun clear() = points.clear()

    companion object {
        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }
}

/** Traces stored next to the trip history, one small CSV per trip. */
class TrackStore(private val dir: File) {
    private fun file(tripId: Long) = File(dir, "$tripId.csv")

    fun save(tripId: Long, points: List<TrackPoint>) {
        if (points.size < 2) return
        dir.mkdirs()
        val tmp = File(dir, "$tripId.csv.tmp")
        tmp.writeText(TrackCsv.encode(points))
        tmp.renameTo(file(tripId))
    }

    fun load(tripId: Long): List<TrackPoint> = file(tripId).takeIf { it.exists() }?.readText()?.let(TrackCsv::decode).orEmpty()
    fun has(tripId: Long): Boolean = file(tripId).exists()
    fun delete(tripId: Long) { file(tripId).delete() }
    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}

object TrackCsv {
    fun encode(points: List<TrackPoint>): String = buildString {
        points.forEach { p ->
            append(p.epochMs).append(',')
            append(String.format(Locale.US, "%.6f,%.6f", p.lat, p.lon)).append(',')
            append(p.speedKmh?.let { String.format(Locale.US, "%.1f", it) } ?: "").append('\n')
        }
    }

    fun decode(text: String): List<TrackPoint> = text.lineSequence().mapNotNull { line ->
        val parts = line.split(',')
        if (parts.size < 3) return@mapNotNull null
        TrackPoint(
            epochMs = parts[0].toLongOrNull() ?: return@mapNotNull null,
            lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
            lon = parts[2].toDoubleOrNull() ?: return@mapNotNull null,
            speedKmh = parts.getOrNull(3)?.toDoubleOrNull(),
        )
    }.toList()
}

/** Files to share: all trips as a spreadsheet (CSV) and a single trip's route as GPX. */
object TripExport {
    private val csvDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    /** Semicolon-separated with decimal commas – opens straight in Polish Excel / LibreOffice. */
    fun tripsCsv(trips: List<TripRecord>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append("start;koniec;dystans_km;czas_min;paliwo_l;spalanie_l_100km;koszt_zl;cena_zl_l;srednia_kmh;max_kmh;eco;0_100_s;cwierc_mili_s;demo\n")
        trips.sortedBy { it.startEpochMs }.forEach { t ->
            fun d(v: Double?, decimals: Int) = v?.let { String.format(Locale("pl"), "%.${decimals}f", it) } ?: ""
            append(Instant.ofEpochMilli(t.startEpochMs).atZone(zone).format(csvDate)).append(';')
            append(Instant.ofEpochMilli(t.endEpochMs).atZone(zone).format(csvDate)).append(';')
            append(d(t.distanceKm, 2)).append(';')
            append(d(t.durationMs / 60_000.0, 1)).append(';')
            append(d(t.fuelUsedL, 2)).append(';')
            append(d(t.avgLPer100Km, 1)).append(';')
            append(d(t.cost, 2)).append(';')
            append(d(t.fuelPricePerL, 2)).append(';')
            append(d(t.avgSpeedKmh, 0)).append(';')
            append(d(t.maxSpeedKmh, 0)).append(';')
            append(t.ecoScore?.toString() ?: "").append(';')
            append(d(t.best0to100Sec, 1)).append(';')
            append(d(t.quarterMileSec, 1)).append(';')
            append(if (t.demo) "tak" else "nie").append('\n')
        }
    }

    fun gpx(trip: TripRecord, points: List<TrackPoint>): String = buildString {
        val iso = DateTimeFormatter.ISO_INSTANT
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Opentaiment\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <trk>\n    <name>Trasa ").append(iso.format(Instant.ofEpochMilli(trip.startEpochMs))).append("</name>\n")
        append("    <trkseg>\n")
        points.forEach { p ->
            append(String.format(Locale.US, "      <trkpt lat=\"%.6f\" lon=\"%.6f\">", p.lat, p.lon))
            append("<time>").append(iso.format(Instant.ofEpochMilli(p.epochMs))).append("</time>")
            p.speedKmh?.let { append(String.format(Locale.US, "<extensions><speed>%.2f</speed></extensions>", it / 3.6)) }
            append("</trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }
}
