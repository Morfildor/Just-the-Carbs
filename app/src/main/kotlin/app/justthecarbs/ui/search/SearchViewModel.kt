package app.justthecarbs.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val hits: List<ProductSearchHit> = emptyList(),
    /** True once a search has run and matched nothing — distinct from "not searched yet". */
    val noMatches: Boolean = false,
    val error: LookupError? = null,
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
 * Search is **explicit**, not as-you-type: Open Food Facts' search endpoint is rate-limited
 * (10 reads/min/IP for search, distinct from the 15/min product-read budget) and must not be hit on
 * every keystroke. Typing only updates [SearchUiState.query]; a network request happens only when
 * [search] is called, from the field's IME "Search" action or a dedicated search button.
 */
class SearchViewModel(private val searchSource: ProductSearchSource) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /**
     * The query the currently-displayed hits/noMatches/error belong to.
     *
     * Distinct from [SearchUiState.query], which is live editor text. Editing the field clears this,
     * which is what lets an A -> B -> A retype search again instead of being deduped against a
     * result set that is no longer on screen.
     */
    private var displayedQuery: String? = null
    private var searchJob: Job? = null

    /** Guards a slower, older response from overwriting a newer one (last-submitted wins). */
    private var requestId = 0L

    fun onQueryChanged(text: String) {
        // Editing invalidates whatever is on screen and whatever is in flight. Bumping requestId is
        // the part that was missing: without it, an in-flight response for the *previous* query
        // still passed runSearch's "am I the latest request" check — because nothing had raised the
        // counter since that request started — and wrote its hits underneath the new editor text.
        if (text != displayedQuery) {
            requestId++
            searchJob?.cancel()
            searchJob = null
            displayedQuery = null
        }
        _state.update {
            it.copy(
                query = text,
                hits = emptyList(),
                noMatches = false,
                error = null,
                searching = false,
            )
        }
    }

    /** Explicit search trigger — IME "Search" action or a search button, never a keystroke. */
    fun search() {
        val terms = _state.value.query.trim()
        if (terms.isBlank()) return
        if (terms.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(searching = false, hits = emptyList(), noMatches = false) }
            return
        }
        // A result for exactly this text is already showing: no duplicate submission.
        if (terms == displayedQuery) return

        displayedQuery = terms
        searchJob?.cancel()
        val thisRequestId = ++requestId
        searchJob = viewModelScope.launch { runSearch(terms, thisRequestId) }
    }

    private suspend fun runSearch(terms: String, thisRequestId: Long) {
        _state.update { it.copy(searching = true, error = null) }
        val result = searchSource.search(terms)
        // A newer search may have started (and won the dedupe/cancel above) while this one was in
        // flight; only the most recent request is allowed to write into state.
        if (thisRequestId != requestId) return

        when (result) {
            is ProductSearchResult.Found -> _state.update {
                it.copy(searching = false, hits = result.hits, noMatches = false, error = null)
            }
            ProductSearchResult.NoMatches -> _state.update {
                it.copy(searching = false, hits = emptyList(), noMatches = true, error = null)
            }
            is ProductSearchResult.Failed -> _state.update {
                // A failure is never rendered as "no matches": this endpoint answers 503 while
                // healthy often enough that conflating them would tell the user their product does
                // not exist because a host was busy.
                it.copy(searching = false, hits = emptyList(), noMatches = false, error = result.error)
            }
        }
    }

    fun retry() {
        // A retry re-runs the same query even though its result is already on screen — clear the
        // dedupe guard first so it is not silently dropped as a duplicate submission.
        displayedQuery = null
        search()
    }

    private companion object {
        /** Below this a search is all noise — "ha" matches thousands of products. */
        const val MIN_QUERY_LENGTH = 3
    }
}
