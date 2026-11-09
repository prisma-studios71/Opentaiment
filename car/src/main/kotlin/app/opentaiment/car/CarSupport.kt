package app.opentaiment.car

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.VehicleHub
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A screen that redraws when vehicle data changes – at most once per [REFRESH_MS], because
 * Android Auto limits how often templates may be refreshed (and the driver can't read faster anyway).
 */
abstract class LiveCarScreen(carContext: CarContext) : Screen(carContext) {

    protected val vehicle = VehicleHub.get(carContext)
    private var lastRefresh = 0L
    private var refreshQueued = false

    protected fun observe(vararg flows: Flow<*>) {
        flows.forEach { flow ->
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { requestRefresh() } }
            }
        }
    }

    private fun requestRefresh() {
        val now = SystemClock.uptimeMillis()
        val wait = REFRESH_MS - (now - lastRefresh)
        if (wait <= 0) {
            lastRefresh = now
            invalidate()
        } else if (!refreshQueued) {
            refreshQueued = true
            lifecycleScope.launch {
                delay(wait)
                refreshQueued = false
                lastRefresh = SystemClock.uptimeMillis()
                invalidate()
            }
        }
    }

    protected val connected: Boolean get() = vehicle.connection.value is ConnectionState.Connected

    /** Shown instead of live data while there is no car (or demo) connected. */
    protected fun notConnectedTemplate(title: String): Template {
        val state = vehicle.connection.value
        val message = when (state) {
            is ConnectionState.Connecting -> "Łączę z ${state.deviceName}…"
            is ConnectionState.Failed -> "Nie udało się połączyć: ${state.message}"
            else -> "Brak połączenia z autem. Podłącz adapter OBD albo włącz tryb demo."
        }
        val builder = MessageTemplate.Builder(message)
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setIcon(icon(R.drawable.ic_car_app))
        if (state !is ConnectionState.Connecting) {
            vehicle.lastDevice()?.takeIf { vehicle.hasBluetoothPermission() }?.let { device ->
                builder.addAction(Action.Builder().setTitle("Połącz").setOnClickListener { vehicle.connect(device) }.build())
            }
            builder.addAction(Action.Builder().setTitle("Demo").setOnClickListener { vehicle.startDemo() }.build())
        } else {
            builder.setLoading(true)
        }
        return builder.build()
    }

    protected fun icon(@DrawableRes res: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()

    protected fun row(title: String, vararg texts: String, @DrawableRes iconRes: Int? = null): Row =
        Row.Builder().setTitle(title).apply {
            texts.filter { it.isNotBlank() }.take(2).forEach { addText(it) }
            iconRes?.let { setImage(icon(it)) }
        }.build()

    /** How many rows this car's screen allows in lists / panes. */
    protected fun listLimit(): Int = contentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6)
    protected fun paneLimit(): Int = contentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE, 4)

    private fun contentLimit(type: Int, fallback: Int): Int =
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
            carContext.getCarService(ConstraintManager::class.java).getContentLimit(type)
        } else {
            fallback
        }

    companion object {
        const val REFRESH_MS = 1_000L
    }
}

private val PL = Locale("pl")
internal fun Double?.f(decimals: Int = 0): String =
    if (this == null || isNaN()) "—" else String.format(PL, "%,.${decimals}f", this)

internal fun duration(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) String.format(PL, "%d h %02d min", m / 60, m % 60) else "$m min"
}
