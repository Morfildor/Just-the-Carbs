package app.justthecarbs.domain

/**
 * The app's single Open Food Facts **search** budget.
 *
 * ## Why this exists
 *
 * OFF's search endpoint allows roughly 10 reads/min/IP — a budget shared by everyone behind one
 * address, not one per user and not one per screen. Search-as-you-type with only a debounce in
 * front of it does not respect that: a 500 ms debounce turns a minute of ordinary typing-with-pauses
 * into far more than ten requests, and the endpoint answers 503 under load. That is what put
 * *"The product database is unavailable"* on screen mid-word on real hardware — a server refusal
 * this app had earned, not a network fault.
 *
 * A debounce is a *typing* heuristic: it asks "has the user stopped?". A budget is a *client*
 * property: it asks "may this app ask again yet?". Those are different questions, and no debounce
 * value answers the second — which is why this is a separate object rather than a larger delay.
 *
 * ## What it is not
 *
 * It does not queue, retry, or perform requests, and it holds no query. A caller asks whether it
 * may go now, and tells it when a request *started*. Keeping it that small is what makes it
 * shareable: [app.justthecarbs.ui.search.SearchViewModel] instances come and go with navigation,
 * and a governor that owned in-flight work could not outlive them.
 *
 * ## Ownership
 *
 * One instance for the process, held by `AppContainer`. Home's inline search and the search screen
 * are two separate `SearchViewModel`s, so a per-ViewModel cooldown would let them spend the budget
 * twice over — the two most likely screens to be used one after the other.
 *
 * Not a `object`/global: this app's existing pattern is the manual `AppContainer`, and a process
 * global would also be untestable without reset hooks that tests could forget to call.
 *
 * ## Thread-safety
 *
 * Every call arrives from a ViewModel coroutine on `Dispatchers.Main`, so the mutable state below
 * has a single confinement. `@Volatile` guards visibility only for the diagnostic reads.
 */
