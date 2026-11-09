package app.opentaiment.headunit.tiles

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Built-in home screen tiles. Apps can be added as tiles too, as "app:<package>". */
enum class BuiltinTile(val id: String, val label: String, val icon: ImageVector) {
    NAVIGATION("nav", "Nawigacja", Icons.Rounded.Navigation),
    MUSIC("music", "Muzyka", Icons.Rounded.MusicNote),
    STATS("stats", "Statystyki", Icons.Rounded.Speed),
    DIAGNOSTICS("diag", "Diagnostyka", Icons.Rounded.Build),
    APPS("apps", "Aplikacje", Icons.Rounded.Apps),
    BLUETOOTH("bt", "Bluetooth", Icons.Rounded.Bluetooth),
    SETTINGS("settings", "Ustawienia", Icons.Rounded.Settings),
    PHONE("phone", "Telefon", Icons.Rounded.Phone),
    ;

    companion object {
        fun byId(id: String): BuiltinTile? = entries.firstOrNull { it.id == id }
    }
}

object TileIds {
    const val APP_PREFIX = "app:"
    fun app(packageName: String) = APP_PREFIX + packageName
    fun packageOf(id: String): String? = id.takeIf { it.startsWith(APP_PREFIX) }?.removePrefix(APP_PREFIX)
}

/** Pure list operations, kept separate from storage so they are easy to test. */
object TileList {
    const val MAX_TILES = 8

    val DEFAULT: List<String> = listOf(
        BuiltinTile.NAVIGATION, BuiltinTile.MUSIC, BuiltinTile.STATS,
        BuiltinTile.DIAGNOSTICS, BuiltinTile.APPS, BuiltinTile.SETTINGS,
    ).map { it.id }

    fun add(tiles: List<String>, id: String): List<String> =
        if (id in tiles || tiles.size >= MAX_TILES) tiles else tiles + id

    fun remove(tiles: List<String>, id: String): List<String> = tiles - id

    fun move(tiles: List<String>, id: String, delta: Int): List<String> {
        val from = tiles.indexOf(id)
        if (from < 0) return tiles
        val to = (from + delta).coerceIn(0, tiles.lastIndex)
        if (to == from) return tiles
        return tiles.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun parse(stored: String?): List<String> =
        stored?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.take(MAX_TILES) ?: DEFAULT
}

/** Persists which tiles are on the home screen and in what order. */
class TilesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    private val _tiles = MutableStateFlow(TileList.parse(prefs.getString(KEY, null)))
    val tiles: StateFlow<List<String>> = _tiles.asStateFlow()

    fun add(id: String) = update(TileList.add(_tiles.value, id))
    fun remove(id: String) = update(TileList.remove(_tiles.value, id))
    fun move(id: String, delta: Int) = update(TileList.move(_tiles.value, id, delta))
    fun reset() = update(TileList.DEFAULT)

    private fun update(tiles: List<String>) {
        _tiles.value = tiles
        prefs.edit { putString(KEY, tiles.joinToString(",")) }
    }

    private companion object {
        const val KEY = "tiles"
    }
}
