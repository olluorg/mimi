package org.ollu.mini.play

import org.ollu.mini.catalog.CatalogEntry

/**
 * How the last game went, from the child's side.
 *
 * Difficulty follows the child within a single sitting, not just across weeks:
 * a toddler who has just failed twice needs the next game to be easier *now*,
 * and a long-run skill profile cannot react that fast.
 */
enum class Momentum {
    /** Needed a lot of help. Step back to something they can win. */
    STRUGGLING,

    /** Somewhere in between. Hold the level. */
    STEADY,

    /** Solved it unaided. They can take more. */
    FLYING
}

/**
 * The family a scene belongs to: sorting, choosing, memory and so on.
 *
 * Taken from the scene's own tags, using the first one the catalog recognises,
 * so content can introduce a new kind of game without touching this code.
 */
val CatalogEntry.kind: String?
    get() = tags.firstOrNull { it in KNOWN_KINDS }

private val KNOWN_KINDS = setOf(
    "sorting", "matching", "memory", "sequencing", "routine", "trace",
    "choice", "cause_effect", "vocabulary", "listening", "emotions",
    "symbols", "maze", "counting", "attention"
)

/** What the child did in the game that just ended. */
data class PlayOutcome(
    val sceneId:   String,
    val mistakes:  Int,
    val hintsUsed: Int,
    val skipped:   Boolean = false,
    /**
     * Turns of a speech game answered and left unanswered. A speech game never
     * scores a mistake, so without these a child who stayed silent through
     * every turn would read as "solved it unaided" and be handed harder words.
     */
    val responses:  Int = 0,
    val unanswered: Int = 0
) {
    val momentum: Momentum
        get() = when {
            skipped                  -> Momentum.STRUGGLING
            unanswered * 2 > responses + unanswered -> Momentum.STRUGGLING
            unanswered > 0                          -> Momentum.STEADY
            mistakes + hintsUsed >= STRUGGLE_HELP -> Momentum.STRUGGLING
            mistakes + hintsUsed == 0             -> Momentum.FLYING
            else                                  -> Momentum.STEADY
        }

    private companion object {
        /** Three nudges in one short game is enough to say it was too hard. */
        const val STRUGGLE_HELP = 3
    }
}

/**
 * Picks what a child plays next when nobody is choosing for them.
 *
 * Deliberately simple and total: it always returns something as long as any
 * scene is playable, because a child left alone with an empty screen has no way
 * to ask what went wrong.
 */
object PlaySessionPlanner {

    /** Games in one sitting, sized for a short attention span. */
    const val SESSION_LENGTH = 5

    /**
     * @param catalog      everything available to play
     * @param playedNow    scene ids already played in this sitting, in order
     * @param completedIds scenes ever finished, for prerequisite gating
     * @param last         how the previous game in this sitting went, if any
     * @param speechFocus  the child's speech targets; when set, every other game
     *                     is a speech game suited to them
     */
    fun nextScene(
        catalog:      List<CatalogEntry>,
        playedNow:    List<String>,
        completedIds: Set<String>,
        last:         PlayOutcome?,
        speechFocus:  List<String> = emptyList()
    ): CatalogEntry? {
        if (catalog.isEmpty()) return null

        val allUnlocked = catalog.filter { entry ->
            entry.requires.all { it in completedIds }
        }.ifEmpty { catalog.filter { it.requires.isEmpty() } }

        if (allUnlocked.isEmpty()) return null

        // Speech practice works in short, frequent doses, and a child tires of
        // it fast. So with a focus set, speech games take the first, third and
        // fifth turns of a sitting and ordinary play fills the gaps — rather
        // than a block of speech that ends in refusal.
        val unlocked = speechTurnPool(allUnlocked, playedNow, speechFocus) ?: allUnlocked

        val target = targetLevel(unlocked, allUnlocked, playedNow, last)
        val fresh  = unlocked.filterNot { it.id in playedNow }

        // Five sorting games in a row is the same game five times as far as a
        // small child is concerned, so a kind already played this sitting is
        // ranked below one that has not been — most heavily for the game that
        // came immediately before.
        val previousKind = last?.sceneId?.let { id -> allUnlocked.firstOrNull { it.id == id }?.kind }
        val kindsPlayed  = playedNow.mapNotNull { id -> allUnlocked.firstOrNull { it.id == id }?.kind }

        fun monotony(entry: CatalogEntry): Int = when {
            entry.kind != null && entry.kind == previousKind -> 2
            entry.kind != null && entry.kind in kindsPlayed  -> 1
            else                                             -> 0
        }

        // Difficulty and sameness are weighed together rather than in sequence:
        // stepping one level off target is a fair price for a game that feels
        // different, and at the gentlest levels — where most scenes are the same
        // kind — sorting by level first would leave no room to vary at all.
        fun cost(entry: CatalogEntry) = kotlin.math.abs(entry.ageMin - target) + monotony(entry)

        fun pick(pool: List<CatalogEntry>): CatalogEntry? = pool.minWithOrNull(
            compareBy<CatalogEntry> { cost(it) }
                .thenBy { kotlin.math.abs(it.ageMin - target) }
                .thenBy { if (it.id in completedIds) 1 else 0 }
                .thenBy { it.id }
        )

        if (last?.momentum != Momentum.STRUGGLING) {
            return pick(fresh) ?: pick(unlocked)
        }

        // A struggling child must never be handed something harder. Once the
        // gentle games are used up, replaying one they have already beaten today
        // is the kinder option — the alternative is to escalate on a child who is
        // already failing, which is how a session ends in tears.
        val atOrBelow = { pool: List<CatalogEntry> -> pool.filter { it.ageMin <= target } }
        return pick(atOrBelow(fresh))
            ?: pick(atOrBelow(unlocked))
            ?: pick(fresh)
            ?: pick(unlocked)
    }

    private fun speechTurnPool(
        unlocked:    List<CatalogEntry>,
        playedNow:   List<String>,
        speechFocus: List<String>
    ): List<CatalogEntry>? {
        if (speechFocus.isEmpty()) return null
        val suited = unlocked.filter { entry ->
            entry.speech?.targets?.any { it in speechFocus } == true
        }
        if (suited.isEmpty()) return null
        val speechPlayed = playedNow.count { id -> unlocked.any { it.id == id && it.isSpeechGame } }
        val speechTurn = speechPlayed * 2 <= playedNow.size
        return if (speechTurn) suited
        else unlocked.filterNot { it.isSpeechGame }.ifEmpty { null }
    }

    /**
     * The difficulty band to aim at, expressed in the `ageMin` the catalog
     * already carries. The first game of a sitting starts at the gentlest level
     * available — a child arriving cold should win before being stretched.
     */
    private fun targetLevel(
        catalog:   List<CatalogEntry>,
        /** Everything playable, to find the last game even when this turn's pool excludes it. */
        everything: List<CatalogEntry>,
        playedNow: List<String>,
        last:      PlayOutcome?
    ): Int {
        val levels = catalog.map { it.ageMin }
        val floor  = levels.minOrNull() ?: 2
        val ceil   = levels.maxOrNull() ?: floor

        if (last == null || playedNow.isEmpty()) return floor

        val current = everything.firstOrNull { it.id == last.sceneId }?.ageMin ?: floor
        val shifted = when (last.momentum) {
            Momentum.STRUGGLING -> current - 1
            Momentum.STEADY     -> current
            Momentum.FLYING     -> current + 1
        }
        return shifted.coerceIn(floor, ceil)
    }
}
