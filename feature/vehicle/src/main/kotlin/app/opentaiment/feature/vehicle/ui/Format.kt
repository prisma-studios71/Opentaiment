package app.opentaiment.feature.vehicle.ui

import java.util.Locale

private val PL = Locale("pl")
internal const val NO_VALUE = "—"

internal fun Double?.fmt(decimals: Int = 0): String =
    if (this == null || isNaN() || isInfinite()) NO_VALUE else String.format(PL, "%,.${decimals}f", this)

internal fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(PL, "%d:%02d:%02d", h, m, s) else String.format(PL, "%d:%02d", m, s)
}

internal fun kwToHp(kw: Double): Double = kw * 1.35962

/** Short live summary for the home screen tile: speed and consumption on separate lines. */
fun app.opentaiment.feature.vehicle.data.VehicleStats.shortSummary(): String? {
    if (!hasData) return null
    val consumption = instantLPer100Km?.let { "${it.fmt(1)} l/100" } ?: fuelRateLh?.let { "${it.fmt(1)} l/h" }
    return listOfNotNull(speedKmh?.let { "${it.fmt()} km/h" }, consumption).joinToString("\n").ifEmpty { null }
}
