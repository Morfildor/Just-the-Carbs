package app.justthecarbs.ui.manual

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ui.editManuallyRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * *Edit* must open on the basis the label stated, never on the screen's default.
 *
 * ## The defect
 *
 * A device recording of `20260902-131545-452`: the scanner proposed `89 g / 100 ml` for a package
 * printing `8,9 g / 100 ml`. The user correctly rejected it and tapped **Edit** — and the manual
 * product screen opened blank with **`100 g`** selected.
 *
 * Nothing was individually wrong. [ManualEntryUiState.basis] defaults to
 * [NutritionBasis.PER_100_G], which is the right default for a screen reached with no context;
 * `ManualEntryViewModel.start` already honours a supplied basis; and the classifier had read
 * `per 100 ml` correctly. The defect was between them — the scanner's *Edit* action was a bare
 * `() -> Unit` that carried no basis at all, so the default stood.
 *
 * ## Why this is a safety defect and not a cosmetic one
 *
 * The basis is not decoration on the number: it decides *the unit the portion field asks a human to
 * measure in*. A user who types the correct `8.9` under a silently-wrong `100 g` has stored a
 * figure against the wrong denominator, and no later stage can detect it — the same class of error
 * this repo already recorded for `PackageQuantityParser.inferBasis`.
 */
class EditBasisPreservationTest {

    // ------------------------------------------------------------------ the route

    @Test
    fun `editing from a per 100 ml reading carries millilitres into the route`() {
        val route = editManuallyRoute("", NutritionBasis.PER_100_ML)
        assertTrue("expected the route to carry PER_100_ML, was: $route", route.contains("basis=PER_100_ML"))
    }

    @Test
    fun `editing from a per 100 g reading carries grams into the route`() {
        val route = editManuallyRoute("", NutritionBasis.PER_100_G)
        assertTrue("expected the route to carry PER_100_G, was: $route", route.contains("basis=PER_100_G"))
    }

    /**
     * The amount stays out of the route on the *Edit* path.
     *
     * *Edit* is reached when the app's number was wrong or was withheld, so pre-filling it would
     * re-propose exactly the figure the user has just declined. Carrying a value is
     * [app.justthecarbs.ui.scan.LabelScannerScreen]'s *Correct* action, which is a different
     * question and keeps its own behaviour.
     */
    @Test
    fun `editing carries no amount`() {
        val route = editManuallyRoute("", NutritionBasis.PER_100_ML)
        assertTrue("the amount must be blank, was: $route", route.contains("carbs=&"))
    }

    /** With no basis established the route says so, and the screen keeps its own default. */
    @Test
    fun `editing with no established basis carries no basis`() {
        val basis: NutritionBasis? = null
        val route = editManuallyRoute("", basis)
        assertTrue("expected an empty basis, was: $route", route.contains("basis="))
        assertTrue("no basis name may appear, was: $route", !route.contains("PER_100"))
    }

    // ------------------------------------------------------------------ the resulting state

    /**
     * The state the manual screen actually renders, given what the route carried.
     *
     * This mirrors `ManualEntryViewModel.start`'s own resolution rather than driving the ViewModel,
     * which needs a repository: the question here is whether a carried basis wins over the default,
     * and that is the one line of logic that answers it.
     */
    private fun basisFor(carried: String, current: NutritionBasis = NutritionBasis.PER_100_G) =
        NutritionBasis.entries.firstOrNull { it.name == carried } ?: current

    @Test
    fun `a carried millilitre basis overrides the grams default`() {
        assertEquals(NutritionBasis.PER_100_ML, basisFor(NutritionBasis.PER_100_ML.name))
    }

    @Test
    fun `a carried gram basis is preserved`() {
        assertEquals(NutritionBasis.PER_100_G, basisFor(NutritionBasis.PER_100_G.name))
    }

    /**
     * An absent basis falls back to the screen's default rather than to nothing.
     *
     * This is the pre-existing behaviour and must not change: a manual entry reached from Home has
     * no label behind it, and grams is the right thing to open on there.
     */
    @Test
    fun `an absent basis keeps the existing default`() {
        assertEquals(NutritionBasis.PER_100_G, basisFor(""))
    }

    /**
     * An unrecognised basis name is not silently reinterpreted.
     *
     * It means a corrupt or downgraded argument, and falling back to the default is the same rule
     * `SettingsRepository` applies to an unrecognised stored theme.
     */
    @Test
    fun `an unknown basis name falls back rather than throwing`() {
        assertEquals(NutritionBasis.PER_100_G, basisFor("PER_100_FURLONG"))
    }

    /**
     * Restoring the route after process death yields the same basis.
     *
     * The basis travels as a route *argument* rather than in a `savedStateHandle` precisely so this
     * holds — the same reasoning the detected carbohydrate figure already follows.
     */
    @Test
    fun `the basis survives a route round trip`() {
        val route = editManuallyRoute("12345", NutritionBasis.PER_100_ML)
        val restored = Regex("basis=([A-Z_0-9]*)").find(route)?.groupValues?.get(1).orEmpty()
        assertEquals(NutritionBasis.PER_100_ML, basisFor(restored))
    }
}
