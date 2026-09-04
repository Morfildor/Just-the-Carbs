package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prints the thirteenth session's corpus-level outcome and pins the fixtures to their bundles.
 *
 * Deliberately light on assertions about *behaviour* — that is
 * [ThirteenthSessionRegressionTest]'s job. What this class guarantees is that the fixtures still
 * describe the captures they were derived from, so a corpus metric is a statement about the phone
 * session rather than about a drifted transcription.
 */
class ThirteenthSessionBaselineTest {

    @Test
    fun `every fixture carries its bundle's own element count`() {
        // From each bundle's `image: WxH elements=N` line. A fixture that lost or gained an element
        // is no longer the device's document, and every metric taken from it would be about
        // something else.
        val expected = mapOf(
            "20260904-080926-938" to 99,
            "20260904-080948-287" to 86,
            "20260904-081003-678" to 96,
            "20260904-081018-275" to 89,
            "20260904-081039-484" to 78,
            "20260904-081055-219" to 95,
            "20260904-081108-784" to 177,
            "20260904-081129-886" to 79,
            "20260904-081141-102" to 94,
            "20260904-081151-032" to 142,
            "20260904-081213-403" to 168,
            "20260904-081226-187" to 153,
            "20260904-081244-479" to 0,
            "20260904-081251-955" to 36,
            "20260904-081307-240" to 36,
            "20260904-081335-279" to 140,
            "20260904-081407-814" to 88,
            "20260904-081421-421" to 89,
            "20260904-081435-300" to 341,
        )
        ThirteenthSessionCorpus.captures.forEach { capture ->
            assertEquals(
                "element count for ${capture.bundle}",
                expected.getValue(capture.bundle),
                capture.document().elements.size,
            )
        }
    }

    @Test
    fun `every capture is 1684x3648, the device's upright capture size`() {
        ThirteenthSessionCorpus.captures.forEach { capture ->
            val document = capture.document()
            assertEquals(capture.bundle, 1684, document.width)
            assertEquals(capture.bundle, 3648, document.height)
        }
    }

    @Test
    fun `the corpus replay prints its table`() {
        val results = ThirteenthSessionReplay.replayAll()
        println(ThirteenthSessionReplay.table(results))
        assertEquals(19, results.size)
        assertTrue(results.all { it.capture.printedCarbs != null || it.capture.bundle == "20260904-081244-479" })
    }
}
