package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * The grams-versus-millilitres decision (release pass §3).
 *
 * The safety claim being pinned is one-directional: **an unproven basis must never become
 * `PER_100_G`.** A wrong answer here is not caught anywhere downstream, because every later stage
 * behaves correctly on the input it was handed — the app simply asks the user to measure the wrong
 * quantity, and the carbohydrate figure they dose from is wrong by the product's density.
 */
class PackageBasisResolverTest {

    private fun resolve(unit: String? = null, text: String? = null) =
        PackageBasisResolver.resolve(unit, text)

    private fun basisOf(unit: String? = null, text: String? = null): NutritionBasis? =
        (resolve(unit, text) as? PackageBasisResolver.Resolution.Resolved)?.basis

    private fun assertUnresolved(unit: String? = null, text: String? = null) {
        assertEquals(
            "\"$text\" (unit=$unit) must not resolve a basis",
            PackageBasisResolver.Resolution.Unresolved,
            resolve(unit, text),
        )
    }

    // ---- 1. Structured OFF unit -------------------------------------------------------------

    @Test
    fun `a structured gram unit resolves grams`() {
        assertEquals(NutritionBasis.PER_100_G, basisOf(unit = "g", text = "500 g"))
    }

    @Test
    fun `a structured millilitre unit resolves millilitres`() {
        assertEquals(NutritionBasis.PER_100_ML, basisOf(unit = "ml", text = "500 ml"))
    }

    /** OFF normalises spellings this app's own parser deliberately does not teach. */
    @Test
    fun `a structured unit rescues free text the parser cannot read`() {
        assertEquals(NutritionBasis.PER_100_G, basisOf(unit = "g", text = "390 gram"))
        assertEquals(NutritionBasis.PER_100_ML, basisOf(unit = "l", text = "1,5 liter"))
    }

    @Test
    fun `structured unit casing and padding do not matter`() {
        assertEquals(NutritionBasis.PER_100_ML, basisOf(unit = " ML ", text = null))
        assertEquals(NutritionBasis.PER_100_G, basisOf(unit = "KG", text = null))
    }

    @Test
    fun `an unsupported structured unit does not resolve`() {
        assertUnresolved(unit = "oz", text = "16 oz")
        assertUnresolved(unit = "pieces", text = "12 pieces")
        assertUnresolved(unit = "", text = null)
    }

    /**
     * Two sources that disagree about what the product is measured in are refused rather than
     * ranked. There is no evidence for preferring either, so picking one is exactly the guess this
     * class exists to prevent.
     */
    @Test
    fun `a structured unit contradicting an unambiguous printed quantity refuses`() {
        assertUnresolved(unit = "ml", text = "500 g")
        assertUnresolved(unit = "g", text = "500 ml")
    }

    /**
     * The contradiction check reads the printed *units*, not only a parsed quantity. `6 x 33 cl`
     * parses to no quantity at all, so a check that only compared against `parse` would let a
     * structured `"g"` through and hand the user a volume to weigh.
     */
    @Test
    fun `a structured unit contradicting a multipack's own units refuses`() {
        assertUnresolved(unit = "g", text = "6 x 33 cl")
        assertUnresolved(unit = "ml", text = "3 x 125 g")
    }

    /** Agreement between the two sources is not a contradiction, obviously — but pin it. */
    @Test
    fun `a structured unit agreeing with a multipack's units resolves`() {
        assertEquals(NutritionBasis.PER_100_ML, basisOf(unit = "ml", text = "6 x 33 cl"))
    }

    // ---- 2. Unambiguous free text ------------------------------------------------------------

    @Test
    fun `a single printed quantity resolves without a structured unit`() {
        assertEquals(NutritionBasis.PER_100_G, basisOf(text = "500 g"))
        assertEquals(NutritionBasis.PER_100_ML, basisOf(text = "500 ml"))
        assertEquals(NutritionBasis.PER_100_ML, basisOf(text = "1.5 L"))
        assertEquals(NutritionBasis.PER_100_ML, basisOf(text = "1,5 l"))
    }

    /**
     * A multipack states no single package size — whether "the package" is one bottle or the crate
     * is the ambiguity [PackageQuantityParser] refuses to resolve. It is not, however, ambiguous
     * about *centilitres*, so the basis follows and the size does not.
     */
    @Test
    fun `a multipack resolves its basis but never a package size`() {
        for (text in listOf("6 x 33 cl", "6 x 250 ml", "6x33cl", "4 × 1 L")) {
            val resolution = resolve(text = text)
            assertEquals(
                "$text should resolve millilitres",
                PackageBasisResolver.Resolution.Resolved(NutritionBasis.PER_100_ML, quantity = null),
                resolution,
            )
        }
    }

    @Test
    fun `a solid multipack resolves grams and no package size`() {
        assertEquals(
            PackageBasisResolver.Resolution.Resolved(NutritionBasis.PER_100_G, quantity = null),
            resolve(text = "3 x 125 g"),
        )
    }

    /** A single quantity keeps its size, so pack shortcuts (§14) are unaffected by this pass. */
    @Test
    fun `a single quantity still carries its package size`() {
        val resolved = resolve(text = "1.5 L") as PackageBasisResolver.Resolution.Resolved
        assertEquals(0, BigDecimal("1500").compareTo(resolved.quantity!!.amount))
    }

    @Test
    fun `mixed units in one string do not resolve`() {
        assertUnresolved(text = "250 g / 300 ml")
        assertUnresolved(text = "500 ml plus 20 g powder")
    }

    // ---- 3. Unresolved ------------------------------------------------------------------------

    @Test
    fun `a missing quantity does not default to grams`() {
        assertUnresolved(text = null)
        assertUnresolved(text = "")
        assertUnresolved(text = "   ")
    }

    @Test
    fun `a malformed quantity does not default to grams`() {
        assertUnresolved(text = "family pack")
        assertUnresolved(text = "1 bottle")
        assertUnresolved(text = "assorted")
        assertUnresolved(text = "12 pieces")
    }

    /**
     * The regression that motivated the whole change, stated as plainly as it can be: every input
     * this class cannot read must come back [PackageBasisResolver.Resolution.Unresolved], never
     * `PER_100_G`. `PackageQuantityParser.inferBasis` returned grams for all of these.
     */
    @Test
    fun `nothing unreadable is ever answered with grams`() {
        val unreadable = listOf(
            null, "", "family pack", "1 bottle", "assorted", "net weight", "XL", "?", "0",
            "6 pack", "one dozen", "250 g / 300 ml",
        )
        for (text in unreadable) {
            assertNull(
                "\"$text\" must not produce a basis",
                basisOf(text = text),
            )
        }
    }

    /** A word merely containing a unit letter is not a unit. */
    @Test
    fun `unit letters inside words are not units`() {
        assertUnresolved(text = "large")
        assertUnresolved(text = "1 glass")
        assertUnresolved(text = "2 bags")
    }

    /** Zero and negative sizes are not quantities, but they do still state a unit. */
    @Test
    fun `a zero quantity still states its unit`() {
        // PackageQuantityParser rejects the size (signum <= 0), so no package size is produced —
        // but "0 ml" is unambiguously a volume, and refusing the basis here would send a user to
        // manual entry over a data-entry slip that says nothing about grams versus millilitres.
        assertEquals(
            PackageBasisResolver.Resolution.Resolved(NutritionBasis.PER_100_ML, quantity = null),
            resolve(text = "0 ml"),
        )
    }
}
