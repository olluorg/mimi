package mimi.core.engine

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mimi.core.model.PropertyValue
import mimi.core.model.SCENE_ENTITY_ID
import mimi.core.world.SceneDefinition
import mimi.core.world.SceneFsmActionDefinition
import mimi.core.world.SceneFsmStateDefinition
import mimi.core.world.SceneFsmTransitionDefinition
import mimi.core.world.SceneStateMachineDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneStateMachineTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `state machine enters initial state and advances after timeout`() = runTest {
        val scene = SceneDefinition(
            sceneId = "fsm_test",
            stateMachine = SceneStateMachineDefinition(
                initialState = "show",
                states = listOf(
                    SceneFsmStateDefinition(
                        id = "show",
                        durationMs = 1000L,
                        inputEnabled = false,
                        onEnter = listOf(
                            SceneFsmActionDefinition(
                                type = "setProperty",
                                entityId = SCENE_ENTITY_ID,
                                field = "prompt",
                                value = PropertyValue.Text("remember")
                            )
                        )
                    ),
                    SceneFsmStateDefinition(
                        id = "recall",
                        inputEnabled = true
                    )
                ),
                transitions = listOf(
                    SceneFsmTransitionDefinition(
                        from = "show",
                        to = "recall",
                        afterMs = 1000L
                    )
                )
            )
        )

        val session = EngineBuilder().build(scene, backgroundScope)
        runCurrent()

        assertEquals(PropertyValue.Text("show"), session.worldState.value.getProperty(SCENE_ENTITY_ID, "sceneState"))
        assertEquals(PropertyValue.Flag(false), session.worldState.value.getProperty(SCENE_ENTITY_ID, "inputEnabled"))
        assertEquals(PropertyValue.Text("remember"), session.worldState.value.getProperty(SCENE_ENTITY_ID, "prompt"))

        advanceTimeBy(1000L)
        runCurrent()

        assertEquals(PropertyValue.Text("recall"), session.worldState.value.getProperty(SCENE_ENTITY_ID, "sceneState"))
        assertEquals(PropertyValue.Flag(true), session.worldState.value.getProperty(SCENE_ENTITY_ID, "inputEnabled"))
    }
}
