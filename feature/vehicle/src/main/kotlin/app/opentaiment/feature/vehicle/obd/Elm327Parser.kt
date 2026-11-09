package app.opentaiment.feature.vehicle.obd

import java.util.Locale

/** Pure parsing of ELM327 text responses – no I/O, fully unit-testable. */
object Elm327Parser {

    /**
     * Extracts data bytes of a mode 01 response for [pid], e.g. "41 0C 1A F8" -> [0x1A, 0xF8].
     * Handles spaces on/off, "SEARCHING...", echoes and several ECUs answering (first one wins).
     * Returns null for "NO DATA", "?", "UNABLE TO CONNECT" and other errors.
     */
    fun mode01Data(raw: String, pid: Int): IntArray? {
        val header = String.format(Locale.US, "41%02X", pid)
        for (line in hexLines(raw)) {
            if (!line.startsWith(header)) continue
            val hex = line.substring(header.length)
            if (hex.length < 2) continue
            return IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }
        }
        return null
    }

    /** Decodes a "supported PIDs" bitmask (PID 00/20/40) into absolute PID numbers. */
    fun supportedPids(data: IntArray, base: Int): Set<Int> = buildSet {
        for (i in 0 until minOf(32, data.size * 8)) {
            if ((data[i / 8] shr (7 - i % 8)) and 1 == 1) add(base + i + 1)
        }
    }

    /** Parses the answer to "ATRV", e.g. "12.6V". */
    fun voltage(raw: String): Double? =
        Regex("""(\d+(?:\.\d+)?)\s*V""").find(raw.uppercase(Locale.US))?.groupValues?.get(1)?.toDoubleOrNull()

    private fun hexLines(raw: String): List<String> =
        raw.uppercase(Locale.US)
            .split('\r', '\n')
            .map { it.replace(" ", "").replace(">", "").trim() }
            .filter { line -> line.isNotEmpty() && line.all { it in '0'..'9' || it in 'A'..'F' } }
}
