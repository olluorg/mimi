package mimi.core.behavior

import mimi.core.engine.defaultBehaviorRegistry
import mimi.core.model.BehaviorRef
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behaviours read config with `config["key"] ?: default`, so anything they do
 * not recognise is ignored without a word. These checks keep that failure mode
 * closed: a scene that configures something the engine will not act on has to
 * fail loudly instead of quietly playing a different game.
 */
class ConfigSpecTest {

    private val registry = defaultBehaviorRegistry()

    private fun validate(type: String, config: Map<String, String>) =
        registry.validateRef(BehaviorRef(type, config))

    @Test
    fun `a key the behavior never reads is rejected`() {
        val result = validate("adaptiveHint", mapOf("mode" to "pattern"))
        assertTrue(!result.isValid)
        assertContains(result.errorList().first(), "does not use config key 'mode'")
    }

    @Test
    fun `the error names what the behavior does accept`() {
        val message = validate("adaptiveHint", mapOf("typo" to "1")).errorList().first()
        assertContains(message, "cooldownMax")
        assertContains(message, "cooldownMin")
    }

    @Test
    fun `an event name the behavior cannot subscribe to is rejected`() {
        val result = validate("playSoundOnEvent", mapOf("on" to "pairMatched"))
        assertTrue(!result.isValid, "'pairMatched' is a feedback type, not an event")
        assertContains(result.errorList().first(), "expected one of")
    }

    @Test
    fun `a sound name nothing can play is rejected`() {
        // A bare unknown name reaches the sound channel, matches nothing and
        // plays silence — indistinguishable from a broken speaker.
        assertTrue(!validate("playSoundOnEvent", mapOf("sound" to "correct")).isValid)
        assertTrue(validate("playSoundOnEvent", mapOf("sound" to "success")).isValid)
        assertTrue(
            validate("playSoundOnEvent", mapOf("sound" to "my_scene/media/bell.mp3")).isValid,
            "a path is always allowed; the catalog gate checks the file exists"
        )
    }

    @Test
    fun `flags must be spelled the way the parser reads them`() {
        assertTrue(validate("freeSelection", mapOf("repeatable" to "true")).isValid)
        assertTrue(
            !validate("freeSelection", mapOf("repeatable" to "yes")).isValid,
            "'yes' parses as null and silently falls back to the default"
        )
    }

    @Test
    fun `numbers outside a sensible range are rejected`() {
        assertTrue(validate("circleTraceValidator", mapOf("minProgress" to "0.8")).isValid)
        assertTrue(!validate("circleTraceValidator", mapOf("minProgress" to "1.5")).isValid)
        assertTrue(!validate("circleTraceValidator", mapOf("minProgress" to "many")).isValid)
    }

    @Test
    fun `a behavior that takes no configuration rejects all of it`() {
        val result = validate("progressTracker", mapOf("anything" to "1"))
        assertTrue(!result.isValid)
        assertContains(result.errorList().first(), "no configuration")
    }

    @Test
    fun `an empty config is always fine`() {
        for (type in listOf("adaptiveHint", "snapToTarget", "freeSelection", "progressTracker")) {
            assertTrue(validate(type, emptyMap()).isValid, "$type rejected an empty config")
        }
    }

    @Test
    fun `every registered behavior declares what it accepts`() {
        // A plugin without a spec accepts anything, which is the hole this was
        // written to close — so a new behavior must not be able to skip it.
        val unguarded = KNOWN_BEHAVIORS.filter { type ->
            validate(type, mapOf("definitely_not_a_real_key_9x" to "1")).isValid
        }
        assertEquals(
            emptyList(), unguarded,
            "these behaviors silently swallow unknown configuration"
        )
    }

    private companion object {
        val KNOWN_BEHAVIORS = listOf(
            "progressTracker", "snapToTarget", "lineConnectValidator", "sequenceValidator",
            "traceValidator", "mazeValidator", "audioPrompt", "propertySelectionValidator",
            "freeSelection", "revealNext", "geometrySelectionValidator", "playSoundOnEvent",
            "adaptiveHint", "difficultyScaler", "visibilityToggle", "lockOnComplete",
            "circleTraceValidator", "memoryFlipController", "pairMatchValidator"
        )
    }
}

/**
 * Pictures must be drawn or photographed, never typed.
 *
 * This kept coming back in a new property each time — first `emoji`, then a
 * geometric-shape character used as a counting dot, then `face` on a memory
 * card — so it is checked as a rule about every displayable property rather
 * than about any one of them.
 */
class TypedPictureTest {

