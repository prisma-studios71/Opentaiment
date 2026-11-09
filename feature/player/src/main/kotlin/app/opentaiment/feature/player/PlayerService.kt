package app.opentaiment.feature.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Plays radio streams and music files in the background, with a media notification and a
 * MediaSession – so steering wheel buttons, Bluetooth remotes and the lock screen control it.
 */
class PlayerService : MediaSessionService() {

    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retries = 0

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true, // pauses for navigation prompts and phone calls
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = retryRadio(player)
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) retries = 0
            }
        })
        val builder = MediaSession.Builder(this, player)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE),
            )
        }
        session = builder.build()
    }

    /** Radio over mobile data drops out in tunnels: try the stream again a few times. */
    private fun retryRadio(player: ExoPlayer) {
        val isRadio = player.currentMediaItem?.mediaId?.startsWith(MusicPlayer.RADIO_PREFIX) == true
        if (!isRadio || retries >= MAX_RETRIES) return
        retries++
        handler.postDelayed({
            if (player.playerError != null) {
                player.prepare()
                player.play()
            }
        }, RETRY_DELAY_MS * retries)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val MAX_RETRIES = 8
        const val RETRY_DELAY_MS = 3_000L
    }
}
