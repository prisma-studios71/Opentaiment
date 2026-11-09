package app.opentaiment.feature.vehicle.data

import java.util.Locale

enum class AlertType(val title: String, val critical: Boolean) {
    OVERHEAT("Silnik się przegrzewa", critical = true),
    OIL_HOT("Za gorący olej", critical = true),
    NOT_CHARGING("Brak ładowania", critical = true),
    OVERVOLTAGE("Za wysokie napięcie", critical = false),
    WEAK_BATTERY("Słaby akumulator", critical = false),
    LOW_FUEL("Mało paliwa", critical = false),
}

data class VehicleAlert(val type: AlertType, val message: String)

/**
 * Watches live values and raises alerts. Each condition must hold for a few consecutive samples
 * (one noisy reading shouldn't scare the driver) and clears only after recovering past a lower
 * threshold (hysteresis), so alerts don't flicker.
 */
class AlertMonitor(private val confirmSamples: Int = 3) {

    private val counters = mutableMapOf<AlertType, Int>()
    private val active = linkedMapOf<AlertType, VehicleAlert>()

    /** Returns currently active alerts, newest condition last. */
    fun update(s: ObdSample): List<VehicleAlert> {
        val rpm = s.rpm
        val v = s.voltage
        check(AlertType.OVERHEAT, s.coolantTempC, raise = { it >= 110 }, clear = { it < 105 }) {
            "Płyn chłodniczy ${it.f()} °C. Zjedź w bezpieczne miejsce i zgaś silnik."
        }
        check(AlertType.OIL_HOT, s.oilTempC, raise = { it >= 135 }, clear = { it < 128 }) {
            "Olej ${it.f()} °C. Zwolnij i daj silnikowi odpocząć."
        }
        val running = rpm != null && rpm > 600
        check(AlertType.NOT_CHARGING, v.takeIf { running }, raise = { it < 12.6 }, clear = { it > 13.0 }) {
            "Napięcie ${it.f(1)} V przy pracującym silniku – sprawdź alternator i pasek."
        }
        check(AlertType.OVERVOLTAGE, v, raise = { it > 15.2 }, clear = { it < 14.9 }) {
            "Napięcie ${it.f(1)} V – możliwa usterka regulatora napięcia."
        }
        val engineOff = rpm == null || rpm < 100
        check(AlertType.WEAK_BATTERY, v.takeIf { engineOff }, raise = { it < 11.9 }, clear = { it > 12.2 }) {
            "Napięcie ${it.f(1)} V przy zgaszonym silniku – akumulator może nie odpalić."
        }
        check(AlertType.LOW_FUEL, s.fuelLevelPct, raise = { it <= 10 }, clear = { it >= 13 }) {
            "Zostało ${it.f()} % paliwa – czas na stację."
        }
        return active.values.toList()
    }

    fun reset() {
        counters.clear()
        active.clear()
    }

    private fun check(
        type: AlertType,
        value: Double?,
        raise: (Double) -> Boolean,
        clear: (Double) -> Boolean,
        message: (Double) -> String,
    ) {
        if (value == null) {
            counters[type] = 0
            if (type == AlertType.NOT_CHARGING || type == AlertType.WEAK_BATTERY) active.remove(type)
            return
        }
        if (type in active) {
            if (clear(value)) {
                active.remove(type)
                counters[type] = 0
            } else {
                active[type] = VehicleAlert(type, message(value)) // keep the number fresh
            }
            return
        }
        val count = if (raise(value)) (counters[type] ?: 0) + 1 else 0
        counters[type] = count
        if (count >= confirmSamples) active[type] = VehicleAlert(type, message(value))
    }

    private fun Double.f(decimals: Int = 0) = String.format(Locale("pl"), "%.${decimals}f", this)
}
