package mimi.core.constraints

import mimi.core.model.*
import mimi.core.plugin.ConstraintEvaluator
import mimi.core.plugin.ConstraintRegistry

// propertyEquals: source[sourceKey] == target[targetKey]
object PropertyEqualsEvaluator : ConstraintEvaluator {
    override val type = "propertyEquals"

    override fun evaluate(source: EntitySnapshot, target: EntitySnapshot, config: Map<String, String>): Boolean {
        val sourceKey = config["source"] ?: config["sourceProperty"] ?: return false
        val targetKey = config["target"] ?: config["targetProperty"] ?: return false
        val sv = source.properties[sourceKey] ?: return false
        val tv = target.properties[targetKey] ?: return false
        return sv == tv
    }
}

// propertyIncludes: target[targetKey] contains source[sourceKey]
object PropertyIncludesEvaluator : ConstraintEvaluator {
    override val type = "propertyIncludes"

    override fun evaluate(source: EntitySnapshot, target: EntitySnapshot, config: Map<String, String>): Boolean {
        val sourceKey = config["source"] ?: config["sourceProperty"] ?: return false
        val targetKey = config["target"] ?: config["targetProperty"] ?: return false
        val sv = (source.properties[sourceKey] as? PropertyValue.Text)?.value ?: return false
        return when (val tv = target.properties[targetKey]) {
            is PropertyValue.Text     -> tv.value == sv
            is PropertyValue.TextList -> sv in tv.values
            else                      -> false
        }
    }
}

// tagMatch: source.tags ∩ target.tags ≠ ∅
object TagMatchEvaluator : ConstraintEvaluator {
    override val type = "tagMatch"

    override fun evaluate(source: EntitySnapshot, target: EntitySnapshot, config: Map<String, String>): Boolean {
        val key = config["tagKey"]
        return if (key != null) {
            source.tags[key] != null && source.tags[key] == target.tags[key]
        } else {
            source.tags.any { (k, v) -> target.tags[k] == v }
        }
    }
}

// allOf: ALL child constraints must pass
class AllOfEvaluator(private val registry: ConstraintRegistry) : ConstraintEvaluator {
    override val type = "allOf"

    override fun evaluate(source: EntitySnapshot, target: EntitySnapshot, config: Map<String, String>): Boolean {
        val types = config["types"]?.split(",")?.map { it.trim() } ?: return false
        return types.all { constraintType ->
            registry.evaluate(constraintType, source, target, config)
        }
    }
}

// anyOf: AT LEAST ONE child constraint must pass
class AnyOfEvaluator(private val registry: ConstraintRegistry) : ConstraintEvaluator {
    override val type = "anyOf"

    override fun evaluate(source: EntitySnapshot, target: EntitySnapshot, config: Map<String, String>): Boolean {
        val types = config["types"]?.split(",")?.map { it.trim() } ?: return false
        return types.any { constraintType ->
            registry.evaluate(constraintType, source, target, config)
        }
    }
}

fun ConstraintRegistry.registerBuiltins() {
    register(PropertyEqualsEvaluator)
    register(PropertyIncludesEvaluator)
    register(TagMatchEvaluator)
    register(AllOfEvaluator(this))
    register(AnyOfEvaluator(this))
}
