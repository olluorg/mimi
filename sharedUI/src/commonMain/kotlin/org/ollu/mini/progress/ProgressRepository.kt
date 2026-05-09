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
}
