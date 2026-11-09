package app.opentaiment.feature.vehicle.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Hands export files to whatever the user picks: Drive, e-mail, a file manager, a USB stick… */
object TripSharing {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

    fun shareCsv(context: Context, trips: List<TripRecord>): Boolean =
        share(context, "opentaiment_trasy.csv", "text/csv", "Trasy z Opentaiment", TripExport.tripsCsv(trips))

    fun shareGpx(context: Context, trip: TripRecord, points: List<TrackPoint>): Boolean {
        val name = "trasa_" + Instant.ofEpochMilli(trip.startEpochMs).atZone(ZoneId.systemDefault()).format(stamp) + ".gpx"
        return share(context, name, "application/gpx+xml", "Trasa z Opentaiment", TripExport.gpx(trip, points))
    }

    private fun share(context: Context, fileName: String, mime: String, title: String, content: String): Boolean {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, fileName)
        // UTF-8 BOM so Excel shows Polish letters right.
        if (mime == "text/csv") file.writeText("﻿" + content) else file.writeText(content)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".export", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
