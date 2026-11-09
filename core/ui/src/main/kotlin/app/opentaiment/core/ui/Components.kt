package app.opentaiment.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().padding(24.dp), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = OtCardShape, colors = colors) { inner() }
    } else {
        Card(modifier = modifier, shape = OtCardShape, colors = colors) { inner() }
    }
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
    Card(
        modifier = modifier.clip(OtCardShape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = OtCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
