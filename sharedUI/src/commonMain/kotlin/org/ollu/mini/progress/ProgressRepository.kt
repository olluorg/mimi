package org.ollu.mini.progress

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mimi.core.engine.SessionMetrics

/**
 * One finished attempt at a scene.
 *
 * The adaptive layer scores a scene on how much help the child needed, not on
 * completion alone, and an adult reviewing progress needs to see change over
 * time — so every attempt is kept, not just the set of scenes ever finished.
 */
@Serializable
data class SessionRecord(
    val sceneId:    String,
    val atEpochMs:  Long,
    val durationMs: Long = 0L,
    val mistakes:   Int  = 0,
    val hintsUsed:  Int  = 0,
    val completed:  Boolean = true,
    /**
     * What the child answered, for scenes whose purpose is answering. A list
     * because a scene may ask several times; empty everywhere else.
     */
    val choices:    List<String> = emptyList(),
    /** Sounds of the child's own the microphone heard, in a speech game. */
    val vocalAttempts:  Int = 0,
    /** Attempts the adult vouched for with the "сказал(а)" button. */
    val adultConfirmed: Int = 0,
    /** Turns that passed without an answer after the word was modelled again. */
    val unanswered:     Int = 0,
    /** Turns the child answered — by voice, picture or the adult's word. */
    val responses:      Int = 0,
    /** Answers where the vowel heard was exactly the one modelled. */
    val exactResponses: Int = 0,
    /**
     * Every turn of a speech game, as "word|outcome" — the material for the
     * per-word report an adult reads between sessions.
     */
    val turns:          List<String> = emptyList(),
    /** Whether the scene was a speech game, so the speech summary can find it. */
    val speechGame:     Boolean = false
) {
    /** Every attempt to communicate, however it was noticed. */
    val speechAttempts: Int get() = vocalAttempts + adultConfirmed

    companion object {
        /** A finished play-through, as it is kept. */
        fun completed(
            sceneId:    String,
            atEpochMs:  Long,
            metrics:    SessionMetrics,
            speechGame: Boolean
        ) = SessionRecord(
            sceneId        = sceneId,
            atEpochMs      = atEpochMs,
            durationMs     = metrics.durationMs,
            mistakes       = metrics.mistakes,
            hintsUsed      = metrics.hintsUsed,
            completed      = true,
            choices        = metrics.choices,
            vocalAttempts  = metrics.vocalAttempts,
            adultConfirmed = metrics.adultConfirmed,
            unanswered     = metrics.unanswered,
            responses      = metrics.responses,
            exactResponses = metrics.exactResponses,
            turns          = metrics.turns.map { it.encode() },
            speechGame     = speechGame
        )
    }
}

/**
 * Everything the app remembers, filed under the child it belongs to.
 *
 * A device is shared — siblings at home, a whole group in a speech room — so
 * nothing here is global. Two children sharing one record pushed each other's
 * difficulty around and unlocked each other's scenes, which is worse than no
 * history at all: the adaptive choice of the next game was being made from
 * somebody else's play.
 */
