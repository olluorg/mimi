package mimi.core.engine

import mimi.core.behavior.GameEvent
import mimi.core.command.Command
import mimi.core.model.SCENE_ENTITY_ID
import mimi.core.model.PropertyValue
import mimi.core.model.Tick
import mimi.core.world.SceneFsmActionDefinition
import mimi.core.world.SceneStateMachineDefinition
import mimi.core.world.SceneFsmTransitionDefinition

private const val FSM_SOURCE   = "scene_fsm"
private const val FSM_PRIORITY = 500

/** The name a scene's state machine uses for [event] in `onEvent`. */
internal fun fsmEventName(event: GameEvent): String = when (event) {
    is GameEvent.Tap -> "tap"
    is GameEvent.DragStart -> "drag_start"
    is GameEvent.DragMove -> "drag_move"
    is GameEvent.Drop -> "drop"
    is GameEvent.Hold -> "hold"
    is GameEvent.TracePoint -> "trace_point"
    is GameEvent.TraceComplete -> "trace_complete"
    is GameEvent.VoiceLevel -> "voice_level"
    is GameEvent.Vocalization -> "vocalization"
    is GameEvent.ClockTick -> "clock_tick"
    is GameEvent.MatchSuccess -> "match_success"
    is GameEvent.MatchFail -> "match_fail"
    is GameEvent.ObjectiveProgressed -> "objective_progressed"
    is GameEvent.ObjectiveCompleted -> "objective_completed"
    is GameEvent.ObjectiveReverted -> "objective_reverted"
    is GameEvent.ObjectiveLocked -> "objective_locked"
    is GameEvent.SceneStarted -> "scene_started"
    is GameEvent.InstructionRequested -> "instruction_requested"
    is GameEvent.SceneCompleted -> "scene_completed"
    is GameEvent.SceneReset -> "scene_reset"
    is GameEvent.SceneStateEntered -> "scene_state_entered"
    is GameEvent.SceneStateTimeout -> "scene_state_timeout"
    is GameEvent.HintTriggered -> "hint_triggered"
    is GameEvent.IdleCheck -> "idle_check"
    is GameEvent.LevelCompleted -> "level_completed"
}

/** The transition out of [current] that [event] fires, if any. */
internal fun SceneStateMachineDefinition.transitionFor(current: String, event: GameEvent): SceneFsmTransitionDefinition? {
    val name = fsmEventName(event)
    return transitions.firstOrNull { transition ->
        transition.from == current && when (event) {
            is GameEvent.SceneStateTimeout -> transition.afterMs != null &&
                (transition.onEvent == null || transition.onEvent == name)
            else -> transition.onEvent == name
        }
    }
}

/** How long [stateId] lasts before it times out, if it does. */
internal fun SceneStateMachineDefinition.timeoutOf(stateId: String): Long? {
    val state = states.firstOrNull { it.id == stateId } ?: return null
    return state.durationMs ?: transitions.firstOrNull { it.from == stateId && it.afterMs != null }?.afterMs
}

/** The scene-entity bookkeeping written on entering a state. */
internal fun fsmStateCommands(stateId: String, inputEnabled: Boolean, tick: Tick): List<Command> = listOf(
    Command.SetProperty(
        target = SCENE_ENTITY_ID, field = "sceneState", value = PropertyValue.Text(stateId),
        priority = FSM_PRIORITY, source = FSM_SOURCE, tick = tick
    ),
    Command.SetProperty(
        target = SCENE_ENTITY_ID, field = "inputEnabled", value = PropertyValue.Flag(inputEnabled),
        priority = FSM_PRIORITY, source = FSM_SOURCE, tick = tick
    )
)

/** Commands for FSM actions; an incomplete action is skipped (the validator reports it). */
internal fun fsmCommands(actions: List<SceneFsmActionDefinition>, tick: Tick): List<Command> =
    actions.mapNotNull { it.toCommand(tick) }

private fun SceneFsmActionDefinition.toCommand(tick: Tick): Command? = when (type) {
    "setProperty" -> {
        val entityId = entityId
        val field    = field
        val value    = value
        if (entityId == null || field == null || value == null) null
        else Command.SetProperty(
            target = entityId, field = field, value = value,
            priority = FSM_PRIORITY, source = FSM_SOURCE, tick = tick
        )
    }
    "setEntityState" -> {
        val entityId = entityId
        val state    = state
        if (entityId == null || state == null) null
        else Command.SetEntityState(
            target = entityId, state = state,
            priority = FSM_PRIORITY, source = FSM_SOURCE, tick = tick
        )
    }
    "triggerFeedback" -> feedbackType?.let {
        Command.TriggerFeedback(
            feedbackType = it, params = params,
            priority = FSM_PRIORITY, source = FSM_SOURCE, tick = tick
        )
    }
    else -> null
}
