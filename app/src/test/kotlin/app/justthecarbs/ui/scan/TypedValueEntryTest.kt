package app.justthecarbs.ui.scan

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.NutritionBasis.PER_100_G
import app.justthecarbs.domain.NutritionBasis.PER_100_ML
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * What the assisted typing steps offer for what the user has typed, and what the keyboard's Done
 * key may do with it.
 *
 * The screen keeps its accept actions in place while nothing usable is typed (so the layout does
 * not jump), and the keyboard may submit only the one action a tap would have submitted with no
 * choice left to make. Neither may widen what can be accepted: an impossible figure still gets no
 * action at all.
 */
class TypedValueEntryTest {

    private val both = listOf(PER_100_G, PER_100_ML)

    @Test
    fun `nothing typed keeps every basis action in place but unusable`() {
        assertEquals(TypedValueEntry.Actions.Pending(both), TypedValueEntry.actions("", both))
    }

    @Test
    fun `a separator alone is not yet a value`() {
        assertEquals(TypedValueEntry.Actions.Pending(both), TypedValueEntry.actions(",", both))
    }

    @Test
    fun `an ordinary value is offered under every plausible basis`() {
        val actions = TypedValueEntry.actions("53,5", both)

        actions as TypedValueEntry.Actions.Offered
        assertEquals(0, actions.value.compareTo(BigDecimal("53.5")))
        assertEquals(both, actions.bases)
    }

    @Test
    fun `a value possible only per millilitre is offered only per millilitre`() {
        val actions = TypedValueEntry.actions("150", both)

        assertEquals(listOf(PER_100_ML), (actions as TypedValueEntry.Actions.Offered).bases)
    }

    @Test
    fun `an impossible value is refused rather than offered or left pending`() {
        assertEquals(TypedValueEntry.Actions.Implausible, TypedValueEntry.actions("790", both))
        assertEquals(TypedValueEntry.Actions.Implausible, TypedValueEntry.actions("790", listOf(PER_100_G)))
    }

    @Test
    fun `a fixed basis lets Done submit the plausible value`() {
        val submission = TypedValueEntry.imeSubmission("2,5", listOf(PER_100_ML))

        assertEquals(0, submission!!.first.compareTo(BigDecimal("2.5")))
        assertEquals(PER_100_ML, submission.second)
    }

    @Test
    fun `Done never submits an impossible value`() {
        assertNull(TypedValueEntry.imeSubmission("790", listOf(PER_100_G)))
    }

    @Test
    fun `Done never submits an empty field`() {
        assertNull(TypedValueEntry.imeSubmission("", listOf(PER_100_G)))
    }

    /**
     * With both bases still open the user has to say which one they mean; the keyboard must not pick.
     */
    @Test
    fun `Done never chooses between two bases`() {
        assertNull(TypedValueEntry.imeSubmission("53,5", both))
    }

    /**
     * Plausibility narrowing the choice to one basis is not the user choosing it. `150` is possible
     * only per 100 ml, but nothing the user did said millilitres; they still tap the action.
     */
    @Test
    fun `Done never takes a basis that plausibility alone narrowed to`() {
        assertTrue(TypedValueEntry.actions("150", both) is TypedValueEntry.Actions.Offered)
        assertNull(TypedValueEntry.imeSubmission("150", both))
    }

    @Test
    fun `no candidate basis offers nothing`() {
        assertEquals(TypedValueEntry.Actions.Implausible, TypedValueEntry.actions("5", emptyList<NutritionBasis>()))
    }
}
