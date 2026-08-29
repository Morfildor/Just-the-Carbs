package app.justthecarbs.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The legacy provider's budget, applied at the provider rather than in the ViewModel
 * (2026-08-28 migration).
 *
 * The move is what lets the two providers have different limits: the primary is paced by a short
 * interval and the legacy fallback keeps the 7 s floor sized to its documented 10 reads/min/IP.
 */
class GovernedProductSearchTest {

    private class FakeSource(
        private val answer: (String) -> ProductSearchResult = { ProductSearchResult.NoMatches },
    ) : ProductSearchSource {
        val calls = mutableListOf<String>()
        override suspend fun search(terms: String): ProductSearchResult {
            calls += terms
            return answer(terms)
        }
    }

    private fun found() = ProductSearchResult.Found(
        listOf(
            ProductSearchHit(
                barcode = "1",
                name = "P",
                brand = null,
                packageQuantity = null,
                carbsPer100 = BigDecimal("10"),
                basis = NutritionBasis.PER_100_G,
                imageUrl = null,
            ),
        ),
    )

    /** A clock the test moves by hand, shared by the governor and the wrapper. */
    private class FakeClock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test
    fun `the first request is permitted and reaches the delegate`() = runTest {
        val clock = FakeClock()
        val delegate = FakeSource { found() }
        val governed = GovernedProductSearch(delegate, RemoteSearchGovernor(nowMs = clock), clock)

        val result = governed.search("gouda")

        assertTrue(result is ProductSearchResult.Found)
        assertEquals(listOf("gouda"), delegate.calls)
    }

    /**
     * A refused request is refused **immediately**, not delayed.
     *
     * Blocking here would put a 7 s wait behind a primary that has already failed — the stacked
     * wait this migration must not create. The caller is told with the same shape the server itself
     * uses when it refuses, so nothing downstream needs a new case.
     */
    @Test
    fun `a request inside the interval is refused without reaching the delegate`() = runTest {
        val clock = FakeClock()
        val delegate = FakeSource { found() }
        val governed = GovernedProductSearch(delegate, RemoteSearchGovernor(nowMs = clock), clock)

        governed.search("first")
        clock.now += 1_000
        val second = governed.search("second")

        assertEquals(listOf("first"), delegate.calls)
        val failure = second as ProductSearchResult.Failed
        assertEquals(LookupError.RATE_LIMITED, failure.error)
        // Told how long the EXISTING block has left, not given a fresh one.
        assertEquals(RemoteSearchGovernor.MIN_INTERVAL_MS - 1_000, failure.retryAfterMs)
    }

    @Test
    fun `a request after the interval is permitted again`() = runTest {
        val clock = FakeClock()
        val delegate = FakeSource { found() }
        val governed = GovernedProductSearch(delegate, RemoteSearchGovernor(nowMs = clock), clock)

        governed.search("first")
        clock.now += RemoteSearchGovernor.MIN_INTERVAL_MS
        governed.search("second")

        assertEquals(listOf("first", "second"), delegate.calls)
    }

    /**
     * The quota is spent by the attempt, not by its outcome — a failing request cost the same as a
     * working one, and a client that only counts successes retries hardest when the endpoint is
     * least able to answer.
     */
    @Test
    fun `a failed request still spends the budget`() = runTest {
        val clock = FakeClock()
        val delegate = FakeSource { ProductSearchResult.Failed(LookupError.SERVER) }
        val governed = GovernedProductSearch(delegate, RemoteSearchGovernor(nowMs = clock), clock)

        governed.search("first")
        clock.now += 1_000
        val second = governed.search("second")

        assertEquals(1, delegate.calls.size)
        assertEquals(LookupError.RATE_LIMITED, (second as ProductSearchResult.Failed).error)
    }

    @Test
    fun `a server 429 extends the shared block beyond the ordinary interval`() = runTest {
        val clock = FakeClock()
        val governor = RemoteSearchGovernor(nowMs = clock)
        val delegate = FakeSource {
            ProductSearchResult.Failed(LookupError.RATE_LIMITED, retryAfterMs = 60_000L)
        }
        val governed = GovernedProductSearch(delegate, governor, clock)

        governed.search("first")

        // Past our own interval, but the server's refusal is still in force.
        clock.now += RemoteSearchGovernor.MIN_INTERVAL_MS + 1
        assertTrue(governor.isServerBackoffActive(clock.now))
        val second = governed.search("second")

        assertEquals(1, delegate.calls.size)
        assertEquals(LookupError.RATE_LIMITED, (second as ProductSearchResult.Failed).error)
    }

