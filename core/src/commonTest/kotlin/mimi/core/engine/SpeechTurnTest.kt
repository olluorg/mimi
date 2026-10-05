package mimi.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeechTurnTest {

    @Test
    fun `a turn survives encoding`() {
        val turn = SpeechTurn("му", SpeechTurn.OUTCOME_EXACT)
        assertEquals(turn, SpeechTurn.decode(turn.encode()))
        assertTrue(turn.exact && turn.answered)
    }

    @Test
    fun `separators inside a word are blanked so the log stays parseable`() {
        val encoded = SpeechTurn("a|b;c", "voice").encode()
        assertEquals(SpeechTurn("a b c", "voice"), SpeechTurn.decode(encoded))
    }

    @Test
    fun `malformed entries are dropped from a log`() {
        val log = listOf("му|voice", "broken", "|none", "мяу|none").joinToString(SpeechTurn.TURN_SEPARATOR)
        assertEquals(
            listOf(SpeechTurn("му", "voice"), SpeechTurn("мяу", SpeechTurn.OUTCOME_NONE)),
            SpeechTurn.decodeLog(log)
        )
        assertNull(SpeechTurn.decode(""))
        assertEquals(emptyList(), SpeechTurn.decodeLog("  "))
    }
}