class RemoteSearchGovernor(
    /**
     * The floor between two requests **for the provider this instance governs**.
     *
     * Parameterised (2026-08-28) rather than read from [MIN_INTERVAL_MS] directly, because the app
     * now has two search providers with genuinely different limits and one number cannot be right
     * for both. The legacy `cgi/search.pl` budget is 10 reads/min/IP and keeps the default; the
     * Search-a-licious service documents no such limit and was measured answering twelve
     * back-to-back requests with no spacing, so pacing it at 7 s would impose a wait its own service
     * does not ask for.
     *
     * Defaulted, so every existing construction site keeps the legacy behaviour unchanged.
     */
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    /**
     * Wall clock, injectable so pacing is testable on virtual time.
     *
     * Deliberately the **last** parameter: existing callers construct this as
     * `RemoteSearchGovernor { scheduler.currentTime }`, and Kotlin binds a trailing lambda to the
     * final parameter. Putting [minIntervalMs] after it would silently break every one of those
     * call sites — which is exactly what happened when this parameter was first added in the other
     * order, caught by the compiler rather than by a test.
     */
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    /** When the most recent request **started**, or null before the first one. */
    @Volatile
    private var lastAttemptAtMs: Long? = null

    /**
     * When a server-imposed backoff expires, or null when none applies.
     *
     * Tracked separately from [lastAttemptAtMs] rather than by pretending an attempt happened
     * later: a 429 is a statement about the *server's* willingness, it can outlast our own interval
     * by minutes, and collapsing the two would lose whichever rule is stricter.
     */
    @Volatile
    private var blockedUntilMs: Long? = null

    /** True when a remote search may be issued at [atMs]. */
    fun permitsRequestAt(atMs: Long = nowMs()): Boolean = waitUntilPermittedMs(atMs) == 0L

    /**
     * How long a caller must wait before [permitsRequestAt] turns true — 0 when it already is.
     *
     * The scheduler delays by exactly this rather than polling, so a cooldown costs one suspended
     * coroutine rather than a wakeup per tick.
     */
    fun waitUntilPermittedMs(atMs: Long = nowMs()): Long {
        // Each rule contributes a wait, never an absolute "earliest" sentinel. An absent rule is 0
        // — "imposes no wait" — rather than Long.MIN_VALUE: subtracting a clock reading from
        // MIN_VALUE UNDERFLOWS to a huge positive number, i.e. "wait ~292 million years", which is
        // indistinguishable at the call site from a legitimate long backoff. Measured, not
        // theorised: the first version of this method did exactly that and parked every first
        // search forever, and `permitsRequestAt` at t=0 could not see it because MIN_VALUE - 0 is
        // still negative. Comparing waits rather than deadlines keeps the arithmetic in range.
        val intervalWait = lastAttemptAtMs?.let { it + minIntervalMs - atMs } ?: 0L
        val backoffWait = blockedUntilMs?.let { it - atMs } ?: 0L
        // The stricter of the two rules always wins. A server's short Retry-After never shortens
        // our own interval, and our interval never releases a longer server backoff.
        return maxOf(intervalWait, backoffWait).coerceAtLeast(0L)
    }

    /**
     * A remote search **started** at [atMs].
     *
     * Recorded at the start, not at completion, and recorded for every outcome — success, empty,
     * network failure, 429 alike. A request that failed cost the same quota as one that worked, and
     * a client that only counts successes retries hardest exactly when the endpoint is least able
     * to answer.
     *
     * Cancellation does not refund it either. Once the call has left the device the quota is spent,
     * whatever this app subsequently does with the response.
     */
    fun recordAttempt(atMs: Long = nowMs()) {
        lastAttemptAtMs = atMs
    }

    /**
     * The server refused with 429 at [atMs], optionally naming how long to wait.
     *
     * [retryAfterMs] comes off the wire and is treated as untrusted: a negative or missing value
     * falls back to [RATE_LIMIT_FALLBACK_BACKOFF_MS] rather than becoming a backoff in the past,
     * and an implausibly long one is capped at [MAX_BACKOFF_MS] so a stray `Retry-After: 86400`
     * cannot disable search for a day.
     *
     * Never shortens an existing backoff — a later, laxer refusal does not release an earlier
     * stricter one.
     */
    fun recordRateLimited(atMs: Long = nowMs(), retryAfterMs: Long?) {
        val backoff = (retryAfterMs?.takeIf { it > 0 } ?: RATE_LIMIT_FALLBACK_BACKOFF_MS)
            .coerceAtMost(MAX_BACKOFF_MS)
        val until = atMs + backoff
        blockedUntilMs = maxOf(blockedUntilMs ?: Long.MIN_VALUE, until)
    }

    /** True while a **server-imposed** backoff is in force — distinct from our own cooldown. */
    fun isServerBackoffActive(atMs: Long = nowMs()): Boolean =
        (blockedUntilMs ?: Long.MIN_VALUE) > atMs

    companion object {
        /**
         * The floor between two remote search requests.
         *
         * 7 s gives at most 9 requests per rolling minute against a documented budget of 10,
         * leaving headroom for the barcode-lookup traffic that shares the same address. Sized to
         * the budget, **not** to how responsive a demo feels: shortening it to make tests or a
         * hand-driven walkthrough snappier is exactly how the 503s came back.
         */
        const val MIN_INTERVAL_MS = 7_000L

        /**
         * The floor between two **primary** (Search-a-licious) requests.
         *
         * Not a rate limit — that service documents none, and twelve back-to-back requests were
         * measured answering 200 in 136–202 ms with no throttling. It is a cheap guard against a
         * pathological caller, sized well below the settle wait so it never delays an ordinary
         * search: with [app.justthecarbs.ui.search.SearchViewModel.REMOTE_SEARCH_SETTLE_MS] at
         * 500 ms, a user typing continuously cannot reach this floor anyway.
         *
         * Deliberately **not** [MIN_INTERVAL_MS]. Applying the legacy endpoint's 7 s budget to a
         * service that does not impose it would spend the entire latency win this migration exists
         * to capture.
         */
        const val PRIMARY_MIN_INTERVAL_MS = 300L

        /**
         * The most requests this client can issue in any 60 s window, by arithmetic: one at t=0
         * plus one every [MIN_INTERVAL_MS] thereafter. Asserted, not asserted-by-comment — see
         * `the documented ceiling matches the interval it is derived from`.
         */
        const val MAX_REQUESTS_PER_MINUTE = 9

        /**
         * Applied when a 429 arrives without a usable `Retry-After`.
         *
         * Deliberately far longer than [MIN_INTERVAL_MS]: the server has just said the ordinary
         * pace was too fast, so resuming at that same pace is the one response guaranteed to be
         * refused again.
         */
        const val RATE_LIMIT_FALLBACK_BACKOFF_MS = 60_000L

        /** Ceiling on any server-named backoff, so one bad header cannot disable search. */
        const val MAX_BACKOFF_MS = 300_000L
    }
}
