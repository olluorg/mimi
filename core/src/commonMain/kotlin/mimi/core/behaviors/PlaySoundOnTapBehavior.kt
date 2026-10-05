package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

// ── playSoundOnTap ────────────────────────────────────────────────────────────
// Presses a tile and hears what it holds. Nothing is scored and nothing moves.

/**
 * A tile the child can press to hear something again.
 *
 * When the question is a sound — an animal's voice, a word — a child who looks
 * away has no way back to it. An adult would say "listen again"; a child
 * playing alone needs a button, and one that costs nothing: pressing it is
 * neither an answer nor a mistake, so it can be pressed as often as they like.
 */
class PlaySoundOnTapBehavior(
    override val id: BehaviorId,
    private val group: String?,
    private val property: String
) : Behavior {
    override val phase      = Phase.FEEDBACK
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.Tap) return emptyList()
        val tappedId = event.source ?: return emptyList()
        val tapped   = ctx.snapshot.getEntity(tappedId) ?: return emptyList()
        if (tapped.state != EntityLifecycleState.ACTIVE) return emptyList()
        if (group != null && tapped.tags["group"] != group) return emptyList()

        // The sound may live on the pressed tile, or — when the tile is a
        // button rather than the thing being heard — anywhere in the round.
        val path = (tapped.properties[property] as? PropertyValue.Text)?.value
            ?: ctx.snapshot.getAllEntities().firstNotNullOfOrNull {
                (it.properties[property] as? PropertyValue.Text)?.value
            }
            ?: return emptyList()

        return listOf(Command.TriggerFeedback(
            feedbackType = "sound",
            params       = mapOf("path" to path),
            source       = id,
            tick         = ctx.tick
        ))
    }
}

class PlaySoundOnTapPlugin : BehaviorPlugin {
    override val behaviorId = "playSoundOnTap"
    override val phase      = Phase.FEEDBACK
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        PlaySoundOnTapBehavior(
            id       = behaviorId,
            group    = config["group"],
            property = config["property"] ?: "sound"
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("playSoundOnTap") {
            text("group")
            text("property")
        }
    }
}
