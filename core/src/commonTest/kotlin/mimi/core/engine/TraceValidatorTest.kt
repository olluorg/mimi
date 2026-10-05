package mimi.core.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mimi.core.behavior.GameEvent
import mimi.core.model.BehaviorRef
import mimi.core.model.PropertyValue
import mimi.core.model.Waypoint
import mimi.core.world.EntityDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneBehaviorBinding
import mimi.core.world.SceneDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TraceValidatorTest {

    private fun sceneWith(waypoints: List<Waypoint>) = SceneDefinition(
        sceneId = "trace_test",
        entities = listOf(
            EntityDefinition(
                id         = "path",
                components = listOf("traceTarget"),
                properties = mapOf("waypoints" to PropertyValue.PointList(waypoints)),
                tags       = mapOf("group" to "trace")
            )
        ),
        objectives = listOf(
            ObjectiveDefinition(
                id     = "win",
                type   = "traceComplete",
                config = mapOf("pathId" to "path", "minAccuracy" to "0.9")
            )
        ),
        behaviors = listOf(
            SceneBehaviorBinding(BehaviorRef("traceValidator", mapOf("group" to "trace")))
        )
    )

    private fun session(scene: SceneDefinition, scope: kotlinx.coroutines.CoroutineScope) =
        EngineBuilder().withDefaultPlugins(defaultConstraintRegistry()).build(scene, scope)

    private suspend fun EngineSession.traceThrough(points: List<Waypoint>) {
        points.forEach { wp ->
            dispatch(GameEvent.TracePoint(PropertyValue.Vec2(wp.x, wp.y), progress = 0f, tick = 0L))
        }
    }

    @Test
    fun `progress advances one waypoint at a time`() = runTest {
        val path = listOf(
            Waypoint(100f, 100f, 40f),
            Waypoint(200f, 100f, 40f),
            Waypoint(300f, 100f, 40f)
        )
        val session = session(sceneWith(path), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(100f, 100f), 0f, tick = 0L))
        runCurrent()
        assertEquals(1.0 / 3, session.worldState.value.getNum("path", "progress"))

        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(200f, 100f), 0f, tick = 0L))
        runCurrent()
        assertEquals(2.0 / 3, session.worldState.value.getNum("path", "progress"))
    }

    @Test
    fun `a point outside every radius does not advance progress`() = runTest {
        val path = listOf(Waypoint(100f, 100f, 40f), Waypoint(200f, 100f, 40f))
        val session = session(sceneWith(path), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(900f, 900f), 0f, tick = 0L))
        runCurrent()

        assertEquals(null, session.worldState.value.getNum("path", "progress"))
    }

    /**
     * A path that comes back to a coordinate it already used — the crossbar of an
     * "A", a figure eight, any closed shape. Waypoints are compared by identity of
     * position, so a value-based lookup would keep re-resolving to the first match
     * and the trace could never finish.
     */
    @Test
    fun `a path that revisits a point still completes`() = runTest {
        val start = Waypoint(100f, 100f, 40f)
        val path = listOf(
            start,
            Waypoint(200f, 200f, 40f),
            Waypoint(300f, 100f, 40f),
            start                       // back to where it began
        )
        val session = session(sceneWith(path), backgroundScope)
        runCurrent()

        session.traceThrough(path)
        runCurrent()

        assertEquals(
            1.0, session.worldState.value.getNum("path", "progress"),
            "every waypoint should be consumed in order, including the repeat"
        )

        session.dispatch(GameEvent.TraceComplete("path", accuracy = 1f, tick = 0L))
        runCurrent()

        assertTrue(
            session.worldState.value.allLevelsComplete(),
            "a fully traced path should finish the scene"
        )
    }

    @Test
    fun `waypoints must be reached in declared order`() = runTest {
        val path = listOf(
            Waypoint(100f, 100f, 40f),
            Waypoint(200f, 100f, 40f),
            Waypoint(300f, 100f, 40f)
        )
        val session = session(sceneWith(path), backgroundScope)
        runCurrent()

        // Jump straight at the last waypoint: the first one is still outstanding.
        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(300f, 100f), 0f, tick = 0L))
        runCurrent()

        assertEquals(
            null, session.worldState.value.getNum("path", "progress"),
            "skipping ahead should not count"
        )
    }
}
