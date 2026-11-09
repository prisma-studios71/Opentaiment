package app.opentaiment.feature.vehicle.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Finished trips, newest first, persisted as a small JSON file. */
class TripHistoryStore(private val file: File, private val scope: CoroutineScope) {

    private val _trips = MutableStateFlow(load())
    val trips: StateFlow<List<TripRecord>> = _trips.asStateFlow()

    fun add(trip: TripRecord) = update((listOf(trip) + _trips.value).take(MAX_TRIPS))
    fun delete(id: Long) = update(_trips.value.filterNot { it.id == id })
    fun clear() = update(emptyList())

    private fun update(trips: List<TripRecord>) {
        _trips.value = trips
        val json = TripJson.encode(trips)
        scope.launch(Dispatchers.IO) {
            synchronized(this@TripHistoryStore) {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json)
                tmp.renameTo(file) // atomic on the same filesystem: never a half-written history
            }
        }
    }

    private fun load(): List<TripRecord> =
        if (file.exists()) TripJson.decode(file.readText()) else emptyList()

    private companion object {
        const val MAX_TRIPS = 500
    }
}
