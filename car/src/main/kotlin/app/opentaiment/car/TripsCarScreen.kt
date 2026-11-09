package app.opentaiment.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import app.opentaiment.feature.vehicle.data.PeriodSummary
import app.opentaiment.feature.vehicle.data.TripSummaries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TripsCarScreen(carContext: CarContext) : LiveCarScreen(carContext) {

    private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale("pl"))

    init {
        observe(vehicle.trips)
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val trips = vehicle.trips.value
        val rows = mutableListOf<Row>()
        rows += summaryRow("Ten tydzień", TripSummaries.thisWeek(trips))
        rows += summaryRow("Ten miesiąc", TripSummaries.thisMonth(trips))
        if (trips.isEmpty()) rows += row("Brak zapisanych tras", "Jazdy z podłączonym OBD zapisują się same")
        trips.forEach { t ->
            val date = Instant.ofEpochMilli(t.startEpochMs).atZone(ZoneId.systemDefault()).format(dateFormat)
            rows += row(
                (if (t.demo) "DEMO · " else "") + date,
                "${t.distanceKm.f(1)} km · ${t.avgLPer100Km.f(1)} l/100 · ${t.cost.f(2)} zł · eco ${t.ecoScore ?: "—"}",
            )
        }
        val list = ItemList.Builder().apply { rows.take(listLimit()).forEach { addItem(it) } }.build()
        return ListTemplate.Builder().setTitle("Trasy").setHeaderAction(Action.BACK).setSingleList(list).build()
    }

    private fun summaryRow(title: String, s: PeriodSummary) = row(
        "$title: ${s.distanceKm.f()} km · ${s.cost.f(2)} zł",
        "${s.trips} tras · średnio ${s.avgLPer100Km.f(1)} l/100 · eco ${s.avgEcoScore ?: "—"}",
    )
}
