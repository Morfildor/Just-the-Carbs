package app.justthecarbs.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import app.justthecarbs.ui.components.dismissKeyboardOnTouch
import app.justthecarbs.ui.components.PrimaryAction
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.components.RefreshErrorBanner
import app.justthecarbs.ui.components.SearchResultRow
import app.justthecarbs.ui.components.SecondaryAction
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.accent

/** Stable handles for instrumented tests. */
const val SEARCH_FIELD_TAG = "search_field"
const val SEARCH_RESULTS_TAG = "search_results"
const val SEARCH_SUBMIT_TAG = "search_submit"
const val SEARCH_REFRESH_ERROR_TAG = "search_refresh_error"
const val SEARCH_REFRESH_PROGRESS_TAG = "search_refresh_progress"
const val SEARCH_RATE_LIMITED_TAG = "search_rate_limited"
const val SEARCH_PENDING_TAG = "search_pending"
const val SEARCH_SEARCHING_TAG = "search_searching"

/**
 * The weighted slot below the field that every search state occupies — results, failure, waiting
 * notice, prompt and refusal alike.
 *
 * Exists so a test can measure where a state sits *within its own region* rather than against an
 * absolute pixel position, which is what makes the placement assertion mean the same thing on any
 * screen size.
 */
const val SEARCH_STATE_REGION_TAG = "search_state_region"

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

    // Back peels one layer at a time: keyboard first, screen second — and this screen needs no code
    // to get the first step, only the discipline not to break it.
    //
    // This screen opens with the field focused and the IME up, and the platform dismisses the
    // keyboard on Back before the press reaches any app handler. So the first Back already puts the
    // keyboard away and the second already reaches the nav host's own pop. Measured on the
    // emulator: Back with no handler registered moves the IME inset 883px -> 0; with an IME-gated
    // handler registered it fires and the inset stays at 883, i.e. adding a handler here would
    // SWALLOW the dismissal rather than implement it.
    //
    // Recorded as an explicit non-change so the next reader does not "fix" the apparent omission.
    // The hazard is the Home one in reverse: there a handler keyed on the query destroyed the
    // search, here a handler keyed on the IME would freeze the keyboard.

    // The field is focused on arrival, so this screen opens ready to type.
    //
    // This screen is not browsed to — it is reached from a *failed* lookup, as the recovery route
    // that offers finding the product by name. The user has already decided to type by the time it
    // appears, and its single input is the only thing on it to tap, so requiring that tap is a step
    // with no decision in it at the exact moment the app has just failed them. The same rule the
    // quick calculator and manual entry already follow for their own single inputs.
    //
    // Guarded on an empty query so returning here with text already in the field (a process death,
    // or coming back from a result) does not re-claim focus and re-open the keyboard over results
    // the user is reading. Keyed on `Unit`, so focus is requested once for the life of the screen
    // and never stolen back mid-session.
    val searchFieldFocus = remember { FocusRequester() }
    val claimsFocusOnArrival = remember { state.query.isEmpty() }
    if (claimsFocusOnArrival) {
        LaunchedEffect(Unit) { searchFieldFocus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // No backdrop motif here. It lives on Home only (2026-09-22 visual pass): on this screen
        // it sat behind the top bar's trailing controls, and decoration may not share a level with
        // a control. The destination is identified by JtcTopBar's DestinationMarker instead.

        Column(
            modifier = Modifier
                .fillMaxSize(),
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screenEdge)
                    .focusRequester(searchFieldFocus)
                    .testTag(SEARCH_FIELD_TAG),
            )

            SearchProgressLine(searching = state.searching, tag = SEARCH_REFRESH_PROGRESS_TAG)

            // One weighted region for every state, tagged so a test can measure where a state sits
            // *within it* rather than against an absolute pixel position.
            //
            // The region ends at the top of the keyboard. The app is edge-to-edge (targetSdk 36), so
            // `adjustResize` no longer shrinks the window and a weighted region runs on underneath an
            // open IME: measured on device, the no-matches panel's "Enter manually" sat at y=1620
            // behind a keyboard starting at y=1517. `navigationBarsPadding` moved up from the list so
            // the two insets are applied once, in order, for every state rather than only for results.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .testTag(SEARCH_STATE_REGION_TAG),
            ) {
                when {
                    // Order matters: a *refresh* failure carries a LookupError exactly like a first-search
                    // failure does, so testing `error != null` first would take the whole region away from
                    // results that are still good. The two are distinguished by what the user stands to
                    // lose, and this branch is the one where they lose nothing.
                    state.hits.isNotEmpty() -> SearchResults(
                        state = state,
                        onSelect = onSelect,
                        onRetry = onRetry,
                        onListTouched = { focusManager.clearFocus() },
                        modifier = Modifier.fillMaxSize(),
                    )

                    state.error != null -> SearchFailure(
                        error = state.error,
                        onScanLabel = onScanLabel,
                        onEnterManually = onEnterManually,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Waiting on our own budget, or on a server backoff, with nothing to show yet. A
                    // spinner is wrong here: it promises something is on the wire when nothing is, and a
                    // spinner held for several seconds reads as a hang. A word does the job honestly.
                    state.awaitingRemotePermit -> SearchInformationalState {
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

                    state.searching -> SearchingLine(tag = SEARCH_SEARCHING_TAG)

                    state.noMatches -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // A search that matched nothing is not a dead end: the two ways of getting a number
                        // without the database are offered right here (§26).
                        RecoveryPanel(
                            title = stringResource(R.string.search_no_matches_title),
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

                    else -> SearchInformationalState {
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
    }
}

/**
 * The hairline under a search field while a search is pending — the one loading indicator either
 * search surface shows.
 *
 * Shown for a first search as well as a refresh (2026-09-17). A first search used to replace the
 * whole region with a large centred spinner, which made every search look like the screen was
 * reloading; over results, blanking a good list on every keystroke is the flicker the live search
 * was built to avoid. The previous results stay usable while it runs.
 *
 * It occupies the gap that was already there rather than adding to it, so nothing below it moves
 * when a search starts or finishes.
 */
@Composable
internal fun SearchProgressLine(searching: Boolean, tag: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(Space.s), contentAlignment = Alignment.Center) {
        if (searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screenEdge)
                    .height(2.dp)
                    // The tag has to survive the semantics wipe below, so it goes inside
                    // clearAndSetSemantics rather than before it — a testTag set outside would be
                    // cleared with everything else and the node would be unfindable.
                    //
                    // Deliberately not a live region and carrying no description: it toggles on every
                    // debounce, and announcing that would talk over the results a TalkBack user is
                    // reading. The completed outcome is what gets announced.
                    .clearAndSetSemantics { testTag = tag },
            )
        }
    }
}

/**
 * The quiet line that stands in for results while a search with nothing to show yet is running.
 *
 * Not a live region, for the same reason as [SearchProgressLine]: it appears on every refinement.
 */
@Composable
internal fun SearchingLine(tag: String, modifier: Modifier = Modifier) {
    SearchInformationalState(modifier = modifier) {
        Text(
            text = stringResource(R.string.search_searching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(tag),
        )
    }
}

/**
 * A short informational line — the prompt, the too-short refusal, the waiting notice.
 *
 * **Placed near the top of its region, never centred in it.** Centring inside the weighted slot put
 * this text roughly halfway down a 2400px screen: measured at y=1403 on SearchScreen with ~1100px of
 * empty page above it, and at y=1436 on Home, where an open keyboard then pinned the app's only
 * piece of guidance against its top edge. A sentence that answers "what do I do now?" belongs
 * directly under the field it is about, where the eye already is after typing.
 *
 * Shared by both search surfaces so the two cannot drift on it again — this is the same defect class
 * this repo has now recorded three times (the pinned *Enter manually* action, the quick-calculation
 * dead space, and this).
 *
 * Kept deliberately small: it is placement only. It owns no wording, no colour and no semantics, so
 * each caller still says its own thing in its own voice.
 */
@Composable
internal fun SearchInformationalState(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screenEdge, vertical = Space.l),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
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
    onListTouched: () -> Unit,
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
                // Reaching for the list puts the keyboard away, without spending the touch that
                // did it. The rule, and why it is `Initial`/non-consuming, lives on the shared
                // modifier — Home's inline results use the same one.
                .dismissKeyboardOnTouch(onListTouched)
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
