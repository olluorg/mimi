package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

// ── audioPrompt ───────────────────────────────────────────────────────────────
// Plays a sound instruction when the scene starts (e.g. "find the cat").

class AudioPromptBehavior(
    override val id: BehaviorId,
    private val sound: String,
    private val ttsText: String?,
    private val onEvent: KClass<out GameEvent>
) : Behavior {
    override val phase      = Phase.FEEDBACK
    override val eventTypes = setOf(onEvent)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> =
        if (ttsText != null) {
            listOf(Command.TriggerFeedback(
                feedbackType = "tts",
                params       = mapOf("text" to ttsText),
                source       = id,
                tick         = ctx.tick
            ))
        } else {
            listOf(Command.TriggerFeedback(
                feedbackType = "sound",
                params       = mapOf("path" to sound),
                source       = id,
                tick         = ctx.tick
            ))
        }
}

class AudioPromptPlugin : BehaviorPlugin {
    override val behaviorId = "audioPrompt"
    override val phase      = Phase.FEEDBACK
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.SceneStarted::class,
        GameEvent.SceneCompleted::class,
        GameEvent.MatchSuccess::class,
        GameEvent.MatchFail::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior {
        val sound   = config["sound"] ?: ""
        val text    = config["text"]
        val onEvent = when (config["on"]) {
            "sceneCompleted" -> GameEvent.SceneCompleted::class
            "matchSuccess"   -> GameEvent.MatchSuccess::class
            "matchFail"      -> GameEvent.MatchFail::class
            else             -> GameEvent.SceneStarted::class  // default: sceneStarted
        }
        return AudioPromptBehavior(id = behaviorId, sound = sound, ttsText = text, onEvent = onEvent)
    }
}

// ── propertySelectionValidator ────────────────────────────────────────────────
// On Tap: if the tapped entity's property matches expected → TENTATIVE + success.
// Otherwise → matchFail feedback.

class PropertySelectionValidatorBehavior(
    override val id:       BehaviorId,
    private val property:  String,
    private val expected:  String
) : Behavior {
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.Tap) return emptyList()
        val tappedId = event.source ?: return emptyList()
        val tapped   = ctx.snapshot.getEntity(tappedId) ?: return emptyList()
        if (tapped.state != EntityLifecycleState.ACTIVE) return emptyList()

        return if (tapped.text(property) == expected) {
            listOf(
                Command.SetEntityState(
                    target = tappedId,
                    state  = EntityLifecycleState.TENTATIVE,
                    source = id,
                    tick   = ctx.tick
                ),
                Command.TriggerFeedback(feedbackType = "matchSuccess", source = id, tick = ctx.tick)
            )
        } else {
            listOf(
                Command.TriggerFeedback(feedbackType = "matchFail", source = id, tick = ctx.tick)
            )
        }
    }
}

class PropertySelectionValidatorPlugin : BehaviorPlugin {
    override val behaviorId = "propertySelectionValidator"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        PropertySelectionValidatorBehavior(
            id       = behaviorId,
            property = config["property"] ?: "name",
            expected = config["expected"] ?: ""
        )
}
