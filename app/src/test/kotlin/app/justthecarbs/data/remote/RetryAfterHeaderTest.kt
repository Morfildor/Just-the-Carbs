package app.justthecarbs.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `Retry-After` is text off the wire, so every case here is about **refusing to trust it** as much
 * as about reading it. A wrong answer in the permissive direction — null, or a small number — makes
 * the client retry into a server that has just refused it, which is the failure this whole pass
 * exists to stop.
 */
class RetryAfterHeaderTest {

    /** A fixed "now" so the HTTP-date form is deterministic. 2026-08-28T12:00:00Z. */
    private val now = 1_787_918_400_000L

    @Test
    fun `delta-seconds is read as milliseconds`() {
        assertEquals(60_000L, RetryAfterHeader.parseMs("60", now))
    }

    @Test
    fun `surrounding whitespace does not defeat it`() {
        assertEquals(30_000L, RetryAfterHeader.parseMs("  30  ", now))
    }

    @Test
    fun `zero is a valid instruction and is not confused with absent`() {
        assertEquals(0L, RetryAfterHeader.parseMs("0", now))
    }

    @Test
    fun `an absent header is null`() {
        assertNull(RetryAfterHeader.parseMs(null, now))
    }

    @Test
    fun `a blank header is null`() {
        assertNull(RetryAfterHeader.parseMs("   ", now))
    }

    @Test
    fun `unparseable text is null rather than zero`() {
        // The distinction that matters: null routes to the governor's conservative fallback, while
        // 0 would read as "retry immediately" — the opposite of what an unreadable refusal means.
        assertNull(RetryAfterHeader.parseMs("soon", now))
        assertNull(RetryAfterHeader.parseMs("60s", now))
    }

    @Test
    fun `a negative delta is null rather than a backoff in the past`() {
        assertNull(RetryAfterHeader.parseMs("-30", now))
    }

    @Test
    fun `an absurdly large delta does not overflow`() {
        // Long.MAX_VALUE seconds * 1000 wraps negative, which would read as "retry immediately".
        val parsed = RetryAfterHeader.parseMs("9223372036854775807", now)
        assertEquals("an unrepresentable delta must not wrap", null, parsed)
    }

    @Test
    fun `an HTTP-date in the future becomes the remaining milliseconds`() {
        // RFC 9110 permits either form and real servers send both.
        assertEquals(120_000L, RetryAfterHeader.parseMs("Fri, 28 Aug 2026 12:02:00 GMT", now))
    }

    @Test
    fun `an HTTP-date in the past is null rather than negative`() {
        assertNull(RetryAfterHeader.parseMs("Fri, 28 Aug 2026 11:00:00 GMT", now))
    }
}
