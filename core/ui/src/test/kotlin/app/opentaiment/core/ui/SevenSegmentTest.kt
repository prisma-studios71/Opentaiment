package app.opentaiment.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SevenSegmentTest {

    @Test
    fun drawsNumbersTimesAndPlaceholders() {
        listOf("63", "10,2", "8.0", "14:32", " 42", "-12", "—5", "3\u00A0090", "3\u202F090").forEach {
            assertTrue("should draw '$it'", SevenSegment.canDraw(it))
        }
    }

    @Test
    fun fallsBackToTextForWordsAndUnits() {
        listOf("", "—", "  ", "6,8 s", "czekam na start…", "1:15:02 h", "98 KM").forEach {
            assertFalse("should not draw '$it'", SevenSegment.canDraw(it))
        }
    }

    @Test
    fun separatorsAreNarrowerThanDigits() {
        val digits = SevenSegment.widthUnits("1432")
        assertTrue(SevenSegment.widthUnits("14:32") < SevenSegment.widthUnits("14832"))
        assertTrue(SevenSegment.widthUnits("10,2") < SevenSegment.widthUnits("1082"))
        assertEquals(digits + 70f, SevenSegment.widthUnits("14320"), 0.001f)
        assertTrue(SevenSegment.widthUnits("3\u00A0090") < SevenSegment.widthUnits("3 090"))
    }
}
