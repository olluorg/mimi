package mimi.core.world

import kotlinx.serialization.Serializable

/**
 * The part of a scene's `meta` block the engine itself needs to read.
 *
 * Most of `meta` — title, age, icon — is for the catalog screen and never
 * reaches the engine. The speech profile does: it is a claim about what the
 * scene does ("this game gets a child to use their voice"), and a claim the
 * validator can check against the behaviors that are actually wired in.
 */
@Serializable
data class SceneAnnotations(
    val speech: SpeechProfile? = null
)

/**
 * Marks a scene as a speech game and says what kind.
 *
 * Values are plain strings rather than enums on purpose: the catalog is
 * delivered over the air, and an older app meeting a stage it does not know
 * yet must still open the scene. The validator holds them to [SpeechVocabulary].
 */
@Serializable
data class SpeechProfile(
    /** Where on the way to talking the game sits — see [SpeechVocabulary.STAGES]. */
    val stage:    String       = "",
    /** Who it is especially for — alalia, apraxia, dysarthria. */
    val targets:  List<String> = emptyList(),
    /** How it gets the child to communicate — see [SpeechVocabulary.MODES]. */
    val modes:    List<String> = emptyList(),
    /** The words or sounds practised, for the adult choosing a game. */
    val words:    List<String> = emptyList(),
    /**
     * What the adult should do while the child plays.
     *
     * Parent-delivered practice is where most of the effect in the research
     * comes from; a speech game played alone is a much weaker thing. So every
     * speech scene tells the adult their part, in one sentence.
     */
    val adultTip: String       = ""
)

/** The closed vocabulary of [SpeechProfile], with the labels an adult sees. */
object SpeechVocabulary {

    /** Ordered from the first step to the last. */
    val STAGES: Map<String, String> = linkedMapOf(
        "aac"       to "Картинки вместо слов",
        "vocal"     to "Голос",
        "imitation" to "Звукоподражание",
        "words"     to "Слова"
    )

    val TARGETS: Map<String, String> = linkedMapOf(
        "alalia"     to "Алалия",
        "apraxia"    to "Диспраксия",
        "dysarthria" to "Дизартрия"
    )

    val MODES: Map<String, String> = linkedMapOf(
        "aac"       to "Просьба картинкой",
        "voice"     to "Управление голосом",
        "cloze"     to "Договори",
        "imitation" to "Повтори за мной",
        "drill"     to "Тренировка слогов"
    )

    /** A drill shorter than this is not enough repetitions to learn a movement. */
    const val MIN_DRILL_TRIALS = 10
}
