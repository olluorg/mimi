package mimi.core.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mimi.core.behavior.GameEvent
import mimi.core.behaviors.AwaitResponseBehavior
import mimi.core.model.BehaviorRef
import mimi.core.model.EntityLifecycleState
import mimi.core.model.PropertyValue
import mimi.core.model.SCENE_ENTITY_ID
import mimi.core.plugin.DefaultFeedbackRegistry
import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext
import mimi.core.world.EntityDefinition
import mimi.core.world.LevelDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneBehaviorBinding
import mimi.core.world.SceneDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The speech mechanics: a word is modelled, the game waits, and the child's
 * voice — or an adult's word for it — answers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpeechBehaviorsTest {

    /** Everything the scene said out loud, in order. */
    private class Recorder : FeedbackChannel {
        override val type = "recorder"
        override val isAvailable = true
        val heard = mutableListOf<Pair<String, Map<String, String>>>()
        override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
            heard += feedbackType to params
        }
        fun spoken() = heard.filter { it.first == "tts" }.map { it.second["text"] }
        fun count(type: String) = heard.count { it.first == type }
    }

    private fun word(id: String, speech: String, reward: String? = null) = EntityDefinition(
        id           = id,
        components   = listOf("tap"),
        properties   = buildMap {
            put("position", PropertyValue.Vec2(500f, 400f))
            put("speech", PropertyValue.Text(speech))
            if (reward != null) put("reward", PropertyValue.Text(reward))
        },
        tags         = mapOf("group" to "targets"),
        initialState = EntityLifecycleState.INACTIVE
    )

    private fun turnScene(config: Map<String, String> = emptyMap(), turns: Int = 2) = SceneDefinition(
        sceneId  = "speech_test",
        entities = listOf(word("cow", "Му!", reward = "_sounds/animals/cow.mp3"), word("car", "Би-би!")),
        objectives = listOf(
            ObjectiveDefinition(id = "turns", type = "interactionCount", config = mapOf("count" to "$turns"))
        ),
        behaviors = listOf(
            SceneBehaviorBinding(BehaviorRef("progressTracker")),
            SceneBehaviorBinding(BehaviorRef("awaitResponse", mapOf(
                "firstLeadMs" to "1000", "leadMs" to "500", "waitMs" to "4000", "rewardMs" to "1000"
            ) + config))
        )
    )

    private fun TestScope.start(scene: SceneDefinition, recorder: Recorder): EngineSession {
        val registry = DefaultFeedbackRegistry().also { it.register(recorder) }
        return EngineBuilder()
            .withDefaultPlugins(defaultConstraintRegistry())
            .withFeedbackRegistry(registry)
            .build(scene, backgroundScope as CoroutineScope, clock = { testScheduler.currentTime })
            .also { runCurrent() }
    }

    private fun EngineSession.current(): String? =
        (worldState.value.getProperty(SCENE_ENTITY_ID, AwaitResponseBehavior.KEY_CURRENT) as? PropertyValue.Text)?.value

    private suspend fun EngineSession.childSays(peak: Float = 0.6f) =
        dispatch(GameEvent.Vocalization(durationMs = 600L, peak = peak, tick = 0L))

    @Test
    fun `the word is modelled once the instruction has had its turn`() = runTest {
        val recorder = Recorder()
        val session = start(turnScene(), recorder)

        assertEquals(emptyList(), recorder.spoken(), "nothing may talk over the scene's own instruction")
        assertEquals(EntityLifecycleState.ACTIVE, session.worldState.value.getEntityState("cow"))
        assertEquals(EntityLifecycleState.INACTIVE, session.worldState.value.getEntityState("car"),
            "one word at a time — a board of pictures would be a choice, not a turn")

        advanceTimeBy(1300); runCurrent()
        assertEquals(listOf("Му!"), recorder.spoken())
    }

    @Test
    fun `any voice after the model answers the turn`() = runTest {
        val recorder = Recorder()
        val session = start(turnScene(), recorder)
        advanceTimeBy(1300); runCurrent()
        advanceTimeBy(2000); runCurrent()

        session.childSays()
        runCurrent()

        assertEquals(EntityLifecycleState.TENTATIVE, session.worldState.value.getEntityState("cow"))
        assertTrue(recorder.heard.any { it.first == "sound" && it.second["path"] == "_sounds/animals/cow.mp3" },
            "the cow moos back when the child says «му»")
        assertEquals(1, session.metrics().vocalAttempts)

        advanceTimeBy(1600); runCurrent()
        assertEquals("car", session.current(), "the next word follows the reward")
    }

    @Test
    fun `the model coming out of the speaker does not answer itself`() = runTest {
        val recorder = Recorder()
        val session = start(turnScene(), recorder)
        advanceTimeBy(1300); runCurrent()

        // Right after the model: this is the device's own voice in the microphone.
        session.childSays()
        runCurrent()

        assertEquals(EntityLifecycleState.ACTIVE, session.worldState.value.getEntityState("cow"))
        assertEquals(0, session.worldState.value.getCounter("responses"))
    }

    @Test
    fun `silence is modelled again and then let go and never scored as a mistake`() = runTest {
        val recorder = Recorder()
        val session = start(turnScene(mapOf("maxPrompts" to "2")), recorder)

        advanceTimeBy(1300); runCurrent()           // first model
        advanceTimeBy(4300); runCurrent()           // second model
        assertEquals(listOf("Му!", "Му!"), recorder.spoken())

        advanceTimeBy(4300); runCurrent()           // gives up on the turn
        advanceTimeBy(1300); runCurrent()           // and moves on

        assertEquals("car", session.current())
        val metrics = session.metrics()
        assertEquals(1, metrics.unanswered)
        assertEquals(0, metrics.mistakes, "not answering yet is not a mistake")
    }

    @Test
    fun `the adult can vouch for an attempt the microphone missed`() = runTest {
        val recorder = Recorder()
        val session = start(turnScene(), recorder)
        advanceTimeBy(1300); runCurrent()

        session.confirmSpeechAttempt()
        runCurrent()

        assertEquals(EntityLifecycleState.TENTATIVE, session.worldState.value.getEntityState("cow"))
        assertEquals(1, session.metrics().adultConfirmed)
        assertEquals(0, session.metrics().vocalAttempts)
    }

    @Test
    fun `pressing the picture hears it again without answering`() = runTest {
        val recorder = Recorder()
        val session = start(turnScene(), recorder)
        advanceTimeBy(1300); runCurrent()

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(500f, 400f), tick = 0L, source = "cow"))
        runCurrent()

        assertEquals(listOf("Му!", "Му!"), recorder.spoken())
        assertEquals(EntityLifecycleState.ACTIVE, session.worldState.value.getEntityState("cow"))
    }

    @Test
    fun `in an AAC scene the pressed picture is the answer`() = runTest {
        val recorder = Recorder()
        val session = start(turnScene(mapOf("accept" to "tap,adult")), recorder)
        advanceTimeBy(1300); runCurrent()

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(500f, 400f), tick = 0L, source = "cow"))
        runCurrent()

        assertEquals(EntityLifecycleState.TENTATIVE, session.worldState.value.getEntityState("cow"))
        assertEquals(1L, session.worldState.value.getCounter("tapResponses"))
    }

    @Test
    fun `the scene ends after the planned turns answered or not`() = runTest {
        val recorder = Recorder()
        val session = start(turnScene(), recorder)
        advanceTimeBy(1300); runCurrent()
        session.confirmSpeechAttempt(); runCurrent()
        advanceTimeBy(1600); runCurrent()
        session.confirmSpeechAttempt(); runCurrent()

        assertTrue(session.worldState.value.allLevelsComplete())
    }

    @Test
    fun `a random drill never asks the same word twice in a row`() = runTest {
        val recorder = Recorder()
        val scene = turnScene(mapOf("order" to "random", "trials" to "12"), turns = 12)
        val session = start(scene, recorder)
        advanceTimeBy(1300); runCurrent()

        val asked = mutableListOf<String>()
        repeat(12) {
            asked += session.current()!!
            session.confirmSpeechAttempt(); runCurrent()
            advanceTimeBy(1600); runCurrent()
        }
        asked.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b, "blocked practice by accident: $asked") }
        assertTrue(session.worldState.value.allLevelsComplete())
    }

    @Test
    fun `praise thins out over a drill`() {
        val praised = (0 until 12).count { AwaitResponseBehavior.praiseDue(it, 12) }
        assertTrue(praised in 7..9, "every turn early, fewer later — got $praised of 12")
        assertTrue((0 until 4).all { AwaitResponseBehavior.praiseDue(it, 12) })
    }

    @Test
    fun `each level of a ladder starts its own turns`() = runTest {
        fun level(id: String, vararg words: Pair<String, String>) = LevelDefinition(
            levelId    = id,
            entities   = words.map { (wid, sp) -> word(wid, sp) },
            objectives = listOf(ObjectiveDefinition("turns", "interactionCount", mapOf("count" to "1")))
        )
        val scene = SceneDefinition(
            sceneId  = "ladder_test",
            levels   = listOf(level("sounds", "a" to "А-а"), level("words", "home" to "Дом")),
            behaviors = listOf(SceneBehaviorBinding(BehaviorRef("awaitResponse", mapOf(
                "firstLeadMs" to "1000", "rewardMs" to "500"
            ))))
        )
        val session = start(scene, Recorder())
        advanceTimeBy(1300); runCurrent()
        assertEquals("a", session.current())

        session.confirmSpeechAttempt(); runCurrent()
        advanceTimeBy(3000); runCurrent()
        assertEquals("home", session.current(), "the next level begins with its own first word")

        session.confirmSpeechAttempt(); runCurrent()
        assertTrue(session.worldState.value.allLevelsComplete())
        assertEquals(2, session.metrics().adultConfirmed, "attempts carry across levels")
    }

    private fun vowelScene() = turnScene().copy(
        entities = listOf(
            word("plane", "У-у!").let { it.copy(properties = it.properties + ("expectSound" to PropertyValue.Text("у"))) },
            word("car", "Би-би!")
        )
    )

    @Test
    fun `saying exactly the modelled vowel earns the bonus`() = runTest {
        val recorder = Recorder()
        val session = start(vowelScene(), recorder)
        advanceTimeBy(1300); runCurrent()
        advanceTimeBy(2000); runCurrent()

        session.dispatch(GameEvent.Vocalization(durationMs = 700L, peak = 0.6f, sound = "у", tick = 0L))
        runCurrent()

        assertEquals(1, session.metrics().exactResponses)
        assertEquals(PropertyValue.Text("bonus"), session.worldState.value.getProperty("plane", "celebrate"))
        assertTrue(recorder.heard.any { it.first == "bonus" })
    }

    @Test
    fun `another vowel still answers the turn without the bonus`() = runTest {
        val recorder = Recorder()
        val session = start(vowelScene(), recorder)
        advanceTimeBy(1300); runCurrent()
        advanceTimeBy(2000); runCurrent()

        session.dispatch(GameEvent.Vocalization(durationMs = 700L, peak = 0.6f, sound = "а", tick = 0L))
        runCurrent()

        assertEquals(EntityLifecycleState.TENTATIVE, session.worldState.value.getEntityState("plane"),
            "any voice counts — the bonus is extra, never a condition")
        assertEquals(0, session.metrics().exactResponses)
        assertTrue(recorder.heard.none { it.first == "bonus" })
    }

    @Test
    fun `every turn is logged with the word and how it went`() = runTest {
        val session = start(vowelScene(), Recorder())
        advanceTimeBy(1300); runCurrent()
        advanceTimeBy(2000); runCurrent()
        session.dispatch(GameEvent.Vocalization(durationMs = 700L, peak = 0.6f, sound = "у", tick = 0L))
        runCurrent()
        advanceTimeBy(1600); runCurrent()          // on to the second word
        advanceTimeBy(1000); runCurrent()
        advanceTimeBy(12_000); runCurrent()        // which passes in silence

        assertEquals(
            listOf(SpeechTurn("У-у!", "exact"), SpeechTurn("Би-би!", "none")),
            session.metrics().turns,
            "the adult needs to see which word was answered and which was not"
        )
    }

    // ── voiceDrive ────────────────────────────────────────────────────────────

    private fun rocketScene(rise: String = "1200") = SceneDefinition(
        sceneId  = "rocket_test",
        entities = listOf(EntityDefinition(
            id = "rocket",
            properties = mapOf("position" to PropertyValue.Vec2(500f, 800f))
        )),
        objectives = listOf(
            ObjectiveDefinition(id = "launches", type = "interactionCount", config = mapOf("count" to "2"))
        ),
        behaviors = listOf(
            SceneBehaviorBinding(BehaviorRef("voiceDrive", mapOf(
                "entity" to "rocket", "fromY" to "800", "toY" to "200", "rise" to rise, "fall" to "400"
            )))
        )
    )

    private fun EngineSession.rocketY(): Float =
        (worldState.value.getProperty("rocket", "position") as PropertyValue.Vec2).y

    @Test
    fun `a voice lifts the rocket and silence lets it sink`() = runTest {
        val session = start(rocketScene(rise = "400"), Recorder())
        advanceTimeBy(500); runCurrent()

        repeat(4) {
            advanceTimeBy(125)
            session.dispatch(GameEvent.VoiceLevel(level = 1f, tick = 0L))
        }
        runCurrent()
        val lifted = session.rocketY()
        assertTrue(lifted < 800f, "the voice should lift the rocket, it is at $lifted")

        advanceTimeBy(1500); runCurrent()
        assertTrue(session.rocketY() > lifted, "without a voice the rocket sinks back")
    }

    @Test
    fun `a long enough voice launches the rocket`() = runTest {
        val session = start(rocketScene(), Recorder())
        advanceTimeBy(500); runCurrent()

        repeat(10) {
            advanceTimeBy(125)
            session.dispatch(GameEvent.VoiceLevel(level = 1f, tick = 0L))
        }
        runCurrent()

        assertEquals(1L, session.worldState.value.getCounter("selections"), "one launch")
        advanceTimeBy(2500); runCurrent()
        assertEquals(800f, session.rocketY(), "the rocket is back on the pad for the next go")
    }

    @Test
    fun `the adult button launches it on a device without a microphone`() = runTest {
        val session = start(rocketScene(), Recorder())
        assertTrue(session.listensForVoice)

        session.confirmSpeechAttempt(); runCurrent()
        advanceTimeBy(2500); runCurrent()
        session.confirmSpeechAttempt(); runCurrent()

        assertTrue(session.worldState.value.allLevelsComplete())
    }

    @Test
    fun `the clock does not count as the child doing something`() = runTest {
        val session = start(turnScene(), Recorder())
        advanceTimeBy(5000); runCurrent()

        assertTrue(session.worldState.value.idleTicks() > 0L,
            "a silent child still has to be offered a hint and a way on")
    }
}
