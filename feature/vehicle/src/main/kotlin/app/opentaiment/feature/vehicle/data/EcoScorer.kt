package app.opentaiment.feature.vehicle.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class EcoScore(
    /** 0–100, higher is better. */
    val score: Int,
    val harshAccelerations: Int,
    val harshBrakings: Int,
    /** Share of moving time spent above the economical rev limit, 0–1. */
    val highRpmShare: Double,
    /** Share of total time spent idling at standstill, 0–1. */
    val idleShare: Double,
    val tips: List<String>,
)

/**
 * Rates driving style the way most eco-driving trainers do: smooth acceleration, anticipating
 * instead of braking hard, shifting early and not idling. Penalties are normalised per 10 minutes
 * so short and long trips are comparable.
 */
class EcoScorer {
    private var totalMs = 0L
    private var movingMs = 0L
    private var idleMs = 0L
    private var highRpmMs = 0L
    private var accelEvents = 0
    private var brakeEvents = 0
    private var inAccel = false
    private var inBrake = false

    fun add(dtMs: Long, speedKmh: Double?, rpm: Double?, accelMs2: Double?, highRpmLimit: Double) {
        if (dtMs <= 0) return
        totalMs += dtMs
        val moving = (speedKmh ?: 0.0) > 2.0
        if (moving) movingMs += dtMs
        if (!moving && rpm != null && rpm > 400) idleMs += dtMs
        if (moving && rpm != null && rpm > highRpmLimit) highRpmMs += dtMs

        if (accelMs2 != null) {
            // Hysteresis so one hard launch counts once, not once per sample.
            if (!inAccel && accelMs2 > HARSH_ACCEL) { inAccel = true; accelEvents++ }
            if (inAccel && accelMs2 < HARSH_ACCEL_EXIT) inAccel = false
            if (!inBrake && accelMs2 < HARSH_BRAKE) { inBrake = true; brakeEvents++ }
            if (inBrake && accelMs2 > HARSH_BRAKE_EXIT) inBrake = false
        }
    }

    /** Null until there is enough driving to judge. */
    fun result(): EcoScore? {
        if (totalMs < MIN_TIME_MS || movingMs == 0L) return null
        val tenMinutes = totalMs / 600_000.0
        val highRpmShare = highRpmMs.toDouble() / movingMs
        val idleShare = idleMs.toDouble() / totalMs

        val penalties = mapOf(
            Penalty.ACCEL to min(25.0, accelEvents / tenMinutes * 5),
            Penalty.BRAKE to min(25.0, brakeEvents / tenMinutes * 5),
            Penalty.RPM to min(25.0, highRpmShare * 100),
            Penalty.IDLE to min(15.0, max(0.0, idleShare - 0.1) * 50),
        )
        val score = (100 - penalties.values.sum()).roundToInt().coerceIn(0, 100)
        val tips = penalties.entries
            .filter { it.value >= 3.0 }
            .sortedByDescending { it.value }
            .map { it.key.tip }
            .ifEmpty { listOf("Jedziesz ekonomicznie – tak trzymaj! 🌱") }

        return EcoScore(score, accelEvents, brakeEvents, highRpmShare, idleShare, tips)
    }

    fun reset() {
        totalMs = 0; movingMs = 0; idleMs = 0; highRpmMs = 0
        accelEvents = 0; brakeEvents = 0; inAccel = false; inBrake = false
    }

    private enum class Penalty(val tip: String) {
        ACCEL("Przyspieszaj płynniej – gwałtowne ruszanie najbardziej podbija spalanie."),
        BRAKE("Patrz dalej przed siebie i hamuj silnikiem – mniej ostrych hamowań."),
        RPM("Zmieniaj bieg wcześniej – wysokie obroty to spalone paliwo."),
        IDLE("Dużo stoisz na wolnych obrotach – przy dłuższym postoju zgaś silnik."),
    }

    companion object {
        const val HARSH_ACCEL = 3.0
        const val HARSH_ACCEL_EXIT = 2.0
        const val HARSH_BRAKE = -3.5
        const val HARSH_BRAKE_EXIT = -2.5
        const val MIN_TIME_MS = 60_000L
    }
}
