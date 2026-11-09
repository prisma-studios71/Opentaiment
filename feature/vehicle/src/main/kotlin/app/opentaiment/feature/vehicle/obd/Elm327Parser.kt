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

    /**
     * Parses a DTC response (mode 03 / 07 / 0A) into codes like "P0420".
     *
     * CAN cars send "43 NN" + NN code pairs (long lists as ISO-TP frames that the ELM prints as
     * "0:…", "1:…" after a byte-count line). Older protocols send "43" + three pairs per line,
     * padded with 0000.
     */
    fun dtcs(raw: String, mode: Int, isCan: Boolean): List<String> {
        val header = String.format(Locale.US, "%02X", mode + 0x40)
        val lines = raw.uppercase(Locale.US)
            .split('\r', '\n')
            .map { it.replace(" ", "").replace(">", "").trim() }
            .filter { it.isNotEmpty() }

        val frameRegex = Regex("^[0-9A-F]:")
        val framed = lines.filter { frameRegex.containsMatchIn(it) }
        val messages = if (framed.isNotEmpty()) {
            val data = framed.joinToString("") { it.substringAfter(':') }
            val byteCount = lines.firstOrNull { it.length == 3 && it.isHex() }?.toInt(16)
            listOf(if (byteCount != null) data.take(byteCount * 2) else data)
        } else {
            lines.filter { it.isHex() }
        }

        val codes = linkedSetOf<String>()
        for (message in messages) {
            if (!message.startsWith(header)) continue
            var payload = message.substring(2)
            if (isCan) {
                if (payload.length < 2) continue
                val count = payload.take(2).toInt(16)
                payload = payload.drop(2).take(count * 4)
            }
            payload.chunked(4)
                .filter { it.length == 4 && it != "0000" }
                .forEach { codes += decodeDtc(it) }
        }
        return codes.toList()
    }

    /** Two DTC bytes -> "P0133". Top 2 bits pick P/C/B/U, the rest are hex digits. */
    fun decodeDtc(hex4: String): String {
        val a = hex4.substring(0, 2).toInt(16)
        val system = "PCBU"[a shr 6]
        val first = (a shr 4) and 0x3
        val second = (a and 0xF).toString(16).uppercase(Locale.US)
        return "$system$first$second${hex4.substring(2).uppercase(Locale.US)}"
    }

    /** "ATDPN" answers e.g. "A6" (auto, protocol 6). Protocols 6–C are CAN. */
    fun isCanProtocol(raw: String): Boolean {
        val last = raw.uppercase(Locale.US).filter { it.isLetterOrDigit() }.lastOrNull() ?: return false
        return last in '6'..'9' || last in 'A'..'C'
    }

    private fun String.isHex() = all { it in '0'..'9' || it in 'A'..'F' }

    private fun hexLines(raw: String): List<String> =
        raw.uppercase(Locale.US)
            .split('\r', '\n')
            .map { it.replace(" ", "").replace(">", "").trim() }
            .filter { line -> line.isNotEmpty() && line.all { it in '0'..'9' || it in 'A'..'F' } }
}
