package org.ollu.mini.progress

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A shared device has to keep children apart.
 *
 * Siblings at home, a whole group in a speech room: before profiles, everything
 * was pooled, so one child's struggles moved another child's difficulty and one
 * child's wins unlocked another's scenes. The next game was being chosen from
 * somebody else's play, which is worse than having no history.
 */
class ProfilesTest {

    private fun repo() = ProgressRepository(MapSettings())

    @Test
    fun `a fresh install has no profiles and still works`() {
        val r = repo()
        assertTrue(r.getProfiles().isEmpty())
        assertNull(r.currentProfileId())
        // Play must be possible before anybody has set anything up.
        r.markCompleted(r.currentChildId(), "sort_fruits_01")
        assertEquals(setOf("sort_fruits_01"), r.getCompletedSceneIds(r.currentChildId()))
    }

    @Test
    fun `the first profile added becomes the current one`() {
        val r = repo()
        val masha = r.addProfile("Маша", "cat")
        assertEquals(masha.id, r.currentProfileId())
        assertEquals("Маша", r.currentProfile()?.name)
    }

    @Test
    fun `two children keep separate histories`() {
        val r = repo()
        val masha = r.addProfile("Маша", "cat")
        val petya = r.addProfile("Петя", "dog")

        r.markCompleted(masha.id, "sort_fruits_01")
        r.recordSession(masha.id, SessionRecord("sort_fruits_01", atEpochMs = 1, mistakes = 2))
        r.setSkillState(masha.id, mapOf("matching" to 0.6))

        assertEquals(emptySet(), r.getCompletedSceneIds(petya.id), "Petya has not played anything")
        assertEquals(emptyList(), r.getSessions(petya.id))
        assertEquals(emptyMap(), r.getSkillState(petya.id))

        assertEquals(setOf("sort_fruits_01"), r.getCompletedSceneIds(masha.id))
        assertEquals(1, r.getSessions(masha.id).size)
        assertEquals(0.6, r.getSkillState(masha.id)["matching"])
    }

    @Test
    fun `what one child unlocks stays locked for the other`() {
        val r = repo()
        val a = r.addProfile("А", "cat")
        val b = r.addProfile("Б", "dog")
        r.markCompleted(a.id, "memory_cards_01")

        assertTrue("memory_cards_01" in r.getCompletedSceneIds(a.id))
        assertTrue(
            "memory_cards_01" !in r.getCompletedSceneIds(b.id),
            "a sequel must not open for a child who never played the first part"
        )
    }

    @Test
    fun `switching profiles switches whose record is in use`() {
        val r = repo()
        val a = r.addProfile("А", "cat")
        val b = r.addProfile("Б", "dog")

        r.setCurrentProfile(a.id)
        r.markCompleted(r.currentChildId(), "x")
        r.setCurrentProfile(b.id)
        r.markCompleted(r.currentChildId(), "y")

        assertEquals(setOf("x"), r.getCompletedSceneIds(a.id))
        assertEquals(setOf("y"), r.getCompletedSceneIds(b.id))
    }

    @Test
    fun `two children with the same name stay separate`() {
        val r = repo()
        val first = r.addProfile("Саша", "cat")
        val second = r.addProfile("Саша", "dog")
        assertNotEquals(first.id, second.id)

        r.markCompleted(first.id, "x")
        assertEquals(emptySet(), r.getCompletedSceneIds(second.id))
    }

    @Test
    fun `removing a profile takes its record with it`() {
        val r = repo()
        val a = r.addProfile("А", "cat")
        r.markCompleted(a.id, "x")
        r.recordSession(a.id, SessionRecord("x", atEpochMs = 1))
        r.setSkillState(a.id, mapOf("memory" to 0.4))

        r.removeProfile(a.id)

        assertTrue(r.getProfiles().none { it.id == a.id })
        assertEquals(emptySet(), r.getCompletedSceneIds(a.id), "a deleted child leaves nothing behind")
        assertEquals(emptyList(), r.getSessions(a.id))
        assertEquals(emptyMap(), r.getSkillState(a.id))
        assertNull(r.currentProfileId(), "the app must not point at a profile that is gone")
    }

    @Test
    fun `renaming keeps the history`() {
        val r = repo()
        val a = r.addProfile("Маша", "cat")
        r.markCompleted(a.id, "x")
        r.renameProfile(a.id, "Мария")

        assertEquals("Мария", r.getProfiles().single().name)
        assertEquals(setOf("x"), r.getCompletedSceneIds(a.id))
    }

    @Test
    fun `an existing install keeps what it had already played`() {
        // What a pre-profiles install looks like on disk.
        val settings = MapSettings(
            "child_name" to "Маша",
            "completed_scenes" to "sort_fruits_01,memory_cards_01"
        )
        val r = ProgressRepository(settings)

        val profile = r.getProfiles().singleOrNull()
        assertEquals("Маша", profile?.name, "the named child becomes a real profile")
        assertEquals(profile?.id, r.currentProfileId())
        assertEquals(
            setOf("sort_fruits_01", "memory_cards_01"),
            r.getCompletedSceneIds(r.currentChildId()),
            "their unlocks must survive the upgrade"
        )
    }

    @Test
    fun `migration runs once and does not overwrite later play`() {
        val settings = MapSettings("child_name" to "Маша", "completed_scenes" to "a")
        ProgressRepository(settings).let { it.markCompleted(it.currentChildId(), "b") }

        val reopened = ProgressRepository(settings)
        assertEquals(setOf("a", "b"), reopened.getCompletedSceneIds(reopened.currentChildId()))
        assertContentEquals(listOf("Маша"), reopened.getProfiles().map { it.name })
    }
}
