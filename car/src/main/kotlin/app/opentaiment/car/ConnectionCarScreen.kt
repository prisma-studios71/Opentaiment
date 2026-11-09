package app.opentaiment.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import app.opentaiment.feature.vehicle.ConnectionState

class ConnectionCarScreen(carContext: CarContext) : LiveCarScreen(carContext) {

    init {
        observe(vehicle.connection)
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val state = vehicle.connection.value
        val rows = mutableListOf<Row>()
        rows += row(
            when (state) {
                ConnectionState.Disconnected -> "Niepołączono"
                is ConnectionState.Connecting -> "Łączę z ${state.deviceName}…"
                is ConnectionState.Connected -> if (state.demo) "Tryb demo" else "Połączono: ${state.deviceName}"
                is ConnectionState.Failed -> "Błąd: ${state.message}"
            },
            iconRes = R.drawable.ic_car_link,
        )
        val last = vehicle.lastDevice()
        if (state is ConnectionState.Connected) {
            rows += clickable("Rozłącz", "Trasa zapisze się w historii") { vehicle.disconnect() }
        } else if (last != null && vehicle.hasBluetoothPermission()) {
            rows += clickable("Połącz z ${last.name}", last.address) { vehicle.connect(last) }
        } else {
            rows += row("Pierwsze połączenie zrób na telefonie", "Opentaiment → Statystyki → Połącz OBD")
        }
        if (!(state is ConnectionState.Connected && state.demo)) {
            rows += clickable("Tryb demo", "Symulowana jazda – do wypróbowania bez auta") { vehicle.startDemo() }
        }
        val list = ItemList.Builder().apply { rows.take(listLimit()).forEach { addItem(it) } }.build()
        return ListTemplate.Builder().setTitle("Połączenie").setHeaderAction(Action.BACK).setSingleList(list).build()
    }

    private fun clickable(title: String, text: String, onClick: () -> Unit): Row =
        Row.Builder().setTitle(title).addText(text).setOnClickListener(onClick).build()
}
