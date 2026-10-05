package org.ollu.mini.progress

import mimi.core.engine.SpeechTurn

/**
 * How a child's attempts at one scene are going.
 *
 * "Help" is mistakes plus hints: the two ways a scene had to carry the child.
 * Falling help across repeated attempts is the signal that a skill is landing,
 * which matters more to a parent or therapist than a completion count — a scene
 * can be finished every time and still be getting harder.
 */
data class SceneInsight(
    val sceneId:       String,
    val attempts:      Int,
    val lastAtEpochMs: Long,
    val avgMistakes:   Double,
    val avgHints:      Double,
    val avgDurationMs: Long,
    val trend:         Trend
) {
    val avgHelp: Double get() = avgMistakes + avgHints
}

enum class Trend {
    /** Needing less help than when they started. */
    IMPROVING,

    /** Holding steady. */
    STEADY,

    /** Needing more help than before, not less. */
    STRUGGLING,

    /** Too few attempts to say anything yet. */
    NEW
}

/**
 * What a child picked when offered a choice, and how often.
 *
 * A choice game has no score. This tally is its whole output: an adult looking
 * at "сок 7, вода 1" is seeing a preference the child has no other way to tell
 * them, which for a non-verbal child is the point of the exercise.
 */
data class ChoiceTally(
    val sceneId: String,
    val counts:  Map<String, Int>
) {
    val total: Int get() = counts.values.sum()
    val favourite: String? get() = counts.maxByOrNull { it.value }?.key
}

/**
 * How often the child tries to communicate in speech games.
 *
 * For a child who is not yet talking this is the number that matters: not
 * whether the words were right, but whether attempts are becoming more
 * frequent. Mistakes and hints, the currency of every other game, say almost
 * nothing here.
 */
/**
 * How one word is going: how often it was asked, how often the child answered
 * it at all, and how often they said exactly it.
 *
 * The number a therapist actually acts on. A session total says the child
 * tried eleven times; this says «му» answered eight times out of eight and
 * «мяу» none out of six — so «мяу» is too hard this week, and «му» is ready
 * to be asked for in a sentence.
 */
data class WordInsight(
    val word:     String,
    val asked:    Int,
    val answered: Int,
    val exact:    Int
) {
    val share: Double get() = if (asked == 0) 0.0 else answered.toDouble() / asked
}

data class SpeechInsight(
    val sessions:         Int,
    val attemptsThisWeek: Int,
    val attemptsLastWeek: Int,
    val heardByMic:       Int,
    val confirmedByAdult: Int,
    /** Attempts where the vowel heard was exactly the one modelled. */
    val exact:            Int = 0,
    /** Share of turns the child answered; null before any turn-based game. */
    val answerRate:       Double?,
    /** Attempts per speech session, oldest first, the last few sessions. */
    val recent:           List<Int>,
    /** Per word, hardest first — what to practise next. */
    val words:            List<WordInsight> = emptyList()
) {
    val trend: Trend get() = when {
        attemptsLastWeek == 0 && attemptsThisWeek == 0 -> Trend.NEW
        attemptsLastWeek == 0                            -> Trend.IMPROVING
        attemptsThisWeek >= attemptsLastWeek * 1.2       -> Trend.IMPROVING
        attemptsThisWeek <= attemptsLastWeek * 0.8       -> Trend.STRUGGLING
        else                                             -> Trend.STEADY
    }
}

data class ProgressInsights(
    val totalSessions:     Int                = 0,
    val totalTimeMs:       Long               = 0L,
    val sessionsLast7Days: Int                = 0,
    val distinctScenes:    Int                = 0,
    val perScene:          List<SceneInsight> = emptyList(),
    val needsAttention:    List<SceneInsight> = emptyList(),
    val strongest:         List<SceneInsight> = emptyList(),
    val choices:           List<ChoiceTally>  = emptyList(),
    val speech:            SpeechInsight?     = null
) {
    val isEmpty: Boolean get() = totalSessions == 0
}

private const val MIN_ATTEMPTS_FOR_TREND = 3

/**
 * How much the average help must move before it counts as a change.
 *
 * One full mistake-or-hint per attempt. The halves being compared are small —
 * often two attempts each — so a smaller threshold turns a single tired run
 * into a reported regression, which is exactly the kind of false alarm that
 * makes a parent distrust the screen.
 */
private const val TREND_SENSITIVITY = 1.0

/** At or above this much help per attempt, a scene is worth an adult's attention. */
private const val HELP_THRESHOLD = 1.5

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Summarises a child's history.
 *
 * [nowEpochMs] is passed in rather than read from the clock so the result is a
 * pure function of its inputs and can be tested.
 */
