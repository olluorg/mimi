package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

class PlaySoundOnEventBehavior(
    override val id:    BehaviorId,
    private val sound:  String,
    private val onEvent: KClass<out GameEvent>
) : Behavior {

    override val phase      = Phase.FEEDBACK
    override val eventTypes = setOf(onEvent)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> =
        listOf(
            Command.TriggerFeedback(
                feedbackType = "sound",
                params       = mapOf("path" to sound),
                source       = id,
                tick         = ctx.tick
            )
        )
}

class PlaySoundOnEventPlugin : BehaviorPlugin {
    override val behaviorId = "playSoundOnEvent"
    override val phase      = Phase.FEEDBACK
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.MatchSuccess::class,
        GameEvent.MatchFail::class,
        GameEvent.SceneCompleted::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior {
        val sound   = config["sound"] ?: ""
        val onEvent = when (config["on"]) {
            "matchFail"      -> GameEvent.MatchFail::class
            "sceneCompleted" -> GameEvent.SceneCompleted::class
            else             -> GameEvent.MatchSuccess::class
        }
        return PlaySoundOnEventBehavior(id = behaviorId, sound = sound, onEvent = onEvent)
    }

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("playSoundOnEvent") {
            oneOf("on", "matchSuccess", "matchFail", "sceneCompleted")
            // A bare name the sound channel does not know plays silence, so the
            // built-in names are a closed set; anything containing "/" is a file.
            sound("sound")
        }
    }
}
