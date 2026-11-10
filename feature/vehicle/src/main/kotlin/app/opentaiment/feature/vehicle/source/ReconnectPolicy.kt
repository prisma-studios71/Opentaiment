package app.opentaiment.feature.vehicle.source

/**
 * How long to wait before reconnecting to the OBD adapter after the link dropped.
 * Quick at first (adapters often reset when the engine cranks), then every 30 s for as long
 * as the car is on – a head unit has no one to press "connect".
 */
object ReconnectPolicy {
    private val STEPS_MS = longArrayOf(2_000, 4_000, 8_000, 15_000, 30_000)

    fun delayMs(attempt: Int): Long = STEPS_MS[attempt.coerceIn(0, STEPS_MS.lastIndex)]
}
