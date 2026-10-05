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
import kotlin.reflect.KClass
import kotlin.time.TimeSource

/**
 * How a single play-through went. Feeds the skill mapper, which scores a scene
 * on how much help the child needed rather than on completion alone.
 */
data class SessionMetrics(
    val mistakes:        Int  = 0,
    val hintsUsed:       Int  = 0,
    val matches:         Int  = 0,
    val durationMs:      Long = 0L,
    val levelsCompleted: Int  = 0,
    /**
     * What the child answered, in a scene where answering was the point.
     *
     * A list, because a scene may ask several times: "do you want an apple?",
     * then a biscuit, then a ball. A choice game produces no score — these
     * answers are its entire result, and the reason an adult opens the progress
     * screen after one.
     */
    val choices:         List<String> = emptyList(),
    /**
     * Times the child made a sound of their own that the microphone heard.
     *
     * In a speech game this is the result. Mistakes and hints say little about
     * a child who is not yet talking; whether they try to more often than last
     * week says almost everything.
     */
    val vocalAttempts:   Int  = 0,
    /** Attempts an adult vouched for with the "сказал(а)" button. */
    val adultConfirmed:  Int  = 0,
    /**
     * Turns that ended without any answer, after the word had been modelled
     * again. Not a mistake — the game moves on regardless — but a rising share
     * of these says the words are too hard or the child is tired.
     */
    val unanswered:      Int  = 0,
    /** Turns of a speech game the child answered — by voice, picture or the adult's word. */
    val responses:       Int  = 0,
    /** Answers where the vowel heard was exactly the one modelled. */
    val exactResponses:  Int  = 0,
    /**
     * Every turn of a speech game: which word, and how it went. A total says
     * the child tried eleven times; this says which words they answer and
     * which they never do, which is what changes what to practise next.
     */
    val turns:           List<SpeechTurn> = emptyList()
) {
    /** The last thing answered, for a scene that only asks once. */
    val choice: String? get() = choices.lastOrNull()
}

