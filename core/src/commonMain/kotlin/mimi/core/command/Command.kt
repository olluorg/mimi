package mimi.core.command

import mimi.core.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Command ───────────────────────────────────────────────────────────────────
// Immutable record of a behavioral intention — a request to change world state.
// Behaviors produce commands; the engine applies them atomically after CRL.

@Serializable
sealed class Command {
    abstract val target:   EntityId
    abstract val field:    PropertyKey
    abstract val priority: Int
    abstract val source:   BehaviorId
    abstract val tick:     Tick

    @Serializable @SerialName("set_property")
    data class SetProperty(
        override val target:   EntityId,
        override val field:    PropertyKey,
        val value:             PropertyValue,
        override val priority: Int        = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command()

    @Serializable @SerialName("increment_counter")
    data class IncrementCounter(
        override val target:   EntityId,
        override val field:    PropertyKey,
        val delta:             Long             = 1L,
        override val priority: Int              = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command()

    @Serializable @SerialName("mark_matched")
    data class MarkMatched(
        override val target:    EntityId,       // drop target entity
        val draggedEntityId:    EntityId,       // the dragged (source) entity
        override val field:     PropertyKey     = "matched",
        override val priority:  Int             = 0,
        override val source:    BehaviorId,
        override val tick:      Tick
    ) : Command()

    @Serializable @SerialName("set_entity_state")
    data class SetEntityState(
        override val target:   EntityId,
        val state:             EntityLifecycleState,
        override val field:    PropertyKey      = "state",
        override val priority: Int              = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command()

    @Serializable @SerialName("set_objective_state")
    data class SetObjectiveState(
        val objectiveId:       ObjectiveId,
        val state:             ObjectiveState,
        override val target:   EntityId        = SCENE_ENTITY_ID,
        override val field:    PropertyKey     = "objective.$objectiveId",
        override val priority: Int             = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command()

    // Bypasses CRL — dispatched directly to FeedbackRegistry after World commit.
    @Serializable @SerialName("trigger_feedback")
    data class TriggerFeedback(
        val feedbackType:      String,
        val params:            Map<String, String> = emptyMap(),
        override val target:   EntityId            = SCENE_ENTITY_ID,
        override val field:    PropertyKey         = "feedback",
        override val priority: Int                 = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command()
}
