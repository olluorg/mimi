package mimi.core.adaptive

import mimi.core.model.*

// ── Tick Rate ──────────────────────────────────────────────────────────────────

const val TICKS_PER_SECOND = 60L

fun secondsToTicks(seconds: Int): Long = seconds * TICKS_PER_SECOND

// ── Hint Level ────────────────────────────────────────────────────────────────

enum class HintLevel(val value: Int) {
    NONE(0),
    SUBTLE(1),    // glow / pulse on target
    DIRECTIONAL(2), // arrow pointing to target
    ASSIST(3)     // animated full assist
}

data class HintThresholds(
    val level1IdleTicks: Long = secondsToTicks(4),
    val level1Mistakes:  Int  = 1,
    val level2IdleTicks: Long = secondsToTicks(8),
    val level2Mistakes:  Int  = 2,
    val level3IdleTicks: Long = secondsToTicks(12),
    val level3Mistakes:  Int  = 3,
    val cooldownMinTicks: Long = secondsToTicks(10),
    val cooldownMaxTicks: Long = secondsToTicks(15)
) {
    fun evaluate(mistakes: Int, idleTicks: Long): HintLevel = when {
        mistakes >= level3Mistakes || idleTicks >= level3IdleTicks -> HintLevel.ASSIST
        mistakes >= level2Mistakes || idleTicks >= level2IdleTicks -> HintLevel.DIRECTIONAL
        mistakes >= level1Mistakes || idleTicks >= level1IdleTicks -> HintLevel.SUBTLE
        else                                                        -> HintLevel.NONE
    }
}

// ── Difficulty ────────────────────────────────────────────────────────────────

@JvmInline
value class Difficulty(val value: Float) {
    init { require(value in 0.1f..1.0f) { "Difficulty must be in [0.1, 1.0]" } }

    operator fun plus(delta: Float)  = Difficulty((value + delta).coerceIn(0.1f, 1.0f))
    operator fun minus(delta: Float) = Difficulty((value - delta).coerceIn(0.1f, 1.0f))

    // effectiveValue = baseValue × (2.0 - difficulty)
    fun scale(base: Float): Float = base * (2.0f - value)
}

val DEFAULT_DIFFICULTY = Difficulty(0.5f)

data class DifficultyAdjustments(
    val decreaseLowSuccessRate: Float     = 0.10f,  // successRate < 40%
    val decreaseMultipleFailures: Float   = 0.15f,  // 3+ failures in window
    val decreaseHighIdle: Float           = 0.10f,  // avg idle > 10s
    val increaseHighSuccessRate: Float    = 0.05f,  // successRate > 80%
    val increasePerfectInteractions: Float = 0.05f  // 3 perfect interactions
)

data class DifficultyConfig(
    val initial:          Difficulty = DEFAULT_DIFFICULTY,
    val updateIntervalMinTicks: Long = secondsToTicks(20),
    val updateIntervalMaxTicks: Long = secondsToTicks(30),
    val adjustments:      DifficultyAdjustments = DifficultyAdjustments()
)

// ── Adaptive Metrics Window ───────────────────────────────────────────────────

data class AdaptiveMetrics(
    val mistakes:           Int,
    val matches:            Int,
    val hints:              Int,
    val idleTicks:          Long,
    val currentDifficulty:  Difficulty,
    val currentHintLevel:   HintLevel,
    val lastHintTick:       Tick,
    val successRateWindow:  Float   // 0.0..1.0, calculated over last difficulty window
)

// ── Scene-level adaptive property keys (on _scene entity) ────────────────────

object AdaptiveFields {
    const val MISTAKES            = "mistakes"
    const val MATCHES             = "matches"
    const val HINTS               = "hints"
    const val IDLE_TICKS          = "idleTicks"
    const val DIFFICULTY          = "difficulty"
    const val HINT_LEVEL          = "hintLevel"
    const val SUCCESS_RATE_WINDOW = "successRateWindow"
}
