package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which row a tap selects when reconstructed row boxes overlap, and what happens when a tap fails.
 *
 * ## The measured geometry
 *
 * On `docs/Scan Evidence 02-09/20260902-085453-023` the drink's rows overlap by 83 px:
 *
 * ```
 * recovered total row : y = 1772..1948
 * sugars child row    : y = 1865..1980
 * ```
 *
 * A tap in 1865..1948 is inside both union boxes. The old rule took whichever came first in document
 * order, which makes the answer depend on reconstruction order rather than on where the finger was.
 */
class RecoveryTapTest {

    private val document = FourthSessionFixtures.drinkRecoveryDeadEnd()
    private val rows = LogicalRowBuilder.build(document)

    private fun rowNamed(fragment: String): LogicalRow =
        rows.first { it.text.contains(fragment, ignoreCase = true) }

    /** The overlap this class exists for is real, or every case below is vacuous. */
    @Test
    fun `the total and child row boxes genuinely overlap`() {
        val total = rowNamed("olhydraten")
        val child = rowNamed("Waarvan suikers")

        assertTrue(
            "expected overlapping boxes, got total=${total.box} child=${child.box}",
            total.box.bottom > child.box.top && total.box.top < child.box.top,
        )
    }

    /**
     * A tap on the carbohydrate **label element** selects the carbohydrate row, even though the
     * child row's union box also covers that y.
     */
    @Test
    fun `a tap on the carbohydrate label selects the total row`() {
        val total = rowNamed("olhydraten")
        val child = rowNamed("Waarvan suikers")
        val label = total.elements.first { it.text.contains("olhydraten", ignoreCase = true) }

        // A y inside the carbohydrate label's own element AND inside the child row's union box.
        //
        // The element's centre (1827) sits above the overlap, so testing there would prove nothing
        // about the ambiguous zone — the precondition below is what caught that and is why this
        // picks a y explicitly rather than reusing a centre. The bottom of the label element is
        // inside 1865..1948, which is exactly the band where a union-box rule has to choose.
        val tappedY = label.box.bottom - 1

        assertTrue(
            "precondition: the tap must fall on the carbohydrate label element",
            tappedY >= label.box.top && tappedY <= label.box.bottom,
        )
        assertTrue(
            "precondition: the tap must also fall inside the child row's union box, " +
                "or this proves nothing (tappedY=$tappedY child=${child.box})",
            tappedY >= child.box.top && tappedY <= child.box.bottom,
        )

        assertFalse(
            "a tap on the carbohydrate label was attributed to the sugars row",
            RecoveryCandidates.isChildRowAt(document, tappedY),
        )
        assertEquals(
            "expected the carbohydrate row's text to be echoed back",
            total.text,
            RecoveryCandidates.rowTextAt(document, tappedY),
        )
    }

    /**
     * And a tap on the sugars row is still rejected.
     *
     * The unconditional child-nutrient exclusion is unchanged: knowing which row the user meant does
     * not change what that row says.
     */
    @Test
    fun `a tap on the sugars row is still refused`() {
        val child = rowNamed("Waarvan suikers")
        val label = child.elements.first { it.text.contains("suikers", ignoreCase = true) }

        assertTrue(
            "a tap on the sugars label must report a child row",
            RecoveryCandidates.isChildRowAt(document, label.box.centerY.toInt()),
        )
        assertTrue(
            "a child row must offer no candidates",
            RecoveryCandidates.onRowAt(document, label.box.centerY.toInt()).isEmpty(),
        )
    }

    /**
     * The dead end: the tap finds the carbohydrate row and the row yields nothing, because its
     * printed value came back as `0.59` and is correctly refused for stating no unit.
     *
     * This is the state that looped. The row is real, the tap was right, and repeating it cannot
     * help — so the app must have something else to offer.
     */
    @Test
    fun `a tap on a found-but-unreadable row yields nothing and has a focused fallback`() {
        val total = rowNamed("olhydraten")
        val label = total.elements.first { it.text.contains("olhydraten", ignoreCase = true) }
        val tappedY = label.box.centerY.toInt()

        assertTrue(
            "the tapped row must yield no candidates, or this is not the dead end",
            RecoveryCandidates.onRowAt(document, tappedY).isEmpty(),
        )
        assertFalse(
            "and it must not be a child row, or the child message would already cover it",
            RecoveryCandidates.isChildRowAt(document, tappedY),
        )

        val focused = FocusedAmountEntry.of(document)
        assertNotNull("no focused-entry target for a row the app did find", focused)
        assertEquals(
            "the basis must come from the label, not from a question",
            app.justthecarbs.domain.NutritionBasis.PER_100_ML,
            focused!!.basis,
        )
    }

    /**
     * The focused fallback cannot invent or change a basis.
     *
     * A document stating no per-100 column gets no target, so the screen cannot offer to fill in a
     * value "under 100 ml" that the label never mentioned.
     */
    @Test
    fun `a document with no stated basis gets no focused target`() {
        val empty = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("Koolhydraten", OcrBox(10, 100, 300, 140), blockId = 0, lineId = 0),
                OcrElement("0.59", OcrBox(400, 100, 480, 140), blockId = 0, lineId = 0),
            ),
        )

        assertEquals(null, FocusedAmountEntry.of(empty))
    }

    /** Two per-100 columns disagreeing is a conflict, not a choice to put to the user. */
    @Test
    fun `a document stating two different bases gets no focused target`() {
        val conflicting = OcrDocument(
            width = 2000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 g", OcrBox(600, 40, 850, 80), blockId = 0, lineId = 0),
                OcrElement("per 100 ml", OcrBox(1200, 40, 1480, 80), blockId = 0, lineId = 0),
                OcrElement("Koolhydraten", OcrBox(10, 100, 300, 140), blockId = 1, lineId = 0),
                OcrElement("0.59", OcrBox(650, 100, 730, 140), blockId = 1, lineId = 0),
            ),
        )

        assertEquals(null, FocusedAmountEntry.of(conflicting))
    }
}
