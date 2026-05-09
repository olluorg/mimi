package mimi.core.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mimi.core.behavior.*
import mimi.core.command.*
import mimi.core.crl.ConflictResolutionLayer
import mimi.core.model.*
import mimi.core.objective.ObjectiveSystem
import mimi.core.plugin.FeedbackContext
import mimi.core.plugin.FeedbackRegistry
import mimi.core.world.*
import kotlin.reflect.KClass

class EngineSession(
    private val scene:            SceneDefinition,
    private val behaviors:        List<Behavior>,
    private val feedbackRegistry: FeedbackRegistry,
    private val objectiveSystem:  ObjectiveSystem,
    private val scope:            CoroutineScope
) {
    private val mutex      = Mutex()
    private var world:     WorldState = DefaultWorldStateFactory.fromScene(scene)
    private var tickCount: Tick = 0L

    private val _worldState = MutableStateFlow<WorldSnapshot>(world.snapshot())
    val worldState: StateFlow<WorldSnapshot> = _worldState.asStateFlow()

    // System events produced by objective evaluation, dispatched next tick
    private val pendingSystemEvents = ArrayDeque<GameEvent>()

    private var idleJob: Job? = null

    init { startIdleLoop() }

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun dispatch(event: GameEvent) = mutex.withLock {
        idleJob?.cancel()
        try {
            // Drain any pending system events first (they run before the user event)
            drainPendingSystemEvents()
            // Process the user input event
            processEvent(event)
        } finally {
            startIdleLoop()
        }
    }

    fun reset() {
        scope.launch {
            mutex.withLock {
                world      = DefaultWorldStateFactory.fromScene(scene)
                tickCount  = 0L
                pendingSystemEvents.clear()
                _worldState.value = world.snapshot()
            }
        }
    }

    fun close() { scope.cancel() }

    // ── Internal Pipeline ─────────────────────────────────────────────────────

    private suspend fun processEvent(event: GameEvent) {
        tickCount++
        val snapshot = world.snapshot()
        val ctx      = DefaultBehaviorContext(snapshot, tickCount)
        val buffer   = DefaultCommandBuffer()

        // Reset idle counter on any input event
        if (event !is GameEvent.SceneCompleted && event !is GameEvent.ObjectiveProgressed) {
            buffer.add(Command.SetProperty(
                target = SCENE_ENTITY_ID, field = "idleTicks",
                value  = PropertyValue.Num(0.0),
                priority = 200, source = "engine", tick = tickCount
            ))
        }

        // Run all phases in order
        for (phase in Phase.entries) {
            val phaseBehaviors = behaviors.filter { it.phase == phase && handlesEvent(it, event) }
            for (behavior in phaseBehaviors) {
                val commands = behavior.handle(event, ctx)
                commands.forEach { buffer.add(it) }
            }
        }

        // CRL — resolve conflicts, separate feedback
        val (resolved, feedback) = ConflictResolutionLayer.resolve(
            buffer.drain(),
            scene.fieldPolicies
        )

        // Commit
        world = world.apply(resolved)
        _worldState.value = world.snapshot()

        // Objective evaluation
        val prevObjStates = scene.objectives.associate { it.id to world.getObjectiveState(it.id) }
        val objResult     = objectiveSystem.evaluate(scene.objectives, world.snapshot(), prevObjStates, tickCount)

        if (objResult.stateCommands.isNotEmpty()) {
            world = world.apply(objResult.stateCommands)
            _worldState.value = world.snapshot()
        }

        // Queue system events for next tick
        pendingSystemEvents.addAll(objResult.systemEvents)

        // Dispatch feedback
        val fbCtx = FeedbackContext(world.snapshot(), tickCount)
        feedback.forEach { feedbackRegistry.dispatch(it.feedbackType, it.params, fbCtx) }
    }

    private suspend fun drainPendingSystemEvents() {
        while (pendingSystemEvents.isNotEmpty()) {
            val sysEvent = pendingSystemEvents.removeFirst()
            processEvent(sysEvent)
        }
    }

    private fun handlesEvent(behavior: Behavior, event: GameEvent): Boolean =
        behavior.eventTypes.isEmpty() || event::class in behavior.eventTypes

    // ── Idle Tick Loop ────────────────────────────────────────────────────────

    private fun startIdleLoop() {
        idleJob = scope.launch {
            val tickMs = 1000L / TICKS_PER_SECOND
            while (isActive) {
                delay(tickMs)
                mutex.withLock {
                    tickCount++
                    val idleCmd = Command.IncrementCounter(
                        target = SCENE_ENTITY_ID,
                        field  = "idleTicks",
                        delta  = 1L,
                        source = "engine",
                        tick   = tickCount
                    )
                    world = world.apply(listOf(idleCmd))
                    _worldState.value = world.snapshot()
                }
            }
        }
    }
}

// ── Engine Builder ────────────────────────────────────────────────────────────

class EngineBuilder {
    private val plugins     = mutableListOf<BehaviorPlugin>()
    private var feedbackReg = mimi.core.plugin.DefaultFeedbackRegistry()
    private var objSystem   = ObjectiveSystem()

    fun withPlugin(plugin: BehaviorPlugin): EngineBuilder {
        plugins.add(plugin)
        return this
    }

    fun withFeedbackRegistry(registry: mimi.core.plugin.FeedbackRegistry): EngineBuilder {
        feedbackReg = registry as? mimi.core.plugin.DefaultFeedbackRegistry ?: feedbackReg
        return this
    }

    fun build(scene: SceneDefinition, scope: CoroutineScope): EngineSession {
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

        return EngineSession(
            scene            = scene,
            behaviors        = behaviors,
            feedbackRegistry = feedbackReg,
            objectiveSystem  = objSystem,
            scope            = scope
        )
    }
}
