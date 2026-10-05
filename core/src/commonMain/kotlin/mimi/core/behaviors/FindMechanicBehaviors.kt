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
    private val onEvent: KClass<out GameEvent>,
    /**
     * A property holding the sound to play, instead of naming one here.
     *
     * A scene declares its behaviors once, so a fixed sound is the same sound
     * in every round. When the sound *is* the question — a cat's voice the
     * child has to recognise — each round has to bring its own, and it travels
     * on the round's own entity.
     */
    private val soundFrom: String? = null
) : Behavior {
    override val phase      = Phase.FEEDBACK

    // The opening instruction also answers "say it again". Other prompts —
    // praise after a match, a line at the end — are not instructions and are
    // not repeated.
    override val eventTypes: Set<KClass<out GameEvent>> =
        if (onEvent == GameEvent.SceneStarted::class) setOf(onEvent, GameEvent.InstructionRequested::class)
        else setOf(onEvent)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        val fromProperty = soundFrom?.let { key ->
            ctx.snapshot.getAllEntities().firstNotNullOfOrNull { entity ->
                (entity.properties[key] as? PropertyValue.Text)?.value?.takeIf { it.isNotBlank() }
            }
        }
        // A recording beats synthesis whenever there is one. These lines are the
        // first thing a child hears in every game, and a system voice reading
        // them is the one part of the app that sounds like a machine — it also
        // pronounces the same sentence differently on every platform, and on
        // some it is missing entirely and the scene opens in silence.
        val path = fromProperty ?: sound.takeIf { it.isNotBlank() }
        if (path != null) {
            return listOf(Command.TriggerFeedback(
                feedbackType = "sound",
                params       = mapOf("path" to path),
                source       = id,
                tick         = ctx.tick
            ))
        }
        // `text` stays on the scene as the fallback for a line nobody has
        // recorded yet, so a new game still says something while it is written.
        if (ttsText != null) {
            return listOf(Command.TriggerFeedback(
                feedbackType = "tts",
                params       = mapOf("text" to ttsText),
                source       = id,
                tick         = ctx.tick
            ))
        }
        return emptyList()
    }
}

class AudioPromptPlugin : BehaviorPlugin {
    override val behaviorId = "audioPrompt"
    override val phase      = Phase.FEEDBACK
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.SceneStarted::class,
        GameEvent.InstructionRequested::class,
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
        return AudioPromptBehavior(
            id        = behaviorId,
            sound     = sound,
            ttsText   = text,
            onEvent   = onEvent,
            soundFrom = config["soundFrom"]
        )
    }

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("audioPrompt") {
            oneOf("on", "sceneStarted", "sceneCompleted", "matchSuccess", "matchFail")
            sound("sound")
            text("text")
            text("soundFrom")
        }
    }
}

// ── propertySelectionValidator ────────────────────────────────────────────────
// On Tap: if the tapped entity's property matches expected → TENTATIVE + success.
// Otherwise → matchFail feedback.

class PropertySelectionValidatorBehavior(
    override val id:       BehaviorId,
    private val property:  String,
    private val expected:  String,
    /**
     * A property to read the expected answer from, instead of fixing it here.
     *
     * Behaviors are declared once for the whole scene, so a fixed answer means
     * every round of a multi-round scene has the same one — and a child soon
     * learns to press the same tile without looking. Naming a property lets each
     * round carry its own answer on the thing being asked about.
     */
    private val expectedFrom: String? = null
) : Behavior {
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.Tap) return emptyList()
        val tappedId = event.source ?: return emptyList()
        val tapped   = ctx.snapshot.getEntity(tappedId) ?: return emptyList()
        if (tapped.state != EntityLifecycleState.ACTIVE) return emptyList()

        // A tile that does not carry the property is not one of the answers —
        // a "listen again" button, say. Judging it would count a mistake
        // against a child who did exactly what the button is for.
        val given = tapped.text(property) ?: return emptyList()

        val want = expectedFrom
            ?.let { key -> ctx.snapshot.getAllEntities().firstNotNullOfOrNull { it.text(key) } }
            ?: expected

        return if (given == want) {
            listOf(
                Command.SetEntityState(
                    target = tappedId,
                    state  = EntityLifecycleState.TENTATIVE,
                    source = id,
                    tick   = ctx.tick
                ),
                Command.TriggerFeedback(
                    feedbackType = "matchSuccess",
                    params       = mapOf("source" to tappedId),
                    source       = id,
                    tick         = ctx.tick
                )
            )
        } else {
            listOf(
                Command.TriggerFeedback(
                    feedbackType = "matchFail",
                    params       = mapOf("source" to tappedId, "reason" to "wrong_choice"),
                    source       = id,
                    tick         = ctx.tick
                )
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
            id           = behaviorId,
            property     = config["property"] ?: "name",
            expected     = config["expected"] ?: "",
            expectedFrom = config["expectedFrom"]
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("propertySelectionValidator") {
            text("property", "expected", "expectedFrom")
        }
    }
}
