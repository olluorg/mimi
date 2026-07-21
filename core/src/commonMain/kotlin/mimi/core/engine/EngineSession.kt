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
    private var fsmTimerJob: Job? = null
    private var currentSceneState: String? = scene.stateMachine?.initialState
    private var currentLevelIndex: Int = 0

    private fun effectiveObjectives() = scene.objectivesForLevel(currentLevelIndex)
    private fun hasNextLevel() = scene.isMultiLevel && currentLevelIndex < scene.levels.lastIndex

    init {
        scope.launch {
            mutex.withLock {
                enterSceneState(currentSceneState, tick = 0L, runEnterActions = true)
            }
            dispatch(GameEvent.SceneStarted(tick = 0L))
        }
        startIdleLoop()
        startHintCheckLoop()
    }

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
                currentLevelIndex = 0
                world             = DefaultWorldStateFactory.fromScene(scene)
                tickCount         = 0L
                currentSceneState = scene.stateMachine?.initialState
                pendingSystemEvents.clear()
                fsmTimerJob?.cancel()
                enterSceneState(currentSceneState, tick = 0L, runEnterActions = true)
                _worldState.value = world.snapshot()
            }
        }
    }

    private fun advanceLevel() {
        currentLevelIndex++
        world             = DefaultWorldStateFactory.forLevel(scene, currentLevelIndex)
        tickCount         = 0L
        currentSceneState = scene.stateMachine?.initialState
        pendingSystemEvents.clear()
        fsmTimerJob?.cancel()
        enterSceneState(currentSceneState, tick = 0L, runEnterActions = true)
        _worldState.value = world.snapshot()
    }

    fun close() {
        fsmTimerJob?.cancel()
        scope.cancel()
    }

    // ── Internal Pipeline ─────────────────────────────────────────────────────

    private suspend fun processEvent(event: GameEvent) {
        tickCount++
        val snapshot = world.snapshot()
        val ctx      = DefaultBehaviorContext(snapshot, tickCount)
        val buffer   = DefaultCommandBuffer()

        if (isInputEvent(event) && !isInputEnabledForCurrentState()) {
            return
        }

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

        // Objective evaluation (use current level's objectives)
        val activeObjectives = effectiveObjectives()
        val prevObjStates    = activeObjectives.associate { it.id to world.getObjectiveState(it.id) }
        val objResult        = objectiveSystem.evaluate(activeObjectives, world.snapshot(), prevObjStates, tickCount)

        if (objResult.stateCommands.isNotEmpty()) {
            world = world.apply(objResult.stateCommands)
            _worldState.value = world.snapshot()
        }

        // Queue system events; intercept SceneCompleted when more levels remain
        val eventsToQueue = objResult.systemEvents.map { ev ->
            if (ev is GameEvent.SceneCompleted && hasNextLevel())
                GameEvent.LevelCompleted(currentLevelIndex, scene.levels[currentLevelIndex].levelId, tickCount)
            else ev
        }
        pendingSystemEvents.addAll(eventsToQueue)

        // Dispatch feedback
        val fbCtx = FeedbackContext(world.snapshot(), tickCount)
        feedback.forEach { feedbackRegistry.dispatch(it.feedbackType, it.params, fbCtx) }

        // Mark the scene fully complete so the UI can show the completion overlay
        if (event is GameEvent.SceneCompleted) {
            world = world.apply(listOf(Command.SetProperty(
                target   = SCENE_ENTITY_ID,
                field    = "allLevelsComplete",
                value    = PropertyValue.Num(1.0),
                priority = 500,
                source   = "engine",
                tick     = tickCount
            )))
            _worldState.value = world.snapshot()
        }

        applyFsmTransitions(event)
    }

    private suspend fun drainPendingSystemEvents() {
        while (pendingSystemEvents.isNotEmpty()) {
            val sysEvent = pendingSystemEvents.removeFirst()
            processEvent(sysEvent)
            if (sysEvent is GameEvent.LevelCompleted) {
                // Hold the mutex during the pause so no input slips through on the old world.
                // advanceLevel() clears pendingSystemEvents, so the while loop exits after this.
                delay(1200L)
                advanceLevel()
                processEvent(GameEvent.SceneStarted(tick = tickCount))
            }
        }
    }

    private fun handlesEvent(behavior: Behavior, event: GameEvent): Boolean =
        behavior.eventTypes.isEmpty() || event::class in behavior.eventTypes

    private fun isSceneComplete() = effectiveObjectives().any {
        world.getObjectiveState(it.id) == ObjectiveState.LOCKED_COMPLETE
    }

    private fun isInputEvent(event: GameEvent): Boolean = when (event) {
        is GameEvent.Tap,
        is GameEvent.DragStart,
        is GameEvent.DragMove,
        is GameEvent.Drop,
        is GameEvent.Hold,
        is GameEvent.TracePoint,
        is GameEvent.TraceComplete -> true
        else -> false
    }

    private fun isInputEnabledForCurrentState(): Boolean {
        val stateId = currentSceneState ?: return true
        return scene.stateMachine?.states?.firstOrNull { it.id == stateId }?.inputEnabled ?: true
    }

    private fun eventName(event: GameEvent): String = when (event) {
        is GameEvent.Tap -> "tap"
        is GameEvent.DragStart -> "drag_start"
        is GameEvent.DragMove -> "drag_move"
        is GameEvent.Drop -> "drop"
        is GameEvent.Hold -> "hold"
        is GameEvent.TracePoint -> "trace_point"
        is GameEvent.TraceComplete -> "trace_complete"
        is GameEvent.MatchSuccess -> "match_success"
        is GameEvent.MatchFail -> "match_fail"
        is GameEvent.ObjectiveProgressed -> "objective_progressed"
        is GameEvent.ObjectiveCompleted -> "objective_completed"
        is GameEvent.ObjectiveReverted -> "objective_reverted"
        is GameEvent.ObjectiveLocked -> "objective_locked"
        is GameEvent.SceneStarted -> "scene_started"
        is GameEvent.SceneCompleted -> "scene_completed"
        is GameEvent.SceneReset -> "scene_reset"
        is GameEvent.SceneStateEntered -> "scene_state_entered"
        is GameEvent.SceneStateTimeout -> "scene_state_timeout"
        is GameEvent.HintTriggered -> "hint_triggered"
        is GameEvent.IdleCheck -> "idle_check"
        is GameEvent.LevelCompleted -> "level_completed"
    }

    private fun applyFsmTransitions(event: GameEvent) {
        val stateMachine = scene.stateMachine ?: return
        val current = currentSceneState ?: return
        val name = eventName(event)
        val transition = stateMachine.transitions.firstOrNull { transition ->
            transition.from == current && when {
                event is GameEvent.SceneStateTimeout -> transition.afterMs != null &&
                    (transition.onEvent == null || transition.onEvent == name)
                else -> transition.onEvent == name
            }
        } ?: return

        applyFsmActions(transition.actions, tickCount)
        enterSceneState(transition.to, tickCount, runEnterActions = true)
    }

    private fun enterSceneState(stateId: String?, tick: Tick, runEnterActions: Boolean) {
        val stateMachine = scene.stateMachine ?: return
        val nextId = stateId ?: return
        val state = stateMachine.states.firstOrNull { it.id == nextId } ?: return

        currentSceneState = nextId
        fsmTimerJob?.cancel()

        val commands = mutableListOf<Command>(
            Command.SetProperty(
                target = SCENE_ENTITY_ID,
                field = "sceneState",
                value = PropertyValue.Text(nextId),
                priority = 500,
                source = "scene_fsm",
                tick = tick
            ),
            Command.SetProperty(
                target = SCENE_ENTITY_ID,
                field = "inputEnabled",
                value = PropertyValue.Flag(state.inputEnabled),
                priority = 500,
                source = "scene_fsm",
                tick = tick
            )
        )
        if (runEnterActions) commands += fsmCommands(state.onEnter, tick)
        commitFsmCommands(commands)
        _worldState.value = world.snapshot()

        val timeout = state.durationMs
            ?: stateMachine.transitions.firstOrNull { it.from == nextId && it.afterMs != null }?.afterMs
        if (timeout != null && timeout > 0L) {
            fsmTimerJob = scope.launch {
                delay(timeout)
                dispatch(GameEvent.SceneStateTimeout(stateId = nextId, tick = 0L))
            }
        }
    }

    private fun applyFsmActions(actions: List<SceneFsmActionDefinition>, tick: Tick) {
        val commands = fsmCommands(actions, tick)
        if (commands.isEmpty()) return
        commitFsmCommands(commands)
        _worldState.value = world.snapshot()
    }

    private fun commitFsmCommands(commands: List<Command>) {
        val feedback = commands.filterIsInstance<Command.TriggerFeedback>()
        val regular = commands.filterNot { it is Command.TriggerFeedback }
        if (regular.isNotEmpty()) {
            world = world.apply(regular)
        }
        val fbCtx = FeedbackContext(world.snapshot(), tickCount)
        feedback.forEach { feedbackRegistry.dispatch(it.feedbackType, it.params, fbCtx) }
    }

    private fun fsmCommands(actions: List<SceneFsmActionDefinition>, tick: Tick): List<Command> =
        actions.mapNotNull { action ->
            when (action.type) {
                "setProperty" -> {
                    val entityId = action.entityId ?: return@mapNotNull null
                    val field = action.field ?: return@mapNotNull null
                    val value = action.value ?: return@mapNotNull null
                    Command.SetProperty(
                        target = entityId,
                        field = field,
                        value = value,
                        priority = 500,
                        source = "scene_fsm",
                        tick = tick
                    )
                }
                "setEntityState" -> {
                    val entityId = action.entityId ?: return@mapNotNull null
                    val state = action.state ?: return@mapNotNull null
                    Command.SetEntityState(
                        target = entityId,
                        state = state,
                        priority = 500,
                        source = "scene_fsm",
                        tick = tick
                    )
                }
                "triggerFeedback" -> {
                    val feedbackType = action.feedbackType ?: return@mapNotNull null
                    Command.TriggerFeedback(
                        feedbackType = feedbackType,
                        params = action.params,
                        priority = 500,
                        source = "scene_fsm",
                        tick = tick
                    )
                }
                else -> null
            }
        }

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

    private fun startHintCheckLoop() {
        scope.launch {
            delay(4000L)
            while (isActive) {
                delay(2000L)
                mutex.withLock {
                    if (!isSceneComplete() && world.snapshot().idleTicks() >= secondsToTicks(4)) {
                        tickCount++
                        processEvent(GameEvent.IdleCheck(tick = tickCount))
                    }
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

        // Each session owns a child scope so close() cancels only its own coroutines
        // without affecting the caller's (Compose) scope.
        val sessionScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))

        return EngineSession(
            scene            = scene,
            behaviors        = behaviors,
            feedbackRegistry = feedbackReg,
            objectiveSystem  = objSystem,
            scope            = sessionScope
        )
    }
}
