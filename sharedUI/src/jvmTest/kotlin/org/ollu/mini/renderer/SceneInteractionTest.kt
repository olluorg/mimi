package org.ollu.mini.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import mimi.core.behavior.GameEvent
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What each gesture step tells the engine, without a screen in the way. */
class SceneInteractionTest {

    // A 1000×1000 canvas maps one pixel to one scene unit.
    private val mapper = CoordinateMapper(Size(1000f, 1000f))

    private fun entity(id: String, component: String, x: Float, y: Float, extra: Map<String, PropertyValue> = emptyMap()) =
        EntitySnapshot(
            id = id,
            components = listOf(component),
            properties = mapOf("position" to PropertyValue.Vec2(x, y)) + extra,
            state = EntityLifecycleState.ACTIVE,
            tags = emptyMap()
        )

    @Test
    fun `dragging a tile onto a basket drops it there`() {
        val apple  = entity("apple", "drag", 200f, 200f)
        val basket = entity("basket", "dropTarget", 700f, 700f)
        val entities = listOf(apple, basket)
        val ui = SceneInteraction()

        assertIs<GameEvent.DragStart>(ui.start(Offset(200f, 200f), entities, mapper))
        val move = assertIs<GameEvent.DragMove>(ui.move(PropertyValue.Vec2(260f, 230f), entities))
        assertEquals(PropertyValue.Vec2(60f, 30f), move.delta)
        ui.move(PropertyValue.Vec2(700f, 700f), entities)

        val drop = assertIs<GameEvent.Drop>(ui.end(entities))
        assertEquals("apple" to "basket", drop.draggedEntity to drop.dropTargetEntity)
        assertNull(ui.draggedEntityId)
    }

    @Test
    fun `letting go over nothing sends no drop`() {
        val apple = entity("apple", "drag", 200f, 200f)
        val ui = SceneInteraction()
        ui.start(Offset(200f, 200f), listOf(apple), mapper)
        assertNull(ui.end(listOf(apple)))
        assertNull(ui.draggedEntityId)
    }

    @Test
    fun `a maze line through a wall is marked blocked and kept after release`() {
        val start = entity("start", "mazeStart", 100f, 500f)
        val wall  = entity(
            "wall", "mazeWall", 500f, 500f,
            mapOf("width" to PropertyValue.Num(40.0), "height" to PropertyValue.Num(400.0))
        )
        val entities = listOf(start, wall)
        val ui = SceneInteraction()

        assertIs<GameEvent.TracePoint>(ui.start(Offset(100f, 500f), entities, mapper))
        ui.move(PropertyValue.Vec2(900f, 500f), entities)
        assertTrue(ui.mazeLineBlocked)

        val done = assertIs<GameEvent.TraceComplete>(ui.end(entities))
        assertEquals("maze_path", done.pathId)
        assertTrue(ui.completedMazeLineBlocked)
        assertEquals(2, ui.completedMazeLinePoints.size)
        assertTrue(ui.mazeLinePoints.isEmpty())
    }

    @Test
    fun `a touch on empty board starts nothing`() {
        val ui = SceneInteraction()
        assertNull(ui.start(Offset(500f, 500f), listOf(entity("apple", "drag", 100f, 100f)), mapper))
        assertNull(ui.move(PropertyValue.Vec2(510f, 510f), emptyList()))
        assertNull(ui.end(emptyList()))
    }
}
