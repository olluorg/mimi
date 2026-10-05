package mimi.core.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import mimi.core.behavior.BehaviorPlugin
import mimi.core.behavior.DefaultBehaviorRegistry
import mimi.core.objective.ObjectiveSystem
import mimi.core.plugin.DefaultFeedbackRegistry
import mimi.core.plugin.FeedbackRegistry
import mimi.core.world.SceneDefinition

class EngineBuilder {
    private val plugins     = mutableListOf<BehaviorPlugin>()
    private var feedbackReg = DefaultFeedbackRegistry()
    private var objSystem   = ObjectiveSystem()

    fun withPlugin(plugin: BehaviorPlugin): EngineBuilder {
        plugins.add(plugin)
        return this
    }

    fun withFeedbackRegistry(registry: FeedbackRegistry): EngineBuilder {
        feedbackReg = registry as? DefaultFeedbackRegistry ?: feedbackReg
        return this
    }

    fun build(
        scene: SceneDefinition,
        scope: CoroutineScope,
        seed:  Long = 0L,
        clock: (() -> Long)? = null
    ): EngineSession {
        val behaviorRegistry = DefaultBehaviorRegistry()
        plugins.forEach { behaviorRegistry.register(it) }

        val behaviors = buildList {
            for (binding in scene.behaviors) {
                add(behaviorRegistry.create(binding.behaviorRef, binding.entityId))
            }
            for (entity in scene.entities) {
                for (ref in entity.behaviors) {
                    if (behaviorRegistry.isRegistered(ref.type)) {
                        add(behaviorRegistry.create(ref, entity.id))
                    }
                }
            }
        }

        // Each session owns a child scope so close() cancels only its own coroutines
        // without affecting the caller's (Compose) scope.
        val sessionScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))

        return EngineSession(
            scene            = scene,
            behaviors        = behaviors,
            feedbackRegistry = feedbackReg,
            objectiveSystem  = objSystem,
            scope            = sessionScope,
            seed             = seed,
            clock            = clock
        )
    }
}
