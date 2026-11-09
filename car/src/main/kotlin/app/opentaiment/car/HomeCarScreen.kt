package app.opentaiment.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.data.dtcCountLabel

/** Opentaiment's start screen in Android Auto. */
class HomeCarScreen(carContext: CarContext) : LiveCarScreen(carContext) {

    init {
        observe(vehicle.connection, vehicle.diagnostics, vehicle.stats)
    }

    @Suppress("DEPRECATION") // setTitle/setHeaderAction keep working on older Android Auto hosts
    override fun onGetTemplate(): Template {
        val stats = vehicle.stats.value
        val connection = vehicle.connection.value
        val items = ItemList.Builder()
            .addItem(
                tile("Jazda", R.drawable.ic_car_drive, stats.speedKmh?.let { "${it.f()} km/h" }) {
                    screenManager.push(DriveCarScreen(carContext))
                },
            )
            .addItem(
                tile("Statystyki", R.drawable.ic_car_stats, stats.instantLPer100Km?.let { "${it.f(1)} l/100" }) {
                    screenManager.push(StatsCarScreen(carContext))
                },
            )
            .addItem(
                tile("Diagnostyka", R.drawable.ic_car_diag, diagnosticsText()) {
                    screenManager.push(DiagnosticsCarScreen(carContext))
                },
            )
            .addItem(
                tile("Trasy", R.drawable.ic_car_trips, "${vehicle.trips.value.size} zapisanych") {
                    screenManager.push(TripsCarScreen(carContext))
                },
            )
            .addItem(
                tile("Wyścig", R.drawable.ic_car_race, stats.best0to100Sec?.let { "0–100 ${it.f(1)} s" }) {
                    screenManager.push(RaceCarScreen(carContext))
                },
            )
            .addItem(
                tile("Połączenie", R.drawable.ic_car_link, connectionText(connection)) {
                    screenManager.push(ConnectionCarScreen(carContext))
                },
            )
            .build()
        return GridTemplate.Builder()
            .setTitle("Opentaiment")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items)
            .build()
    }

    private fun tile(title: String, iconRes: Int, text: String?, onClick: () -> Unit): GridItem =
        GridItem.Builder()
            .setTitle(title)
            .apply { text?.let { setText(it) } }
            .setImage(icon(iconRes))
            .setOnClickListener(onClick)
            .build()

    private fun diagnosticsText(): String? = when (val d = vehicle.diagnostics.value) {
        is DiagnosticsState.Ready -> if (d.report.totalCount == 0) "Brak błędów" else "⚠ ${dtcCountLabel(d.report.totalCount)}"
        DiagnosticsState.Reading -> "Sprawdzam…"
        else -> null
    }

    private fun connectionText(c: ConnectionState): String = when (c) {
        ConnectionState.Disconnected -> "Niepołączono"
        is ConnectionState.Connecting -> "Łączę…"
        is ConnectionState.Connected -> c.deviceName
        is ConnectionState.Failed -> "Błąd"
    }
}
