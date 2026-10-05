package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.engine.COUNTER_EXACT
import mimi.core.engine.COUNTER_RESPONSES
import mimi.core.engine.COUNTER_UNANSWERED
import mimi.core.engine.SpeechTurn
import mimi.core.model.*
import mimi.core.world.WorldSnapshot
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * The turn at the heart of every speech game: say it, then wait.
 *
 * Everything with evidence behind it for a child who barely talks has this
 * shape. An adult models a word, and then — the part that is easy to skip —
 * pauses long enough for the child to try. A game that talks without pausing
 * leaves no room to answer; one that demands the right word fails the attempts
 * that matter most. So:
 *
 *  - the word is modelled, and modelled again if nothing comes back;
 *  - *any* voice counts, or an adult's word that the child tried, or — in an
 *    AAC scene — pressing the picture, because a pressed picture that speaks
 *    is the child's voice;
 *  - silence is never a mistake. After the last model the game moves on, so
 *    a child who is not ready yet is not stuck in front of a question.
 *
 * One entity in [group] is shown at a time. Its `prompt` (a recording) or
 * `speech` (a line to say) is the model; its `reward`, if it has one, plays
 * when the child answers — the cow moos back when the child says "му". An
 * `answer` line is said at the end of every turn: the finished phrase in a
 * "договори" game, answered or not.
 *
 * For practice drills — the motor-learning schedule used with apraxia — words
 * come in [random] order, and the reward thins out as the drill goes on
 * ([fading]): praise for every attempt early, then less, which is what makes
 * the movement stick after the game is over.
 */
