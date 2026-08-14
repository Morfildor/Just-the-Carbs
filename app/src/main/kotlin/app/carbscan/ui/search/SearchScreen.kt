package app.carbscan.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.carbscan.R
import app.carbscan.domain.LookupError
import app.carbscan.domain.ProductSearchHit
import app.carbscan.ui.components.PrimaryAction
import app.carbscan.ui.components.RecoveryPanel
import app.carbscan.ui.components.SearchResultRow
import app.carbscan.ui.components.SecondaryAction
import app.carbscan.ui.theme.Space

/** Stable handles for instrumented tests. */
const val SEARCH_FIELD_TAG = "search_field"
const val SEARCH_RESULTS_TAG = "search_results"

/**
 * Free-text product search (spec §9).
 *
 * The fallback in the failure hierarchy — barcode lookup, then search, then label scan, then manual
 * entry — so it is reached from a failure, never offered as the way to start.
 *
 * Each card carries what someone needs to recognise their own package before relying on its number:
 * image, name, brand and printed package quantity. **Nothing is ever auto-selected.** Even a single
 * result requires a tap, because "only one match" is not the same as "the right match", and the app
 * has no way to tell them apart.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onSelect: (ProductSearchHit) -> Unit,
    onScanLabel: () -> Unit,
    onEnterManually: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.product_back),
                )
            }
            Text(
                text = stringResource(R.string.search_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.s)
                    .semantics { heading() },
            )
        }

        val clearLabel = stringResource(R.string.search_clear)
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            // Search, not Done: this field's action is the screen's whole purpose, and the results
            // are already live, so the key only needs to put the keyboard away.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChanged("") },
                        modifier = Modifier.semantics { contentDescription = clearLabel },
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }
            },
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screenEdge)
                .testTag(SEARCH_FIELD_TAG),
        )

        Spacer(Modifier.height(Space.s))

        when {
            state.error != null -> SearchFailure(
                error = state.error,
                onScanLabel = onScanLabel,
                onEnterManually = onEnterManually,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )

            state.searching && state.hits.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }

            state.noMatches -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // A search that matched nothing is not a dead end: the two ways of getting a number
                // without the database are offered right here (§26).
                RecoveryPanel(
                    title = stringResource(R.string.notfound_title),
                    body = stringResource(R.string.search_no_matches, state.query),
                ) {
                    PrimaryAction(
                        text = stringResource(R.string.product_scan_label),
                        onClick = onScanLabel,
                    )
                    SecondaryAction(
                        text = stringResource(R.string.permission_manual),
                        onClick = onEnterManually,
                    )
                }
            }

            state.hits.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(Space.screenEdge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.search_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.screenEdge)
                    .navigationBarsPadding()
                    .testTag(SEARCH_RESULTS_TAG),
            ) {
                items(state.hits, key = { it.barcode }) { hit ->
                    SearchResultRow(hit = hit, onClick = { onSelect(hit) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SearchFailure(
    error: LookupError,
    onScanLabel: () -> Unit,
    onEnterManually: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (error) {
        LookupError.OFFLINE -> stringResource(R.string.error_offline_title)
        LookupError.TIMEOUT -> stringResource(R.string.error_timeout_title)
        LookupError.RATE_LIMITED -> stringResource(R.string.error_rate_limited_title)
        LookupError.SERVER -> stringResource(R.string.error_server_title)
        LookupError.MALFORMED -> stringResource(R.string.error_malformed_title)
    }
    val body = when (error) {
        LookupError.OFFLINE -> stringResource(R.string.error_offline_body)
        LookupError.RATE_LIMITED -> stringResource(R.string.error_rate_limited_body)
        else -> stringResource(R.string.error_generic_body)
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        RecoveryPanel(title = title, body = body) {
            PrimaryAction(
                text = stringResource(R.string.error_retry),
                onClick = onRetry,
            )
            SecondaryAction(
                text = stringResource(R.string.product_scan_label),
                onClick = onScanLabel,
            )
            SecondaryAction(
                text = stringResource(R.string.permission_manual),
                onClick = onEnterManually,
            )
        }
    }
}
