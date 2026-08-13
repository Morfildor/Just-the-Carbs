package app.carbscan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.carbscan.data.ProductRepository
import app.carbscan.domain.Product
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: ProductRepository) : ViewModel() {

    /**
     * Recents come straight from the database, so home draws without waiting for anything (§7).
     * `WhileSubscribed` keeps the query alive briefly across a rotation instead of re-running it.
     */
    val recents: StateFlow<List<Product>> = repository.observeRecents(RECENTS_LIMIT)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun toggleFavorite(product: Product) {
        viewModelScope.launch { repository.setFavorite(product.barcode, !product.favorite) }
    }

    private companion object {
        /** Enough to cover a normal shop without turning home into a scrolling history (§21). */
        const val RECENTS_LIMIT = 25
    }
}
