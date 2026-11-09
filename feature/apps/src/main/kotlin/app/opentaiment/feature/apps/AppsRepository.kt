package app.opentaiment.feature.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppEntry(val label: String, val packageName: String, val icon: ImageBitmap)

class AppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm = appContext.packageManager

    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.Default) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale("pl"))
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != appContext.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
                )
            }
            .sortedWith(compareBy(collator) { it.label })
    }

    fun launch(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        return start(intent)
    }

    /** Opens the first installed navigation app, preferring dedicated car-friendly ones. */
    fun openNavigation(): Boolean {
        NAV_APPS.firstOrNull { pm.getLaunchIntentForPackage(it) != null }?.let { return launch(it) }
        return start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))
    }

    /** Opens a music app when nothing is playing yet. */
    fun openMusic(): Boolean =
        MUSIC_APPS.firstOrNull { pm.getLaunchIntentForPackage(it) != null }?.let { launch(it) } ?: false

    fun openDialer(): Boolean = start(Intent(Intent.ACTION_DIAL))

    fun openBluetoothSettings(): Boolean = start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openSystemSettings(): Boolean = start(Intent(Settings.ACTION_SETTINGS))

    fun openWebSearch(query: String): Boolean =
        start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))

    private fun start(intent: Intent): Boolean = try {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private companion object {
        const val ICON_PX = 144
        val NAV_APPS = listOf(
            "com.google.android.apps.maps",
            "com.waze",
            "net.osmand.plus",
            "net.osmand",
            "com.sygic.aura",
        )
        val MUSIC_APPS = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "deezer.android.app",
            "com.aspiro.tidal",
            "com.apple.android.music",
        )
    }
}
