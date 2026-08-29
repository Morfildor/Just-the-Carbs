package app.justthecarbs.domain

/**
 * Applies the shared Open Food Facts search budget to whatever source it wraps.
 *
 * ## Why the governor moved down here
 *
 * It used to live in `SearchViewModel`, which was correct while there was exactly one provider and
 * that provider was the rate-limited legacy endpoint. With two providers it is in the wrong place:
 * the ViewModel sits *above* the provider boundary, so a budget applied there would make every
 * primary Search-a-licious query wait out an interval sized for a different service's limits —
 * turning a 150 ms search into a 7 second one for no reason.
 *
 * The budget belongs to the endpoint it protects. Wrapping the legacy source is what keeps
 * [RemoteSearchGovernor] enforcing exactly what it was written to enforce — the 10 reads/min/IP that
 * the legacy `cgi/search.pl` actually imposes — while leaving the primary free.
 *
 * ## What this does *not* do
 *
 * It does not wait. A caller that is refused is refused immediately, with
 * [ProductSearchResult.Failed]`(`[LookupError.RATE_LIMITED]`)` — the same shape the server itself
 * sends when it refuses, so nothing downstream needs a new case. Blocking instead would sit a
 * fallback request on a 7 second delay behind a primary that has already failed, which is the
 * stacked-timeout UX this migration must not create.
 *
 * That is a deliberate difference from the ViewModel's own governor wait, which *does* delay: there,
 * the wait is the whole point (the query is queued and runs when the budget frees). Here the caller
 * is a fallback that only exists because something already went wrong, and making the user wait
 * twice for one search is worse than telling them promptly.
 *
 * ## Rate-limited is not fallback-eligible, and that matters here
 *
 * [FallbackProductSearch] never falls back *from* a rate limit, and this class is the fallback, so a
 * refusal it generates ends the chain — it cannot loop back around. The refusal is also recorded on
 * the governor exactly as a server 429 is, so the shared budget stays honest about a request this
 * app declined to make.
 */
class GovernedProductSearch(
    private val delegate: ProductSearchSource,
    private val governor: RemoteSearchGovernor,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ProductSearchSource {

    override suspend fun search(terms: String): ProductSearchResult {
        val at = nowMs()
        if (!governor.permitsRequestAt(at)) {
            return ProductSearchResult.Failed(
                LookupError.RATE_LIMITED,
                // The caller is told how long the existing block has left rather than being given a
                // fresh backoff to impose. Inventing one here would extend the block every time a
                // request bounced off it, so a busy period could push the budget further and
                // further out — a backoff that grows because it is working.
                retryAfterMs = governor.waitUntilPermittedMs(at).takeIf { it > 0 },
            )
        }

        // Recorded before the call and for every outcome, matching the governor's own contract: a
        // request that fails costs the same quota as one that works.
        governor.recordAttempt(at)

        val result = delegate.search(terms)

        // A server 429 still extends the shared block, whichever provider asked. The budget is a
        // property of the address, not of this call.
        if (result is ProductSearchResult.Failed && result.error == LookupError.RATE_LIMITED) {
            governor.recordRateLimited(nowMs(), result.retryAfterMs)
        }
        return result
    }
}