class AwaitResponseBehavior(
    override val id:        BehaviorId,
    private val group:      String,
    private val firstLeadMs: Long,
    private val leadMs:     Long,
    private val waitMs:     Long,
    private val rewardMs:   Long,
    private val maxPrompts: Int,
    private val trials:     Int?,
    private val random:     Boolean,
    private val fading:     Boolean,
    private val acceptVoice: Boolean,
    private val acceptAdult: Boolean,
    private val acceptTap:   Boolean,
    /**
     * How long after the device speaks to ignore the microphone. The model
     * word coming out of the speaker is the loudest voice in the room, and
     * without this the game would answer itself.
     */
    private val guardMs:    Long
) : Behavior {

    override val phase      = Phase.RESOLUTION
    override val eventTypes = EVENTS

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        val state = TurnState.read(ctx.snapshot)
        return when (event) {
            is GameEvent.SceneStarted -> if (state.phase == null) begin(ctx) else emptyList()
            is GameEvent.ClockTick    -> onClock(state, ctx)
            is GameEvent.Tap          -> onTap(event, state, ctx)
            is GameEvent.Vocalization -> onVoice(event, state, ctx)
            else                      -> emptyList()
        }
    }

    // ── Turn lifecycle ────────────────────────────────────────────────────────

    private fun begin(ctx: BehaviorContext): List<Command> {
        val members = members(ctx)
        if (members.isEmpty()) return emptyList()
        val first = pick(members, trial = 0, previous = null, ctx)
        val commands = mutableListOf<Command>()
        // Only the word being asked is on screen. A board of five pictures is
        // a choice; this is a turn.
        members.filter { it.id != first.id }.forEach {
            commands += Command.SetEntityState(it.id, EntityLifecycleState.INACTIVE, source = id, tick = ctx.tick)
        }
        commands += Command.SetEntityState(first.id, EntityLifecycleState.ACTIVE, source = id, tick = ctx.tick)
        // The first model waits for the scene's own instruction to finish.
        commands += TurnState.write(
            current = first.id, phase = PHASE_LEAD, since = ctx.nowMs,
            // The instruction is playing through the speaker meanwhile.
            prompts = 0, trial = 0, guardUntil = ctx.nowMs + firstLeadMs, ctx = ctx, source = id
        )
        commands += setNum(KEY_LEAD, firstLeadMs.toDouble(), ctx)
        return commands
    }

    private fun onClock(state: TurnState, ctx: BehaviorContext): List<Command> {
        val current = state.current ?: return emptyList()
        val elapsed = ctx.nowMs - state.since
        return when (state.phase) {
            PHASE_LEAD -> {
                val lead = ctx.snapshot.getNum(SCENE_ENTITY_ID, KEY_LEAD)?.toLong() ?: leadMs
                if (elapsed >= lead) model(current, state, prompts = 1, ctx) else emptyList()
            }
            PHASE_WAIT -> when {
                elapsed < waitMs          -> emptyList()
                state.prompts < maxPrompts -> model(current, state, prompts = state.prompts + 1, ctx)
                else                      -> finish(current, state, answered = false, ctx, outcome = SpeechTurn.OUTCOME_NONE)
            }
            PHASE_REWARD -> if (elapsed >= rewardMs) next(current, state, ctx) else emptyList()
            else -> emptyList()
        }
    }

    private fun onTap(event: GameEvent.Tap, state: TurnState, ctx: BehaviorContext): List<Command> {
        val current = state.current ?: return emptyList()
        if (event.source != current) return emptyList()
        if (state.phase != PHASE_LEAD && state.phase != PHASE_WAIT) return emptyList()
        if (acceptTap) return respond(current, state, ctx, kind = "tap")
        // Pressing the picture asks to hear it again — never a wrong answer,
        // and it does not use up one of the models.
        return model(current, state, prompts = maxOf(state.prompts, 1), ctx)
    }

    private fun onVoice(event: GameEvent.Vocalization, state: TurnState, ctx: BehaviorContext): List<Command> {
        val current = state.current ?: return emptyList()
        // Trying before the model has even played is spontaneous speech — the
        // best answer there is, so the lead-in counts as well as the wait.
        if (state.phase != PHASE_LEAD && state.phase != PHASE_WAIT) return emptyList()
        return when {
            event.byAdult -> if (acceptAdult) respond(current, state, ctx, kind = "adult") else emptyList()
            !acceptVoice  -> emptyList()
            ctx.nowMs < state.guardUntil -> emptyList()
            else -> respond(current, state, ctx, kind = "voice", sound = event.sound)
        }
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    private fun model(current: EntityId, state: TurnState, prompts: Int, ctx: BehaviorContext): List<Command> {
        val entity = ctx.snapshot.getEntity(current) ?: return emptyList()
        return listOfNotNull(say(entity, ctx)) + TurnState.write(
            current = current, phase = PHASE_WAIT, since = ctx.nowMs, prompts = prompts,
            trial = state.trial, guardUntil = ctx.nowMs + guardMs, ctx = ctx, source = id
        )
    }

    private fun respond(
        current: EntityId,
        state:   TurnState,
        ctx:     BehaviorContext,
        kind:    String,
        sound:   String? = null
    ): List<Command> {
        val commands = mutableListOf<Command>()
        // Any voice already answers the turn. Saying exactly the sound that was
        // modelled earns more on top — a bigger celebration, and a mark the
        // adult can see — without anything less ever counting against the child.
        val expected = ctx.snapshot.getEntity(current)?.text("expectSound")
        val exact = kind == "voice" && expected != null && sound != null && sound.equals(expected, ignoreCase = true)
        if (exact) {
            commands += increment(COUNTER_EXACT, ctx)
            commands += Command.SetProperty(current, "celebrate", PropertyValue.Text("bonus"), source = id, tick = ctx.tick)
            commands += Command.TriggerFeedback("bonus", mapOf("source" to current), source = id, tick = ctx.tick)
        }
        if (kind == "tap") {
            // In an AAC scene the pressed picture speaks for the child.
            ctx.snapshot.getEntity(current)?.let { say(it, ctx) }?.let { commands += it }
            commands += increment("tapResponses", ctx)
        }
        commands += increment(COUNTER_RESPONSES, ctx)
        return commands + finish(current, state, answered = true, ctx, outcome = if (exact) SpeechTurn.OUTCOME_EXACT else kind)
    }

    private fun finish(
        current:  EntityId,
        state:    TurnState,
        answered: Boolean,
        ctx:      BehaviorContext,
        outcome:  String
    ): List<Command> {
        val commands = mutableListOf<Command>()
        commands += logTurn(current, outcome, ctx)
        // One turn taken, answered or not: `interactionCount` ends the scene
        // after the planned number of turns, so silence cannot trap the child.
        commands += increment("selections", ctx)
        if (answered) {
            commands += Command.SetEntityState(current, EntityLifecycleState.TENTATIVE, source = id, tick = ctx.tick)
            val praise = !fading || praiseDue(state.trial, plannedTrials(ctx))
            val entity = ctx.snapshot.getEntity(current)
            val reward = entity?.text("reward")?.takeIf { it.isNotBlank() }
            if (praise && reward != null) {
                // Cut to the reward pause: some of these recordings run for
                // twelve seconds and would talk over the next word.
                commands += Command.TriggerFeedback(
                    "sound", mapOf("path" to reward, "maxMs" to rewardMs.toString()), source = id, tick = ctx.tick
                )
            }
            // matchSuccess is the full "yes!" — chime, haptics, a reward revealed
            // elsewhere on screen. On a thinned-out turn the child still hears
            // that the attempt landed, just more quietly.
            commands += Command.TriggerFeedback(
                feedbackType = if (praise) "matchSuccess" else "sequenceStepCorrect",
                params       = mapOf("source" to current),
                source       = id,
                tick         = ctx.tick
            )
        } else {
            commands += increment(COUNTER_UNANSWERED, ctx)
        }
        // The whole answer, said back — «…три!» after the child's «и!», or in
        // place of the answer that did not come. Hearing the full word right
        // after their own attempt is how an approximation grows into the word.
        ctx.snapshot.getEntity(current)?.text("answer")?.takeIf { it.isNotBlank() }?.let { answer ->
            commands += Command.TriggerFeedback("tts", mapOf("text" to answer), source = id, tick = ctx.tick)
        }
        commands += TurnState.write(
            current = current, phase = PHASE_REWARD, since = ctx.nowMs, prompts = state.prompts,
            trial = state.trial + 1, guardUntil = ctx.nowMs + guardMs, ctx = ctx, source = id
        )
        return commands
    }

    private fun next(previous: EntityId, state: TurnState, ctx: BehaviorContext): List<Command> {
        val commands = mutableListOf<Command>(
            Command.SetEntityState(previous, EntityLifecycleState.INACTIVE, source = id, tick = ctx.tick)
        )
        if (state.trial >= plannedTrials(ctx)) {
            // The objective completes the scene from the turn count; the last
            // word stays off screen so the completion overlay has the stage.
            commands += setText(KEY_PHASE, PHASE_DONE, ctx)
            return commands
        }
        val members = members(ctx)
        if (members.isEmpty()) return commands
        val upcoming = pick(members, state.trial, previous, ctx)
        commands += Command.SetEntityState(upcoming.id, EntityLifecycleState.ACTIVE, source = id, tick = ctx.tick)
        commands += TurnState.write(
            current = upcoming.id, phase = PHASE_LEAD, since = ctx.nowMs, prompts = 0,
            trial = state.trial, guardUntil = state.guardUntil, ctx = ctx, source = id
        )
        commands += setNum(KEY_LEAD, leadMs.toDouble(), ctx)
        return commands
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun members(ctx: BehaviorContext): List<EntitySnapshot> =
        // Declaration order: a sequential scene asks its words in the order written.
        ctx.snapshot.getEntitiesByTag("group", group)

    private fun plannedTrials(ctx: BehaviorContext): Int = trials ?: members(ctx).size

    private fun pick(
        members:  List<EntitySnapshot>,
        trial:    Int,
        previous: EntityId?,
        ctx:      BehaviorContext
    ): EntitySnapshot {
        if (!random) return members[trial % members.size]
        // Deterministic per play: the session seed and the turn number fix the
        // word, so a replay drills in a new order but a run can be reproduced.
        val seed = ctx.snapshot.getNum(SCENE_ENTITY_ID, "seed")?.toLong() ?: 0L
        val index = Random(seed * 1_000_003L + trial).nextInt(members.size)
        val chosen = members[index]
        // The same word twice in a row is blocked practice by accident.
        return if (chosen.id == previous && members.size > 1) members[(index + 1) % members.size] else chosen
    }

    private fun say(entity: EntitySnapshot, ctx: BehaviorContext): Command? {
        val recording = entity.text("prompt")?.takeIf { it.isNotBlank() }
        if (recording != null) {
            return Command.TriggerFeedback("sound", mapOf("path" to recording), source = id, tick = ctx.tick)
        }
        val line = entity.text("speech") ?: entity.text("name")
        if (!line.isNullOrBlank()) {
            return Command.TriggerFeedback("tts", mapOf("text" to line), source = id, tick = ctx.tick)
        }
        val sound = entity.text("sound")?.takeIf { it.isNotBlank() } ?: return null
        return Command.TriggerFeedback("sound", mapOf("path" to sound), source = id, tick = ctx.tick)
    }

    /**
     * One line per turn — the word and how it went — so an adult can see which
     * words the child answers and which they never do. A session total says a
     * child tried eleven times; this says «му» eight times and «мяу» never,
     * which is what changes what to practise next.
     */
    private fun logTurn(current: EntityId, outcome: String, ctx: BehaviorContext): Command {
        val entity = ctx.snapshot.getEntity(current)
        val word = entity?.text("speech") ?: entity?.text("name") ?: current
        val entry = SpeechTurn(word, outcome).encode()
        val existing = ctx.snapshot.scene()?.text(KEY_LOG).orEmpty()
        // Capped: this travels in the saved session record, and a long drill
        // would otherwise grow it without end.
        val entries = (if (existing.isBlank()) listOf(entry) else existing.split(SpeechTurn.TURN_SEPARATOR) + entry)
            .takeLast(MAX_LOGGED_TURNS)
        return setText(KEY_LOG, entries.joinToString(SpeechTurn.TURN_SEPARATOR), ctx)
    }

    private fun increment(counter: String, ctx: BehaviorContext) =
        Command.IncrementCounter(target = SCENE_ENTITY_ID, field = counter, source = id, tick = ctx.tick)

    private fun setNum(key: String, value: Double, ctx: BehaviorContext) =
        Command.SetProperty(SCENE_ENTITY_ID, key, PropertyValue.Num(value), source = id, tick = ctx.tick)

    private fun setText(key: String, value: String, ctx: BehaviorContext) =
        Command.SetProperty(SCENE_ENTITY_ID, key, PropertyValue.Text(value), source = id, tick = ctx.tick)

    /** Where one turn stands, kept on the scene entity so it survives snapshots. */
    private data class TurnState(
        val current:    EntityId?,
        val phase:      String?,
        val since:      Long,
        val prompts:    Int,
        val trial:      Int,
        val guardUntil: Long
    ) {
        companion object {
            fun read(snapshot: WorldSnapshot): TurnState {
                val scene = snapshot.scene()
                return TurnState(
                    current    = scene?.text(KEY_CURRENT)?.takeIf { it.isNotBlank() },
                    phase      = scene?.text(KEY_PHASE),
                    since      = scene?.num(KEY_SINCE)?.toLong() ?: 0L,
                    prompts    = scene?.num(KEY_PROMPTS)?.toInt() ?: 0,
                    trial      = scene?.num(KEY_TRIAL)?.toInt() ?: 0,
                    guardUntil = scene?.num(KEY_GUARD)?.toLong() ?: 0L
                )
            }

            fun write(
                current: EntityId, phase: String, since: Long, prompts: Int, trial: Int,
                guardUntil: Long, ctx: BehaviorContext, source: BehaviorId
            ): List<Command> = listOf(
                KEY_CURRENT to PropertyValue.Text(current),
                KEY_PHASE   to PropertyValue.Text(phase),
                KEY_SINCE   to PropertyValue.Num(since.toDouble()),
                KEY_PROMPTS to PropertyValue.Num(prompts.toDouble()),
                KEY_TRIAL   to PropertyValue.Num(trial.toDouble()),
                KEY_GUARD   to PropertyValue.Num(guardUntil.toDouble())
            ).map { (key, value) ->
                Command.SetProperty(SCENE_ENTITY_ID, key, value, source = source, tick = ctx.tick)
            }
        }
    }

    companion object {
        val EVENTS: Set<KClass<out GameEvent>> = setOf(
            GameEvent.SceneStarted::class,
            GameEvent.ClockTick::class,
            GameEvent.Tap::class,
            GameEvent.Vocalization::class
        )

        const val KEY_CURRENT = "await.current"
        const val KEY_PHASE   = "await.phase"
        const val KEY_SINCE   = "await.since"
        const val KEY_PROMPTS = "await.prompts"
        const val KEY_TRIAL   = "await.trial"
        const val KEY_GUARD   = "await.guardUntil"
        const val KEY_LOG     = "await.turns"
        const val KEY_LEAD    = "await.leadMs"

        private const val MAX_LOGGED_TURNS = 60

        const val PHASE_LEAD   = "lead"
        const val PHASE_WAIT   = "wait"
        const val PHASE_REWARD = "reward"
        const val PHASE_DONE   = "done"

        /**
         * Praise on every turn for the first third of a drill, every other turn
         * in the second, every third turn after that.
         */
        fun praiseDue(trial: Int, total: Int): Boolean {
            if (total <= 0) return true
            val third = total / 3.0
            return when {
                trial < third     -> true
                trial < 2 * third -> trial % 2 == 0
                else              -> trial % 3 == 0
            }
        }
    }
}

class AwaitResponsePlugin : BehaviorPlugin {
    override val behaviorId = "awaitResponse"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = AwaitResponseBehavior.EVENTS

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior {
        val accept = acceptKinds(config)
        return AwaitResponseBehavior(
            id          = behaviorId,
            group       = config["group"] ?: "targets",
            firstLeadMs = config["firstLeadMs"]?.toDoubleOrNull()?.toLong() ?: 3000L,
            leadMs      = config["leadMs"]?.toDoubleOrNull()?.toLong() ?: 800L,
            waitMs      = config["waitMs"]?.toDoubleOrNull()?.toLong() ?: 6000L,
            rewardMs    = config["rewardMs"]?.toDoubleOrNull()?.toLong() ?: 1800L,
            maxPrompts  = config["maxPrompts"]?.toDoubleOrNull()?.toInt() ?: 2,
            trials      = config["trials"]?.toDoubleOrNull()?.toInt(),
            random      = config["order"] == "random",
            fading      = config["feedback"] == "fading",
            acceptVoice = "voice" in accept,
            acceptAdult = "adult" in accept,
            acceptTap   = "tap" in accept,
            guardMs     = config["guardMs"]?.toDoubleOrNull()?.toLong() ?: 1500L
        )
    }

    override fun validateConfig(config: Map<String, String>): ValidationResult {
        val errors = SPEC.check(config).errorList().toMutableList()
        config["accept"]?.let { raw ->
            val kinds = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (kinds.isEmpty()) errors += "awaitResponse config.accept is empty — nothing could ever answer"
            kinds.filterNot { it in ACCEPT_KINDS }.forEach {
                errors += "awaitResponse config.accept has '$it'; expected any of ${ACCEPT_KINDS.joinToString(", ")}"
            }
        }
        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    companion object {
        val ACCEPT_KINDS = listOf("voice", "adult", "tap")

        /** What may answer a turn; by default the child's voice or the adult's word. */
        fun acceptKinds(config: Map<String, String>): Set<String> =
            (config["accept"] ?: "voice,adult").split(',').map { it.trim() }.toSet()

        private val SPEC = ConfigSpec.of("awaitResponse") {
            text("group", "accept")
            number("firstLeadMs", min = 0.0, max = 20000.0)
            number("leadMs", min = 0.0, max = 20000.0)
            number("waitMs", min = 1000.0, max = 30000.0)
            number("rewardMs", min = 0.0, max = 10000.0)
            number("maxPrompts", min = 1.0, max = 5.0)
            number("trials", min = 1.0, max = 100.0)
            number("guardMs", min = 0.0, max = 5000.0)
            oneOf("order", "sequential", "random")
            oneOf("feedback", "every", "fading")
        }
    }
}