class ProgressRepository(
    private val settings: Settings = Settings()
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        migrateSingleChildData()
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    fun getProfiles(): List<ChildProfile> {
        val raw = settings.getStringOrNull(KEY_PROFILES) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ChildProfile>>(raw) }.getOrDefault(emptyList())
    }

    fun addProfile(name: String, avatar: String): ChildProfile {
        val profiles = getProfiles()
        val profile = ChildProfile(
            // Derived from the name but made unique, so two children called Sasha
            // keep separate histories.
            id     = uniqueId(name, profiles.map { it.id }.toSet()),
            name   = name.trim().ifBlank { "Ребёнок" },
            avatar = avatar
        )
        writeProfiles(profiles + profile)
        if (currentProfileId() == null) setCurrentProfile(profile.id)
        return profile
    }

    fun renameProfile(id: String, name: String) {
        writeProfiles(getProfiles().map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun setProfileAvatar(id: String, avatar: String) {
        writeProfiles(getProfiles().map { if (it.id == id) it.copy(avatar = avatar) else it })
    }

    fun setSpeechFocus(id: String, targets: List<String>) {
        writeProfiles(getProfiles().map { if (it.id == id) it.copy(speechFocus = targets.distinct()) else it })
    }

    /** The speech focus of whoever is playing now, empty when there is none. */
    fun currentSpeechFocus(): List<String> = currentProfile()?.speechFocus.orEmpty()

    // ── Microphone ────────────────────────────────────────────────────────────

    /**
     * How easily the microphone counts a sound as the child's voice, 0..1.
     *
     * A setting of the device and the room more than of the child, so it is
     * kept once rather than per profile: a quiet whisperer in a quiet room
     * needs it high, a noisy classroom needs it low.
     */
    var micSensitivity: Float
        get() = settings.getFloatOrNull(KEY_MIC_SENSITIVITY)?.coerceIn(0f, 1f) ?: DEFAULT_MIC_SENSITIVITY
        set(value) { settings[KEY_MIC_SENSITIVITY] = value.coerceIn(0f, 1f) }

    /** Removes the profile and everything recorded under it. */
    fun removeProfile(id: String) {
        writeProfiles(getProfiles().filterNot { it.id == id })
        settings.remove(sessionsKey(id))
        settings.remove(skillsKey(id))
        settings.remove(completedKey(id))
        if (settings.getStringOrNull(KEY_CURRENT) == id) {
            settings.remove(KEY_CURRENT)
        }
    }

    fun currentProfileId(): String? =
        settings.getStringOrNull(KEY_CURRENT)?.takeIf { id -> getProfiles().any { it.id == id } }

    fun currentProfile(): ChildProfile? = currentProfileId()?.let { id -> getProfiles().find { it.id == id } }

    fun setCurrentProfile(id: String) {
        settings[KEY_CURRENT] = id
    }

    /**
     * Whose record to read and write. Falls back to a single unnamed child so
     * the app works before anybody has set up a profile.
     */
    fun currentChildId(): String = currentProfileId() ?: DEFAULT_CHILD_ID

    private fun writeProfiles(profiles: List<ChildProfile>) {
        settings[KEY_PROFILES] = json.encodeToString(profiles)
    }

    private fun uniqueId(name: String, taken: Set<String>): String {
        val base = name.trim().lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "child" }
        if (base !in taken) return base
        var n = 2
        while ("${base}_$n" in taken) n++
        return "${base}_$n"
    }

    // ── Scene completion ──────────────────────────────────────────────────────

    fun markCompleted(childId: String, sceneId: String) {
        val ids = getCompletedSceneIds(childId).toMutableSet()
        if (ids.add(sceneId)) {
            settings[completedKey(childId)] = ids.sorted().joinToString(",")
        }
    }

    fun getCompletedSceneIds(childId: String): Set<String> =
        settings.getStringOrNull(completedKey(childId))
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    // ── Session history ───────────────────────────────────────────────────────

    fun recordSession(childId: String, record: SessionRecord) {
        val history = (getSessions(childId) + record).takeLast(MAX_SESSIONS)
        settings[sessionsKey(childId)] = json.encodeToString(history)
    }

    /** Attempts oldest-first. Unreadable history is treated as empty rather than crashing. */
    fun getSessions(childId: String): List<SessionRecord> {
        val raw = settings.getStringOrNull(sessionsKey(childId)) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SessionRecord>>(raw) }.getOrDefault(emptyList())
    }

    fun clearSessions(childId: String) {
        settings.remove(sessionsKey(childId))
    }

    // ── Skill profile ─────────────────────────────────────────────────────────

    fun getSkillState(childId: String): Map<String, Double> {
        val raw = settings.getStringOrNull(skillsKey(childId)) ?: return emptyMap()
        // Written as JSON since the rewrite; the older "skill=value,…" form is
        // still read so an existing install keeps its profile.
        return runCatching { json.decodeFromString<Map<String, Double>>(raw) }
            .getOrElse { parseLegacySkillState(raw) }
            .mapValues { it.value.coerceIn(0.0, 1.0) }
    }

    fun setSkillState(childId: String, skills: Map<String, Double>) {
        // Sorted so the stored blob is stable between writes rather than
        // reshuffling with every save.
        val clamped = skills
            .mapValues { it.value.coerceIn(0.0, 1.0) }
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
        settings[skillsKey(childId)] = json.encodeToString<Map<String, Double>>(clamped)
    }

    private fun parseLegacySkillState(raw: String): Map<String, Double> =
        raw.split(",").mapNotNull { item ->
            val parts = item.split("=", limit = 2)
            val skill = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            skill to value
        }.toMap()

    // ── Migration ─────────────────────────────────────────────────────────────

    /**
     * Moves an existing install's single anonymous record onto a real profile.
     *
     * Completed scenes used to be stored under one global key shared by whoever
     * held the tablet; they are handed to the child who was already named, so
     * that child keeps their unlocks instead of starting over.
     */
    private fun migrateSingleChildData() {
        if (settings.getStringOrNull(KEY_MIGRATED) != null) return

        val legacyName = settings.getStringOrNull(KEY_CHILD_NAME)?.trim().orEmpty()
        val legacyDone = settings.getStringOrNull(KEY_LEGACY_COMPLETED).orEmpty()
        val legacyChild = legacyName.ifBlank { DEFAULT_CHILD_ID }

        if (legacyDone.isNotBlank() && settings.getStringOrNull(completedKey(legacyChild)) == null) {
            settings[completedKey(legacyChild)] = legacyDone
        }
        if (legacyName.isNotBlank() && getProfiles().isEmpty()) {
            val profile = ChildProfile(id = legacyChild, name = legacyName, avatar = PROFILE_AVATARS.first())
            writeProfiles(listOf(profile))
            settings[KEY_CURRENT] = profile.id
        }
        settings[KEY_MIGRATED] = "1"
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_CURRENT  = "current_profile"
        const val KEY_MIGRATED = "profiles_migrated"
        const val KEY_MIC_SENSITIVITY = "mic_sensitivity"
        const val DEFAULT_MIC_SENSITIVITY = 0.5f

        /** Pre-profiles keys, read once during migration and then left alone. */
        const val KEY_CHILD_NAME       = "child_name"
        const val KEY_LEGACY_COMPLETED = "completed_scenes"

        const val DEFAULT_CHILD_ID = "default_child"

        // Enough history for months of daily use while keeping the blob small —
        // key-value storage holds it as one string.
        const val MAX_SESSIONS = 500

        fun sessionsKey(childId: String)  = "sessions_$childId"
        fun skillsKey(childId: String)    = "skill_state_$childId"
        fun completedKey(childId: String) = "completed_$childId"
    }
}
