package mimi.core.engine

/**
 * How a single play-through went. Feeds the skill mapper, which scores a scene
 * on how much help the child needed rather than on completion alone.
 */
data class SessionMetrics(
    val mistakes:        Int  = 0,
    val hintsUsed:       Int  = 0,
    val matches:         Int  = 0,
    val durationMs:      Long = 0L,
    val levelsCompleted: Int  = 0,
    /**
     * What the child answered, in a scene where answering was the point.
     *
     * A list, because a scene may ask several times: "do you want an apple?",
     * then a biscuit, then a ball. A choice game produces no score — these
     * answers are its entire result, and the reason an adult opens the progress
     * screen after one.
     */
    val choices:         List<String> = emptyList(),
    /**
     * Times the child made a sound of their own that the microphone heard.
     *
     * In a speech game this is the result. Mistakes and hints say little about
     * a child who is not yet talking; whether they try to more often than last
     * week says almost everything.
     */
    val vocalAttempts:   Int  = 0,
    /** Attempts an adult vouched for with the "сказал(а)" button. */
    val adultConfirmed:  Int  = 0,
    /**
     * Turns that ended without any answer, after the word had been modelled
     * again. Not a mistake — the game moves on regardless — but a rising share
     * of these says the words are too hard or the child is tired.
     */
    val unanswered:      Int  = 0,
    /** Turns of a speech game the child answered — by voice, picture or the adult's word. */
    val responses:       Int  = 0,
    /** Answers where the vowel heard was exactly the one modelled. */
    val exactResponses:  Int  = 0,
    /**
     * Every turn of a speech game: which word, and how it went. A total says
     * the child tried eleven times; this says which words they answer and
     * which they never do, which is what changes what to practise next.
     */
    val turns:           List<SpeechTurn> = emptyList()
) {
    /** The last thing answered, for a scene that only asks once. */
    val choice: String? get() = choices.lastOrNull()
}

/** One turn of a speech game: the word modelled and what came back. */
data class SpeechTurn(
    val word:    String,
    /** exact, voice, adult, tap — or none, when the turn passed in silence. */
    val outcome: String
) {
    val answered: Boolean get() = outcome != OUTCOME_NONE
    val exact:    Boolean get() = outcome == OUTCOME_EXACT

    /** "word|outcome" — the form a turn takes in the turn log and in a saved session. */
    fun encode(): String = "${sanitize(word)}$FIELD_SEPARATOR$outcome"

    companion object {
        const val OUTCOME_NONE  = "none"
        const val OUTCOME_EXACT = "exact"

        /** Separators for the turn log: a word cannot contain either. */
        const val TURN_SEPARATOR  = ";"
        const val FIELD_SEPARATOR = "|"

        fun sanitize(word: String): String =
            word.replace(TURN_SEPARATOR, " ").replace(FIELD_SEPARATOR, " ")

        /** Reverses [encode]; null for anything malformed. */
        fun decode(entry: String): SpeechTurn? {
            val parts = entry.split(FIELD_SEPARATOR)
            return if (parts.size != 2 || parts[0].isBlank()) null else SpeechTurn(parts[0], parts[1])
        }

        /** A whole turn log, as kept on the scene entity. */
        fun decodeLog(raw: String): List<SpeechTurn> =
            if (raw.isBlank()) emptyList() else raw.split(TURN_SEPARATOR).mapNotNull(::decode)
    }
}

/** Counters on the scene entity that make up the speech result of a session. */
const val COUNTER_VOCAL      = "vocalAttempts"
const val COUNTER_ADULT      = "adultConfirmed"
const val COUNTER_UNANSWERED = "unanswered"
const val COUNTER_RESPONSES  = "responses"
const val COUNTER_EXACT      = "exactResponses"