    /**
     * Two governed sources sharing one governor share one budget — the property that stops Home and
     * the search screen spending the legacy allowance twice over.
     */
    @Test
    fun `a shared governor is one budget across two call sites`() = runTest {
        val clock = FakeClock()
        val governor = RemoteSearchGovernor(nowMs = clock)
        val home = FakeSource { found() }
        val screen = FakeSource { found() }

        GovernedProductSearch(home, governor, clock).search("from home")
        clock.now += 1_000
        val fromScreen = GovernedProductSearch(screen, governor, clock).search("from screen")

        assertEquals(1, home.calls.size)
        assertTrue("the second screen must not get its own allowance", screen.calls.isEmpty())
        assertEquals(LookupError.RATE_LIMITED, (fromScreen as ProductSearchResult.Failed).error)
    }

    /**
     * The primary's own governor is a different instance with a different interval, and pacing one
     * provider must not pace the other.
     */
    @Test
    fun `the primary interval is far shorter than the legacy one`() = runTest {
        val clock = FakeClock()
        val primaryGovernor =
            RemoteSearchGovernor(RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS, clock)
        val delegate = FakeSource { found() }
        val governed = GovernedProductSearch(delegate, primaryGovernor, clock)

        governed.search("first")
        clock.now += RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS
        governed.search("second")

        assertEquals(listOf("first", "second"), delegate.calls)
        assertTrue(
            "the primary floor must stay well under the legacy budget",
            RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS < RemoteSearchGovernor.MIN_INTERVAL_MS,
        )
    }

    /**
     * The whole point of the split, expressed as one assertion: a busy legacy budget does not stop
     * the primary from answering.
     */
    @Test
    fun `an exhausted legacy budget does not block the primary`() = runTest {
        val clock = FakeClock()
        val legacyGovernor = RemoteSearchGovernor(nowMs = clock)
        val primaryGovernor =
            RemoteSearchGovernor(RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS, clock)

        val legacy = FakeSource { found() }
        val primary = FakeSource { found() }

        GovernedProductSearch(legacy, legacyGovernor, clock).search("spend it")
        clock.now += RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS

        val result = GovernedProductSearch(primary, primaryGovernor, clock).search("still fine")

        assertTrue(result is ProductSearchResult.Found)
        assertEquals(1, primary.calls.size)
    }

    /**
     * A refusal generated here can never loop back into the chain: [FallbackProductSearch] does not
     * treat a rate limit as fallback-eligible, and this class *is* the fallback.
     */
    @Test
    fun `a governor refusal ends the chain rather than retrying`() = runTest {
        val clock = FakeClock()
        val legacyGovernor = RemoteSearchGovernor(nowMs = clock)
        val legacy = FakeSource { found() }
        val governedLegacy = GovernedProductSearch(legacy, legacyGovernor, clock)

        // Spend the legacy budget, then drive the whole chain with a failing primary.
        governedLegacy.search("warm up")
        clock.now += 1_000

        val chain = FallbackProductSearch(
            primary = FakeSource { ProductSearchResult.Failed(LookupError.SERVER) },
            fallback = governedLegacy,
        )
        val result = chain.search("q")

        // The fallback was consulted and refused itself, without reaching the network a second time.
        assertEquals(1, legacy.calls.size)
        // The PRIMARY's error is what surfaces when both fail — the chain's documented rule. The
        // governor's own refusal is an internal detail; reporting RATE_LIMITED here would tell the
        // user this app is asking too often when the actual problem was the primary's 5xx.
        assertEquals(LookupError.SERVER, (result as ProductSearchResult.Failed).error)
    }

    @Test
    fun `a permitted request that is not rate limited records no backoff`() = runTest {
        val clock = FakeClock()
        val governor = RemoteSearchGovernor(nowMs = clock)
        val governed = GovernedProductSearch(FakeSource { found() }, governor, clock)

        governed.search("q")

        assertFalse(governor.isServerBackoffActive(clock.now))
    }
}
