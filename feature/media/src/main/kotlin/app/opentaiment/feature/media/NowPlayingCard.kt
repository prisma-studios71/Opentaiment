package app.opentaiment.feature.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.OtCard
import kotlinx.coroutines.delay

@Composable
fun MediaPanel(
    hasAccess: Boolean,
    nowPlaying: NowPlaying?,
    onGrantAccess: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !hasAccess -> MessageCard(
            title = "Daj dostęp do muzyki",
            body = "Żeby sterować Spotify, YouTube Music czy radiem, włącz Opentaiment w ustawieniach dostępu do powiadomień.",
            action = "Otwórz ustawienia",
            onAction = onGrantAccess,
            modifier = modifier,
        )
        nowPlaying == null -> MessageCard(
            title = "Cisza w aucie",
            body = "Nic teraz nie gra. Odpal muzykę w dowolnej aplikacji, a pojawi się tutaj.",
            action = "Otwórz muzykę",
            onAction = onOpenPlayer,
            modifier = modifier,
        )
        else -> NowPlayingCard(nowPlaying, onPlayPause, onNext, onPrevious, onOpenPlayer, modifier)
    }
}

@Composable
fun NowPlayingCard(
    state: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OtCard(modifier, onClick = onOpenPlayer) {
        // Artwork takes whatever height is left, but never more than it is wide.
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (state.art != null) {
                    Image(state.art, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(
                        Icons.Rounded.MusicNote, contentDescription = null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(state.appLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            state.title, style = MaterialTheme.typography.headlineMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        state.artist?.let {
            Text(
                it, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(16.dp))
        PlaybackProgress(state)
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(80.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Poprzedni", Modifier.size(48.dp))
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(96.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pauza" else "Graj",
                    modifier = Modifier.size(56.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(80.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Następny", Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun PlaybackProgress(state: NowPlaying) {
    if (state.durationMs <= 0) return
    var position by remember { mutableLongStateOf(state.currentPositionMs()) }
    LaunchedEffect(state) {
        while (true) {
            position = state.currentPositionMs()
            if (!state.isPlaying) break
            delay(500)
        }
    }
    LinearProgressIndicator(
        progress = { (position.toFloat() / state.durationMs).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = MaterialTheme.typography.labelLarge
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(formatTime(position), style = style, color = color)
        Text(formatTime(state.durationMs), style = style, color = color)
    }
}

@Composable
private fun MessageCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OtCard(modifier) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.MusicNote, contentDescription = null, Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                body, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, modifier = Modifier.height(64.dp)) {
                Text(action, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
