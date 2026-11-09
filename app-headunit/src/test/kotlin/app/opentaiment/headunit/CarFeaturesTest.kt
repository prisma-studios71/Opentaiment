package app.opentaiment.headunit

import app.opentaiment.headunit.car.KeyAction
import app.opentaiment.headunit.car.KeyBindings
import app.opentaiment.headunit.car.SpeedVolume
import app.opentaiment.headunit.car.SpeedVolumeController
import app.opentaiment.headunit.car.SpeedVolumeLevel
import app.opentaiment.headunit.car.VolumeControl
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedVolumeTest {
    private val level = SpeedVolumeLevel.MEDIUM // +1 step every 28 km/h from 30 km/h

    @Test
    fun `steps grow with speed and cap`() {
        assertEquals(0, SpeedVolume.steps(10.0, level, 0))
        assertEquals(1, SpeedVolume.steps(35.0, level, 0))
        assertEquals(4, SpeedVolume.steps(120.0, level, 0))
        assertEquals(SpeedVolume.MAX_STEPS, SpeedVolume.steps(300.0, level, 0))
        assertEquals(0, SpeedVolume.steps(200.0, SpeedVolumeLevel.OFF, 3))
    }

    @Test
    fun `hysteresis keeps the step until clearly slower`() {
        // Step 2 starts at 58 km/h.
        assertEquals(2, SpeedVolume.steps(60.0, level, 1))
        assertEquals(2, SpeedVolume.steps(55.0, level, 2))
        assertEquals(1, SpeedVolume.steps(50.0, level, 2))
        // No speed reading (GPS gap): keep what we have.
        assertEquals(2, SpeedVolume.steps(null, level, 2))
    }

    private class FakeVolume(var level: Int, override val maxVolume: Int = 15) : VolumeControl {
        override val volume: Int get() = level
        override fun setVolume(value: Int) { level = value }
    }

    @Test
    fun `adds steps on top of the driver's volume and respects manual changes`() {
        val audio = FakeVolume(5)
        val c = SpeedVolumeController(audio)
        c.update(90.0, level) // 3 steps
        assertEquals(8, audio.volume)
        audio.level = 10 // driver turned it up at speed: base is now 7
        c.update(40.0, level) // down to 1 step
        assertEquals(8, audio.volume)
        c.reset()
        assertEquals(7, audio.volume)
    }

    @Test
    fun `never goes above max`() {
        val audio = FakeVolume(14)
        SpeedVolumeController(audio).update(250.0, level)
        assertEquals(15, audio.volume)
    }
}

class KeyBindingsTest {
    @Test
    fun `round trip and ignore junk`() {
        val map = mapOf(87 to KeyAction.NEXT, 300 to KeyAction.NAVIGATE_HOME)
        assertEquals(map, KeyBindings.parse(KeyBindings.encode(map)))
        assertEquals(mapOf(5 to KeyAction.PHONE), KeyBindings.parse("5=PHONE;x=NEXT;6=NOPE;;7"))
        assertEquals(emptyMap<Int, KeyAction>(), KeyBindings.parse(null))
    }

    @Test
    fun `back and power can't be taken over`() {
        assertEquals(true, KeyBindings.isProtected(4))
        assertEquals(true, KeyBindings.isProtected(26))
        assertEquals(false, KeyBindings.isProtected(87))
    }
}
