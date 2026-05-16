package mimi.core.curriculum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurriculumTest {

    @Test
    fun skillMapperUpdatesOnlyCompletedMatchingScene() {
        val state = ChildSkillState(
            childId = "child_1",
            skills = mapOf("memory" to 0.5)
        )
        val mapping = SceneSkillMapping(
            sceneId = "memory_cards_01",
            skillWeights = mapOf("memory" to 1.0)
        )

        val result = DefaultSkillMapper().updateSkills(
            state = state,
            result = SceneResult(
                sceneId = "memory_cards_01",
                completed = true,
                metrics = SceneMetrics(mistakes = 0, hintsUsed = 0)
            ),
            mapping = mapping
        )

        assertEquals(1, result.updates.size)
        assertTrue(result.newState.proficiency("memory") > 0.5)
    }

    @Test
    fun plannerPrioritizesWeakestSkillDeterministically() {
        val context = CurriculumContext(
            childState = ChildSkillState(
                childId = "child_1",
                skills = mapOf(
                    "memory" to 0.2,
                    "fine_motor" to 0.8
                )
            ),
            availableScenes = listOf(
                SceneCatalogEntry("trace_line_01", mapOf("fine_motor" to 1.0)),
                SceneCatalogEntry("memory_cards_01", mapOf("memory" to 1.0))
            )
        )

        assertEquals("memory_cards_01", BalancedSkillPlanner().selectNextScene(context))
    }

    @Test
    fun plannerBlocksLockedScenes() {
        val context = CurriculumContext(
            childState = ChildSkillState(
                childId = "child_1",
                skills = mapOf("memory" to 0.8)
            ),
            availableScenes = listOf(
                SceneCatalogEntry("memory_cards_01", mapOf("memory" to 1.0)),
                SceneCatalogEntry("memory_cards_02", mapOf("memory" to 1.0), requires = listOf("memory_cards_01"))
            ),
            completedSceneIds = emptySet()
        )
        // _02 is locked, so only _01 is eligible
        assertEquals("memory_cards_01", BalancedSkillPlanner().selectNextScene(context))
    }

    @Test
    fun plannerUnlocksSceneAfterPrereqCompleted() {
        val context = CurriculumContext(
            childState = ChildSkillState(
                childId = "child_1",
                skills = mapOf("memory" to 0.8)
            ),
            availableScenes = listOf(
                SceneCatalogEntry("memory_cards_01", mapOf("memory" to 1.0)),
                SceneCatalogEntry("memory_cards_02", mapOf("memory" to 1.0), requires = listOf("memory_cards_01"))
            ),
            recentSceneIds = listOf("memory_cards_01"),
            completedSceneIds = setOf("memory_cards_01")
        )
        // _01 is recent (excluded from candidates), _02 is now unlocked
        assertEquals("memory_cards_02", BalancedSkillPlanner().selectNextScene(context))
    }

    @Test
    fun plannerUsesDeterministicReinforcementCadence() {
        val context = CurriculumContext(
            childState = ChildSkillState(
                childId = "child_1",
                skills = mapOf(
                    "memory" to 0.2,
                    "fine_motor" to 0.8
                )
            ),
            availableScenes = listOf(
                SceneCatalogEntry("trace_line_01", mapOf("fine_motor" to 1.0)),
                SceneCatalogEntry("memory_cards_01", mapOf("memory" to 1.0))
            ),
            completedSceneCount = 5
        )

        assertEquals("trace_line_01", BalancedSkillPlanner().selectNextScene(context))
    }
}
