package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared Open Food Facts search budget.
 *
 * Every assertion here is about **request accounting**, not about the UI. The governor's whole job
 * is to make "how often may this app ask OFF to search" a single answerable question, so these
 * tests are written as arithmetic against a fake clock rather than as behaviour of a screen.
 */
class RemoteSearchGovernorTest {

    /** A hand-cranked clock. Nothing here waits on real time. */
    private class FakeClock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
    }

    private fun governor(clock: FakeClock) = RemoteSearchGovernor(nowMs = clock)

    @Test
    fun `the first request is permitted immediately`() {
        val clock = FakeClock()
        val governor = governor(clock)

        assertTrue(governor.permitsRequestAt(clock.now))
        assertEquals(0L, governor.waitUntilPermittedMs(clock.now))
    }

    @Test
    fun `a first request at a nonzero clock is permitted and reports no wait`() {
        // Regression, and the reason this case exists separately from the t=0 one above. The first
        // implementation computed a deadline of Long.MIN_VALUE for "no rule applies" and returned
        // `deadline - now`, which UNDERFLOWS to a huge positive wait for any nonzero clock. At t=0
        // it happened to stay negative, so `the first request is permitted immediately` passed
        // while every real first search parked for ~292 million years. A device clock is never 0.
        val clock = FakeClock(now = 1_787_918_400_000L)
        val governor = governor(clock)

        assertEquals(0L, governor.waitUntilPermittedMs(clock.now))
        assertTrue(governor.permitsRequestAt(clock.now))
    }

    @Test
    fun `a wait is never absurdly large`() {
        // The general form of the same defect: whatever the state, a reported wait must be
        // something a caller can sanely delay for. Anything past the ceiling is arithmetic having
        // gone wrong, not a real instruction.
        val clock = FakeClock(now = 1_787_918_400_000L)
        val governor = governor(clock)
        governor.recordAttempt(clock.now)
        governor.recordRateLimited(clock.now, retryAfterMs = null)

        assertTrue(governor.waitUntilPermittedMs(clock.now) <= RemoteSearchGovernor.MAX_BACKOFF_MS)
    }

    @Test
    fun `a second request inside the minimum interval is not permitted`() {
        val clock = FakeClock()
        val governor = governor(clock)
        governor.recordAttempt(clock.now)

        clock.now = RemoteSearchGovernor.MIN_INTERVAL_MS - 1
        assertFalse(governor.permitsRequestAt(clock.now))
        assertEquals(1L, governor.waitUntilPermittedMs(clock.now))
    }

    @Test
    fun `a request exactly at the minimum interval is permitted`() {
        // The boundary is inclusive deliberately: an exclusive one would make the effective interval
        // one millisecond longer than the constant says, which is the kind of drift that makes a
        // documented budget stop matching the code.
        val clock = FakeClock()
        val governor = governor(clock)
        governor.recordAttempt(clock.now)

        clock.now = RemoteSearchGovernor.MIN_INTERVAL_MS
        assertTrue(governor.permitsRequestAt(clock.now))
    }

    @Test
    fun `an attempt is recorded whatever its outcome`() {
        // The governor is told a request STARTED. It is never told whether it succeeded, because a
        // failed request costs the same quota as a successful one — pretending otherwise is how a
        // failing endpoint gets hammered hardest.
        val clock = FakeClock()
        val governor = governor(clock)

        governor.recordAttempt(clock.now)
        clock.now = 100

        assertFalse(governor.permitsRequestAt(clock.now))
    }

    // ---- Server-imposed backoff ------------------------------------------------------------

    @Test
    fun `a rate-limit response with no retry-after applies the fallback backoff`() {
        val clock = FakeClock()
        val governor = governor(clock)
        governor.recordAttempt(clock.now)

        governor.recordRateLimited(clock.now, retryAfterMs = null)

        // Far past the ordinary interval, still blocked: the server's refusal outlives our own
        // cooldown, which is the entire point of tracking it separately.
        clock.now = RemoteSearchGovernor.MIN_INTERVAL_MS + 1
        assertFalse(governor.permitsRequestAt(clock.now))

        clock.now = RemoteSearchGovernor.RATE_LIMIT_FALLBACK_BACKOFF_MS
        assertTrue(governor.permitsRequestAt(clock.now))
    }

    @Test
    fun `a retry-after longer than the fallback is respected`() {
        val clock = FakeClock()
        val governor = governor(clock)

        governor.recordRateLimited(clock.now, retryAfterMs = 120_000)

        clock.now = RemoteSearchGovernor.RATE_LIMIT_FALLBACK_BACKOFF_MS + 1
        assertFalse("the server asked for longer than our fallback", governor.permitsRequestAt(clock.now))

        clock.now = 120_000
        assertTrue(governor.permitsRequestAt(clock.now))
    }

    @Test
    fun `a retry-after shorter than the ordinary interval does not shorten it`() {
        // A server saying "come back in 1 second" does not license breaking the client's own budget.
        // blockedUntil is the MAXIMUM of the two rules, never whichever is looser.
        val clock = FakeClock()
        val governor = governor(clock)
        governor.recordAttempt(clock.now)

        governor.recordRateLimited(clock.now, retryAfterMs = 1_000)

        clock.now = 1_001
        assertFalse(governor.permitsRequestAt(clock.now))
        clock.now = RemoteSearchGovernor.MIN_INTERVAL_MS
        assertTrue(governor.permitsRequestAt(clock.now))
    }

    @Test
    fun `a second rate limit does not shorten an existing longer backoff`() {
        val clock = FakeClock()
        val governor = governor(clock)
        governor.recordRateLimited(clock.now, retryAfterMs = 120_000)

        clock.now = 1_000
        governor.recordRateLimited(clock.now, retryAfterMs = 5_000)

        clock.now = 6_001
        assertFalse("a shorter later backoff must not release an earlier longer one", governor.permitsRequestAt(clock.now))
        clock.now = 120_000
        assertTrue(governor.permitsRequestAt(clock.now))
    }

    @Test
    fun `a negative or absurd retry-after falls back rather than being trusted`() {
        // Retry-After is attacker- and bug-reachable text off the wire. A negative value must not
        // become a backoff in the past, which would read as "you may request again immediately".
        val clock = FakeClock()
        val governor = governor(clock)

        governor.recordRateLimited(clock.now, retryAfterMs = -5_000)

        clock.now = 1
        assertFalse(governor.permitsRequestAt(clock.now))
        clock.now = RemoteSearchGovernor.RATE_LIMIT_FALLBACK_BACKOFF_MS
        assertTrue(governor.permitsRequestAt(clock.now))
    }

    @Test
    fun `an implausibly long retry-after is capped`() {
        // A server (or a proxy) answering "Retry-After: 86400" must not disable search for a day.
        val clock = FakeClock()
        val governor = governor(clock)

        governor.recordRateLimited(clock.now, retryAfterMs = 86_400_000)

        clock.now = RemoteSearchGovernor.MAX_BACKOFF_MS
        assertTrue(governor.permitsRequestAt(clock.now))
    }

    @Test
    fun `a successful attempt after a backoff clears the block`() {
        val clock = FakeClock()
        val governor = governor(clock)
        governor.recordRateLimited(clock.now, retryAfterMs = null)

        clock.now = RemoteSearchGovernor.RATE_LIMIT_FALLBACK_BACKOFF_MS
        assertTrue(governor.permitsRequestAt(clock.now))
        governor.recordAttempt(clock.now)

        // Back to the ordinary interval — the backoff is not sticky.
        clock.now += RemoteSearchGovernor.MIN_INTERVAL_MS
        assertTrue(governor.permitsRequestAt(clock.now))
    }

    // ---- The budget claim ------------------------------------------------------------------

    @Test
    fun `sustained demand cannot exceed the documented per-minute ceiling`() {
        // The headline claim, asserted as a count rather than as a comment. A caller that asks as
        // fast as it is allowed to — which is the worst case any UI can produce — gets this many
        // requests in 60 seconds, and OFF's documented search budget is 10.
        val clock = FakeClock()
        val governor = governor(clock)

        var attempts = 0
        while (clock.now <= 60_000) {
            if (governor.permitsRequestAt(clock.now)) {
                governor.recordAttempt(clock.now)
                attempts++
            }
            clock.now += 10
        }

        assertEquals(RemoteSearchGovernor.MAX_REQUESTS_PER_MINUTE, attempts)
        assertTrue(
            "the ceiling must stay under Open Food Facts' documented 10/min search budget",
            attempts < 10,
        )
    }

    @Test
    fun `the documented ceiling matches the interval it is derived from`() {
        // Guards against the constant and its stated consequence drifting apart — the comment on
        // MAX_REQUESTS_PER_MINUTE is arithmetic, so it can be checked rather than trusted.
        assertEquals(
            (60_000 / RemoteSearchGovernor.MIN_INTERVAL_MS).toInt() + 1,
            RemoteSearchGovernor.MAX_REQUESTS_PER_MINUTE,
        )
    }
}
