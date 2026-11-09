package app.opentaiment.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Template

/** The few numbers worth a glance while driving. */
class DriveCarScreen(carContext: CarContext) : LiveCarScreen(carContext) {

    init {
        observe(vehicle.stats, vehicle.connection)
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        if (!connected || !vehicle.stats.value.hasData) return notConnectedTemplate("Jazda")
        val s = vehicle.stats.value
        val rows = buildList {
            s.alerts.forEach { add(row("⚠ ${it.type.title}", it.message, iconRes = R.drawable.ic_car_warning)) }
            add(row("${s.speedKmh.f()} km/h", "${s.rpm.f()} obr/min", iconRes = R.drawable.ic_car_drive))
            add(
                row(
                    s.instantLPer100Km?.let { "Spalanie ${it.f(1)} l/100 km" } ?: "Spalanie ${s.fuelRateLh.f(1)} l/h",
                    "Średnio ${s.avgLPer100Km.f(1)} l/100 km",
                ),
            )
            add(row("Eco-score ${s.eco?.score ?: "—"}/100", s.eco?.tips?.firstOrNull() ?: "Ocena pojawi się po minucie jazdy"))
            add(row("Trasa ${s.trip.distanceKm.f(1)} km · ${s.trip.fuelCost.f(2)} zł", "Czas ${duration(s.trip.durationMs)}"))
        }
        val pane = Pane.Builder().apply { rows.take(paneLimit()).forEach { addRow(it) } }
            .addAction(Action.Builder().setTitle("Nowa trasa").setOnClickListener { vehicle.resetTrip() }.build())
            .build()
        return PaneTemplate.Builder(pane).setTitle("Jazda").setHeaderAction(Action.BACK).build()
    }
}
