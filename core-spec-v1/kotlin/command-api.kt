package mimi.core.command

import mimi.core.model.*

// ── Command ───────────────────────────────────────────────────────────────────

sealed interface Command {
    val target: EntityId
    val field:  PropertyKey
    val priority: Int
    val source: BehaviorId
    val tick:   Tick

    data class SetProperty(
        override val target:   EntityId,
        override val field:    PropertyKey,
        val value:             PropertyValue,
        override val priority: Int        = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command

    data class IncrementCounter(
        override val target:   EntityId,
        override val field:    PropertyKey,
        val delta:             Long             = 1L,
        override val priority: Int              = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command

    data class MarkMatched(
        override val target:   EntityId,          // drop target entity
        val source:            EntityId,           // dragged entity
        override val field:    PropertyKey = "matched",
        override val priority: Int         = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command

    data class SetEntityState(
        override val target:   EntityId,
        val state:             EntityLifecycleState,
        override val field:    PropertyKey = "state",
        override val priority: Int         = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command

    data class SetObjectiveState(
        val objectiveId:       ObjectiveId,
        val state:             ObjectiveState,
        override val target:   EntityId      = "_scene",
        override val field:    PropertyKey   = "objective.$objectiveId",
        override val priority: Int           = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command

    // Bypasses CRL — queued directly for feedback system
    data class TriggerFeedback(
        val feedbackType:      String,
        val params:            Map<String, Any> = emptyMap(),
        override val target:   EntityId,
        override val field:    PropertyKey   = "feedback",
        override val priority: Int           = 0,
        override val source:   BehaviorId,
        override val tick:     Tick
    ) : Command
}

// ── Command Buffer ─────────────────────────────────────────────────────────────

interface CommandBuffer {
    fun add(command: Command)
    fun drain(): List<Command>
    fun isEmpty(): Boolean
}

// ── Command Resolution Result ──────────────────────────────────────────────────

sealed interface CommandResolution {
    data class Applied(val command: Command)                              : CommandResolution
    data class Merged(val from: List<Command>, val result: Command)      : CommandResolution
    data class Dropped(val command: Command, val reason: String)         : CommandResolution
}
