package org.ollu.mini.renderer

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import mimi.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * A Lottie animation from the catalog, played once.
 *
 * Animations live beside the pictures and sounds in `catalog/_anim/`, so they
 * ship offline in the bundle and can be replaced over the air like any other
 * catalog file. Call it inside `key(...)` to play it again.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun CatalogAnimation(
    path:       String,
    modifier:   Modifier = Modifier,
    iterations: Int = 1,
    contentScale: ContentScale = ContentScale.Fit
) {
    val composition by rememberLottieComposition(path) {
        LottieCompositionSpec.JsonString(Res.readBytes("files/catalog/$path").decodeToString())
    }
    Image(
        painter            = rememberLottiePainter(composition = composition, iterations = iterations),
        contentDescription = null,
        contentScale       = contentScale,
        modifier           = modifier
    )
}

/** Stars flying out of a card the child has just got right. */
const val BURST_ANIMATION = "_anim/burst.json"

/** The bigger burst for saying exactly the sound that was modelled. */
const val BONUS_ANIMATION = "_anim/bonus.json"

/** Paper falling across the screen when a game is finished. */
const val CONFETTI_ANIMATION = "_anim/confetti.json"
