package app.opentaiment.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Template

class RaceCarScreen(carContext: CarContext) : LiveCarScreen(carContext) {

    init {
        observe(vehicle.stats, vehicle.race)
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val s = vehicle.stats.value
        val race = vehicle.race.value
        val sprint = s.sprints.firstOrNull()
        val q = s.quarterMile
        val rows = listOf(
            row(
                "0–100 km/h: ${(sprint?.runningSec ?: sprint?.lastSec)?.let { "${it.f(1)} s" } ?: "—"}",
                "Rekord ${sprint?.bestSec?.let { "${it.f(1)} s" } ?: "—"} · 80–120: ${s.sprints.getOrNull(1)?.bestSec?.let { "${it.f(1)} s" } ?: "—"}",
            ),
            row(
                "¼ mili: ${q?.lastSec?.let { "${it.f(2)} s" } ?: "—"}",
                q?.lastTrapKmh?.let { "Prędkość na mecie ${it.f()} km/h · rekord ${q.bestSec.f(2)} s" } ?: "Ruszaj z miejsca – mierzy się samo",
            ),
            when {
                !race.gpsActive -> row("Okrążenia: GPS wyłączony", "Włącz GPS przyciskiem poniżej")
                !race.hasFinishLine -> row("Okrążenia: ustaw metę", "Stań na linii i kliknij „Meta tutaj”")
                else -> row(
                    "Okrążenie: ${race.currentLapSec?.let { lap(it) } ?: "start…"}",
                    "Najlepsze ${race.bestLapSec?.let { lap(it) } ?: "—"} · okrążeń ${race.laps.size}",
                )
            },
            row(
                "G: boczne ${race.maxLateralG.f(2)} · hamowanie ${race.maxBrakeG.f(2)}",
                "Przyspieszenie max ${race.maxAccelG.f(2)} g",
            ),
        )
        val gpsAction = if (race.gpsActive) {
            Action.Builder().setTitle("Meta tutaj").setOnClickListener {
                if (!vehicle.setFinishLineHere()) CarToast.makeText(carContext, "Czekam na sygnał GPS…", CarToast.LENGTH_SHORT).show()
            }.build()
        } else {
            Action.Builder().setTitle("Włącz GPS").setOnClickListener {
                if (!vehicle.startRaceGps()) {
                    CarToast.makeText(carContext, "Daj dostęp do lokalizacji w apce na telefonie", CarToast.LENGTH_LONG).show()
                }
            }.build()
        }
        val pane = Pane.Builder().apply { rows.take(paneLimit()).forEach { addRow(it) } }
            .addAction(gpsAction)
            .addAction(Action.Builder().setTitle("Reset").setOnClickListener { vehicle.resetRace() }.build())
            .build()
        return PaneTemplate.Builder(pane).setTitle("Wyścig").setHeaderAction(Action.BACK).build()
    }

    private fun lap(sec: Double): String {
        val m = (sec / 60).toInt()
        return if (m > 0) "$m:${(sec - m * 60).f(1).padStart(4, '0')}" else "${sec.f(1)} s"
    }
}
