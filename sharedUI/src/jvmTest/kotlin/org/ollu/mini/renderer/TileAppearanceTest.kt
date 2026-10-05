package org.ollu.mini.renderer

import androidx.compose.ui.graphics.Color
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileAppearanceTest {

    private fun tile(
        vararg props: Pair<String, String>,
        components: List<String> = listOf("tap"),
        state: EntityLifecycleState = EntityLifecycleState.ACTIVE
    ) = EntitySnapshot(
        id = "t",
        components = components,
        properties = props.associate { (k, v) -> k to PropertyValue.Text(v) },
        state = state,
        tags = emptyMap()
    )

    @Test
    fun `a face down memory card shows nothing of its face`() {
        val face = tileFaceOf(tile("cardState" to "hidden", "face" to "A", "faceImage" to "_cards/cat.jpg"))
        assertTrue(face.hidden)
        assertNull(face.emoji)
        assertNull(face.imagePath)
    }

    @Test
    fun `a turned card shows its face picture`() {
        val face = tileFaceOf(tile("cardState" to "revealed", "faceImage" to "_cards/cat.jpg", "image" to "_cards/back.jpg"))
        assertEquals("_cards/cat.jpg", face.imagePath)
    }

    @Test
    fun `a colour swatch glyph is drawn as a circle`() {
        val face = tileFaceOf(tile("color" to "red", "emoji" to "🔴"))
        assertEquals("circle", face.drawnShape)
    }

    @Test
    fun `a basket with its own picture has no plate or frame`() {
        val basket = tile("image" to "_cards/box.jpg", components = listOf("dropTarget"))
        val colors = tileColorsOf(basket, tileFaceOf(basket), isPressed = false, isDestination = false)!!
        assertEquals(Color.Transparent, colors.background)
        assertEquals(Color.Transparent, colors.border)
    }

    @Test
    fun `a pressed tile is framed in the hint colour`() {
        val t = tile("emoji" to "A")
        assertEquals(HINT_ACCENT, tileColorsOf(t, tileFaceOf(t), isPressed = true, isDestination = false)!!.border)
    }
}
