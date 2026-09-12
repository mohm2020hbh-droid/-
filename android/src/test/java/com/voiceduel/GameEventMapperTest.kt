package com.voiceduel

import com.voiceduel.data.GameEvent
import com.voiceduel.data.GameEventMapper
import com.voiceduel.data.GameProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each server message must map onto the event the ViewModel expects. */
@RunWith(RobolectricTestRunner::class)
class GameEventMapperTest {

    private fun map(json: String): GameEvent? =
        GameProtocol.decodeMessage(json)?.let(GameEventMapper::fromMessage)

    @Test
    fun `round start carries the sound and the performer`() {
        val event = map(
            """
            {"type":"round_start","payload":{
              "round_number":2,"total_rounds":6,"sound_id":"cat","sound_name":"مواء قطة",
              "sound_emoji":"🐱","performer_id":"abc123","countdown_seconds":8}}
            """.trimIndent(),
        )

        assertEquals(
            GameEvent.RoundStart(2, 6, "cat", "مواء قطة", "🐱", "abc123", 8),
            event,
        )
    }

    @Test
    fun `round result carries the running totals`() {
        val event = map(
            """
            {"type":"round_result","payload":{"round_number":1,"performer_id":"a",
             "score":80,"total_scores":{"a":80,"b":0},"timed_out":false}}
            """.trimIndent(),
        ) as GameEvent.RoundResult

        assertEquals(80, event.score)
        assertEquals(mapOf("a" to 80, "b" to 0), event.totalScores)
        assertEquals(false, event.timedOut)
    }

    @Test
    fun `a draw maps the null winner to null rather than the string null`() {
        val event = map(
            """{"type":"game_over","payload":{"winner_id":null,"final_scores":{"a":50,"b":50}}}""",
        ) as GameEvent.GameOver

        assertNull(event.winnerId)
        assertEquals(mapOf("a" to 50, "b" to 50), event.finalScores)
    }

    @Test
    fun `a win names the winner`() {
        val event = map(
            """{"type":"game_over","payload":{"winner_id":"a","final_scores":{"a":90,"b":10}}}""",
        ) as GameEvent.GameOver

        assertEquals("a", event.winnerId)
    }

    @Test
    fun `errors map to a failure event carrying the reason`() {
        val event = map("""{"type":"error","payload":{"reason":"invalid_code"}}""")

        assertEquals(GameEvent.Failed(GameProtocol.ERR_INVALID_CODE), event)
    }

    @Test
    fun `an opponent leaving is reported with its reason`() {
        val event = map(
            """{"type":"opponent_disconnected","payload":{"reason":"disconnected"}}""",
        ) as GameEvent.OpponentLeft

        assertEquals("disconnected", event.reason)
    }

    @Test
    fun `unknown message types are ignored`() {
        assertNull(map("""{"type":"something_new","payload":{}}"""))
    }

    @Test
    fun `connected carries this device's player id`() {
        val event = map("""{"type":"connected","payload":{"player_id":"deadbeef"}}""")

        assertTrue(event is GameEvent.Connected)
        assertEquals("deadbeef", (event as GameEvent.Connected).playerId)
    }
}
