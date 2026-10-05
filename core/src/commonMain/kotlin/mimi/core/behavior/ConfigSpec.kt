package mimi.core.behavior

import mimi.core.model.ValidationResult

/**
 * What a behavior accepts in its `config` block.
 *
 * Behaviors read config with `config["key"] ?: default`, which silently ignores
 * anything it does not recognise. A scene author writing `flipBackDelayMs` at a
 * behavior that never reads it, or `on: "pairMatched"` where only
 * `"matchSuccess"` is understood, gets no error — just a game that quietly does
 * something else. Declaring the accepted keys here turns those into build
 * failures before the scene is published.
 */
class ConfigSpec private constructor(
    private val behaviorId: String,
    private val rules:      Map<String, Rule>
) {

    sealed interface Rule {
        fun describe(): String
        fun check(value: String): String?
    }

    private data class OneOf(val allowed: Set<String>) : Rule {
        override fun describe() = "one of ${allowed.sorted().joinToString(", ")}"
        override fun check(value: String) =
            if (value in allowed) null
            else "expected ${describe()}"
    }

    private object Flag : Rule {
        override fun describe() = "true or false"
        override fun check(value: String) =
            if (value.lowercase() in setOf("true", "false")) null
            else "expected ${describe()}"
    }

    private data class Num(val min: Double?, val max: Double?) : Rule {
        override fun describe() = buildString {
            append("a number")
            if (min != null && max != null) append(" between $min and $max")
            else if (min != null) append(" of at least $min")
            else if (max != null) append(" of at most $max")
        }

        override fun check(value: String): String? {
            val parsed = value.toDoubleOrNull() ?: return "expected ${describe()}"
            if (min != null && parsed < min) return "expected ${describe()}"
            if (max != null && parsed > max) return "expected ${describe()}"
            return null
        }
    }

    /**
     * A built-in sound name or a path to a file shipped with the catalog. A bare
     * name outside the built-in set reaches the sound channel, matches nothing
     * and plays silence — which is indistinguishable from a broken speaker.
     */
    private object Sound : Rule {
        override fun describe() =
            "a file path, or one of ${BUILTIN_SOUNDS.sorted().joinToString(", ")}"

        override fun check(value: String) = when {
            value.isBlank()        -> "expected ${describe()}"
            '/' in value           -> null
            value in BUILTIN_SOUNDS -> null
            else                   -> "expected ${describe()}"
        }
    }

    private object FreeText : Rule {
        override fun describe() = "any text"
        override fun check(value: String) = null
    }

    fun check(config: Map<String, String>): ValidationResult {
        val errors = mutableListOf<String>()

        for ((key, value) in config) {
            val rule = rules[key]
            if (rule == null) {
                // An unrecognised key is nearly always a typo or a setting the
                // author believed was doing something.
                errors += "$behaviorId does not use config key '$key'; " +
                    "it accepts ${rules.keys.sorted().joinToString(", ").ifEmpty { "no configuration" }}"
                continue
            }
            rule.check(value)?.let { problem ->
                errors += "$behaviorId config.$key='$value': $problem"
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    class Builder internal constructor(private val behaviorId: String) {
        private val rules = mutableMapOf<String, Rule>()

        /** A closed set of accepted values. */
        fun oneOf(key: String, vararg values: String) {
            rules[key] = OneOf(values.toSet())
        }

        fun flag(key: String) {
            rules[key] = Flag
        }

        fun number(key: String, min: Double? = null, max: Double? = null) {
            rules[key] = Num(min, max)
        }

        /** A built-in sound name or a catalog file path. */
        fun sound(key: String) {
            rules[key] = Sound
        }

        /** Entity ids, group names — checked elsewhere or not at all. */
        fun text(vararg keys: String) {
            keys.forEach { rules[it] = FreeText }
        }

        internal fun build() = ConfigSpec(behaviorId, rules.toMap())
    }

    companion object {
        fun of(behaviorId: String, build: Builder.() -> Unit): ConfigSpec =
            Builder(behaviorId).apply(build).build()

        /** For behaviors that take no configuration at all. */
        fun none(behaviorId: String): ConfigSpec = of(behaviorId) {}
    }
}

/**
 * Sounds the feedback layer knows how to play by name.
 *
 * Anything else is treated as a file path; a bare name that is not in this set
 * reaches the sound channel, matches nothing, and plays silence.
 */
val BUILTIN_SOUNDS: Set<String> = setOf("success", "fail", "complete", "hint")
