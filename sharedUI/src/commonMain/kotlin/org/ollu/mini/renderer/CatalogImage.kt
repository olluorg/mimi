package org.ollu.mini.renderer

import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mimi.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Pictures that ship with a scene.
 *
 * Emoji carry the whole catalog today, which is fine for fruit and shapes but
 * not for the visual supports a non-verbal child actually uses: those work best
 * with photographs of *their* cup, *their* coat, *their* classroom. A therapist
 * can drop such photos into the scene folder next to its sounds and reference
 * them by path, with no code change.
 */
@OptIn(ExperimentalResourceApi::class)
object CatalogImages {
    private val cache = mutableMapOf<String, ImageBitmap>()
    private val mutex = Mutex()

    /**
     * Decoded picture for a path relative to the catalog root, e.g.
     * `my_game_01/media/cup.png`. Returns null when the file is missing or not
     * a readable image — a scene is still playable without its artwork, so a
     * bad path must not take the screen down.
     */
    suspend fun load(path: String): ImageBitmap? {
        cache[path]?.let { return it }
        return mutex.withLock {
            cache[path] ?: runCatching {
                Res.readBytes("files/catalog/$path").decodeToImageBitmap()
            }.getOrNull()?.also { cache[path] = it }
        }
    }
}

/** Every picture in the app has soft corners, in proportion to its size. */
private val PICTURE_SHAPE = RoundedCornerShape(14)

/**
 * Draws a catalog picture, rendering nothing until it is decoded. Callers size
 * the slot themselves so the layout does not jump when the image arrives.
 */
@Composable
fun CatalogImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) { bitmap = CatalogImages.load(path) }

    // The slot is square but a picture need not be. Sizing the image to its own
    // proportions inside the slot is what lets the rounded corners land on the
    // picture's corners rather than on empty space beside it.
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let {
            val ratio = it.width.toFloat() / it.height.coerceAtLeast(1)
            Image(
                bitmap             = it,
                contentDescription = contentDescription,
                modifier           = Modifier
                    .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                    .clip(PICTURE_SHAPE),
                contentScale       = ContentScale.Fit
            )
        }
    }
}
