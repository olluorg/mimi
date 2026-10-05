package mimi.core.scene

import mimi.core.engine.defaultBehaviorRegistry
import mimi.core.engine.defaultConstraintRegistry
import mimi.core.model.BehaviorRef
import mimi.core.model.EntityLifecycleState
import mimi.core.model.PropertyValue
import mimi.core.world.EntityDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneAnnotations
import mimi.core.world.SceneBehaviorBinding
import mimi.core.world.SceneDefinition
import mimi.core.world.SpeechProfile
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * «Запуск речи» lists only games that really ask the child to communicate:
 * a speech label without the mechanic behind it is a build failure.
 */
class SpeechValidationTest {

    private val profile = SpeechProfile(
        stage    = "imitation",
        targets  = listOf("alalia"),
        modes    = listOf("imitation"),
        adultTip = "Скажите слово вместе с игрой и подождите"
    )

    private fun scene(
        speech:    SpeechProfile?,
        behaviors: List<BehaviorRef>,
        objective: String = "interactionCount"
    ) = SceneDefinition(
        sceneId  = "speech_validation",
        entities = listOf(EntityDefinition(
            id           = "cow",
            components   = listOf("tap"),
            properties   = mapOf(
                "position" to PropertyValue.Vec2(500f, 400f),
                "image"    to PropertyValue.Text("_cards/cow.jpg"),
                "speech"   to PropertyValue.Text("Му!")
            ),
            tags         = mapOf("group" to "targets"),
            initialState = EntityLifecycleState.INACTIVE
        )),
        objectives = listOf(ObjectiveDefinition(
            id = "turns", type = objective,
            config = if (objective == "interactionCount") mapOf("count" to "3") else mapOf("group" to "targets")
        )),
        behaviors = (listOf(BehaviorRef("audioPrompt", mapOf("text" to "Повторяй за мной"))) + behaviors)
            .map { SceneBehaviorBinding(it) },
        meta = SceneAnnotations(speech)
    )

    private fun report(scene: SceneDefinition) = SceneValidator.inspect(
        scene              = scene,
        behaviorRegistry   = defaultBehaviorRegistry(),
        constraintRegistry = defaultConstraintRegistry()
    )

    @Test
    fun `a speech game with its mechanic passes`() {
        val result = report(scene(profile, listOf(BehaviorRef("awaitResponse"))))
        assertTrue(result.isValid, result.errors.joinToString("\n"))
    }

    @Test
    fun `a speech label on a game without a speech mechanic is rejected`() {
        val result = report(scene(profile, listOf(BehaviorRef("freeSelection", mapOf("group" to "targets")))))
        assertTrue(result.errors.any { "claims mode 'imitation'" in it }, result.errors.joinToString("\n"))
    }

    @Test
    fun `a drill needs random order and enough repetitions`() {
        val drill = profile.copy(modes = listOf("drill"))
        val short = report(scene(drill, listOf(BehaviorRef("awaitResponse", mapOf("trials" to "4")))))
        assertTrue(short.errors.any { "claims mode 'drill'" in it })

        val real = report(scene(drill, listOf(BehaviorRef("awaitResponse", mapOf("order" to "random", "trials" to "12")))))
        assertTrue(real.isValid, real.errors.joinToString("\n"))
    }

    @Test
    fun `the adult has to be told their part`() {
        val result = report(scene(profile.copy(adultTip = ""), listOf(BehaviorRef("awaitResponse"))))
        assertTrue(result.errors.any { "adultTip" in it })
    }

    @Test
    fun `unknown stages and targets are caught`() {
        val result = report(scene(
            profile.copy(stage = "talking", targets = listOf("stuttering")),
            listOf(BehaviorRef("awaitResponse"))
        ))
        assertTrue(result.errors.any { "stage='talking'" in it })
        assertTrue(result.errors.any { "'stuttering'" in it })
    }

    @Test
    fun `turn based speech games must end by counting turns`() {
        val result = report(scene(profile, listOf(BehaviorRef("awaitResponse")), objective = "anySelected"))
        assertTrue(result.errors.any { "interactionCount" in it }, result.errors.joinToString("\n"))
    }

    @Test
    fun `a voice game without a speech label is flagged`() {
        val result = report(scene(null, listOf(BehaviorRef("awaitResponse"))))
        assertTrue(result.warnings.any { "meta.speech" in it })
    }
}
