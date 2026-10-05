package mimi.core.world

import kotlinx.serialization.Serializable
import mimi.core.model.EntityId
import kotlin.random.Random

/**
 * How a scene differs from one play to the next.
 *
 * A scene that is identical every time teaches the wrong thing: a child
 * replaying it learns that the answer is the tile on the left, not what the
 * tiles mean. It also goes stale within days, which matters most for the child
 * playing alone with no adult to suggest something new.
 *
 * Variation never changes what the scene is about — only where things sit and,
 * where a scene offers a pool, which of them turn up.
 */
@Serializable
data class SceneVariation(
    /** Groups whose members swap places with one another. */
    val shufflePositions: List<String> = emptyList(),
    /** Groups to take only a few members from, drawn at random. */
    val pick: List<PickRule> = emptyList(),
    /** Groups from which one member is drawn to be this play's answer. */
    val choose: List<ChooseRule> = emptyList()
)

/**
 * Draws one member of [group] to be the answer this time.
 *
 * "Нажми на букву А" asked for А on every play, so a child learned where А
 * was rather than what it looks like — and never met any other letter. The
 * chosen member gets the properties named in [copy] (its own value under a new
 * key), which is how the rest of the scene finds out what was chosen: the
 * validator's `expectedFrom`, the objective's `valueFrom`, the prompt's
 * `soundFrom` all read the copied key, and only the chosen member carries it.
 */
@Serializable
data class ChooseRule(
    val group:  String,
    /** Property of the chosen member → the key it is copied to. */
    val copy:   Map<String, String>,
    val tagKey: String = "group"
)

/**
 * Takes [count] members of [group], always including [keep].
 *
 * [keep] exists because a scene usually has one entity it cannot do without —
 * the animal the prompt names, the shape the objective waits for. Dropping it
 * would leave a game that can never be finished.
 */
@Serializable
data class PickRule(
    val group: String,
    val count: Int,
    val keep:  List<EntityId> = emptyList(),
    /**
     * Which tag names the pool. Defaults to the same `group` the objectives use,
     * but a scene can draw on a finer tag: a sorting game needs a few of each
     * kind, not four cards that all belong in the same basket, while its
     * objective still watches the whole `items` group.
     */
    val tagKey: String = "group"
)

object SceneVariations {

    /**
     * Applies [variation] to [entities] using [seed].
     *
     * Deterministic by contract: the same scene and seed always produce the same
     * arrangement, so a run can be reproduced exactly. Freshness comes from the
     * session choosing a new seed, not from the engine being unpredictable.
     */
    fun apply(
        entities:  List<EntityDefinition>,
        variation: SceneVariation?,
        seed:      Long
    ): List<EntityDefinition> {
        if (variation == null) return entities
        val random = Random(seed)
        var result = entities
        // Picking first, so positions are shuffled across what actually remains.
        for (rule in variation.pick) {
            result = applyPick(result, rule, random)
        }
        // Choosing after picking, so the answer is always one of the tiles shown.
        for (rule in variation.choose) {
            result = applyChoose(result, rule, random)
        }
        for (group in variation.shufflePositions) {
            result = shufflePositions(result, group, random)
        }
        return result
    }

    /**
     * Swaps the positions of a group's members among themselves. Every entity
     * keeps its own identity and properties; only the slots are permuted, so an
     * objective that waits on a particular entity is unaffected.
     */
    private fun shufflePositions(
        entities: List<EntityDefinition>,
        group:    String,
        random:   Random
    ): List<EntityDefinition> {
        val members = entities.withIndex().filter { (_, e) -> e.tags["group"] == group }
        if (members.size < 2) return entities

        val slots = members.mapNotNull { (_, e) -> e.properties["position"] }
        if (slots.size != members.size) return entities   // a member without a slot: leave it alone

        val shuffled = slots.shuffled(random)
        val byIndex  = members.map { it.index }.zip(shuffled).toMap()

        return entities.mapIndexed { index, entity ->
            val newPosition = byIndex[index] ?: return@mapIndexed entity
            entity.copy(properties = entity.properties + ("position" to newPosition))
        }
    }

    /**
     * Keeps only some of a group. Entities named in [PickRule.keep] always stay;
     * the rest of the quota is drawn at random, and the survivors take over the
     * positions of the group so the layout has no gaps where a dropped tile was.
     */
    private fun applyPick(
        entities: List<EntityDefinition>,
        rule:     PickRule,
        random:   Random
    ): List<EntityDefinition> {
        val members = entities.filter { it.tags[rule.tagKey] == rule.group }
        if (members.isEmpty() || rule.count >= members.size) return entities
        if (rule.count <= 0) return entities

        val required = members.filter { it.id in rule.keep }
        val optional = members.filterNot { it.id in rule.keep }.shuffled(random)
        val chosen   = (required + optional.take((rule.count - required.size).coerceAtLeast(0)))
            .take(rule.count.coerceAtLeast(required.size))
            .map { it.id }
            .toSet()

        val slots = members.mapNotNull { it.properties["position"] }
        var slotIndex = 0

        return entities.mapNotNull { entity ->
            if (entity.tags[rule.tagKey] != rule.group) return@mapNotNull entity
            if (entity.id !in chosen) return@mapNotNull null
            val slot = slots.getOrNull(slotIndex++) ?: return@mapNotNull entity
            entity.copy(properties = entity.properties + ("position" to slot))
        }
    }

    private fun applyChoose(
        entities: List<EntityDefinition>,
        rule:     ChooseRule,
        random:   Random
    ): List<EntityDefinition> {
        val members = entities.filter { it.tags[rule.tagKey] == rule.group }
        if (members.isEmpty()) return entities
        val chosen = members[random.nextInt(members.size)].id
        return entities.map { entity ->
            if (entity.id != chosen) return@map entity
            val copied = rule.copy.mapNotNull { (from, to) -> entity.properties[from]?.let { to to it } }
            entity.copy(properties = entity.properties + copied)
        }
    }
}
