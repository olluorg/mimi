package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

private const val CARD_STATE_FIELD = "cardState"
private const val CARD_HIDDEN = "hidden"
private const val CARD_REVEALED = "revealed"
private const val CARD_MATCHED = "matched"

class MemoryFlipControllerBehavior(
    override val id: BehaviorId,
    private val group: String,
    private val maxOpenCards: Int,
    /** Cards form a pair when this property matches. */
    private val matchProperty: String
) : Behavior {
    override val phase = Phase.COMMAND_GENERATION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.Tap) return emptyList()
        val tappedId = event.source ?: event.target ?: return emptyList()
        val tapped = ctx.snapshot.getEntity(tappedId) ?: return emptyList()
        if (!tapped.hasTag("group", group)) return emptyList()
        if (tapped.state != EntityLifecycleState.ACTIVE) return emptyList()
        if (tapped.text(CARD_STATE_FIELD) == CARD_MATCHED) return emptyList()

        val cards = ctx.snapshot.getEntitiesByTag("group", group)
        val revealed = cards.filter {
            it.state == EntityLifecycleState.ACTIVE && it.text(CARD_STATE_FIELD) == CARD_REVEALED
        }

        if (revealed.size >= maxOpenCards) {
            val reset = revealed
                .filter { it.id != tappedId }
                .flatMap { card ->
                    listOf(Command.SetProperty(
                        target = card.id,
                        field = CARD_STATE_FIELD,
                        value = PropertyValue.Text(CARD_HIDDEN),
                        priority = 100,
                        source = id,
                        tick = ctx.tick
                    ))
                }
            val revealTapped = if (tapped.text(CARD_STATE_FIELD) == CARD_HIDDEN) {
                listOf(reveal(tapped.id, ctx.tick))
            } else {
                emptyList()
            }
            return reset + revealTapped
        }

        if (tapped.text(CARD_STATE_FIELD) == CARD_REVEALED) return emptyList()

        val openCards = revealed + tapped
        if (openCards.size < maxOpenCards) {
            return listOf(reveal(tapped.id, ctx.tick))
        }

        val first = openCards.first()
        val second = openCards.last()
        val matched = first.text(matchProperty) != null &&
            first.text(matchProperty) == second.text(matchProperty)

        return if (matched) {
            listOf(
                match(first.id, ctx.tick),
                match(second.id, ctx.tick),
                Command.SetEntityState(first.id, EntityLifecycleState.TENTATIVE, priority = 100, source = id, tick = ctx.tick),
                Command.SetEntityState(second.id, EntityLifecycleState.TENTATIVE, priority = 100, source = id, tick = ctx.tick),
                Command.TriggerFeedback(
                    feedbackType = "matchSuccess",
                    params = mapOf("source" to first.id, "target" to second.id),
                    source = id,
                    tick = ctx.tick
                )
            )
        } else {
            listOf(
                reveal(tapped.id, ctx.tick),
                Command.TriggerFeedback(
                    feedbackType = "matchFail",
                    params = mapOf("source" to first.id, "target" to second.id),
                    source = id,
                    tick = ctx.tick
                )
            )
        }
    }

    private fun reveal(target: EntityId, tick: Tick) =
        Command.SetProperty(
            target = target,
            field = CARD_STATE_FIELD,
            value = PropertyValue.Text(CARD_REVEALED),
            priority = 100,
            source = id,
            tick = tick
        )

    private fun match(target: EntityId, tick: Tick) =
        Command.SetProperty(
            target = target,
            field = CARD_STATE_FIELD,
            value = PropertyValue.Text(CARD_MATCHED),
            priority = 100,
            source = id,
            tick = tick
        )
}

class MemoryFlipControllerPlugin : BehaviorPlugin {
    override val behaviorId = "memoryFlipController"
    override val phase = Phase.COMMAND_GENERATION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        MemoryFlipControllerBehavior(
            id = behaviorId,
            group = config["group"] ?: "cards",
            maxOpenCards = config["maxOpenCards"]?.toIntOrNull()?.coerceAtLeast(2) ?: 2,
            matchProperty = config["matchProperty"] ?: "pairId"
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("memoryFlipController") {
            text("group", "matchProperty")
            number("maxOpenCards", min = 2.0)
        }
    }
}

class PairMatchValidatorBehavior(override val id: BehaviorId) : Behavior {
    override val phase = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> = emptyList()
}

class PairMatchValidatorPlugin : BehaviorPlugin {
    override val behaviorId = "pairMatchValidator"
    override val phase = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        PairMatchValidatorBehavior(behaviorId)

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("pairMatchValidator") {
            text("group", "matchProperty")
        }
    }
}
