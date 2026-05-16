package mimi.core.curriculum

import kotlinx.serialization.Serializable

typealias SceneId = String
typealias SkillId = String

@Serializable
data class ChildSkillState(
    val childId: String,
    val skills: Map<SkillId, Double> = emptyMap()
) {
    fun proficiency(skillId: SkillId): Double =
        skills[skillId]?.coerceIn(0.0, 1.0) ?: 0.0

    fun withSkill(skillId: SkillId, value: Double): ChildSkillState =
        copy(skills = skills + (skillId to value.coerceIn(0.0, 1.0)))
}

@Serializable
data class ObjectiveResult(
    val objectiveId: String,
    val completed: Boolean
)

@Serializable
data class SceneMetrics(
    val attempts: Int = 1,
    val mistakes: Int = 0,
    val hintsUsed: Int = 0,
    val durationMs: Long = 0L
)

@Serializable
data class SceneResult(
    val sceneId: SceneId,
    val completed: Boolean,
    val objectiveResults: List<ObjectiveResult> = emptyList(),
    val metrics: SceneMetrics = SceneMetrics()
)

@Serializable
data class SceneSkillMapping(
    val sceneId: SceneId,
    val skillWeights: Map<SkillId, Double>
) {
    fun normalizedWeights(): Map<SkillId, Double> =
        skillWeights
            .filterValues { it > 0.0 }
            .mapValues { it.value.coerceIn(0.0, 1.0) }
}

data class SkillUpdate(
    val skillId: SkillId,
    val before: Double,
    val after: Double,
    val delta: Double,
    val reason: String
)

data class SkillMappingResult(
    val newState: ChildSkillState,
    val updates: List<SkillUpdate>
)

interface SkillMapper {
    fun updateSkills(
        state: ChildSkillState,
        result: SceneResult,
        mapping: SceneSkillMapping
    ): SkillMappingResult
}

class DefaultSkillMapper(
    private val baseGain: Double = 0.08,
    private val minPerformance: Double = 0.25,
    private val mistakePenalty: Double = 0.08,
    private val hintPenalty: Double = 0.05
) : SkillMapper {

    override fun updateSkills(
        state: ChildSkillState,
        result: SceneResult,
        mapping: SceneSkillMapping
    ): SkillMappingResult {
        if (!result.completed || result.sceneId != mapping.sceneId) {
            return SkillMappingResult(state, emptyList())
        }

        val performance = performanceScore(result.metrics)
        var nextState = state
        val updates = mutableListOf<SkillUpdate>()

        for ((skillId, rawWeight) in mapping.normalizedWeights()) {
            val weight = rawWeight.coerceIn(0.0, 1.0)
            val before = nextState.proficiency(skillId)
            val delta = baseGain * weight * performance * (1.0 - before)
            val after = (before + delta).coerceIn(0.0, 1.0)

            if (after != before) {
                nextState = nextState.withSkill(skillId, after)
                updates += SkillUpdate(
                    skillId = skillId,
                    before = before,
                    after = after,
                    delta = after - before,
                    reason = "completed_scene:${result.sceneId}"
                )
            }
        }

        return SkillMappingResult(nextState, updates)
    }

    private fun performanceScore(metrics: SceneMetrics): Double {
        val score = 1.0 -
            metrics.mistakes.coerceAtLeast(0) * mistakePenalty -
            metrics.hintsUsed.coerceAtLeast(0) * hintPenalty
        return score.coerceIn(minPerformance, 1.0)
    }
}

@Serializable
data class SceneCatalogEntry(
    val sceneId: SceneId,
    val skillWeights: Map<SkillId, Double>,
    val requires: List<SceneId> = emptyList()
) {
    fun normalizedWeights(): Map<SkillId, Double> =
        skillWeights
            .filterValues { it > 0.0 }
            .mapValues { it.value.coerceIn(0.0, 1.0) }
}

data class CurriculumContext(
    val childState: ChildSkillState,
    val availableScenes: List<SceneCatalogEntry>,
    val recentSceneIds: List<SceneId> = emptyList(),
    val completedSceneCount: Int = 0,
    val completedSceneIds: Set<SceneId> = emptySet()
)

interface CurriculumPlanner {
    fun selectNextScene(context: CurriculumContext): SceneId?
}

class BalancedSkillPlanner(
    private val recentWindow: Int = 3,
    private val reinforcementEvery: Int = 5
) : CurriculumPlanner {

    override fun selectNextScene(context: CurriculumContext): SceneId? {
        if (context.availableScenes.isEmpty()) return null

        val unlocked = context.availableScenes
            .filter { entry -> entry.requires.all { it in context.completedSceneIds } }
            .ifEmpty { context.availableScenes }

        val candidates = unlocked
            .filterNot { it.sceneId in context.recentSceneIds.takeLast(recentWindow) }
            .ifEmpty { unlocked }

        val mode = if (
            reinforcementEvery > 0 &&
            context.completedSceneCount > 0 &&
            context.completedSceneCount % reinforcementEvery == 0
        ) PlanningMode.REINFORCE else PlanningMode.REMEDIATE

        return candidates
            .sortedBy { it.sceneId }
            .maxWithOrNull(compareBy<SceneCatalogEntry> { score(it, context.childState, mode) }
                .thenByDescending { it.sceneId })
            ?.sceneId
    }

    private fun score(
        scene: SceneCatalogEntry,
        state: ChildSkillState,
        mode: PlanningMode
    ): Double {
        val weights = scene.normalizedWeights()
        if (weights.isEmpty()) return 0.0

        return weights.entries.sumOf { (skillId, weight) ->
            val proficiency = state.proficiency(skillId)
            val skillScore = when (mode) {
                PlanningMode.REMEDIATE -> 1.0 - proficiency
                PlanningMode.REINFORCE -> proficiency
            }
            weight * skillScore
        }
    }

    private enum class PlanningMode {
        REMEDIATE,
        REINFORCE
    }
}
