package app.opentaiment.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val OtCardShape = RoundedCornerShape(28.dp)

/** Base tile of the Opentaiment UI. Content fills the card, so give it a size (weight/fill) from outside. */
@Composable
fun OtCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = OtTheme.style.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = OtTheme.style
    val colors = CardDefaults.cardColors(containerColor = style.cardColor, contentColor = MaterialTheme.colorScheme.onSurface)
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().vfdLines(style.scanlines).padding(padding), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = style.cardShape, colors = colors, border = style.cardBorder) { inner() }
    } else {
        Card(modifier = modifier, shape = style.cardShape, colors = colors, border = style.cardBorder) { inner() }
    }
}

/** Faint horizontal lines over the content, like the mesh in front of a vacuum-fluorescent display. */
private fun Modifier.vfdLines(enabled: Boolean): Modifier = if (!enabled) this else drawWithContent {
    drawContent()
    val step = 3.dp.toPx()
    val line = Color.Black.copy(alpha = 0.22f)
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

/**
 * Full-screen background in the current [UiStyle]: plain, a soft light behind the gauges (Cockpit),
 * slowly drifting colour blobs (Glass) or a dark bezel gradient (Retro).
 */
@Composable
fun OtBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val style = OtTheme.style.style
    val cs = MaterialTheme.colorScheme
    val paint = when (style) {
        UiStyle.COCKPIT -> Modifier.background(cs.background).drawBehind {
            val light = lerp(cs.background, cs.primary, 0.08f)
            drawRect(
                Brush.radialGradient(
                    listOf(light, Color.Transparent),
                    center = Offset(size.width * 0.3f, size.height * 0.4f),
                    radius = size.maxDimension * 0.7f,
                ),
            )
        }
        UiStyle.GLASS -> Modifier.background(cs.background).ambientBlobs(cs.primary, companionOf(cs.primary))
        UiStyle.RETRO -> Modifier.background(Brush.verticalGradient(listOf(lerp(cs.background, Color(0xFF2A2C30), 0.6f), cs.background)))
        else -> Modifier.background(cs.background)
    }
    Box(modifier.then(paint)) {
        CompositionLocalProvider(LocalContentColor provides cs.onBackground) { content() }
    }
}

@Composable
private fun Modifier.ambientBlobs(first: Color, second: Color): Modifier {
    val drift by rememberInfiniteTransition(label = "ambient").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 20_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    return drawBehind {
        val r = size.maxDimension
        fun blob(color: Color, x: Float, y: Float, radius: Float, alpha: Float) {
            val c = Offset(x, y)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = radius), radius, c)
        }
        blob(first, size.width * (0.08f + 0.14f * drift), size.height * (0.1f + 0.12f * drift), r * 0.62f, 0.55f)
        blob(second, size.width * (0.96f - 0.12f * drift), size.height * (0.95f - 0.14f * drift), r * 0.55f, 0.5f)
        blob(lerp(first, second, 0.5f), size.width * (0.48f + 0.06f * drift), size.height * 0.45f, r * 0.34f, 0.22f)
    }
}

/** A second colour for the Glass background: the accent turned around the colour wheel. */
private fun companionOf(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return Color.hsv((hsv[0] + 300f) % 360f, (hsv[1] + 0.15f).coerceAtMost(1f), hsv[2])
}

/** Icon of a tile: a vector from the icon set, or another app's launcher icon. */
sealed interface TileIcon {
    data class Vector(val image: ImageVector) : TileIcon
    data class Bitmap(val image: ImageBitmap) : TileIcon
}

@Composable
fun TileIconView(icon: TileIcon, size: Dp, modifier: Modifier = Modifier) {
    when (icon) {
        is TileIcon.Vector -> Icon(icon.image, contentDescription = null, modifier.size(size), tint = MaterialTheme.colorScheme.primary)
        is TileIcon.Bitmap -> Image(icon.image, contentDescription = null, modifier.size(size))
    }
}

/** Large launcher tile – min touch target well above 76dp. Long-press is used to edit the home screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionTile(
    icon: TileIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val style = OtTheme.style
    Card(
        modifier = modifier.clip(style.cardShape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = style.cardShape,
        colors = CardDefaults.cardColors(containerColor = style.cardColor, contentColor = MaterialTheme.colorScheme.onSurface),
        border = style.cardBorder,
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TileIconView(icon, 44.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
