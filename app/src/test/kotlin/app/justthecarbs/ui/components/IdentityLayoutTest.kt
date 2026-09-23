package app.justthecarbs.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityLayoutTest {

    // Pixel figures at 1dp = 1px for readability: a 48dp caption, a 4dp gap, the 240/200/160dp
    // photo sizes, and rows whose height is their thumbnail's (the facts beside it are shorter).
    private val rows = listOf(RowOption(144, 144), RowOption(128, 128), RowOption(112, 112))

    private fun layoutFor(
        available: Int,
        heroCapable: Boolean = true,
        keyboardOpen: Boolean = false,
        heroHeights: List<Int> = listOf(240, 200, 160),
        rowOptions: List<RowOption> = rows,
    ) = identityLayoutFor(
        heroCapable = heroCapable,
        keyboardOpen = keyboardOpen,
        available = available,
        heroCaption = 48,
        captionGap = 4,
        heroHeights = heroHeights,
        rowOptions = rowOptions,
    )

    @Test
    fun `plenty of room gives the largest photo size and no more`() {
        assertEquals(IdentityLayout.Hero(240), layoutFor(available = 900))
    }

    @Test
    fun `the photo steps down through the fixed sizes, never to a size in between`() {
        assertEquals(IdentityLayout.Hero(240), layoutFor(available = 240 + 4 + 48))
        assertEquals(IdentityLayout.Hero(200), layoutFor(available = 240 + 4 + 48 - 1))
        assertEquals(IdentityLayout.Hero(200), layoutFor(available = 200 + 4 + 48))
        assertEquals(IdentityLayout.Hero(160), layoutFor(available = 200 + 4 + 48 - 1))
        assertEquals(IdentityLayout.Hero(160), layoutFor(available = 160 + 4 + 48))
    }

    @Test
    fun `below the smallest hero the largest row that fits is used`() {
        assertEquals(IdentityLayout.Row(144), layoutFor(available = 160 + 4 + 48 - 1))
        assertEquals(IdentityLayout.Row(144), layoutFor(available = 144))
        assertEquals(IdentityLayout.Row(128), layoutFor(available = 143))
        assertEquals(IdentityLayout.Row(112), layoutFor(available = 127))
    }

    @Test
    fun `a row is chosen by its whole height, facts included, not by its thumbnail`() {
        // Large text: the facts beside a 144dp thumbnail wrap to 170dp, beside 112dp to 150dp.
        val tallFacts = listOf(RowOption(144, 170), RowOption(128, 160), RowOption(112, 150))
        assertEquals(IdentityLayout.Row(128), layoutFor(available = 165, heroCapable = false, rowOptions = tallFacts))
    }

    @Test
    fun `the order the sizes are listed in does not matter`() {
        assertEquals(IdentityLayout.Hero(200), layoutFor(available = 260, heroHeights = listOf(160, 240, 200)))
        assertEquals(IdentityLayout.Row(128), layoutFor(available = 130, rowOptions = rows.reversed()))
    }

    @Test
    fun `a nameless quick calculation has no plate and never gets the hero`() {
        assertEquals(IdentityLayout.Row(144), layoutFor(available = 2000, heroCapable = false))
    }

    @Test
    fun `with the keyboard closed the smallest row is kept even when it overflows`() {
        // The calculator reserves the row's height before the portion controls take theirs, so
        // this is the header's floor, never a reason to disappear.
        assertEquals(IdentityLayout.Row(112), layoutFor(available = 40))
    }

    @Test
    fun `with the keyboard open there is no stepping, only the smallest row`() {
        assertEquals(IdentityLayout.Row(112), layoutFor(available = 900, keyboardOpen = true))
    }

    @Test
    fun `with the keyboard open the header gives way when even the row does not fit`() {
        assertEquals(IdentityLayout.Hidden, layoutFor(available = 111, keyboardOpen = true))
        assertEquals(IdentityLayout.Row(112), layoutFor(available = 112, keyboardOpen = true))
    }

    @Test
    fun `an unbounded height gives the largest photo size`() {
        assertEquals(IdentityLayout.Hero(240), layoutFor(available = Int.MAX_VALUE))
    }
}
