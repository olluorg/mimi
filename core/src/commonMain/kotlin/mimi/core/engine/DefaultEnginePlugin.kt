package mimi.core.engine

import mimi.core.behavior.BehaviorPlugin
import mimi.core.behavior.BehaviorRegistry
import mimi.core.behavior.DefaultBehaviorRegistry
import mimi.core.behaviors.*
import mimi.core.constraints.registerBuiltins
import mimi.core.plugin.ConstraintRegistry
import mimi.core.plugin.DefaultConstraintRegistry

/**
 * Every behavior the engine ships with. Exposed on its own so that tooling —
 * scene validation, the catalog gate in CI — can build the same registry the
 * runtime uses instead of keeping a second list that drifts out of sync.
 */
fun defaultBehaviorPlugins(constraintRegistry: ConstraintRegistry): List<BehaviorPlugin> = listOf(
    ProgressTrackerPlugin(),
    SnapToTargetPlugin(constraintRegistry),
    LineConnectValidatorPlugin(constraintRegistry),
    SequenceValidatorPlugin(),
    TraceValidatorPlugin(),
    MazeValidatorPlugin(),
    AudioPromptPlugin(),
    PropertySelectionValidatorPlugin(),
    FreeSelectionPlugin(),
    RevealNextPlugin(),
    GeometrySelectionValidatorPlugin(),
    PlaySoundOnEventPlugin(),
    PlaySoundOnTapPlugin(),
    AdaptiveHintPlugin(),
    DifficultyScalerPlugin(),
    VisibilityTogglePlugin(),
    LockOnCompletePlugin(),
    CircleTraceValidatorPlugin(),
    MemoryFlipControllerPlugin(),
    PairMatchValidatorPlugin(),
    AwaitResponsePlugin(),
    VoiceDrivePlugin()
)

/** A constraint registry with every built-in evaluator registered. */
fun defaultConstraintRegistry(): ConstraintRegistry =
    DefaultConstraintRegistry().also { it.registerBuiltins() }

/** A behavior registry holding every built-in plugin, for validation and tooling. */
fun defaultBehaviorRegistry(
    constraintRegistry: ConstraintRegistry = defaultConstraintRegistry()
): BehaviorRegistry = DefaultBehaviorRegistry().also { registry ->
    defaultBehaviorPlugins(constraintRegistry).forEach { registry.register(it) }
}

fun EngineBuilder.withDefaultPlugins(constraintRegistry: ConstraintRegistry): EngineBuilder {
    constraintRegistry.registerBuiltins()
    return defaultBehaviorPlugins(constraintRegistry)
        .fold(this) { builder, plugin -> builder.withPlugin(plugin) }
}
