package app.opentaiment.headunit

import app.opentaiment.headunit.tiles.HomeWidget
import app.opentaiment.headunit.tiles.TileIds
import app.opentaiment.headunit.tiles.WidgetSlots
import app.opentaiment.headunit.tiles.TileList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileListTest {

    @Test
    fun addIgnoresDuplicatesAndRespectsLimit() {
        assertEquals(listOf("a", "b"), TileList.add(listOf("a"), "b"))
        assertEquals(listOf("a"), TileList.add(listOf("a"), "a"))
        val full = (1..TileList.MAX_TILES).map { "t$it" }
        assertEquals(full, TileList.add(full, "extra"))
    }

    @Test
    fun moveUpDownAndClamp() {
        val tiles = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), TileList.move(tiles, "b", -1))
        assertEquals(listOf("a", "c", "b"), TileList.move(tiles, "b", 1))
        assertEquals(tiles, TileList.move(tiles, "a", -1))
        assertEquals(tiles, TileList.move(tiles, "missing", 1))
    }

    @Test
    fun parseFallsBackToDefaultsAndCleansUp() {
        assertEquals(TileList.DEFAULT, TileList.parse(null))
        assertEquals(listOf("nav", "app:com.spotify.music"), TileList.parse("nav, app:com.spotify.music,,nav"))
        assertEquals(emptyList<String>(), TileList.parse(""))
    }

    @Test
    fun appIds() {
        assertEquals("app:com.waze", TileIds.app("com.waze"))
        assertEquals("com.waze", TileIds.packageOf("app:com.waze"))
        assertNull(TileIds.packageOf("nav"))
    }

    @Test
    fun widgetSlotsSwapWhenWidgetIsAlreadyShown() {
        val slots = WidgetSlots.DEFAULT // clock, weather, music
        assertEquals(
            listOf(HomeWidget.SPEED, HomeWidget.WEATHER, HomeWidget.MUSIC),
            WidgetSlots.set(slots, 0, HomeWidget.SPEED),
        )
        // music moves to slot 0, the clock takes music's old place
        assertEquals(
            listOf(HomeWidget.MUSIC, HomeWidget.WEATHER, HomeWidget.CLOCK),
            WidgetSlots.set(slots, 0, HomeWidget.MUSIC),
        )
        assertEquals(slots, WidgetSlots.set(slots, 5, HomeWidget.ECO))
    }

    @Test
    fun widgetSlotsParsing() {
        assertEquals(WidgetSlots.DEFAULT, WidgetSlots.parse(null))
        assertEquals(WidgetSlots.DEFAULT, WidgetSlots.parse("clock,weather")) // wrong count
        assertEquals(WidgetSlots.DEFAULT, WidgetSlots.parse("clock,nope,music"))
        assertEquals(
            listOf(HomeWidget.ECO, HomeWidget.TRIP, HomeWidget.SPEED),
            WidgetSlots.parse("eco, trip,speed"),
        )
    }
}
