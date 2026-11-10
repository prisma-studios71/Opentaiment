package app.opentaiment.headunit.car

/**
 * Speed-dependent volume (like factory car radios): from [START_KMH] on, one volume step more
 * every `kmhPerStep` km/h, up to [MAX_STEPS]. Going down needs [HYSTERESIS_KMH] less speed,
 * so the volume doesn't pump up and down around a threshold.
 */
object SpeedVolume {
    const val START_KMH = 30.0
    const val MAX_STEPS = 6
    const val HYSTERESIS_KMH = 6.0

    fun steps(speedKmh: Double?, level: SpeedVolumeLevel, current: Int): Int {
        val perStep = level.kmhPerStep ?: return 0
        if (speedKmh == null) return current
        val raw = ((speedKmh - START_KMH) / perStep).let { if (it < 0) 0 else it.toInt() + 1 }.coerceAtMost(MAX_STEPS)
        if (raw >= current) return raw
        // Only drop a step once clearly below the threshold that added it.
        val thresholdOfCurrent = START_KMH + (current - 1) * perStep
        return if (speedKmh < thresholdOfCurrent - HYSTERESIS_KMH) raw.coerceAtLeast(0) else current
    }
}

/** Something that can read and set the media volume (AudioManager in the app, a fake in tests). */
interface VolumeControl {
    val volume: Int
    val maxVolume: Int
    fun setVolume(value: Int)
}

/**
 * Applies [SpeedVolume] on top of whatever volume the driver chose. When the driver turns the
 * knob, that becomes the new base – we only ever add our own steps to it.
 */
class SpeedVolumeController(private val control: VolumeControl) {
    var appliedSteps = 0
        private set

    fun update(speedKmh: Double?, level: SpeedVolumeLevel) {
        val target = SpeedVolume.steps(speedKmh, level, appliedSteps)
        if (target == appliedSteps) return
        val base = control.volume - appliedSteps
        control.setVolume((base + target).coerceIn(0, control.maxVolume))
        appliedSteps = target
    }

    /** Feature switched off / car stopped: give back the driver's own volume. */
    fun reset() {
        if (appliedSteps == 0) return
        control.setVolume((control.volume - appliedSteps).coerceIn(0, control.maxVolume))
        appliedSteps = 0
    }
}