fun buildProgressInsights(
    sessions:   List<SessionRecord>,
    nowEpochMs: Long
): ProgressInsights {
    if (sessions.isEmpty()) return ProgressInsights()

    val perScene = sessions
        .groupBy { it.sceneId }
        .map { (sceneId, records) -> insightFor(sceneId, records) }
        .sortedByDescending { it.lastAtEpochMs }

    val needsAttention = perScene
        .filter { it.avgHelp >= HELP_THRESHOLD && it.trend != Trend.IMPROVING }
        .sortedByDescending { it.avgHelp }
        .take(5)

    val strongest = perScene
        .filter { it.attempts >= MIN_ATTEMPTS_FOR_TREND && it.avgHelp < HELP_THRESHOLD }
        .sortedBy { it.avgHelp }
        .take(5)

    val choices = sessions
        .filter { it.choices.isNotEmpty() }
        .groupBy { it.sceneId }
        .map { (sceneId, records) ->
            ChoiceTally(
                sceneId = sceneId,
                // Every answer counts, not just the last one: a scene that asks
                // five times is five things the child told the adult.
                counts  = records
                    .flatMap { it.choices }
                    .filter { it.isNotBlank() }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .associate { it.key to it.value }
            )
        }
        .filter { it.counts.isNotEmpty() }
        .sortedByDescending { it.total }

    return ProgressInsights(
        totalSessions     = sessions.size,
        totalTimeMs       = sessions.sumOf { it.durationMs },
        sessionsLast7Days = sessions.count { nowEpochMs - it.atEpochMs <= WEEK_MS },
        distinctScenes    = perScene.size,
        perScene          = perScene,
        needsAttention    = needsAttention,
        strongest         = strongest,
        choices           = choices,
        speech            = speechInsightOf(sessions, nowEpochMs)
    )
}

private const val RECENT_SPEECH_SESSIONS = 10

/**
 * Words ordered by how much trouble they give: least answered first, and
 * among equals the one asked most, because that is the one the child has had
 * the most chances at.
 */
private fun wordsOf(sessions: List<SessionRecord>): List<WordInsight> =
    sessions
        .flatMap { it.turns }
        .mapNotNull(SpeechTurn::decode)
        .groupBy { it.word }
        .map { (word, turns) ->
            WordInsight(
                word     = word,
                asked    = turns.size,
                answered = turns.count { it.answered },
                exact    = turns.count { it.exact }
            )
        }
        .sortedWith(compareBy({ it.share }, { -it.asked }))

private fun speechInsightOf(sessions: List<SessionRecord>, nowEpochMs: Long): SpeechInsight? {
    val speech = sessions
        .filter { it.speechGame || it.speechAttempts > 0 || it.responses > 0 || it.unanswered > 0 }
        .sortedBy { it.atEpochMs }
    if (speech.isEmpty()) return null
    fun attemptsBetween(fromAgo: Long, toAgo: Long) = speech
        .filter { nowEpochMs - it.atEpochMs in fromAgo until toAgo }
        .sumOf { it.speechAttempts }
    val turns = speech.sumOf { it.responses + it.unanswered }
    return SpeechInsight(
        sessions         = speech.size,
        attemptsThisWeek = attemptsBetween(0L, WEEK_MS + 1),
        attemptsLastWeek = attemptsBetween(WEEK_MS + 1, 2 * WEEK_MS + 1),
        heardByMic       = speech.sumOf { it.vocalAttempts },
        confirmedByAdult = speech.sumOf { it.adultConfirmed },
        exact            = speech.sumOf { it.exactResponses },
        answerRate       = if (turns == 0) null else speech.sumOf { it.responses }.toDouble() / turns,
        recent           = speech.takeLast(RECENT_SPEECH_SESSIONS).map { it.speechAttempts },
        words            = wordsOf(speech)
    )
}

private fun insightFor(sceneId: String, records: List<SessionRecord>): SceneInsight {
    val ordered = records.sortedBy { it.atEpochMs }
    return SceneInsight(
        sceneId       = sceneId,
        attempts      = ordered.size,
        lastAtEpochMs = ordered.last().atEpochMs,
        avgMistakes   = ordered.map { it.mistakes }.average(),
        avgHints      = ordered.map { it.hintsUsed }.average(),
        avgDurationMs = if (ordered.isEmpty()) 0L else ordered.sumOf { it.durationMs } / ordered.size,
        trend         = trendOf(ordered)
    )
}

/**
 * Compares help needed in the earlier half of the attempts against the later
 * half. Halves rather than first-vs-last because a single bad run on a tired
 * day should not read as a regression.
 */
private fun trendOf(ordered: List<SessionRecord>): Trend {
    if (ordered.size < MIN_ATTEMPTS_FOR_TREND) return Trend.NEW

    val midpoint = ordered.size / 2
    val earlier  = ordered.take(midpoint)
    val later    = ordered.drop(ordered.size - midpoint)
    if (earlier.isEmpty() || later.isEmpty()) return Trend.NEW

    fun help(records: List<SessionRecord>) =
        records.map { (it.mistakes + it.hintsUsed).toDouble() }.average()

    val delta = help(later) - help(earlier)
    return when {
        delta <= -TREND_SENSITIVITY -> Trend.IMPROVING
        delta >= TREND_SENSITIVITY  -> Trend.STRUGGLING
        else                        -> Trend.STEADY
    }
}