    private fun tile(vararg props: Pair<String, String>) = mimi.core.world.EntityDefinition(
        id = "tile",
        components = listOf("tap"),
        properties = mapOf("position" to mimi.core.model.PropertyValue.Vec2(0f, 0f)) +
            props.associate { (k, v) -> k to mimi.core.model.PropertyValue.Text(v) }
    )

    private fun errorsFor(entity: mimi.core.world.EntityDefinition): List<String> =
        mimi.core.scene.SceneValidator.inspect(
            mimi.core.world.SceneDefinition(
                sceneId = "t",
                entities = listOf(entity),
                objectives = listOf(
                    mimi.core.world.ObjectiveDefinition("o", "anySelected", mapOf("group" to "g"))
                )
            ),
            defaultBehaviorRegistry()
        ).errors.filter { "characters" in it }

    @Test
    fun `an emoji used as a picture is rejected wherever it is written`() {
        for (key in listOf("emoji", "label", "name", "face", "faceLabel", "speech")) {
            assertTrue(
                errorsFor(tile(key to "🍎")).isNotEmpty(),
                "an emoji in '$key' should be rejected"
            )
        }
    }

    @Test
    fun `a geometric character used as a counting dot is rejected`() {
        assertTrue(errorsFor(tile("label" to "● ● ●")).isNotEmpty())
    }

    @Test
    fun `letters digits and words are fine`() {
        for (text in listOf("1", "А", "нос", "Сначала", "«да»", "10 — 20")) {
            assertEquals(
                emptyList(), errorsFor(tile("label" to text)),
                "'$text' is ordinary text and should be allowed"
            )
        }
    }

    @Test
    fun `a tile that draws its picture may still be labelled`() {
        assertEquals(emptyList(), errorsFor(tile("image" to "_cards/apple.jpg", "label" to "Яблоко")))
        assertEquals(emptyList(), errorsFor(tile("pictogram" to "nose", "label" to "нос")))
        assertEquals(emptyList(), errorsFor(tile("shape" to "circle", "color" to "red")))
    }
}

/**
 * A basket has to look like what it takes.
 *
 * Reported three times by the same parent — baskets labelled "Большой" and
 * "Маленький", baskets reading "Овощи" and "Фрукты", baskets that fell through
 * to their own ids. Each time the scene was fixed and the next one shipped with
 * the same mistake, because nothing said a drop target owes the child a
 * picture. Now something does.
 */
class BasketPictureTest {

    private fun basket(vararg props: Pair<String, mimi.core.model.PropertyValue>) =
        mimi.core.world.EntityDefinition(
            id = "basket",
            components = listOf("dropTarget"),
            properties = mapOf("position" to mimi.core.model.PropertyValue.Vec2(0f, 0f)) + props
        )

    private fun text(value: String) = mimi.core.model.PropertyValue.Text(value)

    private fun errorsFor(entity: mimi.core.world.EntityDefinition): List<String> =
        mimi.core.scene.SceneValidator.inspect(
            mimi.core.world.SceneDefinition(
                sceneId = "t",
                entities = listOf(entity),
                objectives = listOf(
                    mimi.core.world.ObjectiveDefinition("o", "anySelected", mapOf("group" to "g"))
                )
            ),
            defaultBehaviorRegistry()
        ).errors.filter { "drop target" in it }

    @Test
    fun `a basket named only in words is rejected`() {
        assertTrue(errorsFor(basket("label" to text("Овощи"))).isNotEmpty())
        assertTrue(errorsFor(basket("name" to text("Большой"))).isNotEmpty())
        // Nothing at all is the worst case: the renderer falls back to the id.
        assertTrue(errorsFor(basket()).isNotEmpty())
    }

    @Test
    fun `any of the four ways of drawing one is enough`() {
        assertEquals(emptyList(), errorsFor(basket("image" to text("_cards/pear.jpg"))))
        assertEquals(emptyList(), errorsFor(basket("shape" to text("circle"))))
        assertEquals(emptyList(), errorsFor(basket("pictogram" to text("hand"))))
        assertEquals(
            emptyList(),
            errorsFor(basket("count" to mimi.core.model.PropertyValue.Num(2.0)))
        )
    }

    @Test
    fun `a drawn basket may still carry a word for the adult reading over a shoulder`() {
        assertEquals(
            emptyList(),
            errorsFor(basket("image" to text("_cards/pear.jpg"), "label" to text("Фрукты")))
        )
    }

    @Test
    fun `a tile that is not a basket is left alone`() {
        val plainTile = mimi.core.world.EntityDefinition(
            id = "word",
            components = listOf("tap"),
            properties = mapOf(
                "position" to mimi.core.model.PropertyValue.Vec2(0f, 0f),
                "label" to text("А")
            )
        )
        assertEquals(emptyList(), errorsFor(plainTile))
    }
}
