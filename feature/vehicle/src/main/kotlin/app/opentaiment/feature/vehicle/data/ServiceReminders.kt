package app.opentaiment.feature.vehicle.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Something the car needs every N km and/or every N days: oil, inspection, insurance… */
data class ServiceReminder(
    val id: String,
    val name: String,
    /** null = not distance based. */
    val intervalKm: Int?,
    /** null = not time based. */
    val intervalDays: Int?,
    /** Odometer reading when it was last done. */
    val lastDoneKm: Double?,
    val lastDoneEpochDay: Long?,
)

enum class ReminderLevel { OK, SOON, DUE }

data class ReminderStatus(
    val reminder: ServiceReminder,
    val kmLeft: Double?,
    val daysLeft: Long?,
    val level: ReminderLevel,
) {
    /** "za 1 200 km", "za 14 dni", "przeterminowane o 300 km"… */
    val label: String
        get() {
            val parts = listOfNotNull(
                kmLeft?.let { if (it >= 0) "za ${fmtKm(it)} km" else "${fmtKm(-it)} km po terminie" },
                daysLeft?.let { if (it >= 0) "za ${daysLabel(it)}" else "${daysLabel(-it)} po terminie" },
            )
            return parts.joinToString(" lub ").ifEmpty { "Ustaw, kiedy było robione" }
        }

    private fun fmtKm(v: Double) = String.format(java.util.Locale("pl"), "%,.0f", v)
    private fun daysLabel(d: Long) = when (d) {
        1L -> "1 dzień"
        else -> "$d dni"
    }
}

object ServiceReminders {
    const val SOON_KM = 1_000.0
    const val SOON_DAYS = 30L

    val DEFAULTS = listOf(
        ServiceReminder("oil", "Wymiana oleju", intervalKm = 15_000, intervalDays = 365, lastDoneKm = null, lastDoneEpochDay = null),
        ServiceReminder("inspection", "Przegląd techniczny", intervalKm = null, intervalDays = 365, lastDoneKm = null, lastDoneEpochDay = null),
        ServiceReminder("insurance", "OC", intervalKm = null, intervalDays = 365, lastDoneKm = null, lastDoneEpochDay = null),
        ServiceReminder("timing", "Rozrząd", intervalKm = 120_000, intervalDays = 365 * 5, lastDoneKm = null, lastDoneEpochDay = null),
        ServiceReminder("brakes", "Płyn hamulcowy", intervalKm = null, intervalDays = 365 * 2, lastDoneKm = null, lastDoneEpochDay = null),
    )

    fun status(reminder: ServiceReminder, odometerKm: Double?, today: LocalDate): ReminderStatus {
        val kmLeft = if (reminder.intervalKm != null && reminder.lastDoneKm != null && odometerKm != null) {
            reminder.lastDoneKm + reminder.intervalKm - odometerKm
        } else null
        val daysLeft = if (reminder.intervalDays != null && reminder.lastDoneEpochDay != null) {
            ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(reminder.lastDoneEpochDay).plusDays(reminder.intervalDays.toLong()))
        } else null
        val level = when {
            (kmLeft != null && kmLeft < 0) || (daysLeft != null && daysLeft < 0) -> ReminderLevel.DUE
            (kmLeft != null && kmLeft <= SOON_KM) || (daysLeft != null && daysLeft <= SOON_DAYS) -> ReminderLevel.SOON
            else -> ReminderLevel.OK
        }
        return ReminderStatus(reminder, kmLeft, daysLeft, level)
    }

    /** Most urgent first. */
    fun statuses(reminders: List<ServiceReminder>, odometerKm: Double?, today: LocalDate): List<ReminderStatus> =
        reminders.map { status(it, odometerKm, today) }
            .sortedWith(compareByDescending<ReminderStatus> { it.level.ordinal }.thenBy { it.daysLeft ?: Long.MAX_VALUE })

    /**
     * Odometer: the car's own reading when it reports one, otherwise the value the user typed in
     * plus everything driven since (from real, non-demo trips).
     */
    fun odometer(carKm: Double?, baseKm: Double?, baseEpochMs: Long, trips: List<TripRecord>): Double? =
        carKm ?: baseKm?.let { base -> base + trips.filter { !it.demo && it.startEpochMs >= baseEpochMs }.sumOf { it.distanceKm } }

    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.now().atZone(zone).toLocalDate()

    fun encode(reminders: List<ServiceReminder>): String = JSONArray().apply {
        reminders.forEach { r ->
            put(
                JSONObject().put("id", r.id).put("name", r.name)
                    .putOpt("km", r.intervalKm).putOpt("days", r.intervalDays)
                    .putOpt("lastKm", r.lastDoneKm).putOpt("lastDay", r.lastDoneEpochDay),
            )
        }
    }.toString()

    fun decode(json: String?): List<ServiceReminder> {
        if (json == null) return DEFAULTS
        val array = try { JSONArray(json) } catch (e: Exception) { return DEFAULTS }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            ServiceReminder(
                id = o.optString("id").ifEmpty { return@mapNotNull null },
                name = o.optString("name"),
                intervalKm = if (o.has("km")) o.optInt("km") else null,
                intervalDays = if (o.has("days")) o.optInt("days") else null,
                lastDoneKm = if (o.has("lastKm")) o.optDouble("lastKm") else null,
                lastDoneEpochDay = if (o.has("lastDay")) o.optLong("lastDay") else null,
            )
        }
    }
}
