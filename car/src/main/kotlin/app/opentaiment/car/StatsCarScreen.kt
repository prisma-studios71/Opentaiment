package app.opentaiment.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template

class StatsCarScreen(carContext: CarContext) : LiveCarScreen(carContext) {

    init {
        observe(vehicle.stats, vehicle.connection)
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        if (!connected || !vehicle.stats.value.hasData) return notConnectedTemplate("Statystyki")
        val s = vehicle.stats.value
        val hp = { kw: Double? -> kw?.let { it * 1.35962 } }
        val rows = listOf(
            row("Moc ${hp(s.powerKw).f()} KM", "Szczyt ${hp(s.peakPowerKw).f()} KM · moment ${s.torqueNm.f()} Nm"),
            row(
                "Spalanie średnio ${s.avgLPer100Km.f(1)} l/100 km",
                "Spalone ${s.trip.fuelUsedL.f(2)} l · ${s.trip.fuelCost.f(2)} zł",
            ),
            row(
                "Przyspieszenie ${((s.accelMs2 ?: 0.0) / 9.81).f(2)} g",
                "Max ${(s.maxAccelMs2 / 9.81).f(2)} g",
            ),
            row(
                "Silnik ${s.coolantTempC.f()} °C",
                "Olej ${s.oilTempC.f()} °C · obciążenie ${s.engineLoadPct.f()} % · przepustnica ${s.throttlePct.f()} %",
            ),
            row("Akumulator ${s.voltage.f(1)} V", "Paliwo ${s.fuelLevelPct.f()} % · na zewnątrz ${s.ambientTempC.f()} °C"),
            row(
                "Trasa ${s.trip.distanceKm.f(1)} km",
                "Średnio ${s.trip.avgSpeedKmh.f()} km/h · max ${s.trip.maxSpeedKmh.f()} km/h · ${duration(s.trip.durationMs)}",
            ),
        )
        val list = ItemList.Builder().apply { rows.take(listLimit()).forEach { addItem(it) } }.build()
        return ListTemplate.Builder().setTitle("Statystyki").setHeaderAction(Action.BACK).setSingleList(list).build()
    }
}
