package app.opentaiment.feature.vehicle.tpms

import java.util.Locale

enum class Wheel(val label: String, val short: String) {
    FRONT_LEFT("Przód lewe", "LP"),
    FRONT_RIGHT("Przód prawe", "PP"),
    REAR_LEFT("Tył lewe", "LT"),
    REAR_RIGHT("Tył prawe", "PT"),
}

/** One reading from a tyre sensor. */
data class TpmsReading(
    val sensorId: String,
    /** Gauge pressure in bar (what a tyre pump shows). */
    val pressureBar: Double,
    val temperatureC: Double,
    val batteryPct: Int?,
    /** Wheel number the sensor reports for itself (1–4), if its format has one. */
    val factoryPosition: Int?,
    val leaking: Boolean,
    val timeMs: Long,
)

enum class TpmsProblem(val label: String) {
    LOW("Za niskie ciśnienie"),
    HIGH("Za wysokie ciśnienie"),
    HOT("Gorąca opona"),
    LEAK("Ucieka powietrze"),
    LOW_BATTERY("Słaba bateria czujnika"),
    STALE("Brak sygnału"),
}

data class TpmsLimits(val minBar: Double = 1.9, val maxBar: Double = 3.3, val maxTempC: Double = 80.0)

data class WheelStatus(val wheel: Wheel, val reading: TpmsReading?, val problems: List<TpmsProblem>)

data class TpmsState(
    val scanning: Boolean = false,
    val wheels: List<WheelStatus> = Wheel.entries.map { WheelStatus(it, null, emptyList()) },
    /** Sensors heard nearby that are not assigned to a wheel. */
    val unassigned: List<TpmsReading> = emptyList(),
    val limits: TpmsLimits = TpmsLimits(),
    val error: String? = null,
) {
    val hasAnyReading: Boolean get() = wheels.any { it.reading != null }
    val problems: List<Pair<Wheel, TpmsProblem>> get() = wheels.flatMap { w -> w.problems.map { w.wheel to it } }
}

/**
 * Decodes advertisements of the common cheap BLE tyre sensors (sold as "TPMS BLE", "SYTPMS",
 * app "TPMS II" – external caps or internal valves). They broadcast manufacturer data with
 * company id 0x0100 followed by 16 bytes:
 *
 * ```
 * [0..5]  sensor address, first byte 0x80..0x83 = wheel 1..4 as printed on the sensor
 * [6..9]  pressure above atmospheric, Pa, little endian
 * [10..13] temperature, 0.01 °C, little endian, signed
 * [14]    battery %
 * [15]    flags (non-zero = leaking / alarm)
 * ```
 */
object TpmsParser {
    const val COMPANY_ID = 0x0100

    fun parse(data: ByteArray, nowMs: Long): TpmsReading? {
        if (data.size < 16) return null
        fun u8(i: Int) = data[i].toInt() and 0xFF
        fun le32(i: Int): Long = (u8(i).toLong()) or (u8(i + 1).toLong() shl 8) or (u8(i + 2).toLong() shl 16) or (u8(i + 3).toLong() shl 24)

        val id = (0 until 6).joinToString(":") { String.format(Locale.US, "%02X", u8(it)) }
        val pressurePa = le32(6)
        val tempRaw = le32(10).toInt() // signed 32-bit
        val pressureBar = pressurePa / 100_000.0
        val tempC = tempRaw / 100.0
        // Sanity: reject garbage from other devices that happen to use the same company id.
        if (pressureBar !in 0.0..8.0 || tempC !in -45.0..130.0) return null
        val first = u8(0)
        val position = if (first in 0x80..0x83) first - 0x80 + 1 else null
        return TpmsReading(
            sensorId = id,
            pressureBar = pressureBar,
            temperatureC = tempC,
            batteryPct = u8(14).takeIf { it in 0..100 },
            factoryPosition = position,
            leaking = u8(15) != 0,
            timeMs = nowMs,
        )
    }

    fun problems(reading: TpmsReading?, limits: TpmsLimits, nowMs: Long, staleMs: Long = STALE_MS): List<TpmsProblem> {
        if (reading == null) return emptyList()
        if (nowMs - reading.timeMs > staleMs) return listOf(TpmsProblem.STALE)
        return buildList {
            // A sensor resting off the tyre reads ~0 bar: only warn when it looks mounted.
            if (reading.pressureBar in 0.3..limits.minBar) add(TpmsProblem.LOW)
            if (reading.pressureBar > limits.maxBar) add(TpmsProblem.HIGH)
            if (reading.temperatureC > limits.maxTempC) add(TpmsProblem.HOT)
            if (reading.leaking) add(TpmsProblem.LEAK)
            if (reading.batteryPct != null && reading.batteryPct < 15) add(TpmsProblem.LOW_BATTERY)
        }
    }

    /** Wheel for a sensor: what the user assigned, otherwise the number printed on the sensor. */
    fun wheelFor(reading: TpmsReading, assigned: Map<String, Wheel>): Wheel? =
        assigned[reading.sensorId] ?: reading.factoryPosition?.let { Wheel.entries.getOrNull(it - 1) }
            ?.takeIf { w -> assigned.values.none { it == w } }

    /** Sensors that went quiet for this long count as lost (they send every few seconds while driving). */
    const val STALE_MS = 15 * 60_000L
}
