package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

/**
 * Selection where there is no wrong answer.
 *
 * Every other validator in the engine asks "did the child pick the right one?".
 * The first two rungs of communication have no right one:
 *
 *  - cause and effect — touching anything makes something happen, which is how a
 *    child learns their actions change the world at all;
 *  - choosing — offered juice or water, whichever they touch *is* the answer.
 *    The act of choosing is the skill being taught, and marking a choice wrong
 *    teaches the opposite of what the game is for.
 *
 * So this behavior never emits matchFail. It records what was chosen on the
 * scene entity so an adult can see it afterwards.
 */
class FreeSelectionBehavior(
    override val id:       BehaviorId,
    private val group:        String?,
    private val repeatable:   Boolean,
    private val hideOnSelect: Boolean,
    private val speak:        Boolean
) : Behavior {

    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.Tap) return emptyList()
        val tappedId = event.source ?: return emptyList()
        val tapped   = ctx.snapshot.getEntity(tappedId) ?: return emptyList()

        if (group != null && tapped.tag("group") != group) return emptyList()
        if (!repeatable && tapped.state != EntityLifecycleState.ACTIVE) return emptyList()

        val commands = mutableListOf<Command>()

        // Cause and effect needs a consequence the child can see. Hiding the
        // target makes the bubble actually pop; a plain colour change is too
        // subtle to read as "I did that". A repeatable target stays live so it
        // can be played with again; otherwise it settles like any solved piece.
        val nextState = when {
            hideOnSelect -> EntityLifecycleState.INACTIVE
            repeatable   -> null
            else         -> EntityLifecycleState.TENTATIVE
        }
        if (nextState != null) {
            commands += Command.SetEntityState(
                target = tappedId,
                state  = nextState,
                source = id,
                tick   = ctx.tick
            )
        }

        // What the child picked. In a choice scene this is the whole point of
        // the exercise — the adult wants to know it was juice, not water.
        commands += Command.SetProperty(
            target = SCENE_ENTITY_ID,
            field  = "lastChoice",
            value  = PropertyValue.Text(tapped.text("name") ?: tappedId),
            source = id,
            tick   = ctx.tick
        )
        commands += Command.IncrementCounter(
            target = SCENE_ENTITY_ID,
            field  = "selections",
            delta  = 1L,
            source = id,
            tick   = ctx.tick
        )
        // Say what was touched. On a board of needs this is the child speaking:
        // they press the cup and the device says "пить" out loud, which is the
        // entire function of an AAC board and something no other behavior does —
        // audioPrompt can only speak a line fixed when the scene was written.
        //
        // A recorded voice is preferred over synthesis wherever the card has one:
        // it is clearer, always pronounces the word the same way, and does not
        // depend on a speech engine being installed.
        if (speak) {
            val recording = tapped.text("sound")
            val spoken    = tapped.text("speech") ?: tapped.text("name") ?: tapped.text("label")
            when {
                !recording.isNullOrBlank() -> commands += Command.TriggerFeedback(
                    feedbackType = "sound",
                    params       = mapOf("path" to recording),
                    source       = id,
                    tick         = ctx.tick
                )
                !spoken.isNullOrBlank() -> commands += Command.TriggerFeedback(
                    feedbackType = "tts",
                    params       = mapOf("text" to spoken),
                    source       = id,
                    tick         = ctx.tick
                )
            }
        }

        commands += Command.TriggerFeedback(
            feedbackType = "matchSuccess",
            params       = mapOf("source" to tappedId),
            source       = id,
            tick         = ctx.tick
        )
        return commands
    }
}

class FreeSelectionPlugin : BehaviorPlugin {
    override val behaviorId = "freeSelection"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        FreeSelectionBehavior(
            id         = behaviorId,
            group        = config["group"],
            repeatable   = config["repeatable"]?.toBooleanStrictOrNull() ?: false,
            hideOnSelect = config["hideOnSelect"]?.toBooleanStrictOrNull() ?: false,
            speak        = config["speak"]?.toBooleanStrictOrNull() ?: false
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("freeSelection") {
            text("group")
            flag("repeatable")
            flag("hideOnSelect")
            flag("speak")
        }
    }
}
