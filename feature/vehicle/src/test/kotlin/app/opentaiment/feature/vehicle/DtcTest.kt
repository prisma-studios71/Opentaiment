package app.opentaiment.feature.vehicle

import app.opentaiment.feature.vehicle.data.DtcDictionary
import app.opentaiment.feature.vehicle.obd.Elm327Parser
import app.opentaiment.feature.vehicle.data.dtcCountLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcTest {

    @Test
    fun decodesCodeLetters() {
        assertEquals("P0133", Elm327Parser.decodeDtc("0133"))
        assertEquals("C0300", Elm327Parser.decodeDtc("4300"))
        assertEquals("B1234", Elm327Parser.decodeDtc("9234"))
        assertEquals("U0123", Elm327Parser.decodeDtc("C123"))
        assertEquals("P242F", Elm327Parser.decodeDtc("242F"))
    }

    @Test
    fun canSingleFrameWithCount() {
        assertEquals(listOf("P0133", "P0221"), Elm327Parser.dtcs("43 02 01 33 02 21\r\r>", 0x03, isCan = true))
    }

    @Test
    fun canNoCodes() {
        assertEquals(emptyList<String>(), Elm327Parser.dtcs("4300\r", 0x03, isCan = true))
        assertEquals(emptyList<String>(), Elm327Parser.dtcs("NO DATA\r", 0x0A, isCan = true))
    }

    @Test
    fun canMultiFrame() {
        val raw = "00A\r0:430401330221\r1:02220304000000\r\r"
        assertEquals(listOf("P0133", "P0221", "P0222", "P0304"), Elm327Parser.dtcs(raw, 0x03, isCan = true))
    }

    @Test
    fun canSeveralEcusAnswer() {
        val raw = "43010420\r4301C100\r"
        assertEquals(listOf("P0420", "U0100"), Elm327Parser.dtcs(raw, 0x03, isCan = true))
    }

    @Test
    fun legacyProtocolPadsWithZeros() {
        assertEquals(listOf("P0133", "P0221"), Elm327Parser.dtcs("43 01 33 02 21 00 00\r", 0x03, isCan = false))
        assertEquals(
            listOf("P0171", "P0300", "P0420", "P0442"),
            Elm327Parser.dtcs("43017103000420\r43044200000000\r", 0x03, isCan = false),
        )
    }

    @Test
    fun pendingUsesMode47() {
        assertEquals(listOf("P0171"), Elm327Parser.dtcs("47010171\r", 0x07, isCan = true))
        assertEquals(emptyList<String>(), Elm327Parser.dtcs("43010171\r", 0x07, isCan = true))
    }

    @Test
    fun detectsCanProtocol() {
        assertTrue(Elm327Parser.isCanProtocol("A6\r"))
        assertTrue(Elm327Parser.isCanProtocol("8"))
        assertFalse(Elm327Parser.isCanProtocol("A3\r")) // ISO 9141-2
        assertFalse(Elm327Parser.isCanProtocol("5"))     // KWP2000 fast init
    }

    @Test
    fun dictionary() {
        assertEquals("Niska sprawność katalizatora (rząd 1)", DtcDictionary.describe("P0420"))
        assertEquals("Usterka zapłonu lub wypadanie zapłonów", DtcDictionary.describe("P0399"))
        assertTrue(DtcDictionary.describe("P1234").startsWith("Kod producenta"))
        assertEquals("Podwozie (ABS, zawieszenie)", DtcDictionary.system("C0035"))
    }

    @Test
    fun polishPlural() {
        assertEquals("1 błąd", dtcCountLabel(1))
        assertEquals("3 błędy", dtcCountLabel(3))
        assertEquals("5 błędów", dtcCountLabel(5))
        assertEquals("12 błędów", dtcCountLabel(12))
        assertEquals("22 błędy", dtcCountLabel(22))
    }
}
