package app.opentaiment.feature.vehicle.obd

import app.opentaiment.feature.vehicle.data.DtcReport
import app.opentaiment.feature.vehicle.data.FreezeFrame
import app.opentaiment.feature.vehicle.data.Readiness
import java.io.IOException
import java.util.Locale

/** High-level ELM327 protocol on top of an [ObdTransport]. */
class Elm327(private val transport: ObdTransport) {

    private companion object {
        val FREEZE_FRAME_PIDS = listOf(
            ObdPid.RPM, ObdPid.SPEED, ObdPid.COOLANT_TEMP, ObdPid.ENGINE_LOAD, ObdPid.THROTTLE,
            ObdPid.SHORT_FUEL_TRIM, ObdPid.LONG_FUEL_TRIM, ObdPid.INTAKE_MAP, ObdPid.INTAKE_TEMP, ObdPid.MAF,
        )
    }

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

    suspend fun readDtcs(): DtcReport {
        val isCan = Elm327Parser.isCanProtocol(transport.send("ATDPN"))
        val status = Elm327Parser.mode01Data(transport.send("0101"), 0x01)
        fun codes(mode: Int, raw: String) = Elm327Parser.dtcs(raw, mode, isCan)
        val stored = codes(0x03, transport.send("03", 8_000))
        return DtcReport(
            milOn = status?.firstOrNull()?.let { it and 0x80 != 0 },
            stored = stored,
            pending = codes(0x07, transport.send("07", 8_000)),
            permanent = codes(0x0A, transport.send("0A", 8_000)),
            freezeFrame = if (stored.isNotEmpty()) readFreezeFrame() else null,
            readiness = status?.let(Readiness::decode),
        )
    }

    /** Mode 02 frame 0: which code triggered it, then a handful of values from that moment. */
    suspend fun readFreezeFrame(): FreezeFrame? {
        val dtcBytes = Elm327Parser.mode02Data(transport.send("020200"), 0x02) ?: return null
        if (dtcBytes.size < 2 || (dtcBytes[0] == 0 && dtcBytes[1] == 0)) return null
        val dtc = Elm327Parser.decodeDtc(String.format(Locale.US, "%02X%02X", dtcBytes[0], dtcBytes[1]))
        val values = linkedMapOf<ObdPid, Double>()
        for (pid in FREEZE_FRAME_PIDS) {
            val cmd = String.format(Locale.US, "02%02X00", pid.code)
            Elm327Parser.mode02Data(transport.send(cmd), pid.code)?.let(pid::decodeOrNull)?.let { values[pid] = it }
        }
        return FreezeFrame(dtc, values)
    }

    /** Mode 04: clears codes, turns Check Engine off and resets readiness monitors. */
    suspend fun clearDtcs() {
        val response = transport.send("04", 8_000)
        if (!response.replace(" ", "").contains("44")) {
            throw IOException("Sterownik odrzucił kasowanie (${response.trim()}). Wyłącz silnik, zostaw włączony zapłon.")
        }
    }
}
