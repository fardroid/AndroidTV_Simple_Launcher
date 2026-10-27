package tld.my.hearth.ui

// The row of apps along the bottom. Centred when it fits; otherwise it slides to keep the selected tile in the middle (clamped
// at the ends). The selected tile grows a little, its name with it, in one movement about the icon's bottom edge.

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tld.my.hearth.AppEntry
import tld.my.hearth.Plates
import tld.my.hearth.TileShape

/** A tile of the home row: an app, or one of the launcher's own tiles (its settings, the box's settings). */
data class TileItem(val key: String, val label: String, val app: AppEntry?, val glyph: Glyph? = null)

const val TILE_GAP = 16

@Composable
fun Dock(
    items: List<TileItem>,
    selected: Int,
    focused: Boolean,
    /** a held arrow key: each step glides with a short linear move */
    fast: Boolean,
    /** the tile being moved (re-order mode) */
    moving: String?,
    dock: Plates.Dock,
    shape: TileShape,
    modifier: Modifier,
) {
    val step = dock.tileW + TILE_GAP
    val total = items.size * step - TILE_GAP
    // with the focus on a card the row stays as it is, only no tile is enlarged (a see-through layer would also clip its edges)
    BoxWithConstraints(modifier) {
        val viewport = maxWidth.value
        val fits = total <= viewport
        val target = if (fits) -(viewport - total) / 2f
        else (selected * step + dock.tileW / 2f - viewport / 2f).coerceIn(0f, total - viewport)
        val offset by animateFloatAsState(target, if (fast) tween(150, easing = LinearEasing) else tween(260, easing = Ease.out), label = "track")
        items.forEachIndexed { i, item ->
            key(item.key) {
                Tile(
                    item, focused && i == selected, item.key == moving, dock, shape,
                    Modifier.graphicsLayer { translationX = (i * step - offset) * density },
                )
            }
        }
    }
}

@Composable
private fun Tile(item: TileItem, focused: Boolean, moving: Boolean, dock: Plates.Dock, shape: TileShape, modifier: Modifier) {
    val scale by animateFloatAsState(if (moving) 1.2f else if (focused) Plates.FOCUS_SCALE else 1f, tween(200, easing = Ease.dock), label = "tile")
    val labelColor by animateColorAsState(if (focused) Hue.textPrimary else Hue.textSecondary, tween(160), label = "label")
    val height = dock.tileH + dock.labelBlock
    val radius = RoundedCornerShape(24.dp)
    Column(
        modifier.width(dock.tileW.dp).height(height.dp).graphicsLayer {
            scaleX = scale; scaleY = scale
            transformOrigin = TransformOrigin(0.5f, dock.tileH.toFloat() / height)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(dock.tileW.dp, dock.tileH.dp)
                .softShadow(24f, 28f, 12f, Color(0x66000000))
                .clip(radius)
                .background(if (focused) Hue.tileBgFocus else Hue.tileBg)
                .then(if (moving) Modifier.border(3.dp, Hue.textPrimary, radius) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val app = item.app
            when {
                app == null -> item.glyph?.let { LineGlyph(it, Modifier.fillMaxSize(0.5f)) }
                shape == TileShape.BANNER && app.banner != null ->
                    Image(app.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
                app.icon != null -> Image(
                    app.icon, null,
                    if (shape == TileShape.BANNER && !app.iconFull) Modifier.fillMaxSize(0.8f) else if (shape == TileShape.BANNER) Modifier.fillMaxSize(0.62f).clip(RoundedCornerShape(18.dp)) else Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit, filterQuality = FilterQuality.Medium,
                )
                else -> BasicText(app.label.take(1).uppercase(), style = text(64f, 600))
            }
        }
        if (dock.labelBlock > 0) {
            BasicText(
                item.label,
                Modifier.padding(top = 14.dp).requiredWidth((dock.tileW + TILE_GAP - 8).dp).wrapContentHeight(Alignment.Top, unbounded = true),
                // two lines must fit the label block (2.4 lines high, see Plates.dock) whatever the rounding
                style = text(dock.label.toFloat(), 400, labelColor, lineHeight = 1.18f).copy(textAlign = TextAlign.Center),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
