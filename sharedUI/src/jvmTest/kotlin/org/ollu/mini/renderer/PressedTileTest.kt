package org.ollu.mini.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** A pressed card answers by growing where it stands, not by moving. */
@OptIn(ExperimentalTestApi::class)
class PressedTileTest {

    private val card = EntitySnapshot(
        id = "more",
        components = listOf("tap"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(700f, 650f),
            "name" to PropertyValue.Text("ещё")
        ),
        state = EntityLifecycleState.ACTIVE,
        tags = emptyMap()
    )

    @Test
    fun `pressing a card scales it around its own centre`() = runComposeUiTest {
        var pressed by mutableStateOf(false)
        setContent {
            Box(Modifier.size(900.dp, 600.dp)) {
                EntityRenderer(card, CoordinateMapper(Size(900f, 600f)), isPressed = pressed, onTap = {})
            }
        }
        waitForIdle()
        val node = onNodeWithContentDescription("ещё", substring = true)
        val before = node.fetchSemanticsNode().boundsInRoot

        pressed = true
        mainClock.advanceTimeBy(500)
        waitForIdle()
        val after = node.fetchSemanticsNode().boundsInRoot

        assertTrue(after.width > before.width * 1.1f, "the card should grow: $before -> $after")
        assertTrue(abs(after.center.x - before.center.x) < 2f && abs(after.center.y - before.center.y) < 2f,
            "the card must stay where it is: centre ${before.center} -> ${after.center}")
    }
}
