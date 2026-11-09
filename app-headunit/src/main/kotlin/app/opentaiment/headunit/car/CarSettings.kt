package app.opentaiment.headunit.car

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeedVolumeLevel(val label: String, val kmhPerStep: Double?) {
    OFF("Wyłączona", null),
    LOW("Lekko", 40.0),
    MEDIUM("Średnio", 28.0),
    HIGH("Mocno", 18.0),
}

/** Everything that makes the tablet behave like a built-in car radio. */
data class CarSettings(
    /** Start in the background when the device boots (and open the launcher if allowed). */
    val autostart: Boolean = true,
    /** Tablet on a car charger: USB power on = ignition on, power gone = ignition off. */
    val ignitionByPower: Boolean = false,
    /** Turn the last radio station back on when the car starts. */
    val resumeRadio: Boolean = true,
    val speedVolume: SpeedVolumeLevel = SpeedVolumeLevel.OFF,
    /** Set the system screen brightness for day and night (needs "modify system settings"). */
    val brightnessControl: Boolean = false,
    val dayBrightnessPct: Int = 100,
    val nightBrightnessPct: Int = 25,
    /** Small bubble with speed and consumption on top of navigation and other apps. */
    val overlay: Boolean = false,
    val homeAddress: String = "",
    val workAddress: String = "",
    val keyBindings: Map<Int, KeyAction> = emptyMap(),
)

class CarSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("car", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<CarSettings> = _settings.asStateFlow()

    fun update(transform: (CarSettings) -> CarSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit {
            putBoolean("autostart", s.autostart)
            putBoolean("ignition_power", s.ignitionByPower)
            putBoolean("resume_radio", s.resumeRadio)
            putString("speed_volume", s.speedVolume.name)
            putBoolean("brightness", s.brightnessControl)
            putInt("day_brightness", s.dayBrightnessPct)
            putInt("night_brightness", s.nightBrightnessPct)
            putBoolean("overlay", s.overlay)
            putString("home", s.homeAddress)
            putString("work", s.workAddress)
            putString("keys", KeyBindings.encode(s.keyBindings))
        }
    }

    private fun load(): CarSettings {
        val d = CarSettings()
        return CarSettings(
            autostart = prefs.getBoolean("autostart", d.autostart),
            ignitionByPower = prefs.getBoolean("ignition_power", d.ignitionByPower),
            resumeRadio = prefs.getBoolean("resume_radio", d.resumeRadio),
            speedVolume = prefs.getString("speed_volume", null)
                ?.let { n -> SpeedVolumeLevel.entries.firstOrNull { it.name == n } } ?: d.speedVolume,
            brightnessControl = prefs.getBoolean("brightness", d.brightnessControl),
            dayBrightnessPct = prefs.getInt("day_brightness", d.dayBrightnessPct),
            nightBrightnessPct = prefs.getInt("night_brightness", d.nightBrightnessPct),
            overlay = prefs.getBoolean("overlay", d.overlay),
            homeAddress = prefs.getString("home", "").orEmpty(),
            workAddress = prefs.getString("work", "").orEmpty(),
            keyBindings = KeyBindings.parse(prefs.getString("keys", null)),
        )
    }

    companion object {
        @Volatile
        private var instance: CarSettingsStore? = null

        fun get(context: Context): CarSettingsStore = instance ?: synchronized(this) {
            instance ?: CarSettingsStore(context).also { instance = it }
        }
    }
}
