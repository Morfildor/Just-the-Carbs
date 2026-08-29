package app.justthecarbs.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The short-lived search memory (2026-08-28 efficiency pass).
 *
 * Two properties carry the whole feature, and they pull in opposite directions:
 *
 * - **a repeated query must cost nothing** — otherwise there is no point;
 * - **nothing transient may be remembered** — a cached 503 would keep an outage on screen for five
 *   minutes after the service recovered, which is strictly worse than the request it saved.
 *
 * Every test below counts *delegate calls*, never elapsed time or return values alone: the saving
 * is the request that did not happen, so that is what is asserted. Time is virtual throughout —
 * `nowMs` is a settable `Long`, so a five-minute expiry costs no wall-clock delay and there is no
 * sleep anywhere in this file.
 */
class CachedProductSearchTest {

    /** Records what it was asked and answers as instructed. Call order is the assertion subject. */
    private class FakeSource(
        private var answer: (String) -> ProductSearchResult = { ProductSearchResult.NoMatches },
    ) : ProductSearchSource {
        val calls = mutableListOf<String>()

        override suspend fun search(terms: String): ProductSearchResult {
            calls += terms
            return answer(terms)
        }

        fun answerWith(next: (String) -> ProductSearchResult) {
            answer = next
        }
    }

    private fun hit(barcode: String) = ProductSearchHit(
        barcode = barcode,
        name = "Product $barcode",
        brand = null,
        packageQuantity = null,
        carbsPer100 = BigDecimal("10"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    private fun found(vararg barcodes: String) = ProductSearchResult.Found(barcodes.map(::hit))

    /** Virtual clock. Tests advance it explicitly; nothing here waits on real time. */
    private class FakeClock(var nowMs: Long = 1_000_000L)

    private fun cache(source: ProductSearchSource, clock: FakeClock) =
        CachedProductSearch(source, nowMs = { clock.nowMs })

    // ---------------------------------------------------------------- hits and misses

    @Test
    fun `an exact repeated query is answered from memory`() = runTest {
        val source = FakeSource { found("A", "B") }
        val clock = FakeClock()
        val cache = cache(source, clock)

        val first = cache.search("chocolate")
        val second = cache.search("chocolate")

        assertEquals(listOf("chocolate"), source.calls)
        assertEquals(first, second)
    }

    @Test
    fun `surrounding whitespace resolves to the same entry`() = runTest {
        val source = FakeSource { found("A") }
        val cache = cache(source, FakeClock())

        cache.search("chocolate")
        val padded = cache.search("   chocolate   ")

        // One request for both: the two strings normalize to the same search, exactly as they do
        // for the ViewModel and for the data source itself.
        assertEquals(listOf("chocolate"), source.calls)
        assertEquals(found("A"), padded)
    }

    @Test
    fun `a different query is not a hit`() = runTest {
        val source = FakeSource { terms -> found(terms.uppercase()) }
        val cache = cache(source, FakeClock())

        cache.search("chocolate")
        cache.search("gouda")

        assertEquals(listOf("chocolate", "gouda"), source.calls)
    }

    @Test
    fun `case is not folded, because the provider is free to treat it as meaningful`() = runTest {
        val source = FakeSource { found("A") }
        val cache = cache(source, FakeClock())

        cache.search("chocolate")
        cache.search("Chocolate")

        // Merging these would mean answering one query with another query's results. The cache
        // remembers answers; it does not decide which questions are equivalent.
        assertEquals(listOf("chocolate", "Chocolate"), source.calls)
    }

    // ---------------------------------------------------------------- expiry

    @Test
    fun `an entry older than the TTL is refetched`() = runTest {
        val source = FakeSource { found("A") }
        val clock = FakeClock()
        val cache = cache(source, clock)

        cache.search("chocolate")
        clock.nowMs += CachedProductSearch.TTL_MS
        cache.search("chocolate")

        assertEquals(listOf("chocolate", "chocolate"), source.calls)
    }

    @Test
    fun `an entry one millisecond inside the TTL is still used`() = runTest {
        val source = FakeSource { found("A") }
        val clock = FakeClock()
        val cache = cache(source, clock)

        cache.search("chocolate")
        clock.nowMs += CachedProductSearch.TTL_MS - 1
        cache.search("chocolate")

        // Pins the boundary from the other side, so a change from `<` to `<=` cannot pass unnoticed.
        assertEquals(listOf("chocolate"), source.calls)
    }

    @Test
    fun `an entry stamped in the future is treated as expired, not as fresh`() = runTest {
        val source = FakeSource { found("A") }
        val clock = FakeClock()
        val cache = cache(source, clock)

        cache.search("chocolate")
        // The clock moves backwards — a timezone or NTP correction. Read naively, `now - stored` is
        // negative and the entry looks younger than the TTL, which would pin it until real time
        // caught up. Same hazard, and the same rule, as the product-refresh freshness window.
        clock.nowMs -= 60_000
        cache.search("chocolate")

        assertEquals(listOf("chocolate", "chocolate"), source.calls)
    }

    @Test
    fun `an expired entry does not keep occupying a slot`() = runTest {
        val source = FakeSource { found("A") }
        val clock = FakeClock()
        val cache = cache(source, clock)

        cache.search("stale")
        clock.nowMs += CachedProductSearch.TTL_MS
        // Reading it is what evicts it. Fill the rest of the cache and then check that the entries
        // stored *after* the expiry all survived — which they cannot if the dead one is still held.
        cache.search("stale")

        repeat(CachedProductSearch.MAX_ENTRIES - 1) { cache.search("q$it") }
        source.calls.clear()
        repeat(CachedProductSearch.MAX_ENTRIES - 1) { cache.search("q$it") }

        assertEquals(emptyList<String>(), source.calls)
    }

    // ---------------------------------------------------------------- bounds

    @Test
    fun `the cache never holds more than its bound`() = runTest {
        val source = FakeSource { found("A") }
        val cache = cache(source, FakeClock())

        repeat(CachedProductSearch.MAX_ENTRIES + 10) { cache.search("q$it") }
        source.calls.clear()

        // Read the SURVIVORS first. Order matters here and getting it wrong makes the test lie:
        // reading an evicted entry refetches it, which stores it again and evicts one of the
        // survivors — so checking the evicted ones first would manufacture the misses it then
        // "found". The newest MAX_ENTRIES must all answer without a request.
        repeat(CachedProductSearch.MAX_ENTRIES) { cache.search("q${it + 10}") }
        assertEquals(
            "every entry inside the bound must still be held",
            emptyList<String>(),
            source.calls,
        )

        // And the ten oldest must be gone, proving the bound actually evicted rather than grew.
        repeat(10) { cache.search("q$it") }
        assertEquals(10, source.calls.size)
    }

    @Test
    fun `eviction is least-recently-used, not merely oldest-first`() = runTest {
        val source = FakeSource { found("A") }
        val cache = cache(source, FakeClock())

        repeat(CachedProductSearch.MAX_ENTRIES) { cache.search("q$it") }
        // Touch the oldest entry, making it the most recently used.
        cache.search("q0")
        // One more entry forces exactly one eviction.
        cache.search("overflow")
        source.calls.clear()

        cache.search("q0")
        assertEquals(
            "the re-read entry must have survived — that is the difference between LRU and FIFO",
            emptyList<String>(),
            source.calls,
        )

        cache.search("q1")
        assertEquals(
            "the genuinely least-recently-used entry is the one that goes",
            listOf("q1"),
            source.calls,
        )
    }

    // ---------------------------------------------------------------- what is NOT cached

    @Test
    fun `a successful result is remembered`() = runTest {
        val source = FakeSource { found("A") }
        val cache = cache(source, FakeClock())

        cache.search("chocolate")
        val again = cache.search("chocolate")

        assertEquals(listOf("chocolate"), source.calls)
        assertEquals(found("A"), again)
    }

    @Test
    fun `no failure is ever remembered`() = runTest {
        // Every failure the app can produce, each checked separately: a single loop that stopped at
        // the first would leave the rest unproven, and RATE_LIMITED in particular is the one whose
        // caching would be most damaging (it would outlive the backoff it describes).
        for (error in LookupError.entries) {
            val source = FakeSource { ProductSearchResult.Failed(error) }
            val cache = cache(source, FakeClock())

            cache.search("chocolate")
            cache.search("chocolate")

            assertEquals(
                "$error must stay retryable",
                listOf("chocolate", "chocolate"),
                source.calls,
            )
        }
    }

    @Test
    fun `a malformed response is not remembered`() = runTest {
        val source = FakeSource { ProductSearchResult.Failed(LookupError.MALFORMED) }
        val cache = cache(source, FakeClock())

        cache.search("chocolate")
        cache.search("chocolate")

        // Called out separately from the loop above because MALFORMED is the fallback-eligible
        // failure: caching it would suppress the legacy provider for five minutes, which is the
        // exact defect the previous pass removed from the data source.
        assertEquals(listOf("chocolate", "chocolate"), source.calls)
    }

    @Test
    fun `a no-matches answer is not remembered`() = runTest {
        val source = FakeSource { ProductSearchResult.NoMatches }
        val cache = cache(source, FakeClock())

        cache.search("zzzznotaproduct")
        cache.search("zzzznotaproduct")

        // An answer, not a failure — but the one users react to by editing and coming back, and the
        // one whose staleness reads as "this product does not exist". Left uncached deliberately.
        assertEquals(listOf("zzzznotaproduct", "zzzznotaproduct"), source.calls)
    }

    @Test
    fun `a failure after a success does not evict the good entry`() = runTest {
        val source = FakeSource { found("A") }
        val clock = FakeClock()
        val cache = cache(source, clock)

        cache.search("chocolate")
        source.answerWith { ProductSearchResult.Failed(LookupError.SERVER) }
        clock.nowMs += CachedProductSearch.TTL_MS
        cache.search("chocolate")

        // The expired read refetched and got a 503. Nothing was written, so a later attempt still
        // goes to the network rather than being answered by a remembered failure.
        source.calls.clear()
        source.answerWith { found("B") }
        val recovered = cache.search("chocolate")

        assertEquals(listOf("chocolate"), source.calls)
        assertEquals(found("B"), recovered)
    }

    @Test
    fun `a cancelled search writes nothing`() = runTest {
        val gate = CompletableDeferred<ProductSearchResult>()
        val source = object : ProductSearchSource {
            val calls = mutableListOf<String>()
            override suspend fun search(terms: String): ProductSearchResult {
                calls += terms
                return gate.await()
            }
        }
        val cache = CachedProductSearch(source, nowMs = { 1_000L })

        val job = launch(StandardTestDispatcher(testScheduler)) { cache.search("chocolate") }
        testScheduler.advanceUntilIdle()
        job.cancelAndJoin()

        // The delegate never returned, so there is nothing to store — and the abandoned query must
        // not leave a half-formed entry behind for the next one to read.
        gate.complete(found("A"))
        source.calls.clear()
        cache.search("chocolate")

        assertEquals(listOf("chocolate"), source.calls)
    }

    @Test
    fun `a blank query is passed straight through and occupies no entry`() = runTest {
        val source = FakeSource { ProductSearchResult.NoMatches }
        val cache = cache(source, FakeClock())

        cache.search("   ")
        cache.search("")

        // Clearing the field must not consume one of the bounded slots, and must not be answerable
        // from a previous blank "search" that was never a search at all.
        assertEquals(listOf("   ", ""), source.calls)
    }

    // ---------------------------------------------------------------- the scenario it exists for

    @Test
    fun `chocolate then gouda then chocolate costs two requests, not three`() = runTest {
        val source = FakeSource { terms -> found(terms) }
        val clock = FakeClock()
        val cache = cache(source, clock)

        cache.search("chocolate")
        cache.search("gouda")
        clock.nowMs += 30_000 // well inside the TTL
        val third = cache.search("chocolate")

        assertEquals(listOf("chocolate", "gouda"), source.calls)
        assertEquals(found("chocolate"), third)
    }

    @Test
    fun `the same query submitted repeatedly costs one request`() = runTest {
        val source = FakeSource { found("A") }
        val cache = cache(source, FakeClock())

        repeat(10) { cache.search("chocolate") }

        assertEquals(listOf("chocolate"), source.calls)
    }

    @Test
    fun `the hits handed back on a hit are the ones that were stored`() = runTest {
        val source = FakeSource { found("A", "B", "C") }
        val cache = cache(source, FakeClock())

        val first = cache.search("chocolate") as ProductSearchResult.Found
        val second = cache.search("chocolate") as ProductSearchResult.Found

        // Order matters: it is the provider's relevance ranking, and a cache that reordered results
        // would change what the user sees on the second look at the same query.
        assertEquals(first.hits.map { it.barcode }, second.hits.map { it.barcode })
        assertEquals(listOf("A", "B", "C"), second.hits.map { it.barcode })
    }

    // ---------------------------------------------------------------- chain behaviour

    @Test
    fun `a cache hit never reaches the legacy fallback`() = runTest {
        val primary = FakeSource { found("A") }
        val fallback = FakeSource { found("LEGACY") }
        val chain = FallbackProductSearch(
            primary = cache(primary, FakeClock()),
            fallback = fallback,
        )

        chain.search("chocolate")
        chain.search("chocolate")

        assertEquals(listOf("chocolate"), primary.calls)
        assertEquals(
            "a hit is an ordinary primary success, so the chain stops there by construction",
            emptyList<String>(),
            fallback.calls,
        )
    }

    @Test
    fun `a fallback answer is never filed under the primary's cache`() = runTest {
        val primary = FakeSource { ProductSearchResult.Failed(LookupError.SERVER) }
        val fallback = FakeSource { found("LEGACY") }
        val chain = FallbackProductSearch(
            primary = cache(primary, FakeClock()),
            fallback = fallback,
        )

        chain.search("chocolate")
        chain.search("chocolate")

        // The cache wraps the primary only. Both searches must reach both providers: the primary
        // failed (nothing to cache) and the legacy answer belongs to a different provider, so
        // storing it would make a cached result's provenance unanswerable.
        assertEquals(listOf("chocolate", "chocolate"), primary.calls)
        assertEquals(listOf("chocolate", "chocolate"), fallback.calls)
    }

    @Test
    fun `a cached answer still carries usable hits`() = runTest {
        val source = FakeSource { found("8712345678901") }
        val cache = cache(source, FakeClock())

        cache.search("chocolate")
        val hit = (cache.search("chocolate") as ProductSearchResult.Found).hits.single()

        // A cached hit is discovery data and must remain exactly that: a barcode to look up, never
        // a Product. The canonical lookup after selection is what supplies every figure the user
        // acts on, and this class cannot reach it.
        assertEquals("8712345678901", hit.barcode)
        assertNotNull(hit.name)
    }

    @Test
    fun `two callers sharing one instance share its entries`() = runTest {
        val source = FakeSource { found("A") }
        val shared = cache(source, FakeClock())

        // Home's inline search and the search screen are two ViewModels holding one instance from
        // AppContainer. Moving between them with the same query in mind is the case this saves.
        val fromHome = shared.search("chocolate")
        val fromSearchScreen = shared.search("chocolate")

        assertEquals(listOf("chocolate"), source.calls)
        assertEquals(fromHome, fromSearchScreen)
    }

    @Test
    fun `the bound and the TTL are the documented values`() {
        // Pins the two constants the brief specifies, so a change to either is a deliberate edit to
        // a test rather than a silent adjustment of the app's request economy.
        assertEquals(20, CachedProductSearch.MAX_ENTRIES)
        assertEquals(5 * 60 * 1000L, CachedProductSearch.TTL_MS)
        assertTrue(CachedProductSearch.TTL_MS > 0)
    }

    @Test
    fun `a hit returns without suspending on the delegate`() = runTest {
        val gate = CompletableDeferred<ProductSearchResult>()
        var callCount = 0
        val source = object : ProductSearchSource {
            override suspend fun search(terms: String): ProductSearchResult {
                callCount++
                return if (callCount == 1) found("A") else gate.await()
            }
        }
        val cache = CachedProductSearch(source, nowMs = { 1_000L })

        cache.search("chocolate")
        // If this reached the delegate it would suspend on `gate` forever and the test would hang
        // rather than fail — so completing at all is the assertion.
        val second = cache.search("chocolate")

        assertEquals(1, callCount)
        assertSame(ProductSearchResult.Found::class.java, second.javaClass)
    }
}
