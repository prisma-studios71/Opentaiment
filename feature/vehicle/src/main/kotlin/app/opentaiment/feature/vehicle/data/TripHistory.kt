package app.opentaiment.feature.vehicle.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A finished drive, as stored in the trip history. */
data class TripRecord(
    val startEpochMs: Long,
    val endEpochMs: Long,
    val distanceKm: Double,
    val durationMs: Long,
    val movingMs: Long,
    val fuelUsedL: Double,
    val fuelPricePerL: Double,
    val maxSpeedKmh: Double,
    val ecoScore: Int?,
    val best0to100Sec: Double?,
    val quarterMileSec: Double?,
    val demo: Boolean,
) {
    val id: Long get() = startEpochMs
    val cost: Double get() = fuelUsedL * fuelPricePerL
    val avgLPer100Km: Double? get() = if (distanceKm >= 0.1 && fuelUsedL > 0) fuelUsedL / distanceKm * 100 else null
    val avgSpeedKmh: Double? get() = if (movingMs > 0) distanceKm / (movingMs / 3_600_000.0) else null
}

data class PeriodSummary(
    val trips: Int,
    val distanceKm: Double,
    val drivingMs: Long,
    val fuelUsedL: Double,
    val cost: Double,
    val avgLPer100Km: Double?,
    val avgEcoScore: Int?,
)

object TripSummaries {

    /** Real (non-demo) trips that started inside [from, to). */
    fun summarize(trips: List<TripRecord>, from: Long, to: Long): PeriodSummary {
        val inRange = trips.filter { !it.demo && it.startEpochMs in from until to }
        val distance = inRange.sumOf { it.distanceKm }
        val fuel = inRange.sumOf { it.fuelUsedL }
        // Weight eco scores by distance: a 40 km trip says more than a 1 km one.
        val scored = inRange.filter { it.ecoScore != null && it.distanceKm > 0 }
        val ecoWeight = scored.sumOf { it.distanceKm }
        return PeriodSummary(
            trips = inRange.size,
            distanceKm = distance,
            drivingMs = inRange.sumOf { it.durationMs },
            fuelUsedL = fuel,
            cost = inRange.sumOf { it.cost },
            avgLPer100Km = if (distance >= 0.1 && fuel > 0) fuel / distance * 100 else null,
            avgEcoScore = if (ecoWeight > 0) (scored.sumOf { it.ecoScore!! * it.distanceKm } / ecoWeight).toInt() else null,
        )
    }

    fun thisWeek(trips: List<TripRecord>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): PeriodSummary {
        val monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return summarize(trips, monday.startMs(zone), monday.plusWeeks(1).startMs(zone))
    }

    fun thisMonth(trips: List<TripRecord>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): PeriodSummary {
        val first = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().withDayOfMonth(1)
        return summarize(trips, first.startMs(zone), first.plusMonths(1).startMs(zone))
    }

    private fun LocalDate.startMs(zone: ZoneId) = atStartOfDay(zone).toInstant().toEpochMilli()
}

/** JSON (de)serialisation of the history file. Unknown/missing fields fall back to safe defaults. */
object TripJson {
    fun encode(trips: List<TripRecord>): String = JSONArray().apply {
        trips.forEach { t ->
            put(
                JSONObject()
                    .put("start", t.startEpochMs)
                    .put("end", t.endEpochMs)
                    .put("km", t.distanceKm)
                    .put("dur", t.durationMs)
                    .put("moving", t.movingMs)
                    .put("fuel", t.fuelUsedL)
                    .put("price", t.fuelPricePerL)
                    .put("vmax", t.maxSpeedKmh)
                    .putOpt("eco", t.ecoScore)
                    .putOpt("s100", t.best0to100Sec)
                    .putOpt("qm", t.quarterMileSec)
                    .put("demo", t.demo),
            )
        }
    }.toString()

    fun decode(json: String): List<TripRecord> {
        val array = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            TripRecord(
                startEpochMs = o.optLong("start"),
                endEpochMs = o.optLong("end"),
                distanceKm = o.optDouble("km", 0.0),
                durationMs = o.optLong("dur"),
                movingMs = o.optLong("moving"),
                fuelUsedL = o.optDouble("fuel", 0.0),
                fuelPricePerL = o.optDouble("price", 0.0),
                maxSpeedKmh = o.optDouble("vmax", 0.0),
                ecoScore = if (o.has("eco")) o.optInt("eco") else null,
                best0to100Sec = if (o.has("s100")) o.optDouble("s100") else null,
                quarterMileSec = if (o.has("qm")) o.optDouble("qm") else null,
                demo = o.optBoolean("demo", false),
            )
        }
    }
}
