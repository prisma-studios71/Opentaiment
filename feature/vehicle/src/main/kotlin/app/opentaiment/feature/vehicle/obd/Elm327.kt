package app.opentaiment.feature.vehicle.obd

import java.io.IOException
import java.util.Locale

/** High-level ELM327 protocol on top of an [ObdTransport]. */
class Elm327(private val transport: ObdTransport) {

    suspend fun initialize() {
        transport.send("ATZ", 5_000) // reset
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) {
            transport.send(cmd, 2_000) // echo off, no linefeeds, no spaces, no headers, adaptive timing, auto protocol
        }
        // The first real request triggers the protocol search, which can take a while.
        val response = transport.send("0100", 15_000)
        if (Elm327Parser.mode01Data(response, 0x00) == null) {
            throw IOException("Auto nie odpowiada (${response.trim().ifEmpty { "brak odpowiedzi" }}). Czy zapłon jest włączony?")
        }
    }

    suspend fun supportedPids(): Set<Int> {
        val result = mutableSetOf<Int>()
        var base = 0x00
        while (base <= 0x40) {
            val cmd = String.format(Locale.US, "01%02X", base)
            val data = Elm327Parser.mode01Data(transport.send(cmd, 5_000), base) ?: break
            if (data.size < 4) break
            val supported = Elm327Parser.supportedPids(data, base)
            result += supported
            if ((base + 0x20) !in supported) break
            base += 0x20
        }
        return result
    }

    suspend fun read(pid: ObdPid): Double? =
        Elm327Parser.mode01Data(transport.send(pid.command), pid.code)?.let(pid::decodeOrNull)

    suspend fun readVoltage(): Double? = Elm327Parser.voltage(transport.send("ATRV"))
}
