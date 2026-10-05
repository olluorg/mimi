package mimi.core.world

import mimi.core.model.PropertyValue
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SceneVariationTest {

    private fun item(id: String, x: Float, group: String = "items", kind: String? = null) =
        EntityDefinition(
            id         = id,
            components = listOf("tap"),
            properties = mapOf("position" to PropertyValue.Vec2(x, 300f)),
            tags       = buildMap {
                put("group", group)
                if (kind != null) put("kind", kind)
            }
        )

    private val six = (1..6).map { item("e$it", it * 100f) }

    private fun positionsOf(entities: List<EntityDefinition>) =
        entities.associate { it.id to it.properties["position"] }

    @Test
    fun `no variation leaves the scene exactly as written`() {
        assertEquals(six, SceneVariations.apply(six, null, seed = 42L))
    }

    @Test
    fun `shuffling moves entities without inventing or losing any`() {
        val varied = SceneVariations.apply(
            six, SceneVariation(shufflePositions = listOf("items")), seed = 7L
        )
        assertEquals(six.map { it.id }.toSet(), varied.map { it.id }.toSet())
        assertEquals(
            six.mapNotNull { it.properties["position"] }.toSet(),
            varied.mapNotNull { it.properties["position"] }.toSet(),
            "the same slots should be occupied, just by different entities"
        )
        assertNotEquals(positionsOf(six), positionsOf(varied), "something should have moved")
    }

    @Test
    fun `the same seed always deals the same arrangement`() {
        val variation = SceneVariation(shufflePositions = listOf("items"))
        assertEquals(
            SceneVariations.apply(six, variation, seed = 99L),
            SceneVariations.apply(six, variation, seed = 99L),
            "the engine must stay reproducible for a given seed"
        )
    }

    @Test
    fun `different seeds deal different arrangements`() {
        val variation = SceneVariation(shufflePositions = listOf("items"))
        val seen = (1L..12L).map { positionsOf(SceneVariations.apply(six, variation, it)) }.toSet()
        assertTrue(seen.size > 1, "twelve seeds produced one identical layout every time")
    }

    @Test
    fun `picking keeps the requested number and fills the front slots`() {
        val varied = SceneVariations.apply(
            six, SceneVariation(pick = listOf(PickRule(group = "items", count = 3))), seed = 5L
        )
        assertEquals(3, varied.size)
        assertEquals(
            six.take(3).mapNotNull { it.properties["position"] },
            varied.mapNotNull { it.properties["position"] },
            "survivors should close ranks rather than leave gaps where a card was dropped"
        )
    }

    @Test
    fun `an entity the scene cannot do without is always kept`() {
        val variation = SceneVariation(
            pick = listOf(PickRule(group = "items", count = 2, keep = listOf("e5")))
        )
        repeat(20) { seed ->
            val varied = SceneVariations.apply(six, variation, seed.toLong())
            assertContains(varied.map { it.id }, "e5", "the target was dropped with seed $seed")
            assertEquals(2, varied.size)
        }
    }

    @Test
    fun `picking never removes entities outside the pool`() {
        val withTargets = six + item("basket", 900f, group = "targets")
        val varied = SceneVariations.apply(
            withTargets, SceneVariation(pick = listOf(PickRule("items", 2))), seed = 3L
        )
        assertContains(varied.map { it.id }, "basket", "a drop target is not part of the draw")
    }

    @Test
    fun `a pool smaller than the quota is left untouched`() {
        val varied = SceneVariations.apply(
            six, SceneVariation(pick = listOf(PickRule("items", count = 10))), seed = 1L
        )
        assertEquals(six, varied)
    }

    @Test
    fun `a finer tag can select within a group the objective still watches`() {
        val mixed = listOf(
            item("f1", 100f, kind = "food"), item("f2", 200f, kind = "food"),
            item("f3", 300f, kind = "food"),
            item("t1", 400f, kind = "toys"), item("t2", 500f, kind = "toys"),
            item("t3", 600f, kind = "toys")
        )
        val varied = SceneVariations.apply(
            mixed,
            SceneVariation(
                pick = listOf(
                    PickRule("food", count = 1, tagKey = "kind"),
                    PickRule("toys", count = 1, tagKey = "kind")
                )
            ),
            seed = 11L
        )
        assertEquals(2, varied.size)
        assertEquals(
            1, varied.count { it.tags["kind"] == "food" },
            "a sorting game needs one of each kind, not two of the same basket"
        )
        assertEquals(1, varied.count { it.tags["kind"] == "toys" })
        assertTrue(varied.all { it.tags["group"] == "items" }, "the objective's group is untouched")
    }

    @Test
    fun `a group with a single member is left alone`() {
        val one = listOf(item("only", 100f))
        assertEquals(
            one,
            SceneVariations.apply(one, SceneVariation(shufflePositions = listOf("items")), 4L)
        )
    }
}
