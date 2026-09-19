package app.justthecarbs.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.ui.home.HOME_SEARCH_FIELD_TAG
import app.justthecarbs.ui.home.HomeScreen
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Home taking focus for the launcher's *Search* shortcut (1.0.8).
 *
 * The shortcut's promise is that it delivers someone ready to type, so the thing worth asserting is
 * that the field is genuinely focused — and, more importantly, that a *repeat* of the same shortcut
 * works. That second case is the one this app has already got wrong once on a device, for the
 * Barcode shortcut: an effect keyed on the destination rather than on the delivery held the value
 * it had already seen, so the second tap did nothing.
 */
class SearchShortcutFocusTest {

    @get:Rule
    val compose = createComposeRule()

    private val focused = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)

    /** Drives Home with a settable focus-request id, exactly as the nav host supplies it. */
    private class Harness {
        var handled = 0
    }

    private fun showHome(harness: Harness): (Long) -> Unit {
        var request by mutableLongStateOf(0L)
        compose.setContent {
            JustTheCarbsTheme {
                var query by remember { mutableStateOf("") }
                HomeScreen(
                    recents = emptyList(),
                    settings = AppSettings(),
                    onScan = {},
                    onManualEntry = {},
                    onOpenProduct = {},
                    onToggleFavorite = {},
                    onOpenSettings = {},
                    searchState = SearchUiState(query = query),
                    onSearchQueryChanged = { query = it },
                    searchFocusRequest = request,
                    onSearchFocusHandled = { harness.handled++ },
                )
            }
        }
        return { id -> request = id }
    }

    @Test
    fun theSearchFieldIsNotFocusedOnAnOrdinaryLaunch() {
        // The resting state: opening the app normally must not raise the keyboard over Home.
        val harness = Harness()
        showHome(harness)
        compose.waitForIdle()

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Focused, false),
        )
        assertEquals(0, harness.handled)
    }

    @Test
    fun theShortcutFocusesTheSearchField() {
        val harness = Harness()
        val deliver = showHome(harness)

        deliver(1L)
        compose.waitForIdle()

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assert(focused)
        assertEquals("the request must be reported handled exactly once", 1, harness.handled)
    }

    @Test
    fun tappingTheSameShortcutAgainFocusesAgain() {
        // The defect measured on device for the Barcode shortcut, in its Search form: two taps
        // produce two identical *destinations*, so only a fresh delivery id makes the second one a
        // distinct event. Focus is moved away in between so re-focusing is observable.
        val harness = Harness()
        val deliver = showHome(harness)

        deliver(1L)
        compose.waitForIdle()
        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assert(focused)

        // Something else takes focus, as an ordinary navigation would.
        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performTextInput("x")
        compose.waitForIdle()

        deliver(2L)
        compose.waitForIdle()

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assert(focused)
        assertEquals("each delivery is handled once", 2, harness.handled)
    }

    @Test
    fun aHandledRequestIsNotReplayed() {
        // What stops a rotation re-focusing the field: the id Home has already handled is reset by
        // the nav host, and re-composing with the resting value must do nothing.
        val harness = Harness()
        val deliver = showHome(harness)

        deliver(1L)
        compose.waitForIdle()
        assertEquals(1, harness.handled)

        deliver(0L)
        compose.waitForIdle()

        assertEquals("the resting value must never be treated as a delivery", 1, harness.handled)
    }

    @Test
    fun theExistingQueryIsPreservedWhenTheShortcutArrives() {
        // Someone who had already typed something and comes back via the shortcut keeps their work:
        // the shortcut asks for focus, never for a reset.
        val harness = Harness()
        val deliver = showHome(harness)

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performTextInput("hagelslag")
        compose.waitForIdle()

        deliver(1L)
        compose.waitForIdle()

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assert(focused)
        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("hagelslag")),
        )
    }
}
