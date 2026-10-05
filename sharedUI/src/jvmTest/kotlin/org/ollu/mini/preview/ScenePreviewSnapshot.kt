package org.ollu.mini.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import mimi.core.engine.EngineBuilder
import mimi.core.engine.defaultConstraintRegistry
import mimi.core.engine.withDefaultPlugins
import mimi.core.scene.SceneLoader
import org.ollu.mini.renderer.SceneRenderer
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders scenes to PNG, for looking at rather than asserting on.
 *
 * Runs only when `mimi.previewDir` is set, so it costs the normal test run
 * nothing: `./gradlew :sharedUI:jvmTest --tests '*ScenePreviewSnapshot*' -Dmimi.previewDir=/tmp/x`.
 */
@OptIn(ExperimentalTestApi::class)
class ScenePreviewSnapshot {

    private val out = System.getProperty("mimi.previewDir")?.let(::File)
    private val catalogDir = File(System.getProperty("mimi.catalogDir") ?: "catalog")
    private val scenes = (System.getProperty("mimi.previewScenes") ?: "sort_food_01").split(",")

    @Test
    fun `render scenes to png`() {
        val dir = out ?: return
        dir.mkdirs()
        for (sceneId in scenes) runComposeUiTest {
            val scene = SceneLoader.load(File(catalogDir, "$sceneId/$sceneId.json").readText())
            setContent {
                val scope = rememberCoroutineScope()
                val session = EngineBuilder().withDefaultPlugins(defaultConstraintRegistry()).build(scene, scope)
                androidx.compose.foundation.layout.Box(Modifier.size(900.dp, 600.dp)) {
                    SceneRenderer(snapshot = session.worldState.value, session = session, scope = scope)
                }
            }
            // Pictures decode asynchronously.
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            Thread.sleep(1_500)
            mainClock.advanceTimeBy(500)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$sceneId.png"))
        }
    }
}
