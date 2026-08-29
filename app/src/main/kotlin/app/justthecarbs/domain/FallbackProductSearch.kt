package app.justthecarbs.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs the primary text-search provider and, only for defined failures, the fallback.
 *
 * ```
 *  primary ──▶ Found / NoMatches ─────────────▶ returned as-is, fallback NEVER runs
 *          └─▶ Failed(eligible)   ──▶ fallback ──▶ its answer, or the PRIMARY's error if it fails too
 *          └─▶ Failed(ineligible) ────────────▶ returned as-is, fallback NEVER runs
 *          └─▶ CancellationException ─────────▶ rethrown, fallback NEVER runs
 * ```
 *
 * It is itself a [ProductSearchSource], so nothing above it — not `SearchViewModel`, not the screens
 * — knows there is more than one provider. The generation/staleness rules, the settle wait and the
 * governor all sit above this and are untouched by it: from the ViewModel's side this is one search
 * that either answers or does not.
 *
 * ## Zero results is an answer, not a failure
 *
 * The single most important rule here. A provider replying "nothing matches" has done its job, and
 * re-asking a second provider would double the request cost of the *common* case — every deliberate
 * search for something genuinely absent — while also letting the two providers disagree about
 * whether a product exists. [ProductSearchResult.NoMatches] therefore returns immediately.
 *
 * ## Which failures are worth a second request
 *
 * Only those where a *different host* plausibly answers. That is the whole test, and it is why
 * [LookupError.RATE_LIMITED] is excluded: a rate limit is this app being told it is asking too often,
 * and answering that by immediately asking somewhere else is precisely the behaviour the limit
 * exists to stop. It is also the one failure the caller already handles well — the governor records
 * the backoff and the queued query resumes on its own.
 *
 * ## Cancellation is never a failure
 *
 * A superseded query must not spend a fallback request; it must not spend anything. Because a
 * cancelled coroutine throws rather than returning a value, this class must not swallow that
 * exception — see [search]. Turning it into an ordinary failure would both trigger a pointless
 * fallback and, worse, let an abandoned query commit an error over a newer one's results.
 */
