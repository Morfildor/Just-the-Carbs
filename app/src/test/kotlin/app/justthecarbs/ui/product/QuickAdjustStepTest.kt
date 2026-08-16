package app.justthecarbs.ui.product

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * The ± step size scales with the package (§16).
 *
 * A fixed ±5 g served neither end of the range this app covers: on a 500 g pasta pack it is
 * one-hundredth of the package, and on a 20 g biscuit it is a quarter of the item. These cases pin
 * the two properties that make the scaling safe rather than merely convenient — that it only ever
 * derives from a package size the app actually read, and that it never produces a step a person
 * would not choose themselves.
 */
class QuickAdjustStepTest {

    private fun step(pack: String?) = quickAdjustStep(pack?.let(::BigDecimal))

    @Test
    fun `no package size keeps the original five gram step`() {
        // The safe default. Most products reach the calculator without a confidently-parsed package
        // size, and inventing a step from a size the app does not have is the guessed-shortcut
        // problem §14 rules out — the same reason PackShortcuts stays hidden here.
        assertEquals(5, step(null))
    }

    @Test
    fun `a small item keeps a fine step`() {
        // A 20 g biscuit: ±5 is already a quarter of the item, and anything coarser would make the
        // buttons useless for the products they matter most on.
        assertEquals(5, step("20"))
        assertEquals(5, step("100"))
    }

    @Test
    fun `a large pack gets a coarse step`() {
        // A 400 g loaf and a 1 L carton. At ±5 these took twenty taps to move anywhere.
        assertEquals(25, step("400"))
        assertEquals(50, step("1000"))
    }

    @Test
    fun `every step is a round number a person would choose`() {
        // The row is used by feel while holding food. A mathematically-derived "+37" would be
        // defensible and unusable, so the ladder is fixed rather than computed from a percentage.
        val sizes = listOf(null, "20", "50", "119", "120", "299", "300", "749", "750", "2000")
        val allowed = setOf(5, 10, 25, 50)

        sizes.forEach { size ->
            val value = step(size)
            assertEquals(
                "step for package size $size should be one of $allowed but was $value",
                true,
                value in allowed,
            )
        }
    }

    @Test
    fun `the step never decreases as the package grows`() {
        // Monotonicity across the boundaries. Without this a 299 g pack could get a coarser step
        // than a 300 g one, which would read as a bug to anyone who noticed it.
        val ascending = listOf("10", "119", "120", "299", "300", "749", "750", "5000")
            .map { step(it) }

        ascending.zipWithNext { smaller, larger ->
            assertEquals(
                "step ladder must not decrease as package size grows: saw $smaller then $larger",
                true,
                larger >= smaller,
            )
        }
    }
}
