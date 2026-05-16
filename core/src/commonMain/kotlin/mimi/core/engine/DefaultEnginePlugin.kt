package mimi.core.engine

import mimi.core.behavior.BehaviorPlugin
import mimi.core.behaviors.*
import mimi.core.constraints.registerBuiltins
import mimi.core.plugin.ConstraintRegistry

fun EngineBuilder.withDefaultPlugins(constraintRegistry: ConstraintRegistry): EngineBuilder {
    constraintRegistry.registerBuiltins()
    return this
        .withPlugin(ProgressTrackerPlugin())
        .withPlugin(SnapToTargetPlugin(constraintRegistry))
        .withPlugin(LineConnectValidatorPlugin(constraintRegistry))
        .withPlugin(SequenceValidatorPlugin())
        .withPlugin(TraceValidatorPlugin())
        .withPlugin(MazeValidatorPlugin())
        .withPlugin(AudioPromptPlugin())
        .withPlugin(PropertySelectionValidatorPlugin())
        .withPlugin(GeometrySelectionValidatorPlugin())
        .withPlugin(PlaySoundOnEventPlugin())
        .withPlugin(AdaptiveHintPlugin())
        .withPlugin(DifficultyScalerPlugin())
        .withPlugin(VisibilityTogglePlugin())
        .withPlugin(LockOnCompletePlugin())
        .withPlugin(CircleTraceValidatorPlugin())
        .withPlugin(MemoryFlipControllerPlugin())
        .withPlugin(PairMatchValidatorPlugin())
}
