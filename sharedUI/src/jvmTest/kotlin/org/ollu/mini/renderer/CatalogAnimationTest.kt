package org.ollu.mini.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The generated Lottie files exist, and the player actually draws them. */
@OptIn(ExperimentalTestApi::class)
class CatalogAnimationTest {

    private val catalogDir = File(System.getProperty("mimi.catalogDir") ?: "catalog")

    @Test
    fun `every animation the app plays is in the catalog`() {
        for (path in listOf(BURST_ANIMATION, BONUS_ANIMATION, CONFETTI_ANIMATION)) {
            assertTrue(File(catalogDir, path).isFile, "$path is missing — run tools/generate_animations.py")
        }
    }

    @Test
    fun `the burst draws something half way through`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.size(300.dp)) { CatalogAnimation(BURST_ANIMATION, Modifier.fillMaxSize()) }
        }
        // Loading the file happens off the test clock, so the moment the burst
        // starts is not known in advance: step through time and look at every
        // frame until one of them has the stars in it.
        var coloured = 0
        repeat(60) {
            if (coloured > 50) return@repeat
            mainClock.advanceTimeBy(32)
            Thread.sleep(30)
            waitForIdle()
            coloured = colouredPixels(onRoot().captureToImage().toAwtImage())
        }
        assertTrue(coloured > 50, "expected stars on screen mid-burst, found $coloured coloured pixels")
    }

    private fun colouredPixels(image: java.awt.image.BufferedImage): Int {
        var coloured = 0
        for (x in 0 until image.width step 3) for (y in 0 until image.height step 3) {
            val argb = image.getRGB(x, y)
            val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
            if ((argb ushr 24) > 0 && maxOf(r, g, b) - minOf(r, g, b) > 60) coloured++
        }
        return coloured
    }
}