/** One turn of a speech game: the word modelled and what came back. */
data class SpeechTurn(
    val word:    String,
    /** exact, voice, adult, tap — or none, when the turn passed in silence. */
    val outcome: String
) {
    val answered: Boolean get() = outcome != "none"
    val exact:    Boolean get() = outcome == "exact"
}

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
    private var carriedMistakes: Long = 0L
    private var carriedHints:    Long = 0L
    private var carriedVocal:    Long = 0L
    private var carriedAdult:    Long = 0L
    private var carriedUnanswered: Long = 0L
    private var carriedResponses:  Long = 0L
    private var carriedExact:      Long = 0L
    private val carriedTurns = mutableListOf<SpeechTurn>()
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

    /**
     * What this play-through cost the child so far: the numbers the curriculum
     * scores a scene on. Safe to read at any time; read it when the scene
     * completes to score the attempt.
     */
    /** Plays the scene's opening instruction again — the "повтори задание" button. */
    suspend fun repeatInstruction() = dispatch(GameEvent.InstructionRequested(tick = tickCount))

    fun metrics(): SessionMetrics {
        val snapshot = world.snapshot()
        return SessionMetrics(
            mistakes        = (carriedMistakes + snapshot.mistakes()).toInt(),
            hintsUsed       = (carriedHints + snapshot.hints()).toInt(),
            matches         = snapshot.matches().toInt(),
            durationMs      = startMark.elapsedNow().inWholeMilliseconds,
            levelsCompleted = currentLevelIndex,
            choices         = carriedChoices + listOfNotNull(currentChoice()),
            vocalAttempts   = (carriedVocal + snapshot.getCounter(COUNTER_VOCAL)).toInt(),
            adultConfirmed  = (carriedAdult + snapshot.getCounter(COUNTER_ADULT)).toInt(),
            unanswered      = (carriedUnanswered + snapshot.getCounter(COUNTER_UNANSWERED)).toInt(),
            responses       = (carriedResponses + snapshot.getCounter(COUNTER_RESPONSES)).toInt(),
            exactResponses  = (carriedExact + snapshot.getCounter(COUNTER_EXACT)).toInt(),
            turns           = carriedTurns + turnsOf(snapshot)
        )
    }

    /** The turn log the speech behavior keeps on the scene entity. */
    private fun turnsOf(snapshot: WorldSnapshot): List<SpeechTurn> {
        val raw = (snapshot.getProperty(SCENE_ENTITY_ID, AwaitResponseBehavior.KEY_LOG) as? PropertyValue.Text)
            ?.value.orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(AwaitResponseBehavior.TURN_SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(AwaitResponseBehavior.FIELD_SEPARATOR)
            if (parts.size != 2 || parts[0].isBlank()) null else SpeechTurn(parts[0], parts[1])
        }
    }

    private fun currentChoice(): String? =
        (world.snapshot().getProperty(SCENE_ENTITY_ID, "lastChoice") as? PropertyValue.Text)?.value

    fun reset() {
        scope.launch {
            mutex.withLock {
                carriedMistakes   = 0L
                carriedHints      = 0L
                carriedVocal      = 0L
                carriedAdult      = 0L
                carriedUnanswered = 0L
                carriedResponses  = 0L
                carriedExact      = 0L
                carriedTurns.clear()
                carriedChoices.clear()
                startMark         = TimeSource.Monotonic.markNow()
                currentLevelIndex = 0
                // A replay deals a fresh arrangement: "again" should not mean
                // the identical screen the child has just solved.
                seed              = seed + 1
                world             = DefaultWorldStateFactory.fromScene(scene, seed)
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
        carriedMistakes  += world.mistakes()
        carriedHints     += world.hints()
        carriedVocal     += world.getCounter(COUNTER_VOCAL)
        carriedAdult     += world.getCounter(COUNTER_ADULT)
        carriedUnanswered += world.getCounter(COUNTER_UNANSWERED)
        carriedResponses  += world.getCounter(COUNTER_RESPONSES)
        carriedExact      += world.getCounter(COUNTER_EXACT)
        carriedTurns      += turnsOf(world.snapshot())
        currentChoice()?.let { carriedChoices += it }
        currentLevelIndex++
        world             = DefaultWorldStateFactory.forLevel(scene, currentLevelIndex, seed)
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

    private fun eventName(event: GameEvent): String = when (event) {
        is GameEvent.Tap -> "tap"
        is GameEvent.DragStart -> "drag_start"
        is GameEvent.DragMove -> "drag_move"
        is GameEvent.Drop -> "drop"
        is GameEvent.Hold -> "hold"
        is GameEvent.TracePoint -> "trace_point"
        is GameEvent.TraceComplete -> "trace_complete"
        is GameEvent.VoiceLevel -> "voice_level"
        is GameEvent.Vocalization -> "vocalization"
        is GameEvent.ClockTick -> "clock_tick"
        is GameEvent.MatchSuccess -> "match_success"
        is GameEvent.MatchFail -> "match_fail"
        is GameEvent.ObjectiveProgressed -> "objective_progressed"
        is GameEvent.ObjectiveCompleted -> "objective_completed"
        is GameEvent.ObjectiveReverted -> "objective_reverted"
        is GameEvent.ObjectiveLocked -> "objective_locked"
        is GameEvent.SceneStarted -> "scene_started"
        is GameEvent.InstructionRequested -> "instruction_requested"
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

/** Counters on the scene entity that make up the speech result of a session. */
const val COUNTER_VOCAL      = "vocalAttempts"
const val COUNTER_ADULT      = "adultConfirmed"
const val COUNTER_UNANSWERED = "unanswered"
const val COUNTER_RESPONSES  = "responses"
const val COUNTER_EXACT      = "exactResponses"

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
