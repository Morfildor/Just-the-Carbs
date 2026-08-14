package app.carbscan.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.carbscan.data.ProductRepository
import app.carbscan.domain.LookupError
import app.carbscan.domain.ProductSearchHit
import app.carbscan.domain.ProductSearchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 */
class SearchViewModel(private val repository: ProductRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    @OptIn(FlowPreview::class)
    private val queries = MutableStateFlow("")

    init {
        // Debounced so typing "hagelslag" is one request, not nine. Open Food Facts allows 15
        // reads/min/IP for the whole app, so an un-debounced field would exhaust the budget mid-word
        // and start failing the user's actual lookups (§15).
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            queries
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(QUERY_SETTLE_MS)
                .collect { runSearch(it) }
        }
    }

    fun onQueryChanged(text: String) {
        queries.value = text
        _state.update {
            it.copy(
                query = text,
                // Clearing the field returns to the prompt rather than leaving stale results
                // sitting under an empty query, which would look like matches for nothing.
                hits = if (text.isBlank()) emptyList() else it.hits,
                noMatches = false,
                error = null,
            )
        }
    }

    private suspend fun runSearch(terms: String) {
        if (terms.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(searching = false, hits = emptyList(), noMatches = false) }
            return
        }

        _state.update { it.copy(searching = true, error = null) }
        when (val result = repository.search(terms)) {
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
        viewModelScope.launch { runSearch(_state.value.query.trim()) }
    }

    private companion object {
        /** Long enough to cover typing a word, short enough not to feel stalled. */
        const val QUERY_SETTLE_MS = 400L

        /** Below this a search is all noise — "ha" matches thousands of products. */
        const val MIN_QUERY_LENGTH = 3
    }
}
