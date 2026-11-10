package app.opentaiment.feature.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale

/** Music files Android has indexed: internal storage, SD card and (on most head units) USB sticks. */
class LocalMusic(context: Context) {
    private val appContext = context.applicationContext

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission()) == PackageManager.PERMISSION_GRANTED

    suspend fun load(): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        val tracks = ArrayList<LocalTrack>()
        appContext.contentResolver.query(
            collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            @Suppress("DEPRECATION")
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val trackId = c.getLong(id)
                val path = c.getString(data).orEmpty()
                tracks += LocalTrack(
                    id = trackId,
                    title = c.getString(title)?.takeIf { it.isNotBlank() } ?: File(path).nameWithoutExtension,
                    artist = c.getString(artist)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING },
                    album = c.getString(album),
                    durationMs = c.getLong(duration),
                    uri = ContentUris.withAppendedId(collection, trackId).toString(),
                    folder = File(path).parentFile?.name ?: "Muzyka",
                )
            }
        }
        sortTracks(tracks)
    }

    companion object {
        /** By folder, then title – how people browse a USB stick in the car. */
        fun sortTracks(tracks: List<LocalTrack>): List<LocalTrack> {
            val collator = Collator.getInstance(Locale("pl"))
            return tracks.sortedWith(compareBy<LocalTrack, String>(collator) { it.folder }.thenBy(collator) { it.title })
        }
    }
}
