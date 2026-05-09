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
}
