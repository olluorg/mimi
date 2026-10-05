package org.ollu.mini.renderer

import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A line joins two cards whichever one the child starts from.
 *
 * It used to start only from the left-hand card: reaching for the fish first
 * drew nothing, and a child cannot tell "wrong end" from "broken".
 */
class ConnectionDirectionTest {

    private fun card(id: String, component: String, x: Float, y: Float, state: EntityLifecycleState = EntityLifecycleState.ACTIVE) =
        EntitySnapshot(
            id = id,
            components = listOf(component),
            properties = mapOf("position" to PropertyValue.Vec2(x, y)),
            state = state,
            tags = emptyMap()
        )

    private val cat  = card("cat", "connectSource", 200f, 300f)
    private val dog  = card("dog", "connectSource", 200f, 600f)
    private val fish = card("fish", "connectTarget", 800f, 300f)
    private val bone = card("bone", "connectTarget", 800f, 600f)
    private val board = listOf(cat, dog, fish, bone)

    private fun at(entity: EntitySnapshot) = entity.vec2("position")!!

    @Test
    fun `from the animal to its food`() {
        assertEquals("cat" to "fish", connectionFrom("cat", at(fish), board))
    }

    @Test
    fun `from the food back to the animal gives the same connection`() {
        assertEquals("cat" to "fish", connectionFrom("fish", at(cat), board))
    }

    @Test
    fun `a line that ends on nothing connects nothing`() {
        assertNull(connectionFrom("cat", PropertyValue.Vec2(500f, 50f), board))
    }

    @Test
    fun `two cards of the same kind do not connect`() {
        assertNull(connectionFrom("cat", at(dog), board), "animal to animal is not a pair")
        assertNull(connectionFrom("fish", at(bone), board), "food to food is not a pair")
    }

    @Test
    fun `a card already joined is not joined again`() {
        val joined = board.map { if (it.id == "fish") it.copy(state = EntityLifecycleState.LOCKED) else it }
        assertNull(connectionFrom("dog", at(fish), joined))
    }
}
