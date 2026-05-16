package mimi.core.scene

import mimi.core.behavior.BehaviorRegistry
import mimi.core.model.ValidationResult
import mimi.core.world.SceneDefinition

object SceneValidator {

    fun validate(scene: SceneDefinition, behaviorRegistry: BehaviorRegistry): ValidationResult {
        val errors = mutableListOf<String>()

        // Entity IDs must be unique
        val entityIds = scene.entities.map { it.id }
        val duplicateIds = entityIds.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            errors += "Duplicate entity IDs: $duplicateIds"
        }

        // Objective IDs must be unique
        val objectiveIds = scene.objectives.map { it.id }
        val dupObjIds = objectiveIds.groupBy { it }.filter { it.value.size > 1 }.keys
        if (dupObjIds.isNotEmpty()) {
            errors += "Duplicate objective IDs: $dupObjIds"
        }

        // Objective prerequisites must exist and be acyclic
        val objIdSet = objectiveIds.toSet()
        for (obj in scene.objectives) {
            val req = obj.requires
            if (req != null && req !in objIdSet) {
                errors += "Objective '${obj.id}' requires unknown objective '$req'"
            }
        }
        if (hasPrereqCycle(scene)) {
            errors += "Objective prerequisites contain a cycle"
        }

        // All behavior refs must be registered
        val allRefs = scene.behaviors.map { it.behaviorRef } +
            scene.entities.flatMap { it.behaviors }
        for (ref in allRefs) {
            if (!behaviorRegistry.isRegistered(ref.type)) {
                errors += "Unknown behavior type: '${ref.type}'"
            } else {
                val result = behaviorRegistry.validateRef(ref)
                errors += result.errorList()
            }
        }

        val fsm = scene.stateMachine
        if (fsm != null) {
            val stateIds = fsm.states.map { it.id }
            val stateIdSet = stateIds.toSet()
            val duplicateStateIds = stateIds.groupBy { it }.filter { it.value.size > 1 }.keys
            if (duplicateStateIds.isNotEmpty()) {
                errors += "Duplicate FSM state IDs: $duplicateStateIds"
            }
            if (fsm.initialState !in stateIdSet) {
                errors += "FSM initial state '${fsm.initialState}' is not declared"
            }
            for (transition in fsm.transitions) {
                if (transition.from !in stateIdSet) {
                    errors += "FSM transition references unknown from state '${transition.from}'"
                }
                if (transition.to !in stateIdSet) {
                    errors += "FSM transition references unknown to state '${transition.to}'"
                }
                if (transition.onEvent == null && transition.afterMs == null) {
                    errors += "FSM transition '${transition.from}' -> '${transition.to}' needs onEvent or afterMs"
                }
            }
            val entityIdsWithScene = objIdSet + scene.entities.map { it.id }
            for (state in fsm.states) {
                validateFsmActions(state.onEnter, entityIdsWithScene, errors)
            }
            for (transition in fsm.transitions) {
                validateFsmActions(transition.actions, entityIdsWithScene, errors)
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    private fun validateFsmActions(actions: List<mimi.core.world.SceneFsmActionDefinition>, ids: Set<String>, errors: MutableList<String>) {
        for (action in actions) {
            val entityId = action.entityId
            if (entityId != null && entityId !in ids && entityId != mimi.core.model.SCENE_ENTITY_ID) {
                errors += "FSM action references unknown entity '$entityId'"
            }
            when (action.type) {
                "setProperty" -> {
                    if (action.entityId == null || action.field == null || action.value == null) {
                        errors += "FSM setProperty action requires entityId, field, and value"
                    }
                }
                "setEntityState" -> {
                    if (action.entityId == null || action.state == null) {
                        errors += "FSM setEntityState action requires entityId and state"
                    }
                }
                "triggerFeedback" -> {
                    if (action.feedbackType == null) {
                        errors += "FSM triggerFeedback action requires feedbackType"
                    }
                }
                else -> errors += "Unknown FSM action type '${action.type}'"
            }
        }
    }

    private fun hasPrereqCycle(scene: SceneDefinition): Boolean {
        val prereqMap = scene.objectives.associate { it.id to it.requires }
        for (start in prereqMap.keys) {
            val visited = mutableSetOf<String>()
            var current: String? = start
            while (current != null) {
                if (!visited.add(current)) return true
                current = prereqMap[current]
            }
        }
        return false
    }
}
