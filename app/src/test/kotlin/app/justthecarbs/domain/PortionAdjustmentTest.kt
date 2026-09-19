package app.justthecarbs.domain

import app.justthecarbs.domain.PortionAdjustment.Operation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The quick-adjust arithmetic (1.0.8).
 *
 * Every assertion compares with [BigDecimal.compareTo] rather than `equals` where the scale is not
 * itself the subject: `equals` distinguishes `37.5` from `37.50`, which is a difference about
 * representation and not about the portion. Where the scale *is* the point — halving's bound, and
 * the `5E+1` trap — it is asserted through the string form instead.
 */
class PortionAdjustmentTest {

    private fun apply(current: String?, operation: Operation): BigDecimal =
        PortionAdjustment.apply(current?.let(::BigDecimal), operation)

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertTrue("expected $expected but was $actual", BigDecimal(expected).compareTo(actual) == 0)

    // ---- weight ---------------------------------------------------------------------------------

    @Test
    fun `halving a weight`() = assertAmount("37.5", apply("75", Operation.Halve))

    @Test
    fun `doubling a weight`() = assertAmount("150", apply("75", Operation.Double))

    @Test
    fun `stepping a weight down`() = assertAmount("65", apply("75", Operation.Step(-10)))

    @Test
    fun `stepping a weight up`() = assertAmount("85", apply("75", Operation.Step(10)))

    // ---- count ----------------------------------------------------------------------------------

    @Test
    fun `halving a count`() {
        // Legitimate, and not a new idea: PortionResolver and DirectCarbCalculator both multiply a
        // BigDecimal count, so half a slice has always been expressible. Half of three is 1.5, not
        // 1 or 2 — rounding it would silently change what the user asked for.
        assertAmount("1.5", apply("3", Operation.Halve))
    }

    @Test
    fun `doubling a count`() = assertAmount("4", apply("2", Operation.Double))

    @Test
    fun `stepping a count down`() = assertAmount("2", apply("3", Operation.Step(-1)))

    @Test
    fun `stepping a count up`() = assertAmount("4", apply("3", Operation.Step(1)))

    // ---- the lower boundary ---------------------------------------------------------------------

    @Test
    fun `a step below zero floors at zero rather than going negative`() {
        // The rule the whole screen depends on: a negative portion is not a state the field can
        // hold, and a negative carbohydrate figure is not a thing to show someone dosing insulin.
        assertAmount("0", apply("5", Operation.Step(-10)))
        assertAmount("0", apply("1", Operation.Step(-10)))
        assertAmount("0", apply("0", Operation.Step(-1)))
    }

    @Test
    fun `no operation on any small amount can produce a negative`() {
        // Swept rather than sampled: the floor is one `max` call and a future edit that moves it
        // into one branch would still pass a single hand-picked case.
        val operations = listOf(
            Operation.Halve,
            Operation.Double,
            Operation.Step(-1),
            Operation.Step(-10),
            Operation.Step(-25),
            Operation.Step(1),
        )
        val amounts = listOf(null, "0", "0.1", "0.5", "1", "2.5", "9", "10", "26")
        amounts.forEach { amount ->
            operations.forEach { operation ->
                val result = apply(amount, operation)
                assertTrue(
                    "$amount then $operation produced $result",
                    result.signum() >= 0,
                )
            }
        }
    }

    @Test
    fun `halving zero stays at zero`() = assertAmount("0", apply("0", Operation.Halve))

    @Test
    fun `an empty field is treated as zero`() {
        // What the field actually holds before anything is typed. `+10` is a useful way to start;
        // halving nothing has nothing to halve and must not throw.
        assertAmount("10", apply(null, Operation.Step(10)))
        assertAmount("0", apply(null, Operation.Halve))
        assertAmount("0", apply(null, Operation.Double))
    }

    // ---- precision ------------------------------------------------------------------------------

    @Test
    fun `halving keeps a decimal that matters`() = assertAmount("6.25", apply("12.5", Operation.Halve))

    @Test
    fun `halving is bounded so repeated halving stays measurable`() {
        // 9.375 is not a portion anyone weighs, and three more halvings would make it worse. The
        // bound is on the operation, and two places is where it sits.
        assertAmount("9.38", apply("18.75", Operation.Halve))
        assertEquals(
            "halving must not exceed its own scale bound",
            PortionAdjustment.HALVE_SCALE,
            apply("18.75", Operation.Halve).scale(),
        )
    }

    @Test
    fun `an exact half carries no trailing zero`() {
        // `75.0` in a field the user is about to edit is noise, and ResultFormatter.editable would
        // strip it anyway — doing it here means every consumer sees the same number.
        assertEquals("75", apply("150", Operation.Halve).toPlainString())
    }

    @Test
    fun `a halved multiple of ten is normalised off negative scale`() {
        // The `stripTrailingZeros` trap this codebase has hit twice: `BigDecimal("50")
        // .stripTrailingZeros()` is `5E+1` at **scale -1**, equal by compareTo to 50 and printed
        // by `toString()` as "5E+1".
        //
        // Asserted on the scale and on `toString`, deliberately not on `toPlainString`: measured,
        // `toPlainString` renders the un-normalised `5E+1` as "50" anyway, so a toPlainString
        // assertion here passes with the normalisation removed and pins nothing. This codebase's
        // own formatter uses toPlainString and is therefore already safe; what this guards is the
        // next caller that reaches for `toString()`, string interpolation, or a scale-sensitive
        // `equals` — all of which see the difference.
        val fifty = apply("100", Operation.Halve)
        assertEquals("a portion must not carry negative scale", 0, fifty.scale())
        assertEquals("50", fifty.toString())
        assertEquals("5", apply("10", Operation.Halve).toString())
    }

    @Test
    fun `doubling is exact and never rounds`() {
        assertAmount("24.69", apply("12.345", Operation.Double))
        assertEquals("24.690", apply("12.345", Operation.Double).toPlainString())
    }

    @Test
    fun `a large weight doubles without loss`() = assertAmount("1000", apply("500", Operation.Double))

    // ---- the calculator downstream ---------------------------------------------------------------

    @Test
    fun `an adjusted portion feeds the ordinary calculator unchanged`() {
        // The point of returning BigDecimal: an adjusted amount is the same kind of number as a
        // typed one, so it goes through CarbCalculator with nothing in between. 37.5 g of a
        // 48.2 g/100 g product is 18.075 g.
        val adjusted = apply("75", Operation.Halve)
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("48.2"),
            portion = adjusted,
            basis = NutritionBasis.PER_100_G,
        )
        assertAmount("18.075", result.exact)
    }

    @Test
    fun `an adjusted count feeds the direct-carb calculator unchanged`() {
        val adjusted = apply("3", Operation.Halve)
        assertAmount(
            "21.3",
            DirectCarbCalculator.exactCarbs(count = adjusted, carbsPerUnit = BigDecimal("14.2")),
        )
    }

    @Test
    fun `a step carries the size its caller chose`() {
        // The weight rail scales its step to the package size and a count's is always one, so the
        // size belongs to the Step rather than to a fixed set of operations here.
        assertAmount("125", apply("100", Operation.Step(25)))
        assertAmount("50", apply("100", Operation.Step(-50)))
    }
}
