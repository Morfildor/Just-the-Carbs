package app.justthecarbs.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.ui.components.JtcTopBar
import app.justthecarbs.ui.components.PrimaryAction
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.components.RefreshErrorBanner
import app.justthecarbs.ui.components.SearchResultRow
import app.justthecarbs.ui.components.SecondaryAction
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.Space

/** Stable handles for instrumented tests. */
const val SEARCH_FIELD_TAG = "search_field"
const val SEARCH_RESULTS_TAG = "search_results"
const val SEARCH_SUBMIT_TAG = "search_submit"
const val SEARCH_REFRESH_ERROR_TAG = "search_refresh_error"
const val SEARCH_REFRESH_PROGRESS_TAG = "search_refresh_progress"
const val SEARCH_RATE_LIMITED_TAG = "search_rate_limited"
const val SEARCH_PENDING_TAG = "search_pending"

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
    onSearchSubmit: () -> Unit,
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
            .background(MaterialTheme.colorScheme.background),
    ) {
        JtcTopBar(
            title = stringResource(R.string.search_title),
            destination = Destination.SEARCH,
            onBack = onBack,
        )

        val clearLabel = stringResource(R.string.search_clear)
        val searchLabel = stringResource(R.string.search_submit)
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            // Typing searches by itself, debounced in the ViewModel so a typed word costs one
            // request rather than one per keystroke (Open Food Facts' search endpoint allows
            // 10 reads/min/IP). The IME action and the trailing icon remain: they skip the wait for
            // anyone who has finished typing, and are the only way in for anyone not on a soft
            // keyboard. Both go through the same request pipeline, so neither can duplicate the
            // other's call.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearchSubmit()
                    focusManager.clearFocus()
                },
            ),
            // Both sized explicitly, like every other IconButton in the app. A text field's
            // decoration slots constrain their content, so an unsized IconButton here measured 40dp
            // rather than the Material default 48 — measured at 105px on a 420dpi device.
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChanged("") },
                            modifier = Modifier
                                .size(Space.minTouchTarget)
                                .semantics { contentDescription = clearLabel },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                    IconButton(
                        onClick = {
                            onSearchSubmit()
                            focusManager.clearFocus()
                        },
                        modifier = Modifier
                            .size(Space.minTouchTarget)
                            .testTag(SEARCH_SUBMIT_TAG)
                            .semantics { contentDescription = searchLabel },
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                }
            },
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screenEdge)
                .testTag(SEARCH_FIELD_TAG),
        )

        // Refreshing over results that are still on screen: a hairline under the field, not a
        // spinner replacing the list. Blanking a good list on every keystroke and rebuilding it is
        // the flicker this whole pass exists to avoid, and the previous results stay usable — the
        // user can tap one while the newer search is still running.
        //
        // The indicator occupies the gap that was already there rather than adding to it, so the
        // results below do not jump by its height each time a search starts and finishes.
        Box(modifier = Modifier.fillMaxWidth().height(Space.s), contentAlignment = Alignment.Center) {
            if (state.searching && state.hits.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.screenEdge)
                        .height(2.dp)
                        // The tag has to survive the semantics wipe below, so it goes inside
                        // clearAndSetSemantics rather than before it — a testTag set outside would
                        // be cleared with everything else and the node would be unfindable.
                        //
                        // Deliberately not a live region and carrying no description: it toggles on
                        // every debounce, and announcing that would talk over the results a TalkBack
                        // user is reading. The completed outcome is what gets announced.
                        .clearAndSetSemantics { testTag = SEARCH_REFRESH_PROGRESS_TAG },
                )
            }
        }

        when {
            // Order matters: a *refresh* failure carries a LookupError exactly like a first-search
            // failure does, so testing `error != null` first would take the whole region away from
            // results that are still good. The two are distinguished by what the user stands to
            // lose, and this branch is the one where they lose nothing.
            state.hits.isNotEmpty() -> SearchResults(
                state = state,
                onSelect = onSelect,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )

            state.error != null -> SearchFailure(
                error = state.error,
                onScanLabel = onScanLabel,
                onEnterManually = onEnterManually,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )

            // Waiting on our own budget, or on a server backoff, with nothing to show yet. A
            // spinner is wrong here: it promises something is on the wire when nothing is, and a
            // spinner held for several seconds reads as a hang. A word does the job honestly.
            state.awaitingRemotePermit -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(Space.screenEdge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (state.rateLimited) R.string.search_rate_limited else R.string.search_updating,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(SEARCH_PENDING_TAG),
                )
            }

            state.searching -> Box(
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

            else -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(Space.screenEdge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // A submission refused for being too short says so, rather than leaving the
                    // generic prompt up — which is the same screen the tap started from and so reads
                    // as the button not having registered. Not an error colour: nothing has gone
                    // wrong, the app is stating a requirement.
                    text = if (state.queryTooShort) {
                        pluralStringResource(
                            R.plurals.search_too_short,
                            SearchViewModel.MIN_QUERY_LENGTH,
                            SearchViewModel.MIN_QUERY_LENGTH,
                        )
                    } else {
                        stringResource(R.string.search_prompt)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    // The refusal is announced on change so a TalkBack user hears it; without that
                    // the screen is silent after the tap, which is the same dead end by another
                    // route. The *prompt* is deliberately not a live region: with live search it is
                    // re-rendered while the user types, and announcing "type a product name" over
                    // their own typing is exactly the live-region spam this pass had to avoid.
                    modifier = if (state.queryTooShort) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * The result list, plus the inline notice a failed refresh leaves above it.
 *
 * Shared shape with Home's inline results deliberately — the two screens must not disagree about
 * what "results are on screen and one refresh failed" looks like.
 */
@Composable
private fun SearchResults(
    state: SearchUiState,
    onSelect: (ProductSearchHit) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Rate limiting first, and deliberately without a Retry action: the queued query resumes by
        // itself, so a button there would only invite the request hammering the backoff exists to
        // stop. It is not an error and is never drawn as one.
        if (state.rateLimited) {
            RefreshErrorBanner(
                text = stringResource(R.string.search_rate_limited),
                modifier = Modifier
                    .padding(horizontal = Space.screenEdge, vertical = Space.xs)
                    .testTag(SEARCH_RATE_LIMITED_TAG),
            )
        } else if (state.refreshFailed) {
            RefreshErrorBanner(
                text = stringResource(R.string.search_refresh_failed),
                retryText = stringResource(R.string.error_retry),
                onRetry = onRetry,
                modifier = Modifier
                    .padding(horizontal = Space.screenEdge, vertical = Space.xs)
                    .testTag(SEARCH_REFRESH_ERROR_TAG),
            )
        }
        LazyColumn(
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
