package app.opentaiment.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.AppManager
import androidx.car.app.model.Alert
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.validation.HostValidator
import androidx.car.app.versioning.CarAppApiLevels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.opentaiment.feature.vehicle.VehicleHub
import app.opentaiment.feature.vehicle.data.VehicleAlert
import kotlinx.coroutines.launch

/**
 * Entry point Android Auto binds to. Opentaiment adds its own screens to Android Auto –
 * navigation and music stay with Android Auto's own apps.
 */
class OpentaimentCarAppService : CarAppService() {

    // Side-loaded hobby app: accept any Android Auto host. A Play Store build should use
    // HostValidator.Builder with the allow-list from androidx.car.app's resources instead.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = OpentaimentSession()
}

class OpentaimentSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        val vehicle = VehicleHub.get(carContext)
        // The car just started: reconnect to the adapter used last time.
        vehicle.autoConnect()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                vehicle.newAlerts.collect(::showAlert)
            }
        }
        return HomeCarScreen(carContext)
    }

    private fun showAlert(alert: VehicleAlert) {
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_5) {
            val carAlert = Alert.Builder(alert.type.ordinal, CarText.create(alert.type.title), ALERT_DURATION_MS)
                .setSubtitle(CarText.create(alert.message))
                .setIcon(CarIcon.ALERT)
                .build()
            carContext.getCarService(AppManager::class.java).showAlert(carAlert)
        } else {
            CarToast.makeText(carContext, "${alert.type.title}: ${alert.message}", CarToast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val ALERT_DURATION_MS = 10_000L
    }
}
