package app.justthecarbs.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.RemoteSearchGovernor
import app.justthecarbs.domain.SavedProductSearch
import app.justthecarbs.domain.SavedProductSearchSource
import app.justthecarbs.domain.SearchQueryMatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    /**
     * A remote search for the current query is wanted and has not yet completed.
     *
     * Covers all three waits — settling, waiting for the shared request budget, and the call
     * itself. The screen draws one continuous progress affordance across them because they are one
     * thing to the user ("newer results are coming"); [awaitingRemotePermit] separates them only
     * where the *wording* has to differ.
     */
    val searching: Boolean = false,
    /**
     * The hits currently on screen.
     *
     * May be [remoteHits] as returned, or a locally narrowed view of them — see [narrowedLocally].
     * Never hits from a query the current one did not grow out of.
     */
    val hits: List<ProductSearchHit> = emptyList(),
    /** True once a search has run and matched nothing — distinct from "not searched yet". */
    val noMatches: Boolean = false,
    /**
     * A **submission** was refused for being shorter than [SearchViewModel.MIN_QUERY_LENGTH].
     *
     * Distinct from every other state here because nothing was asked of the network: it is not a
     * failure, not an empty result, and not a search in progress. It exists because refusing the
     * request silently left the tap with no observable consequence at all — same screen, same
     * prompt, no spinner — which reads as the button having missed rather than as the app declining.
     *
     * Set **only** by an explicit [SearchViewModel.search]. Live typing never sets it: telling
     * someone their query is too short while they are still typing it is an error message about a
     * mistake nobody has made yet, and as a live region it would announce on every keystroke.
     */
    val queryTooShort: Boolean = false,
    val error: LookupError? = null,
    /**
     * [error] happened while usable [hits] were on screen, so it is a *refresh* failure.
     *
     * The distinction is about what the user loses, not about what went wrong: the two states carry
     * the same [LookupError]. With nothing on screen a failure is the whole story and gets the
     * recovery panel; over a list someone is already reading and can already tap, replacing that
     * list with a panel takes away everything they had because one background request came back
     * 503 — on an endpoint that answers 503 while otherwise healthy, mid-word.
     *
     * Always false when [hits] is empty, which is what lets a renderer treat the two as exclusive.
     */
    val refreshFailed: Boolean = false,
    /**
     * The query is valid and wanted, but the shared Open Food Facts budget will not allow the
     * request yet.
     *
     * **This is not an error and must never be drawn as one.** It is the app pacing itself, and the
     * queued query will run automatically the moment the budget allows — nothing is lost and there
     * is nothing for the user to do. Separated from [searching] only so the screen can say
     * "Updating…" rather than implying a request is on the wire when none is.
     */
    val awaitingRemotePermit: Boolean = false,
    /**
     * The server refused with 429 and its backoff is still in force.
     *
     * Distinct from [error] `== RATE_LIMITED`: this says the wait is *ongoing and handled*, so the
     * screen offers no Retry — inviting taps during an enforced backoff produces exactly the
     * hammering the backoff exists to stop. The queued query resumes on its own.
     */
    val rateLimited: Boolean = false,
    /**
     * [hits] is a locally filtered view of [SearchViewModel.remoteHits], not a remote answer for
     * the current query.
     *
     * Surfaced so the screen never presents a narrowed list as a completed result: something is
     * still coming, and the progress affordance stays up until it arrives.
     */
    val narrowedLocally: Boolean = false,
)

