package app.justthecarbs.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The primary/fallback provider chain (2026-08-28 migration).
 *
 * The rules pinned here are about **request economy and honesty**, in that order of subtlety:
 *
 * - a provider that answered — with hits *or* with "nothing matches" — ends the chain, so the common
 *   case costs exactly one request;
 * - only failures where a *different host* might plausibly answer are worth a second one;
 * - a cancelled query spends nothing at all and must not be convertible into a failure.
 */
class FallbackProductSearchTest {

    /** Records what it was asked, and answers with whatever it was told to. */
    private class FakeSource(
        private val answer: (String) -> ProductSearchResult = { ProductSearchResult.NoMatches },
    ) : ProductSearchSource {
        val calls = mutableListOf<String>()

        override suspend fun search(terms: String): ProductSearchResult {
            calls += terms
            return answer(terms)
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

    private fun found(vararg barcodes: String) =
        ProductSearchResult.Found(barcodes.map(::hit))

    private fun failed(error: LookupError) = ProductSearchResult.Failed(error)

    /** Every event the chain reported, in order. Used to prove the diagnostics are wired. */
    private class RecordingLog : SearchProviderLog {
        val events = mutableListOf<String>()
        override fun onPrimaryStarted() { events += "primary-start" }
        override fun onPrimarySucceeded(hitCount: Int) { events += "primary-ok($hitCount)" }
        override fun onPrimaryNoMatches() { events += "primary-none" }
        override fun onPrimaryFailed(error: LookupError) { events += "primary-fail($error)" }
        override fun onFallbackStarted() { events += "fallback-start" }
        override fun onFallbackSucceeded(hitCount: Int) { events += "fallback-ok($hitCount)" }
        override fun onFallbackNoMatches() { events += "fallback-none" }
        override fun onFallbackFailed(error: LookupError) { events += "fallback-fail($error)" }
    }

    // ---- A: primary success --------------------------------------------------------------------

    @Test
    fun `a successful primary search never calls the fallback`() = runTest {
        val primary = FakeSource { found("100", "200") }
        val fallback = FakeSource()

        val result = FallbackProductSearch(primary, fallback).search("chocolate")

        assertEquals(listOf("100", "200"), (result as ProductSearchResult.Found).hits.map { it.barcode })
        assertEquals(listOf("chocolate"), primary.calls)
        assertTrue("the fallback must not be consulted", fallback.calls.isEmpty())
    }

    // ---- B: primary legitimately empty ---------------------------------------------------------

    /**
     * The most important economy rule in the chain. A valid "nothing matches" is an answer, and
     * re-asking a second provider would double the cost of every deliberate search for something
     * that genuinely is not in the database.
     */
    @Test
    fun `a valid no-results answer never calls the fallback`() = runTest {
        val primary = FakeSource { ProductSearchResult.NoMatches }
        val fallback = FakeSource { found("999") }

        val result = FallbackProductSearch(primary, fallback).search("zzzqqxx")

        assertEquals(ProductSearchResult.NoMatches, result)
        assertTrue("no-results is an answer, not a failure", fallback.calls.isEmpty())
    }

    // ---- C/D/E: fallback-eligible failures ------------------------------------------------------

    @Test
    fun `a primary network failure falls back exactly once`() = runTest {
        val primary = FakeSource { failed(LookupError.OFFLINE) }
        val fallback = FakeSource { found("500") }

        val result = FallbackProductSearch(primary, fallback).search("gouda")

        assertEquals(listOf("500"), (result as ProductSearchResult.Found).hits.map { it.barcode })
        assertEquals(listOf("gouda"), fallback.calls)
    }

    @Test
    fun `a primary server failure falls back`() = runTest {
        val fallback = FakeSource { found("1") }

        FallbackProductSearch(FakeSource { failed(LookupError.SERVER) }, fallback).search("q")

        assertEquals(1, fallback.calls.size)
    }

    @Test
    fun `a primary timeout falls back`() = runTest {
        val fallback = FakeSource { found("1") }

        FallbackProductSearch(FakeSource { failed(LookupError.TIMEOUT) }, fallback).search("q")

        assertEquals(1, fallback.calls.size)
    }

    @Test
    fun `a malformed primary response falls back`() = runTest {
        val fallback = FakeSource { found("1") }

        FallbackProductSearch(FakeSource { failed(LookupError.MALFORMED) }, fallback).search("q")

        assertEquals(1, fallback.calls.size)
    }

    /**
     * The one failure that is deliberately **not** eligible.
     *
     * A rate limit is this app being told it asks too often; answering that by immediately asking a
     * second host is the exact behaviour the limit exists to stop.
     */
    @Test
    fun `a rate-limited primary does NOT fall back`() = runTest {
        val primary = FakeSource { ProductSearchResult.Failed(LookupError.RATE_LIMITED, 5_000L) }
        val fallback = FakeSource { found("1") }

        val result = FallbackProductSearch(primary, fallback).search("q")

        assertTrue(fallback.calls.isEmpty())
        val failure = result as ProductSearchResult.Failed
        assertEquals(LookupError.RATE_LIMITED, failure.error)
        // The server's stated wait survives the chain, so the governor can honour it exactly.
        assertEquals(5_000L, failure.retryAfterMs)
    }

    // ---- F: cancellation -------------------------------------------------------------------------

    /**
     * A superseded query must spend nothing.
     *
     * The chain must not catch `CancellationException`: doing so would turn an abandoned query into
     * a `Failed`, which then looks fallback-eligible and buys a request nobody is waiting for.
     *
     * Driven by cancelling a real coroutine parked inside the primary, rather than by throwing the
     * exception directly — the latter would pass even if the chain wrapped the call in a bare
     * `try/catch (Throwable)`.
     */
    @Test
    fun `a cancelled primary search never calls the fallback`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val primary = object : ProductSearchSource {
            override suspend fun search(terms: String): ProductSearchResult {
                entered.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val fallback = FakeSource { found("1") }
        val chain = FallbackProductSearch(primary, fallback)

        val job: Job = launch { chain.search("superseded") }
        entered.await()
        job.cancelAndJoin()

        assertTrue("a cancelled query must not spend a fallback request", fallback.calls.isEmpty())
        assertTrue(job.isCancelled)
    }

    /**
     * The same guarantee against a transport that does **not** honour cancellation promptly.
     *
     * A fake that unwinds instantly would let this pass even if the chain mishandled the exception;
     * `NonCancellable` reproduces the real hazard, the same lesson the search staleness tests
     * already record.
     */
    @Test
    fun `cancellation still prevents fallback when the transport ignores it`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val primary = object : ProductSearchSource {
            override suspend fun search(terms: String): ProductSearchResult =
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                    ProductSearchResult.Failed(LookupError.SERVER)
                }
        }
        val fallback = FakeSource { found("1") }
        val chain = FallbackProductSearch(primary, fallback)

        val job = launch { chain.search("superseded") }
        entered.await()
        job.cancel()
        release.complete(Unit)
        job.join()

        assertTrue(
            "an abandoned query must not reach the fallback even if its transport finished",
            fallback.calls.isEmpty(),
        )
    }

    // ---- I: both fail ----------------------------------------------------------------------------

    /**
     * When both providers fail the **primary's** error is reported.
     *
     * The fallback is an implementation detail the user never asked for, and the legacy endpoint's
     * habitual 503 would otherwise mask a genuine offline state — sending the user to retry a
     * network they do not have.
     */
    @Test
    fun `when both providers fail the primary's error is what surfaces`() = runTest {
        val primary = FakeSource { failed(LookupError.OFFLINE) }
        val fallback = FakeSource { failed(LookupError.SERVER) }

        val result = FallbackProductSearch(primary, fallback).search("q")

        assertEquals(LookupError.OFFLINE, (result as ProductSearchResult.Failed).error)
        assertEquals(1, fallback.calls.size)
    }

    @Test
    fun `a fallback that finds nothing reports no-matches rather than the primary's failure`() = runTest {
        val primary = FakeSource { failed(LookupError.SERVER) }
        val fallback = FakeSource { ProductSearchResult.NoMatches }

        val result = FallbackProductSearch(primary, fallback).search("q")

        assertEquals(ProductSearchResult.NoMatches, result)
    }

    // ---- boundary / diagnostics ------------------------------------------------------------------

    /**
     * The chain is itself a [ProductSearchSource], which is what keeps every caller — ViewModels,
     * screens, the repository — unaware that more than one provider exists.
     */
    @Test
    fun `the chain is substitutable for a single provider`() {
        val chain: ProductSearchSource =
            FallbackProductSearch(FakeSource(), FakeSource())

        assertTrue(chain is ProductSearchSource)
    }

    @Test
    fun `diagnostics record which provider answered`() = runTest {
        val log = RecordingLog()

        FallbackProductSearch(
            primary = FakeSource { failed(LookupError.SERVER) },
            fallback = FakeSource { found("1", "2") },
            log = log,
        ).search("q")

        assertEquals(
            listOf("primary-start", "primary-fail(SERVER)", "fallback-start", "fallback-ok(2)"),
            log.events,
        )
    }

    @Test
    fun `diagnostics distinguish a primary answer from a fallback one`() = runTest {
        val log = RecordingLog()

        FallbackProductSearch(FakeSource { found("1") }, FakeSource(), log).search("q")

        assertEquals(listOf("primary-start", "primary-ok(1)"), log.events)
        assertFalse(log.events.any { it.startsWith("fallback") })
    }

    @Test
    fun `the terms reach both providers unchanged`() = runTest {
        val primary = FakeSource { failed(LookupError.SERVER) }
        val fallback = FakeSource { found("1") }

        FallbackProductSearch(primary, fallback).search("  spaced query  ")

        // The chain transforms nothing; normalization belongs to the ViewModel and each data source
        // trims for itself. Two normalization sites would let them disagree about what was asked.
        assertEquals(listOf("  spaced query  "), primary.calls)
        assertEquals(listOf("  spaced query  "), fallback.calls)
    }

    @Test
    fun `a rate-limited failure with no stated wait keeps its null`() = runTest {
        val primary = FakeSource { failed(LookupError.RATE_LIMITED) }

        val result = FallbackProductSearch(primary, FakeSource()).search("q")

        assertNull((result as ProductSearchResult.Failed).retryAfterMs)
    }
}
