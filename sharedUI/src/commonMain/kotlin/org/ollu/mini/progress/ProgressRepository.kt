package org.ollu.mini.progress

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

class ProgressRepository {
    private val settings = Settings()

    fun markCompleted(sceneId: String) {
        val ids = getCompletedSceneIds().toMutableSet()
        ids.add(sceneId)
        settings["completed_scenes"] = ids.joinToString(",")
    }

    fun getCompletedSceneIds(): Set<String> =
        settings.getStringOrNull("completed_scenes")
            ?.split(",")?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()

    fun getChildName(): String = settings.getStringOrNull("child_name") ?: ""

    fun setChildName(name: String) { settings["child_name"] = name.trim() }

    fun getSkillState(childId: String): Map<String, Double> {
        val raw = settings.getStringOrNull("skill_state_$childId") ?: return emptyMap()
        return raw.split(",")
            .mapNotNull { item ->
                val parts = item.split("=", limit = 2)
                val skill = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val value = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                skill to value.coerceIn(0.0, 1.0)
            }
            .toMap()
    }

    fun setSkillState(childId: String, skills: Map<String, Double>) {
        settings["skill_state_$childId"] = skills.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value.coerceIn(0.0, 1.0)}" }
    }
}