/**
 * Free-text product search (spec §9).
 *
 * A fallback for when a barcode does not resolve, never the primary way in. Nothing here selects a
 * result: [SearchUiState.hits] are candidates the user reads and taps, and the tap runs an ordinary
 * barcode lookup — so a searched product enters the app through exactly the same validated path as
 * a scanned one, and "no fuzzy match is ever auto-selected" holds because there is no code that
 * could do it.
 *
 * ## Two clocks, deliberately separated
 *
 * Typing is answered **locally and instantly**; Open Food Facts is asked **rarely**. Those are
 * different problems and this class no longer conflates them:
 *
 * ```
 *  keystroke ──▶ local narrowing (instant, no network)          ── what the user sees
 *            └─▶ desired remote query (one, always the newest)
 *                    │ settle REMOTE_SEARCH_SETTLE_MS
 *                    │ then wait for RemoteSearchGovernor
 *                    ▼
 *                 one request                                    ── what OFF sees
 * ```
 *
 * The previous design — "500 ms pause means send another request" — is what put *"The product
 * database is unavailable"* on screen mid-word on real hardware. A debounce answers "has the user
 * stopped typing?", which is not the same question as "may this app ask again yet?", and no
 * debounce value answers the second. See [RemoteSearchGovernor].
 *
 * ## Coalescing is structural, not a queue
 *
 * There is no queue of pending queries and no way to build one: [requests] is a `MutableStateFlow`
 * holding **at most one** desired query, and `flatMapLatest` tears down the previous inner flow —
 * settle wait, governor wait and all — whenever a newer one arrives. Typing `cho`→`chocolate`
 * during a cooldown therefore cannot issue five requests when the budget frees up; the four older
 * values were overwritten before either wait finished, so only `chocolate` exists to be sent.
 *
 * ## Cancellation is the optimisation; the generation check is the invariant
 *
 * [requestGeneration] is bumped on every request and re-checked at the single point where a result
 * is written into state, so a response completing after a newer request began cannot land —
 * whether or not the transport honoured cancellation. See [runSearch].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchSource: ProductSearchSource,
    /**
     * The **shared** Open Food Facts search budget.
     *
     * Injected rather than constructed here, and that is the whole point: Home's inline search and
     * the search screen are two separate `SearchViewModel` instances, so a governor owned per
     * ViewModel would let the two most-likely-consecutive screens spend the budget twice over.
     * `AppContainer` holds the one instance the app uses.
     *
     * Defaulted so tests that do not care about pacing need not supply one; tests that do care
     * pass a governor with a fake clock.
     */
    private val governor: RemoteSearchGovernor = RemoteSearchGovernor(),
    /**
     * The products already on this device, searched **before and independently of** the network.
     *
     * Null disables local search entirely and restores the pre-2026-09-19 behaviour exactly, which
     * is what keeps every existing test in this file meaningful: they were written against a
     * ViewModel with no local source and still exercise the same remote pipeline unchanged.
     *
     * Shared across both `SearchViewModel` instances like the governor and the cache are — not
     * because there is a budget to protect (reading the local store costs nothing) but because
     * there is exactly one store, and constructing an adapter per screen would be two objects
     * describing one thing.
     *
     * Declared **before** [nowMs] so that stays the trailing parameter: every existing test
     * constructs this ViewModel with a clock as a trailing lambda, and moving that position would
     * silently rebind those lambdas to this parameter — a hazard this file already records for
     * `RemoteSearchGovernor`'s own clock argument.
     */
    private val savedProducts: SavedProductSearchSource? = null,
    /**
     * Wall clock, injectable so request pacing is testable on virtual time.
     *
     * Must agree with the clock the [governor] was built with — both default to
     * `System.currentTimeMillis`, and tests pass the same fake to both.
     */
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /**
     * One request to search [terms], either after the settle delay ([immediate] false, from typing)
     * or skipping it ([immediate] true, from the IME action, the search button or a retry).
     *
     * [immediate] skips the **settle wait only**. It has never been able to skip the governor, and
     * that is the point: "the user has finished typing" is information about the user, not about
     * the request budget, so Enter and Retry are as bound by the shared interval as typing is.
     *
     * [generation] is what makes two requests for the *same* terms distinguishable: a retry asks
     * for a query that is already on screen, and without it `flatMapLatest` on a `StateFlow` would
     * see an equal value and never re-emit.
     */
    private data class SearchRequest(
        val terms: String,
        val immediate: Boolean,
        val generation: Long,
    )

    private val requests = MutableStateFlow<SearchRequest?>(null)

    /**
     * The query the currently-displayed hits/noMatches/error belong to.
     *
     * Distinct from [SearchUiState.query], which is live editor text. It is what dedupes a request
     * for terms whose results are already showing, and it is cleared whenever the displayed results
     * are cleared — which is what lets an A -> B -> A retype search again.
     */
    private var displayedQuery: String? = null

    /**
     * The last query a remote search actually returned hits for, and the hits as returned.
     *
     * Kept beside [SearchUiState.hits] rather than instead of it because local narrowing must be
     * non-destructive: narrowing `choc` to `chocolate` and then back to `choco` has to widen again,
     * which is impossible if the narrowed list overwrote the remote one. This pair is the canonical
     * remote answer; what the screen shows is derived from it.
     */
    private var remoteQuery: String? = null
    private var remoteHits: List<ProductSearchHit> = emptyList()

    /**
     * The terms of the most recently *issued or scheduled* request, displayed or not.
     *
     * Separate from [displayedQuery] because a request that is settling, waiting for the budget or
     * in flight has no results yet: without this a user typing "milk", then " milk " (which
     * normalizes identically) would schedule a second call for the same terms.
     */
    private var requestedQuery: String? = null

    /**
     * Monotonic request counter. The **only** thing that decides whether a completed search may
     * write into state — see the check in [runSearch].
     */
    private var requestGeneration = 0L

    /**
     * True while a scheduled request is still waiting — settling, or held by the governor.
     *
     * [search] reads it to tell "the wait can still be skipped" from "the call has already gone
     * out". Written only from the single pipeline coroutine and from [onQueryChanged]/[search] on
     * the main dispatcher, so there is no concurrent access to guard.
     */
    private var waitPending = false

    /** The coroutine running the current search, cancelled when a newer request replaces it. */
    private var inFlight: Job? = null

    /**
     * The saved-product hits for [localQuery], and the query they answer.
     *
     * Kept beside the remote pair for the same reason that one is kept beside
     * [SearchUiState.hits]: what the screen shows is *derived* from both, so neither may be
     * overwritten by the merge. A remote answer arriving must be mergeable with the local hits
     * already on screen, and a remote failure must leave them exactly as they were.
     */
    private var localQuery: String? = null
    private var localHits: List<ProductSearchHit> = emptyList()

    /**
     * The local search's **own** generation counter, deliberately separate from
     * [requestGeneration].
     *
     * The two clocks tick at different rates and for different reasons: a local search starts on
     * every keystroke that meets the minimum length, a remote one only after the settle wait and
     * the governor. Sharing one counter would mean either the local search bumping a number the
     * remote pipeline reads as "a newer request exists" — silently dropping an in-flight remote
     * answer on every keystroke — or the local search testing a counter it does not control, so a
     * remote request landing mid-read would invalidate a perfectly current local result.
     *
     * The invariant this enforces is the same one [requestGeneration] enforces for the network,
     * and it is enforced the same way: re-checked at the single point where a local result becomes
     * state, so an older read cannot land whether or not its coroutine was cancelled.
     */
    private var localGeneration = 0L

    /** The coroutine reading the local store, cancelled when a newer query replaces it. */
    private var localInFlight: Job? = null

    /**
     * The [SearchRequest.generation] that has already consumed its one automatic 429 resume, or
     * null before any query has needed one (P0/P1 §7).
     *
     * Keyed to a generation rather than a plain flag: a flag belongs to the ViewModel, but the
     * budget it bounds is meant to belong to one *query*. With a plain `Boolean`, query A hitting
     * 429 and consuming the flag left it set for query B — a completely unrelated search the user
     * typed next — so B's own first 429 was wrongly treated as an already-exhausted retry and
     * reported as a dead-end failure instead of getting the automatic resume every fresh query is
     * entitled to. Comparing against [request]'s own generation makes the check self-resetting: a
     * new query always carries a generation this field cannot yet equal, with no explicit
     * "reset on success" step needed (the old `resumedAfterRateLimit = false` on a successful
     * result is gone because there is nothing left to reset — the *next* query's generation will
     * simply never match whatever this field currently holds).
     */
    private var retriedGeneration: Long? = null

    init {
        viewModelScope.launch {
            requests
                .flatMapLatest { request ->
                    flow {
                        // A null request means "nothing is wanted" — the field was cleared, or a
                        // submission was refused. Emitting an empty flow here is what CANCELS a
                        // pending wait: flatMapLatest tears down the previous inner flow on every
                        // upstream value, so the null must reach it. Filtering nulls out upstream
                        // would leave the queued delay running and fire a request for a query the
                        // user has already deleted.
                        if (request == null) return@flow

                        // 1. Settle. Long enough not to react to an ordinary mid-word pause. An
                        //    immediate request skips it: the user has said they are done typing.
                        if (!request.immediate) delay(REMOTE_SEARCH_SETTLE_MS)

                        // 2. The shared budget. NOT skippable by any caller — see SearchRequest.
                        //    Delaying by exactly the remaining wait rather than polling means a
                        //    cooldown costs one suspended coroutine, not a wakeup per tick.
                        //
                        //    Re-read in a loop because the wait can grow while we are in it: a 429
                        //    landing for a *different* screen's search extends the block, and a
                        //    single pre-computed delay would then expire early and issue the very
                        //    request the backoff exists to prevent.
                        //
                        //    Unbounded rather than capped at a fixed number of rounds (P1 §6): a rate
                        //    limiter that eventually sends anyway is not a rate limiter, and a cap
                        //    here meant exactly that — once `MAX_PERMIT_WAIT_ROUNDS` re-checks had
                        //    happened, the loop fell through to `emit(request)` regardless of whether
                        //    `wait` was still positive, so a governor that kept extending its block
                        //    (e.g. repeated 429s for another screen's search) was silently overridden
                        //    after 8 rounds. The bound this replaced was defending against a genuine
                        //    but different hazard — a busy loop under clock skew — and `delay(wait)`
                        //    already closes that on its own: every iteration suspends for the actual
                        //    remaining wait rather than polling on a fixed interval, so the loop
                        //    cannot spin regardless of how many times the block is extended, and it
                        //    terminates the moment `waitUntilPermittedMs` reports zero.
                        var wait = governor.waitUntilPermittedMs(nowMs())
                        if (wait > 0) {
                            markAwaitingPermit()
                            while (wait > 0) {
                                delay(wait)
                                wait = governor.waitUntilPermittedMs(nowMs())
                            }
                        }

                        waitPending = false
                        emit(request)
                    }
                }
                // Each search runs in its OWN child coroutine rather than in the collector.
                //
                // `collectLatest` was tried here and is wrong: it waits for the previous block to
                // finish unwinding before it starts the next one, so a transport that does not
                // return promptly on cancellation stalls the whole pipeline — the *next* query is
                // never even sent. Measured, not argued: with a non-cancellation-honouring fake,
                // three stale-protection tests failed with the second query missing from the call
                // list entirely.
                //
                // Launching instead means a newer request is issued immediately, whatever the older
                // one's transport is doing. The older call is still cancelled (its coroutine is a
                // child of this scope and is cancelled below), and if it completes anyway, the
                // generation check in runSearch is what stops it reaching the screen. This is the
                // reason that check cannot be replaced by cancellation.
                .collect { request ->
                    inFlight?.cancel()
                    inFlight = launch { runSearch(request) }
                }
        }
    }

    /** The queued query is waiting on the budget rather than on the network. */
    private fun markAwaitingPermit() {
        _state.update {
            it.copy(
                searching = true,
                awaitingRemotePermit = true,
                rateLimited = governor.isServerBackoffActive(nowMs()),
            )
        }
    }

    /**
     * Live editor text changed.
     *
     * Answers the keystroke **locally and immediately**, then schedules at most one remote refresh.
     * Nothing here is a submission, so nothing here can produce [SearchUiState.queryTooShort].
     */
    fun onQueryChanged(text: String) {
        val terms = normalize(text)
        val hadResultsForThisQuery = terms == displayedQuery
        // Already asked for and still scheduled or running. "milk" -> "milk " normalizes to the
        // same search, so re-issuing it would spend a second request for an answer already on its
        // way. Only the editor text changes.
        val alreadyRequested = !hadResultsForThisQuery && terms == requestedQuery

        if (alreadyRequested) {
            _state.update { it.copy(query = text, queryTooShort = false) }
            return
        }

        // Any edit abandons whatever was scheduled or in flight, including an edit that lands back
        // on the query whose results are already showing (A -> B -> A). That last case is why this
        // is not inside the `!hadResultsForThisQuery` branch: B's search is still pending, and
        // leaving it there would keep the progress line up for a request nobody is waiting on and
        // let B's answer arrive over A's results.
        //
        // Bumping the generation is the part that matters: without it, an in-flight response for
        // the previous query still passed runSearch's "am I the latest request" check — nothing had
        // raised the counter since that request started — and wrote its hits underneath the new
        // editor text.
        requestGeneration++
        requestedQuery = null
        waitPending = false
        requests.value = null
        // The null above cancels a pending settle/governor wait (flatMapLatest tears the inner flow
        // down); a search already past both is a launched coroutine and has to be cancelled here.
        inFlight?.cancel()
        inFlight = null

        // The displayed results survive only if this text is what produced them.
        if (!hadResultsForThisQuery) displayedQuery = null

        val willSearch = !hadResultsForThisQuery && terms.length >= MIN_QUERY_LENGTH

        // What the user sees, answered without the network. `hits` for a query that grew out of the
        // one the remote results belong to is those results narrowed; for a divergent query it is
        // nothing, because showing chocolate for "gouda" would be a wrong answer rather than a
        // stale one.
        val narrowedHits = if (willSearch || hadResultsForThisQuery) localHitsFor(terms) else emptyList()
        val narrowed = narrowedHits.isNotEmpty() && terms != remoteQuery

        // Saved products carried over only for the query that produced them. Anything else drops
        // them here and the read started below replaces them — a saved hit for the previous word is
        // as wrong as a remote one.
        val keepSaved = terms == localQuery
        if (!keepSaved) {
            localQuery = null
            localHits = emptyList()
        }
        val savedHits = if (keepSaved) localHits else emptyList()

        _state.update {
            it.copy(
                query = text,
                hits = when {
                    hadResultsForThisQuery -> it.hits
                    // Saved products lead a query that has no remote answer yet; where a narrowed
                    // remote page also exists, the two are merged by the same rule the arriving
                    // answer will use, so a row does not move when the network catches up.
                    else -> SavedProductSearch.merge(terms, savedHits, narrowedHits, RESULT_LIMIT)
                },
                // Verdicts about the *previous* query go immediately either way. "No products found
                // for X" and a network error are statements about a completed search; keeping them
                // beside newer text would attribute them to a query that has not run.
                noMatches = if (hadResultsForThisQuery) it.noMatches else false,
                error = if (hadResultsForThisQuery) it.error else null,
                refreshFailed = if (hadResultsForThisQuery) it.refreshFailed else false,
                // The notice belongs to one submission, not to the field. Left standing it would sit
                // beside a query that is now long enough and has not been submitted.
                queryTooShort = false,
                searching = willSearch,
                awaitingRemotePermit = false,
                rateLimited = if (willSearch) governor.isServerBackoffActive(nowMs()) else false,
                // Saved hits answer the query, but they are not the whole answer while a remote
                // search is still coming, so they count as a local view exactly as a narrowed
                // remote page does. The screen uses this only to keep the progress affordance up.
                narrowedLocally = when {
                    hadResultsForThisQuery -> it.narrowedLocally
                    else -> narrowed || (savedHits.isNotEmpty() && terms != remoteQuery)
                },
            )
        }

        // Started after the state write, so the keystroke's own synchronous result is on screen
        // before anything asynchronous can touch it — and started whenever the query is long
        // enough, including when `willSearch` is false because the remote answer is already here:
        // a saved product matching the text is worth showing either way.
        if (terms.length >= MIN_QUERY_LENGTH) startLocalSearch(terms) else cancelLocalSearch()

        if (!willSearch) return

        requestedQuery = terms
        waitPending = true
        requests.value = SearchRequest(terms, immediate = false, generation = ++requestGeneration)
    }

    /**
     * Read the saved products for [terms] and publish them, unless a newer query has started.
     *
     * Runs on every keystroke that meets the minimum length, with no settle wait and no governor —
     * see the constructor's [savedProducts] and the field comment on [localGeneration] for why
     * those two exist for the network and must not be borrowed for this.
     *
     * The staleness rule is the remote pipeline's, applied to its own counter: the generation is
     * captured before the read and re-checked at the one point a result becomes state. That check
     * is the invariant and the cancellation below is the optimisation — a source that ignores
     * cancellation still cannot land an old answer, because the counter has moved.
     */
    private fun startLocalSearch(terms: String) {
        val source = savedProducts ?: return
        // Already answered for exactly these terms. The store has not changed under us within one
        // editing session in any way this screen can observe, so re-reading it would cost a table
        // scan per keystroke to produce the list already on screen.
        if (terms == localQuery) return

        val generation = ++localGeneration
        localInFlight?.cancel()
        localInFlight = viewModelScope.launch {
            val products = runCatching { source.allProducts() }.getOrNull() ?: return@launch
            val hits = SavedProductSearch.search(terms, products, RESULT_LIMIT)
            // The single point where a local result becomes state, and the single place staleness
            // is decided. A newer query has raised the counter, so this read is dropped even if its
            // coroutine was never actually cancelled.
            if (generation != localGeneration) return@launch

            localQuery = terms
            localHits = hits
            if (hits.isEmpty()) return@launch

            _state.update {
                // Merged against whatever remote hits belong to this same query. Nothing else about
                // the state is touched: a local read is not an answer to the remote search, so it
                // cannot clear `searching`, resolve `noMatches`, or dismiss an error.
                val remote = if (remoteQuery == terms) remoteHits else emptyList()
                it.copy(
                    hits = SavedProductSearch.merge(terms, hits, remote, RESULT_LIMIT),
                    // A saved product *is* a result, so a screen that was about to say "no products
                    // found" no longer may. The remote search's own verdict is preserved in
                    // `remoteHits`/`remoteQuery` and re-applied if the user edits away and back.
                    noMatches = false,
                    // A failure that already arrived becomes a *refresh* failure now that there is
                    // something to keep — the same demotion `runSearch` performs when hits survive a
                    // failed refresh, reached in the other order. `error` is deliberately left as it
                    // is: the established contract carries it alongside `refreshFailed`, and the
                    // screen decides between the two by testing `hits.isNotEmpty()` first.
                    refreshFailed = it.error != null || it.refreshFailed,
                    narrowedLocally = remote.isEmpty(),
                )
            }
        }
    }

    /**
     * The saved hits belonging to [terms], or nothing.
     *
     * Guarded on the query rather than returning [localHits] outright: a remote answer can arrive
     * for a query the user has since edited past, and merging the previous word's saved products
     * into it would be the same staleness the generation checks exist to prevent, arriving through
     * the other pipeline.
     */
    private fun savedHitsFor(terms: String): List<ProductSearchHit> =
        if (localQuery == terms) localHits else emptyList()

    /** Drop any saved-product work and results — the query is gone or too short to answer. */
    private fun cancelLocalSearch() {
        localGeneration++
        localInFlight?.cancel()
        localInFlight = null
        localQuery = null
        localHits = emptyList()
    }

    /**
     * The hits to show for [terms] without asking the network, while its own answer loads.
     *
     * Only when [terms] grew out of the query the remote results belong to. Every other relationship
     * returns nothing, and the two that look tempting are the reasons why:
     *
     * - **Shortening** (`chocolate` -> `choc`) is not safe. The `chocolate` set is a *subset* of
     *   what `choc` matches, so presenting it would be showing a narrow answer as a complete one.
     * - **Divergence** (`chocolate` -> `gouda`) is not safe for the obvious reason, and is handled
     *   by the same rule rather than by a second one.
     *
     * For a refinement, a row stays only if it contains the words the edit **added**, as judged by
     * [SearchQueryMatcher] — the same matcher that ranks the answer, so a row does not vanish here
     * and reappear there. The words already on screen were the service's to match, and it matched
     * them in ways a name does not always show: "hagelslag" found "Chocoladehagel puur".
     *
     * A refinement that leaves no row returns nothing, and the screen shows that the new query is
     * loading. Until 2026-09-17 it kept every row instead, to avoid a blank flash; on device that
     * showed the "krokante pizza" results under "krokante pizza Albert heijn" as if they answered it.
     */
    private fun localHitsFor(terms: String): List<ProductSearchHit> {
        val base = remoteQuery ?: return emptyList()
        if (remoteHits.isEmpty()) return emptyList()
        val needle = SearchQueryMatcher.fold(terms)
        val baseText = SearchQueryMatcher.fold(base)
        if (!needle.startsWith(baseText)) return emptyList()
        if (needle == baseText) return remoteHits

        val baseWords = SearchQueryMatcher(base, emptyList()).words.toSet()
        val addedWords = SearchQueryMatcher(terms, emptyList()).words.filterNot { it in baseWords }
        val subjects = remoteHits.map { SearchQueryMatcher.Subject(listOf(it.name), it.brand) }
        val matcher = SearchQueryMatcher(addedWords.joinToString(" "), subjects)
        return remoteHits.filterIndexed { index, _ ->
            matcher.match(subjects[index]).strength != SearchQueryMatcher.Strength.WEAK
        }
    }

    /**
     * Explicit search trigger — the IME "Search" action or the search button.
     *
     * Skips the settle wait, never the budget. Goes through the same [requests] pipeline as live
     * typing, which is what guarantees a pending settle for the identical query is *replaced*
     * rather than left to fire a second call behind this one.
     */
    fun search() {
        val terms = normalize(_state.value.query)
        // An empty field is not a refused search: the user has not asked for anything, and the
        // screen's own prompt already says what to type. A "3 characters" message here would be an
        // error about a mistake nobody made.
        if (terms.isBlank()) return
        if (terms.length < MIN_QUERY_LENGTH) {
            requests.value = null
            requestedQuery = null
            waitPending = false
            inFlight?.cancel()
            inFlight = null
            // The refusal clears the list, so the saved hits that were in it go too — and the read
            // that would have replaced them is abandoned rather than left to write into a screen
            // that has just said the query is too short.
            cancelLocalSearch()
            _state.update {
                it.copy(
                    searching = false,
                    hits = emptyList(),
                    noMatches = false,
                    error = null,
                    refreshFailed = false,
                    awaitingRemotePermit = false,
                    rateLimited = false,
                    narrowedLocally = false,
                    queryTooShort = true,
                )
            }
            return
        }
        // A result for exactly this text is already showing: no duplicate submission.
        if (terms == displayedQuery) return

        // Already scheduled or in flight for these exact terms, and nothing an explicit trigger can
        // do would make it happen sooner. This is the guard that makes repeated Enter and repeated
        // Retry free.
        //
        // Three states have to be told apart here, and collapsing any two of them breaks something:
        //
        //  - **in flight** — the call is on the wire. Re-issuing spends a second request for an
        //    answer already coming. Swallow.
        //  - **held by the budget** — settle is over, the governor has it. Enter cannot skip the
        //    budget, and re-emitting would restart the governor wait, so a user tapping Enter
        //    steadily could postpone their own search indefinitely. Swallow.
        //  - **still settling** — the ONE case an explicit trigger genuinely improves, because
        //    skipping the settle wait is exactly what Enter means. Fall through and re-issue.
        //
        // Both mistakes were made here and caught by test: testing `requestedQuery` alone swallowed
        // Retry entirely (a finished search leaves requestedQuery set, so Retry did nothing at
        // all), and treating any pending request as a duplicate swallowed Enter into the settle
        // delay it exists to skip.
        val settling = waitPending && governor.permitsRequestAt(nowMs()) && inFlight?.isActive != true
        val pending = waitPending || inFlight?.isActive == true
        if (terms == requestedQuery && pending && !settling) {
            _state.update {
                it.copy(
                    searching = true,
                    awaitingRemotePermit = !governor.permitsRequestAt(nowMs()),
                )
            }
            return
        }

        requestedQuery = terms
        waitPending = true
        requests.value = SearchRequest(terms, immediate = true, generation = ++requestGeneration)
    }

    private suspend fun runSearch(request: SearchRequest) {
        // The attempt is recorded HERE, at the start, and for every outcome. A request that fails
        // costs the same quota as one that works, and cancellation does not refund it — once the
        // call has left the device the budget is spent whatever this app does with the answer.
        governor.recordAttempt(nowMs())

        _state.update {
            it.copy(
                searching = true,
                error = null,
                refreshFailed = false,
                queryTooShort = false,
                awaitingRemotePermit = false,
                rateLimited = false,
            )
        }
        val result = searchSource.search(request.terms)

        // The one place a result becomes state, and the one place staleness is decided. A newer
        // request has raised the generation, so an older response — success, empty or failure alike
        // — is dropped here even if its coroutine was never actually cancelled.
        //
        // A rate limit is the exception on ONE axis only: the backoff is recorded even for a stale
        // response, because the server's refusal is a fact about the shared budget rather than an
        // answer to this query. Nothing else about a stale response is allowed to touch state.
        if (result is ProductSearchResult.Failed && result.error == LookupError.RATE_LIMITED) {
            governor.recordRateLimited(nowMs(), result.retryAfterMs)
        }
        if (request.generation != requestGeneration) return

        displayedQuery = request.terms

        when (result) {
            is ProductSearchResult.Found -> {
                remoteQuery = request.terms
                remoteHits = result.hits
                _state.update {
                    it.copy(
                        searching = false,
                        hits = SavedProductSearch.merge(
                            request.terms,
                            savedHitsFor(request.terms),
                            result.hits,
                            RESULT_LIMIT,
                        ),
                        noMatches = false,
                        error = null,
                        refreshFailed = false,
                        awaitingRemotePermit = false,
                        rateLimited = false,
                        narrowedLocally = false,
                    )
                }
            }
            // An answer, not an absence of one. Any *remote* list kept on screen during the refresh
            // was kept only because a replacement was coming — this IS the replacement, so it goes.
            // Leaving it would present one query's products as the result for text that genuinely
            // has none. Saved products are not covered by that reasoning; see below.
            ProductSearchResult.NoMatches -> {
                remoteQuery = request.terms
                remoteHits = emptyList()
                // Saved products survive this, and that is the one place "no matches" had to become
                // narrower. The service saying it holds no such record is an answer about the
                // service; a product on this device is not covered by it, and telling someone their
                // own saved product does not exist — while it is sitting in the store — would be
                // the flatly wrong version of an honest empty result.
                val saved = savedHitsFor(request.terms)
                _state.update {
                    it.copy(
                        searching = false,
                        hits = saved,
                        noMatches = saved.isEmpty(),
                        error = null,
                        refreshFailed = false,
                        awaitingRemotePermit = false,
                        rateLimited = false,
                        // The remote search is finished and these are all the results there are, so
                        // this is a complete answer rather than a local view of a pending one.
                        narrowedLocally = false,
                    )
                }
            }
            is ProductSearchResult.Failed -> _state.update {
                // A failure is never rendered as "no matches": this endpoint answers 503 while
                // healthy often enough that conflating them would tell the user their product does
                // not exist because a host was busy.
                //
                // Nor does it discard a list that is still on screen. A failed refresh has produced
                // no answer at all, so the previous results remain the newest thing anyone knows —
                // still tappable, still correct for the query that produced them — and the failure
                // is reported beside them rather than in place of them. Only a failure with nothing
                // to keep becomes the full-screen recovery panel.
                // Saved products are re-merged rather than read off `it.hits`, so the outcome does
                // not depend on whether the local read happened to finish before the failure
                // arrived. Both orders now produce the same screen: the saved list, with the
                // failure reported beside it.
                //
                // This is what makes the app usable with no network at all. Every hit here is a
                // product already on the device, so a failed or impossible request costs the user
                // the products they have never saved — and nothing else.
                val kept = SavedProductSearch.merge(
                    request.terms,
                    savedHitsFor(request.terms),
                    it.hits,
                    RESULT_LIMIT,
                )
                val keptResults = kept.isNotEmpty()
                val limited = result.error == LookupError.RATE_LIMITED
                it.copy(
                    // A rate limit is not over when the response arrives — the backoff has just
                    // begun, and the queued query will run when it expires. Reporting it as a
                    // finished search would drop the progress affordance and invite a retry into a
                    // server that has just refused one.
                    searching = limited,
                    awaitingRemotePermit = limited,
                    rateLimited = limited,
                    hits = kept,
                    noMatches = false,
                    // A rate limit never becomes a full-screen failure: it is a wait this app is
                    // already handling, not a dead end, and the recovery panel's actions answer a
                    // question ("the database has nothing for you") that was not asked.
                    error = if (limited) null else result.error,
                    refreshFailed = !limited && keptResults,
                )
            }
        }

        // A rate limit leaves the query queued and resumes it automatically once the backoff
        // expires: the pipeline re-enters the governor wait, which now reflects the block just
        // recorded, and fires when it lifts. No polling.
        //
        // Bounded to ONE automatic resume per query, and that bound is load-bearing rather than
        // defensive. Re-arming unconditionally means a server answering 429 to everything gets
        // request → 429 → re-arm → request forever: an unbounded retry loop against the endpoint
        // least able to absorb one, which is the precise failure this pass exists to prevent.
        // (Found by writing it that way: the test suite hung, spending virtual time in the loop.)
        //
        // One resume is the right number because it covers the case that motivates the feature —
        // a burst that has passed by the time the backoff expires — while a second failure means
        // the limit is sustained, and the honest answer then is to stop and tell the user rather
        // than to keep asking. Any later keystroke, Enter or Retry schedules a fresh attempt, and
        // each of those is a deliberate act by the user rather than a loop of this app's own making.
        if (result is ProductSearchResult.Failed &&
            result.error == LookupError.RATE_LIMITED &&
            request.generation == requestGeneration
        ) {
            if (retriedGeneration == request.generation) {
                // This exact generation already consumed its one automatic resume and was refused
                // again. Report it as a finished, actionable failure instead of silently waiting
                // forever behind a progress line.
                _state.update {
                    it.copy(
                        searching = false,
                        awaitingRemotePermit = false,
                        rateLimited = false,
                        error = LookupError.RATE_LIMITED,
                        refreshFailed = it.hits.isNotEmpty(),
                    )
                }
                return
            }
            displayedQuery = null
            requestedQuery = request.terms
            waitPending = true
            // The retry gets a fresh generation, and that new generation — not the one that just
            // failed — is what must be recorded as "already resumed": it is the retry's own 429
            // this check exists to catch.
            val retryGeneration = ++requestGeneration
            retriedGeneration = retryGeneration
            requests.value = SearchRequest(request.terms, immediate = true, generation = retryGeneration)
        }
        // No explicit reset on success: a query that gets through simply never reaches this branch
        // again, and the next distinct query carries a generation `retriedGeneration` cannot equal.
    }

    /**
     * Try the current query again after a failure.
     *
     * Subject to the governor exactly like everything else, which is what stops repeated taps
     * hammering a failing endpoint: the request is scheduled, not sent, and the shared interval
     * decides when it leaves. Tapping ten times produces one request.
     */
    fun retry() {
        // A retry re-runs the current query even though its outcome is already on screen — clear the
        // dedupe guards first so it is not silently dropped as a duplicate submission. The request
        // carries a fresh generation, so it is a genuinely new emission rather than an equal value
        // that flatMapLatest would ignore.
        displayedQuery = null
        // Deliberately NOT clearing requestedQuery: search()'s guards read it to recognise a retry
        // for a query that is already scheduled, which is what makes repeated Retry taps free
        // rather than each one restarting the governor wait.
        search()
    }

    /**
     * The one definition of "the same search".
     *
     * Shared by the live and the explicit path deliberately: two validation rules would let a query
     * be too short for one trigger and long enough for the other. Trimming only — the app must not
     * transform what the user typed beyond what the data source itself does, which also trims.
     */
    private fun normalize(text: String): String = text.trim()

    companion object {
        /**
         * Below this a search is all noise — "ha" matches thousands of products.
         *
         * Public so the screen can state the requirement in the notice using the same number the
         * refusal is made with, rather than repeating "3" in a string that could drift from it.
         */
        const val MIN_QUERY_LENGTH = 3

        /**
         * The most results either source, or a merge of both, may put on screen.
         *
         * The same number the remote provider already applies
         * ([app.justthecarbs.data.remote.SearchALiciousApi.SEARCH_RESULT_LIMIT] = 20), restated here
         * rather than imported because this is the *screen's* budget: it bounds a merged list that
         * no single provider produced, and a merge that could exceed the limit either source obeys
         * would make a local hit's arrival lengthen the list rather than join it.
         */
        const val RESULT_LIMIT = 20

        /**
         * How long typing must pause before a remote search is *scheduled*.
         *
         * Raised to 1 s when the legacy endpoint's 7 s budget made every request precious, returned
         * to 500 ms on 2026-08-28 when Search-a-licious became the primary provider, and lowered to
         * **350 ms** on 2026-09-17.
         *
         * Measured on virtual time with a 180 ms service response, typing "krokante pizza albert
         * heijn": at 500 ms results arrived 690 ms after the last key; at 350 ms, 540 ms. At 300 ms
         * (490 ms) a steady typist pausing 320 ms between keys sent 22 requests instead of 4, one
         * per keystroke, so 300 ms was not taken. Pinned by `a slow, steady typist still costs one
         * request per word` and `a search is sent within 350 ms of the last keystroke`.
         *
         * It is **not** what bounds the request rate. The primary's floor is
         * [RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS] and the legacy fallback keeps
         * [RemoteSearchGovernor.MIN_INTERVAL_MS], applied inside
         * [app.justthecarbs.domain.GovernedProductSearch]. Lowering this cannot exceed either
         * budget; it can only waste settle time.
         */
        const val REMOTE_SEARCH_SETTLE_MS = 350L
    }
}
