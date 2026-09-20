package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The imported-photo selection rules, pinned where a test can reach them.
 *
 * The rule that matters most here is the refusal: **a photograph with two distinct products in it
 * must never resolve to one of them silently.** That is a decision no camera test can observe, in a
 * path whose failure is invisible afterwards — the user sees a product page and has no reason to
 * doubt it — so it lives in [ImportedBarcodeSelection] and is asserted here rather than inside a
 * composable that binds a camera.
 */
class ImportedBarcodeSelectionTest {

    private fun detection(
        value: String,
        format: BarcodeFormat = BarcodeFormat.EAN_13,
    ) = NormalizedBarcode(
        value = value,
        format = format,
        // Geometry is irrelevant to this decision — an imported photograph is deliberately not
        // subject to the live path's region and size gates — so every fixture uses one box. A test
        // that varied it would imply position mattered here, which is exactly what must not be true.
        box = NormalizedBox(left = 0.1, top = 0.1, right = 0.9, bottom = 0.5),
        timestampNanos = 0L,
    )

    @Test
    fun `no detections is no barcode`() {
        assertEquals(ImportedBarcodeSelection.Outcome.None, ImportedBarcodeSelection.of(emptyList()))
    }

    @Test
    fun `one detection continues immediately`() {
        val outcome = ImportedBarcodeSelection.of(listOf(detection("8712100849060")))

        assertEquals(ImportedBarcodeSelection.Outcome.Single("8712100849060"), outcome)
    }

    @Test
    fun `repeated reads of one printed barcode collapse to a single code`() {
        // ML Kit routinely reports the same printed barcode more than once in a still — the reason
        // duplicate collapse is required rather than merely tidy: without it, one photograph of one
        // packet would ask the user which of two identical numbers they meant.
        val outcome = ImportedBarcodeSelection.of(
            listOf(
                detection("8712100849060"),
                detection("8712100849060"),
                detection("8712100849060"),
            ),
        )

        assertEquals(ImportedBarcodeSelection.Outcome.Single("8712100849060"), outcome)
    }

    @Test
    fun `a UPC-A and its EAN-13 spelling are one product, not a choice`() {
        // `036000291452` normalises to `0036000291452`; both are the same article, and offering
        // both would ask the user to choose between two spellings of one answer.
        val outcome = ImportedBarcodeSelection.of(
            listOf(
                detection("0036000291452", BarcodeFormat.EAN_13),
                detection("0036000291452", BarcodeFormat.UPC_A),
            ),
        )

        assertEquals(ImportedBarcodeSelection.Outcome.Single("0036000291452"), outcome)
    }

    @Test
    fun `two distinct products are offered as a choice and never picked`() {
        val outcome = ImportedBarcodeSelection.of(
            listOf(detection("8712100849060"), detection("5000159484695")),
        )

        assertEquals(
            ImportedBarcodeSelection.Outcome.Choice(listOf("8712100849060", "5000159484695")),
            outcome,
        )
    }

    @Test
    fun `a choice preserves first-seen order and collapses duplicates within it`() {
        // Order is first-seen rather than sorted so the list does not reshuffle between two
        // recognitions of the same photograph, which on screen reads as the app changing its mind.
        val outcome = ImportedBarcodeSelection.of(
            listOf(
                detection("5000159484695"),
                detection("8712100849060"),
                detection("5000159484695"),
                detection("96385074", BarcodeFormat.EAN_8),
            ),
        )

        assertEquals(
            ImportedBarcodeSelection.Outcome.Choice(
                listOf("5000159484695", "8712100849060", "96385074"),
            ),
            outcome,
        )
    }

    @Test
    fun `three distinct products all reach the choice, none discarded`() {
        // A shelf photograph is the ordinary case for this: silently dropping the third would be a
        // quieter version of guessing.
        val outcome = ImportedBarcodeSelection.of(
            listOf(
                detection("8712100849060"),
                detection("5000159484695"),
                detection("4006381333931"),
            ),
        )

        assertEquals(
            ImportedBarcodeSelection.Outcome.Choice(
                listOf("8712100849060", "5000159484695", "4006381333931"),
            ),
            outcome,
        )
    }
}
