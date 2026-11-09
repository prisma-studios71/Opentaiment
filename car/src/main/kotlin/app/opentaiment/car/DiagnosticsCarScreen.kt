package app.opentaiment.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.data.DtcDictionary
import app.opentaiment.feature.vehicle.data.DtcKind

class DiagnosticsCarScreen(carContext: CarContext) : LiveCarScreen(carContext) {

    init {
        observe(vehicle.diagnostics, vehicle.connection)
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        if (!connected) return notConnectedTemplate("Diagnostyka")
        val state = vehicle.diagnostics.value
        // List templates allow only one titled action in the strip – clearing lives in the last row.
        val actions = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Odczytaj").setOnClickListener { vehicle.readDtcs() }.build())
            .build()
        val builder = ListTemplate.Builder().setTitle("Diagnostyka").setHeaderAction(Action.BACK).setActionStrip(actions)

        val report = when (state) {
            is DiagnosticsState.Ready -> state.report
            is DiagnosticsState.Failed -> state.previous
            DiagnosticsState.Reading, DiagnosticsState.Clearing -> return builder.setLoading(true).build()
            DiagnosticsState.Idle -> null
        }
        val rows = mutableListOf<Row>()
        if (state is DiagnosticsState.Failed) rows += row("⚠ ${state.message}", iconRes = R.drawable.ic_car_warning)
        if (report == null) {
            rows += row("Nie odczytano jeszcze błędów", "Kliknij „Odczytaj”")
        } else {
            rows += row(
                when (report.milOn) {
                    true -> "Check Engine świeci"
                    false -> "Check Engine nie świeci"
                    null -> "Stan kontrolki nieznany"
                },
                if (report.totalCount == 0) "Brak błędów" else "Błędy: ${report.totalCount}",
                iconRes = if (report.milOn == true) R.drawable.ic_car_warning else R.drawable.ic_car_diag,
            )
            listOf(DtcKind.STORED to report.stored, DtcKind.PENDING to report.pending, DtcKind.PERMANENT to report.permanent)
                .forEach { (kind, codes) ->
                    codes.forEach { code -> rows += row("$code · ${kind.label}", DtcDictionary.describe(code)) }
                }
            report.readiness?.let { r ->
                rows += row(
                    if (r.ready) "Gotowe do badania spalin" else "Badanie spalin: ${r.incomplete.size} testy w toku",
                    r.incomplete.joinToString { it.name }.ifEmpty { "Wszystkie testy zakończone" },
                )
            }
        }
        val clearRow = report?.takeIf { it.totalCount > 0 || it.milOn == true }?.let {
            Row.Builder()
                .setTitle("Skasuj błędy…")
                .addText("Gasi Check Engine – zapytam jeszcze o potwierdzenie")
                .setOnClickListener { screenManager.push(ConfirmClearCarScreen(carContext)) }
                .build()
        }
        val limit = listLimit()
        val visible = if (clearRow != null) rows.take(limit - 1) + clearRow else rows.take(limit)
        val list = ItemList.Builder().apply { visible.forEach { addItem(it) } }.build()
        return builder.setSingleList(list).build()
    }
}

class ConfirmClearCarScreen(carContext: CarContext) : LiveCarScreen(carContext) {
    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(
            "Zgaśnie Check Engine i wyzerują się testy gotowości. Jeśli usterka nadal jest, błąd wróci. " +
                "Rób to na postoju, przy zgaszonym silniku.",
        )
            .setTitle("Skasować błędy?")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder().setTitle("Skasuj").setOnClickListener {
                    vehicle.clearDtcs()
                    screenManager.pop()
                }.build(),
            )
            .addAction(Action.Builder().setTitle("Anuluj").setOnClickListener { screenManager.pop() }.build())
            .build()
}
