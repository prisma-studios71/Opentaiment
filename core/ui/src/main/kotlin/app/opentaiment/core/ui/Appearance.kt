package app.opentaiment.core.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

enum class NightMode(val label: String) {
    AUTO("Automatycznie"),
    OFF("Wyłączony"),
    ALWAYS("Zawsze"),
}

data class AppearanceSettings(
    val style: UiStyle = UiStyle.CLASSIC,
    val accent: Accent = Accent.BLUE,
    val nightMode: NightMode = NightMode.AUTO,
    val dimAtNight: Boolean = true,
)

/** Effective look right now: settings + whether it is currently night. */
data class AppearanceState(val settings: AppearanceSettings, val isNight: Boolean)

/**
 * Stores appearance settings and decides when it is night: between local sunset and sunrise
 * (from the last known location), or 20:00–6:00 when we don't know where we are.
 */
class AppearanceController(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AppearanceState(load(), isNight = false))
    val state: StateFlow<AppearanceState> = _state.asStateFlow()

    init {
        refresh()
        scope.launch {
            while (true) {
                delay(60_000)
                refresh()
            }
        }
    }

    fun update(settings: AppearanceSettings) {
        prefs.edit {
            putString(KEY_STYLE, settings.style.name)
            putString(KEY_ACCENT, settings.accent.name)
            putString(KEY_NIGHT, settings.nightMode.name)
            putBoolean(KEY_DIM, settings.dimAtNight)
        }
        _state.value = AppearanceState(settings, computeNight(settings))
    }

    fun refresh() {
        val settings = _state.value.settings
        _state.value = AppearanceState(settings, computeNight(settings))
    }

    private fun computeNight(settings: AppearanceSettings): Boolean = when (settings.nightMode) {
        NightMode.OFF -> false
        NightMode.ALWAYS -> true
        NightMode.AUTO -> {
            val now = ZonedDateTime.now()
            val location = lastLocation()
            if (location != null) SunCalc.isNight(now, location.first, location.second)
            else now.hour >= 20 || now.hour < 6
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(): Pair<Double, Double>? {
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return null
        return try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
                ?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun load() = AppearanceSettings(
        style = prefs.getString(KEY_STYLE, null)?.let { n -> UiStyle.entries.firstOrNull { it.name == n } } ?: UiStyle.CLASSIC,
        accent = prefs.getString(KEY_ACCENT, null)?.let { n -> Accent.entries.firstOrNull { it.name == n } } ?: Accent.BLUE,
        nightMode = prefs.getString(KEY_NIGHT, null)?.let { n -> NightMode.entries.firstOrNull { it.name == n } } ?: NightMode.AUTO,
        dimAtNight = prefs.getBoolean(KEY_DIM, true),
    )

    private companion object {
        const val KEY_STYLE = "style"
        const val KEY_ACCENT = "accent"
        const val KEY_NIGHT = "night"
        const val KEY_DIM = "dim"
    }
}

/** Lowers the screen brightness at night (only for this app's window). */
fun applyNightBrightness(window: Window, state: AppearanceState) {
    val attrs = window.attributes
    attrs.screenBrightness = if (state.isNight && state.settings.dimAtNight) 0.12f
    else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = attrs
}

/** Settings block: interface style, accent colour, night mode and dimming. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsSection(settings: AppearanceSettings, onChange: (AppearanceSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Styl interfejsu", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UiStyle.entries.forEach { style ->
                StylePreview(style, settings.accent, selected = style == settings.style) { onChange(settings.copy(style = style)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            settings.style.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text("Kolor akcentu", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Accent.entries.forEach { accent ->
                val selected = accent == settings.accent
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(accent.color)
                        .border(3.dp, if (selected) MaterialTheme.colorScheme.onSurface else accent.color, CircleShape)
                        .clickable { onChange(settings.copy(accent = accent)) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = accent.label, tint = accent.onColor)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Tryb nocny", style = MaterialTheme.typography.titleMedium)
        Text(
            "Po zachodzie słońca ekran robi się czerwono-czarny i ciemniejszy – nie oślepia w nocy.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NightMode.entries.forEach { mode ->
                if (mode == settings.nightMode) {
                    Button(onClick = {}) { Text(mode.label) }
                } else {
                    OutlinedButton(onClick = { onChange(settings.copy(nightMode = mode)) }) { Text(mode.label) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Przyciemniaj ekran w nocy", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = settings.dimAtNight, onCheckedChange = { onChange(settings.copy(dimAtNight = it)) })
        }
    }
}

/** A small live sample of a style: its background, a card and a big number, drawn by that style's own theme. */
@Composable
private fun StylePreview(style: UiStyle, accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val frame = RoundedCornerShape(18.dp)
    Column(Modifier.width(156.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(156.dp, 108.dp).clip(frame)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    frame,
                )
                .clickable(onClickLabel = style.label, onClick = onClick),
        ) {
            OpentaimentTheme(accent = accent, style = style) {
                OtBackground(Modifier.fillMaxSize().padding(10.dp)) {
                    OtCard(Modifier.fillMaxSize(), padding = 10.dp) {
                        Text("km/h", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        OtNumber("63", 40.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
            }
            Text(style.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}
