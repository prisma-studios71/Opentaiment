package app.opentaiment.feature.vehicle

import app.opentaiment.feature.vehicle.obd.Elm327Parser
import app.opentaiment.feature.vehicle.obd.ObdPid
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Elm327ParserTest {

    @Test
    fun parsesWithAndWithoutSpaces() {
        assertArrayEquals(intArrayOf(0x1A, 0xF8), Elm327Parser.mode01Data("41 0C 1A F8\r\r", 0x0C))
        assertArrayEquals(intArrayOf(0x1A, 0xF8), Elm327Parser.mode01Data("410C1AF8\r", 0x0C))
    }

    @Test
    fun skipsSearchingAndEcho() {
        assertArrayEquals(intArrayOf(0x3C), Elm327Parser.mode01Data("010D\rSEARCHING...\r410D3C\r\r", 0x0D))
    }

    @Test
    fun firstEcuWinsWhenSeveralAnswer() {
        assertArrayEquals(intArrayOf(0x50), Elm327Parser.mode01Data("410D50\r410D51\r", 0x0D))
    }

    @Test
    fun errorsGiveNull() {
        assertNull(Elm327Parser.mode01Data("NO DATA\r", 0x0C))
        assertNull(Elm327Parser.mode01Data("?\r", 0x0C))
        assertNull(Elm327Parser.mode01Data("UNABLE TO CONNECT\r", 0x0C))
        assertNull(Elm327Parser.mode01Data("410D3C", 0x0C)) // answer for another PID
    }

    @Test
    fun decodesPids() {
        assertEquals(1726.0, ObdPid.RPM.decodeOrNull(intArrayOf(0x1A, 0xF8))!!, 0.001)
        assertEquals(60.0, ObdPid.SPEED.decodeOrNull(intArrayOf(0x3C))!!, 0.001)
        assertEquals(90.0, ObdPid.COOLANT_TEMP.decodeOrNull(intArrayOf(130))!!, 0.001)
        assertEquals(12.34, ObdPid.MAF.decodeOrNull(intArrayOf(0x04, 0xD2))!!, 0.001)
        assertEquals(100.0, ObdPid.THROTTLE.decodeOrNull(intArrayOf(255))!!, 0.001)
        assertNull(ObdPid.RPM.decodeOrNull(intArrayOf(0x1A))) // too short
    }

    @Test
    fun decodesSupportedPidBitmask() {
        // Classic example from the OBD-II PID documentation.
        val pids = Elm327Parser.supportedPids(intArrayOf(0xBE, 0x1F, 0xA8, 0x13), 0x00)
        assertEquals(
            setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20),
            pids,
        )
    }

    @Test
    fun parsesVoltage() {
        assertEquals(12.6, Elm327Parser.voltage("12.6V\r")!!, 0.001)
        assertNull(Elm327Parser.voltage("?"))
    }
}
