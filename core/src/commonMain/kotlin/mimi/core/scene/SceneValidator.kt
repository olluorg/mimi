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

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
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
