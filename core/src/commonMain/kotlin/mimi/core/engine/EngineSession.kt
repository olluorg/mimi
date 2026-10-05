package mimi.core.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mimi.core.behavior.*
import mimi.core.behaviors.AwaitResponseBehavior
import mimi.core.command.*
import mimi.core.crl.ConflictResolutionLayer
import mimi.core.model.*
import mimi.core.objective.ObjectiveSystem
import mimi.core.plugin.FeedbackContext
import mimi.core.plugin.FeedbackRegistry
import mimi.core.world.*
import kotlin.time.TimeSource

class EngineSession(
    private val scene:            SceneDefinition,
    private val behaviors:        List<Behavior>,
    private val feedbackRegistry: FeedbackRegistry,
    private val objectiveSystem:  ObjectiveSystem,
    private val scope:            CoroutineScope,
    /**
     * Chooses this play-through's arrangement. The same seed always rebuilds the
     * same scene, so a run stays reproducible; a new seed per play is what makes
     * the game look different the second time a child opens it.
     */
    private var seed:             Long = 0L,
    /**
     * Milliseconds since the session began. Injectable so tests can run a
     * five-second pause in virtual time.
     */
    clock:                        (() -> Long)? = null
) {
    private val sessionStart = TimeSource.Monotonic.markNow()
    private val nowMs: () -> Long = clock ?: { sessionStart.elapsedNow().inWholeMilliseconds }
    private val mutex      = Mutex()
    private var world:     WorldState = DefaultWorldStateFactory.fromScene(scene, seed)
    private var tickCount: Tick = 0L

    private val _worldState = MutableStateFlow<WorldSnapshot>(world.snapshot())
    val worldState: StateFlow<WorldSnapshot> = _worldState.asStateFlow()

    // System events produced by objective evaluation, dispatched next tick
    private val pendingSystemEvents = ArrayDeque<GameEvent>()

    private var idleJob: Job? = null
    private var fsmTimerJob: Job? = null
    private var currentSceneState: String? = scene.stateMachine?.initialState
    private var currentLevelIndex: Int = 0

    // Advancing a level rebuilds the world from scratch, which zeroes its counters.
    // The learning profile is scored per scene, not per level, so the totals so far
    // are carried across the boundary.
    private val carriedCounters = mutableMapOf<String, Long>()
    private val carriedTurns    = mutableListOf<SpeechTurn>()
    // Each round rebuilds the world, which would otherwise drop the answer given
    // in it — so a five-question scene reported only its last answer.
    private val carriedChoices = mutableListOf<String>()
    private var startMark = TimeSource.Monotonic.markNow()

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
        if (behaviors.any { GameEvent.ClockTick::class in it.eventTypes }) startClockLoop()
    }

    /**
     * Whether anything in this scene reacts to the child's voice. The screen
     * opens the microphone only for these scenes, and shows the adult's
     * "сказал(а)" button only here.
     */
    val listensForVoice: Boolean = behaviors.any { behavior ->
        GameEvent.Vocalization::class in behavior.eventTypes ||
            GameEvent.VoiceLevel::class in behavior.eventTypes
    }

    /** The adult heard the child try, whether or not the microphone did. */
    suspend fun confirmSpeechAttempt() =
        dispatch(GameEvent.Vocalization(durationMs = 0L, peak = 0f, byAdult = true, tick = tickCount))

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun dispatch(event: GameEvent) = mutex.withLock {
        idleJob?.cancel()
        try {
            // Drain any pending system events first (they run before the user event)
            drainPendingSystemEvents()
            // Process the user input event
            processEvent(event)
            // …then settle everything it set off. Without this the match counters,
            // the level hand-off and the completion flag all waited for the child's
            // next tap, so solving the last piece appeared to do nothing.
            drainPendingSystemEvents()
        } finally {
            startIdleLoop()
        }
    }

    /** Plays the scene's opening instruction again — the "повтори задание" button. */
    suspend fun repeatInstruction() = dispatch(GameEvent.InstructionRequested(tick = tickCount))

    /**
     * What this play-through cost the child so far: the numbers the curriculum
     * scores a scene on. Safe to read at any time; read it when the scene
     * completes to score the attempt.
     */
    fun metrics(): SessionMetrics {
        val snapshot = world.snapshot()
        fun total(counter: String) = ((carriedCounters[counter] ?: 0L) + snapshot.getCounter(counter)).toInt()
        return SessionMetrics(
            mistakes        = total("mistakes"),
            hintsUsed       = total("hints"),
            matches         = snapshot.matches().toInt(),
            durationMs      = startMark.elapsedNow().inWholeMilliseconds,
            levelsCompleted = currentLevelIndex,
            choices         = carriedChoices + listOfNotNull(currentChoice(snapshot)),
            vocalAttempts   = total(COUNTER_VOCAL),
            adultConfirmed  = total(COUNTER_ADULT),
            unanswered      = total(COUNTER_UNANSWERED),
            responses       = total(COUNTER_RESPONSES),
            exactResponses  = total(COUNTER_EXACT),
            turns           = carriedTurns + turnsOf(snapshot)
        )
    }

    /** The turn log the speech behavior keeps on the scene entity. */
    private fun turnsOf(snapshot: WorldSnapshot): List<SpeechTurn> =
        SpeechTurn.decodeLog(
            (snapshot.getProperty(SCENE_ENTITY_ID, AwaitResponseBehavior.KEY_LOG) as? PropertyValue.Text)
                ?.value.orEmpty()
        )

    private fun currentChoice(snapshot: WorldSnapshot): String? =
        (snapshot.getProperty(SCENE_ENTITY_ID, "lastChoice") as? PropertyValue.Text)?.value

    fun reset() {
        scope.launch {
            mutex.withLock {
                carriedCounters.clear()
                carriedTurns.clear()
                carriedChoices.clear()
                startMark         = TimeSource.Monotonic.markNow()
                currentLevelIndex = 0
                // A replay deals a fresh arrangement: "again" should not mean
                // the identical screen the child has just solved.
                seed              = seed + 1
                restartWorld(DefaultWorldStateFactory.fromScene(scene, seed))
            }
        }
    }

    private fun advanceLevel() {
        val snapshot = world.snapshot()
        for (counter in CARRIED_COUNTERS) {
            carriedCounters[counter] = (carriedCounters[counter] ?: 0L) + snapshot.getCounter(counter)
        }
        carriedTurns += turnsOf(snapshot)
        currentChoice(snapshot)?.let { carriedChoices += it }
        currentLevelIndex++
        restartWorld(DefaultWorldStateFactory.forLevel(scene, currentLevelIndex, seed))
    }

    /** Swaps in a freshly built world and starts the scene's state machine over. */
    private fun restartWorld(fresh: WorldState) {
        world             = fresh
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
        val ctx      = DefaultBehaviorContext(snapshot, tickCount, nowMs())
        val buffer   = DefaultCommandBuffer()

        if (isInputEvent(event) && !isInputEnabledForCurrentState()) {
            return
        }

        // Reset idle counter on any input event. The clock is not the child
        // doing something, so it must not keep a silent child from being helped.
        if (event !is GameEvent.SceneCompleted && event !is GameEvent.ObjectiveProgressed &&
            event !is GameEvent.ClockTick) {
            buffer.add(Command.SetProperty(
                target = SCENE_ENTITY_ID, field = "idleTicks",
                value  = PropertyValue.Num(0.0),
                priority = 200, source = "engine", tick = tickCount
            ))
        }

        // Every attempt to speak is counted here, once, whichever behaviors
        // react to it — a scene may both move a rocket and answer a question
        // with the same "a-a-a", and it is still one attempt.
        if (event is GameEvent.Vocalization) {
            buffer.add(Command.IncrementCounter(
                target = SCENE_ENTITY_ID,
                field  = if (event.byAdult) COUNTER_ADULT else COUNTER_VOCAL,
                source = "engine",
                tick   = tickCount
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

        // Most clock ticks change nothing; publishing a fresh snapshot for each
        // would redraw the whole scene four times a second for no reason.
        if (event is GameEvent.ClockTick && resolved.isEmpty() && feedback.isEmpty()) return

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

        // Behaviors announce a match by triggering matchSuccess/matchFail feedback.
        // The engine turns that into the matching system event so every mechanic —
        // drag-and-drop, tap, trace, memory pairs — feeds the same counters without
        // each behavior having to emit the event itself. Derived only from real
        // input, so a match event can never cascade into another one.
        if (!isMatchEvent(event)) {
            pendingSystemEvents.addAll(matchEventsFrom(feedback))
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

    private fun isMatchEvent(event: GameEvent): Boolean =
        event is GameEvent.MatchSuccess || event is GameEvent.MatchFail

    private fun matchEventsFrom(feedback: List<Command.TriggerFeedback>): List<GameEvent> =
        feedback.mapNotNull { fb ->
            val source = fb.params["source"] ?: return@mapNotNull null
            val target = fb.params["target"] ?: source
            when (fb.feedbackType) {
                "matchSuccess" -> GameEvent.MatchSuccess(source, target, tickCount)
                "matchFail"    -> GameEvent.MatchFail(
                    source = source,
                    target = target,
                    reason = fb.params["reason"] ?: "constraint",
                    tick   = tickCount
                )
                else -> null
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
        is GameEvent.TraceComplete,
        is GameEvent.VoiceLevel,
        is GameEvent.Vocalization -> true
        else -> false
    }

    private fun isInputEnabledForCurrentState(): Boolean {
        val stateId = currentSceneState ?: return true
        return scene.stateMachine?.states?.firstOrNull { it.id == stateId }?.inputEnabled ?: true
    }

    private fun applyFsmTransitions(event: GameEvent) {
        val stateMachine = scene.stateMachine ?: return
        val current      = currentSceneState ?: return
        val transition   = stateMachine.transitionFor(current, event) ?: return

        commitFsmCommands(fsmCommands(transition.actions, tickCount))
        enterSceneState(transition.to, tickCount, runEnterActions = true)
    }

    private fun enterSceneState(stateId: String?, tick: Tick, runEnterActions: Boolean) {
        val stateMachine = scene.stateMachine ?: return
        val nextId = stateId ?: return
        val state = stateMachine.states.firstOrNull { it.id == nextId } ?: return

        currentSceneState = nextId
        fsmTimerJob?.cancel()

        val commands = fsmStateCommands(nextId, state.inputEnabled, tick).toMutableList()
        if (runEnterActions) commands += fsmCommands(state.onEnter, tick)
        commitFsmCommands(commands)

        val timeout = stateMachine.timeoutOf(nextId)
        if (timeout != null && timeout > 0L) {
            fsmTimerJob = scope.launch {
                delay(timeout)
                dispatch(GameEvent.SceneStateTimeout(stateId = nextId, tick = 0L))
            }
        }
    }

    private fun commitFsmCommands(commands: List<Command>) {
        if (commands.isEmpty()) return
        val feedback = commands.filterIsInstance<Command.TriggerFeedback>()
        val regular = commands.filterNot { it is Command.TriggerFeedback }
        if (regular.isNotEmpty()) {
            world = world.apply(regular)
        }
        val fbCtx = FeedbackContext(world.snapshot(), tickCount)
        feedback.forEach { feedbackRegistry.dispatch(it.feedbackType, it.params, fbCtx) }
        _worldState.value = world.snapshot()
    }

    // ── Idle Tick Loop ────────────────────────────────────────────────────────

    /**
     * Advances idle time while nobody is touching the screen.
     *
     * A tick stays 1/60 s so that hint thresholds and cooldowns keep their
     * meaning, but the loop wakes once a second and advances the clock by a
     * whole second's worth of ticks. Waking 60 times a second — and pushing a
     * fresh snapshot each time, which recomposes the whole scene — drained
     * battery on a screen where nothing was happening. Idle thresholds are
     * measured in seconds, so one-second resolution is all they need.
     */
    private fun startIdleLoop() {
        idleJob = scope.launch {
            while (isActive) {
                delay(1000L)
                mutex.withLock {
                    tickCount += TICKS_PER_SECOND
                    val idleCmd = Command.IncrementCounter(
                        target = SCENE_ENTITY_ID,
                        field  = "idleTicks",
                        delta  = TICKS_PER_SECOND,
                        source = "engine",
                        tick   = tickCount
                    )
                    world = world.apply(listOf(idleCmd))
                    _worldState.value = world.snapshot()
                }
            }
        }
    }

    /**
     * Drives [GameEvent.ClockTick] for behaviors that wait.
     *
     * Deliberately not routed through [dispatch]: that restarts the idle loop,
     * and a clock firing every quarter second would keep idle time at zero
     * forever — no hint, no way past a scene, for exactly the silent child who
     * needs them.
     */
    private fun startClockLoop() {
        scope.launch {
            while (isActive) {
                delay(CLOCK_PERIOD_MS)
                mutex.withLock {
                    drainPendingSystemEvents()
                    tickCount++
                    processEvent(GameEvent.ClockTick(tick = tickCount))
                    drainPendingSystemEvents()
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

/** How often waiting behaviors are woken. A pause is felt in whole seconds. */
private const val CLOCK_PERIOD_MS = 250L

/** Counters reset by a level change whose totals are still owed to the session. */
private val CARRIED_COUNTERS = listOf(
    "mistakes", "hints",
    COUNTER_VOCAL, COUNTER_ADULT, COUNTER_UNANSWERED, COUNTER_RESPONSES, COUNTER_EXACT
)
