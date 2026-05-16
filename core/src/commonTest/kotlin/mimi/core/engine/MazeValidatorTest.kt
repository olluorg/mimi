package mimi.core.engine

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mimi.core.behavior.GameEvent
import mimi.core.model.EntityLifecycleState
import mimi.core.model.ObjectiveState
import mimi.core.model.PropertyValue
import mimi.core.model.ResolutionPolicy
import mimi.core.plugin.DefaultConstraintRegistry
import mimi.core.world.EntityDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneBehaviorBinding
import mimi.core.world.SceneDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class MazeValidatorTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `maze path completes when line reaches exit without touching walls`() = runTest {
        val session = EngineBuilder().withDefaultPlugins(DefaultConstraintRegistry()).build(makeScene(), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(100f, 100f), 0f, 0L, source = "start"))
        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(100f, 220f), 0f, 0L))
        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(220f, 220f), 0f, 0L))
        session.dispatch(GameEvent.TraceComplete("maze_path", 1f, 0L))

        assertEquals(EntityLifecycleState.TENTATIVE, session.worldState.value.getEntityState("maze_path"))
        assertEquals(ObjectiveState.LOCKED_COMPLETE, session.worldState.value.getObjectiveState("obj1"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `maze path does not complete when line touches wall`() = runTest {
        val session = EngineBuilder().withDefaultPlugins(DefaultConstraintRegistry()).build(makeScene(), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(100f, 100f), 0f, 0L, source = "start"))
        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(160f, 160f), 0f, 0L))
        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(220f, 220f), 0f, 0L))
        session.dispatch(GameEvent.TraceComplete("maze_path", 1f, 0L))

        assertEquals(EntityLifecycleState.ACTIVE, session.worldState.value.getEntityState("maze_path"))
        assertEquals(ObjectiveState.PROGRESSING, session.worldState.value.getObjectiveState("obj1"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `new maze attempt clears previous wall collision`() = runTest {
        val session = EngineBuilder().withDefaultPlugins(DefaultConstraintRegistry()).build(makeScene(), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(100f, 100f), 0f, 0L, source = "start"))
        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(160f, 160f), 0f, 0L))
        session.dispatch(GameEvent.TraceComplete("maze_path", 1f, 0L))

        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(100f, 100f), 0f, 0L, source = "start"))
        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(100f, 220f), 0f, 0L))
        session.dispatch(GameEvent.TracePoint(PropertyValue.Vec2(220f, 220f), 0f, 0L))
        session.dispatch(GameEvent.TraceComplete("maze_path", 1f, 0L))

        assertEquals(EntityLifecycleState.TENTATIVE, session.worldState.value.getEntityState("maze_path"))
        assertEquals(ObjectiveState.LOCKED_COMPLETE, session.worldState.value.getObjectiveState("obj1"))
    }

    private fun makeScene() = SceneDefinition(
        sceneId = "maze_test",
        entities = listOf(
            EntityDefinition(
                id = "maze_path",
                components = listOf("mazePath"),
                properties = mapOf(
                    "progress" to PropertyValue.Num(0.0),
                    "mazeBlocked" to PropertyValue.Flag(false)
                )
            ),
            EntityDefinition(
                id = "start",
                components = listOf("mazeStart"),
                properties = mapOf(
                    "position" to PropertyValue.Vec2(100f, 100f),
                    "radius" to PropertyValue.Num(60.0)
                )
            ),
            EntityDefinition(
                id = "exit",
                components = listOf("mazeExit"),
                properties = mapOf(
                    "position" to PropertyValue.Vec2(220f, 220f),
                    "radius" to PropertyValue.Num(50.0)
                )
            ),
            EntityDefinition(
                id = "wall",
                components = listOf("mazeWall"),
                properties = mapOf(
                    "position" to PropertyValue.Vec2(160f, 160f),
                    "width" to PropertyValue.Num(30.0),
                    "height" to PropertyValue.Num(30.0)
                ),
                tags = mapOf("group" to "maze_wall")
            )
        ),
        objectives = listOf(
            ObjectiveDefinition(
                id = "obj1",
                type = "traceComplete",
                config = mapOf("pathId" to "maze_path", "minAccuracy" to "1.0")
            )
        ),
        behaviors = listOf(
            SceneBehaviorBinding(
                behaviorRef = mimi.core.model.BehaviorRef(
                    type = "mazeValidator",
                    config = mapOf(
                        "pathId" to "maze_path",
                        "startEntity" to "start",
                        "exitEntity" to "exit",
                        "wallGroup" to "maze_wall",
                        "wallPadding" to "0"
                    )
                )
            )
        ),
        fieldPolicies = mapOf(
            "progress" to ResolutionPolicy.LAST_WINS,
            "mazeBlocked" to ResolutionPolicy.LAST_WINS,
            "lastX" to ResolutionPolicy.LAST_WINS,
            "lastY" to ResolutionPolicy.LAST_WINS
        )
    )
}
