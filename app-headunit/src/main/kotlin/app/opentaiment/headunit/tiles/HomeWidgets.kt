package app.opentaiment.headunit.tiles

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.TireRepair
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a big card ("widget") on the home screen can show. */
enum class HomeWidget(val id: String, val label: String, val description: String, val icon: ImageVector) {
    CLOCK("clock", "Zegar", "Godzina i data", Icons.Rounded.Schedule),
    WEATHER("weather", "Pogoda", "Temperatura, opis, ostrzeżenie o śliskiej drodze", Icons.Rounded.Cloud),
    MUSIC("music", "Muzyka", "Co gra i sterowanie odtwarzaniem", Icons.Rounded.MusicNote),
    SPEED("speed", "Prędkość", "Prędkość, obroty i przepustnica (OBD)", Icons.Rounded.Speed),
    CONSUMPTION("fuel", "Spalanie", "Chwilowe i średnie spalanie, poziom paliwa (OBD)", Icons.Rounded.LocalGasStation),
    ECO("eco", "Eco-score", "Ocena stylu jazdy i podpowiedź (OBD)", Icons.Rounded.Eco),
    TRIP("trip", "Trasa", "Dystans, czas i koszt bieżącej jazdy (OBD)", Icons.Rounded.Route),
    ENGINE("engine", "Silnik", "Temperatury, akumulator, obciążenie (OBD)", Icons.Rounded.Thermostat),
    DIAGNOSTICS("diag", "Błędy", "Check Engine i liczba błędów (OBD)", Icons.Rounded.Build),
    WEEK("week", "Ten tydzień", "Kilometry, koszt paliwa i eco z całego tygodnia", Icons.Rounded.CalendarMonth),
    RADIO("radio", "Radio", "Co gra w radiu i szybki wybór ulubionych stacji", Icons.Rounded.Radio),
    NAVIGATION("nav", "Nawigacja", "Do domu, do pracy, stacje paliw w pobliżu", Icons.Rounded.Navigation),
    TPMS("tpms", "Opony", "Ciśnienie w kołach z czujników TPMS (BLE)", Icons.Rounded.TireRepair),
    SERVICE("service", "Serwis", "Najbliższy olej, przegląd, OC", Icons.Rounded.CarRepair),
    ;

    companion object {
        fun byId(id: String): HomeWidget? = entries.firstOrNull { it.id == id }
    }
}

/** Slot names in the order they are stored: two small cards on the left, one large in the middle. */
object WidgetSlots {
    const val COUNT = 3
    val NAMES = listOf("Lewy górny", "Lewy dolny", "Duży, na środku")
    val DEFAULT = listOf(HomeWidget.CLOCK, HomeWidget.WEATHER, HomeWidget.MUSIC)

    /** Puts [widget] into [slot]; if it is already shown elsewhere, the two slots swap. */
    fun set(slots: List<HomeWidget>, slot: Int, widget: HomeWidget): List<HomeWidget> {
        if (slot !in slots.indices) return slots
        val result = slots.toMutableList()
        val other = result.indexOf(widget)
        if (other >= 0 && other != slot) result[other] = result[slot]
        result[slot] = widget
        return result
    }

    fun parse(stored: String?): List<HomeWidget> {
        val parsed = stored?.split(',')?.mapNotNull { HomeWidget.byId(it.trim()) }.orEmpty()
        return if (parsed.size == COUNT) parsed else DEFAULT
    }
}

class WidgetsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    private val _widgets = MutableStateFlow(WidgetSlots.parse(prefs.getString(KEY, null)))
    val widgets: StateFlow<List<HomeWidget>> = _widgets.asStateFlow()

    fun set(slot: Int, widget: HomeWidget) = update(WidgetSlots.set(_widgets.value, slot, widget))
    fun reset() = update(WidgetSlots.DEFAULT)

    private fun update(widgets: List<HomeWidget>) {
        _widgets.value = widgets
        prefs.edit { putString(KEY, widgets.joinToString(",") { it.id }) }
    }

    private companion object {
        const val KEY = "widgets"
    }
}
