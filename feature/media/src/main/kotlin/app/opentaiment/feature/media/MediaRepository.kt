package app.opentaiment.feature.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the "best" active media session on the device (the one that is playing, otherwise the
 * last one we followed) and exposes it as [nowPlaying]. Works with any app that publishes a
 * MediaSession: Spotify, YouTube Music, Tidal, radio apps, podcast apps…
 */
class MediaRepository(context: Context) {

    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(appContext, MediaListenerService::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _hasAccess = MutableStateFlow(false)
    /** False until the user enables our notification listener in system settings. */
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private var controller: MediaController? = null
    private var listening = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        refresh(sessions.orEmpty())
    }

    /** Call from onResume – the user may have just come back from granting access. */
    fun start() {
        val access = NotificationManagerCompat.getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)
        _hasAccess.value = access
        if (!access) {
            stop()
            return
        }
        if (!listening) {
            try {
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, mainHandler)
                listening = true
            } catch (e: SecurityException) {
                _hasAccess.value = false
                return
            }
        }
        refresh()
    }

    fun stop() {
        if (listening) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            listening = false
        }
        attach(null)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** Brings the player app to front. Returns false when nothing is known to be playing. */
    fun openPlayer(): Boolean {
        val pkg = controller?.packageName ?: return false
        val intent = appContext.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun refresh(sessions: List<MediaController>? = null) {
        val list = sessions ?: try {
            sessionManager.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            emptyList()
        }
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.packageName == controller?.packageName }
            ?: list.firstOrNull()
        attach(best)
    }

    private fun attach(newController: MediaController?) {
        if (newController?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = newController
            newController?.registerCallback(controllerCallback, mainHandler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _nowPlaying.value = null
            return
        }
        val md = c.metadata
        val ps = c.playbackState
        val label = appLabel(c.packageName)
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        _nowPlaying.value = NowPlaying(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: label,
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = art?.asImageBitmap(),
            isPlaying = ps?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
            appLabel = label,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = ps?.position ?: 0L,
            positionUpdatedAt = ps?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed ?: 1f,
        )
    }

    private fun appLabel(pkg: String): String = try {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