class FallbackProductSearch(
    private val primary: ProductSearchSource,
    private val fallback: ProductSearchSource,
    /** Notified as the chain progresses. Debug-only diagnostics by default; see [SearchProviderLog]. */
    private val log: SearchProviderLog = SearchProviderLog.None,
) : ProductSearchSource {

    override suspend fun search(terms: String): ProductSearchResult {
        log.onPrimaryStarted()

        // NOT wrapped in try/catch. A CancellationException must propagate untouched: catching it
        // here would convert an abandoned query into a `Failed`, which would then look
        // fallback-eligible and spend a request on a query nobody is waiting for. Every non-cancel
        // failure is already a returned `Failed` value rather than an exception, because both data
        // sources map their exceptions internally — so there is nothing legitimate left to catch.
        val primaryResult = primary.search(terms)

        when (primaryResult) {
            is ProductSearchResult.Found -> {
                log.onPrimarySucceeded(primaryResult.hits.size)
                return primaryResult
            }
            // An answer. Returned without consulting the fallback — see the class KDoc.
            ProductSearchResult.NoMatches -> {
                log.onPrimaryNoMatches()
                return primaryResult
            }
            is ProductSearchResult.Failed -> {
                log.onPrimaryFailed(primaryResult.error)
                if (!isFallbackEligible(primaryResult.error)) return primaryResult
            }
        }

        // The query may have been abandoned while the primary was failing, and a fallback request is
        // the most expensive thing this chain can do — it spends the scarce legacy budget. So the
        // obsolete case is checked explicitly rather than relied upon to surface by itself.
        //
        // Measured, not defensive: without this line the "transport ignores cancellation" test below
        // fails. A primary that finishes despite cancellation returns an ordinary `Failed`, and
        // `fallback.search` may then run to completion without ever suspending — so nothing on that
        // path would have thrown, and a superseded query would quietly buy a legacy request.
        //
        // `ensureActive` throws CancellationException, which propagates for the same reason nothing
        // above catches it: an abandoned query must produce no result at all, never a failure that
        // could land on a newer query's results.
        currentCoroutineContext().ensureActive()

        log.onFallbackStarted()
        val fallbackResult = fallback.search(terms)

        return when (fallbackResult) {
            is ProductSearchResult.Found -> {
                log.onFallbackSucceeded(fallbackResult.hits.size)
                fallbackResult
            }
            ProductSearchResult.NoMatches -> {
                log.onFallbackNoMatches()
                fallbackResult
            }
            is ProductSearchResult.Failed -> {
                log.onFallbackFailed(fallbackResult.error)
                // Both providers failed. The PRIMARY's error is reported, not the fallback's.
                //
                // The fallback is an implementation detail the user never asked for, and its
                // failure mode is frequently less representative of what went wrong: the legacy
                // endpoint answers 503 while otherwise healthy, so surfacing SERVER when the real
                // problem was that the device is offline would send the user to retry a network
                // they do not have. The primary is the provider that was actually asked.
                primaryResult
            }
        }
    }

    /**
     * True when a *different host* might plausibly answer where the primary could not.
     *
     * - [LookupError.OFFLINE] / [LookupError.TIMEOUT] — transport-level. Worth one attempt: the two
     *   providers are different hosts, and one being unreachable does not mean both are. The wasted
     *   case (the device is genuinely offline) costs one immediately-failing request, bounded by the
     *   HTTP client's own timeouts.
     * - [LookupError.SERVER] — the exact case this pass exists for, in the other direction.
     * - [LookupError.MALFORMED] — an unusable response shape. The other provider has an entirely
     *   different envelope and is unaffected by whatever broke this one.
     * - [LookupError.RATE_LIMITED] — **not eligible.** See the class KDoc.
     */
    private fun isFallbackEligible(error: LookupError): Boolean = when (error) {
        LookupError.OFFLINE,
        LookupError.TIMEOUT,
        LookupError.SERVER,
        LookupError.MALFORMED,
        -> true

        LookupError.RATE_LIMITED -> false
    }
}

/**
 * Diagnostics for the provider chain.
 *
 * Exists to answer one question during physical-device testing: *did the primary answer this query,
 * or did the fallback?* Without it a successful fallback is invisible — which is the intended user
 * experience and exactly what makes the migration impossible to validate by looking at the screen.
 *
 * **No query text is ever passed to it.** The events carry a provider stage and a result count, and
 * nothing the user typed. That follows the app's existing practice of keeping user-entered text out
 * of logs, and it means enabling these diagnostics cannot turn search into a keystroke recorder.
 *
 * [None] is the default and does nothing at all, so a release build pays no cost and R8 can fold the
 * calls away.
 */
interface SearchProviderLog {
    fun onPrimaryStarted() {}
    fun onPrimarySucceeded(hitCount: Int) {}
    fun onPrimaryNoMatches() {}
    fun onPrimaryFailed(error: LookupError) {}

    /**
     * The primary answered with matches, but not one of them could be mapped to a usable hit.
     *
     * Reported separately from [onPrimaryFailed] because the two look identical from outside — both
     * become a fallback — while meaning very different things. An ordinary failure is a host being
     * unreachable; this is a host that answered fine and said something the app could not read,
     * which is the signature of a schema change upstream and the one condition worth noticing early.
     *
     * [rawHitCount] is how many entries arrived; [reportedCount] is the total the service claimed.
     * Counts only — no query text and no record contents.
     */
    fun onPrimaryHitsUnusable(rawHitCount: Int, reportedCount: Int?) {}
    fun onFallbackStarted() {}
    fun onFallbackSucceeded(hitCount: Int) {}
    fun onFallbackNoMatches() {}
    fun onFallbackFailed(error: LookupError) {}

    object None : SearchProviderLog
}
