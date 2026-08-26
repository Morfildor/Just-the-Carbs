package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The framing-size advisory, pinned against the values that were actually measured.
 *
 * The numbers in these tests are not invented. They are the median height fractions
 * `TextResolutionCalibrationTest` recorded on the real corpus, so a threshold change that
 * contradicts the measurement fails here.
 */
class TextResolutionGuidanceTest {

    /** A document whose median element height is [height] against a frame of [frameHeight]. */
    private fun document(height: Int, frameHeight: Int, count: Int = 20): OcrDocument {
        val elements = (0 until count).map {
            OcrElement(
                text = "x",
                box = OcrBox(0, it * height * 2, 40, it * height * 2 + height),
                blockId = 0,
                lineId = it,
            )
        }
        return OcrDocument(width = 1000, height = frameHeight, elements = elements)
    }

    @Test
    fun `an empty frame is not a complaint about framing`() {
        val estimate = TextResolutionGuidance.estimate(OcrDocument(1000, 1000, emptyList()))
        assertEquals(TextResolutionGuidance.Readiness.NO_TEXT, estimate.readiness)
    }

    @Test
    fun `a frame with only a couple of recognized words is not yet evidence`() {
        // Two words is a camera that has barely started, not a framing verdict.
        val estimate = TextResolutionGuidance.estimate(document(height = 4, frameHeight = 1536, count = 2))
        assertEquals(TextResolutionGuidance.Readiness.NO_TEXT, estimate.readiness)
    }

    @Test
    fun `the measured kinder failure at ratio 0_30 is reported as too small`() {
        // Measured: medianFrac 0.00716, production verdict NotFound.
        val estimate = TextResolutionGuidance.estimate(document(height = 11, frameHeight = 1536))
        assertTrue("0.00716 must fall below the cutoff", estimate.medianHeightFraction < 0.0090)
        assertEquals(TextResolutionGuidance.Readiness.TOO_SMALL, estimate.readiness)
    }

    @Test
    fun `the measured sondey success at ratio 0_30 is NOT called too small`() {
        // Measured: medianFrac 0.00977, production verdict Confident 61.9. This is the case that
        // forced the threshold below 0.010 — telling this user to move closer would contradict a
        // scan that works.
        val estimate = TextResolutionGuidance.estimate(document(height = 15, frameHeight = 1536))
        assertTrue(estimate.medianHeightFraction >= 0.0090)
        assertEquals(TextResolutionGuidance.Readiness.READY, estimate.readiness)
    }

    @Test
    fun `the measured kinder success at ratio 1_00 is NOT called too small`() {
        // Measured: medianFrac 0.00938, production verdict Confident 53.5. The tightest constraint
        // on the threshold from above.
        val estimate = TextResolutionGuidance.estimate(document(height = 15, frameHeight = 1600))
        assertTrue(estimate.medianHeightFraction >= 0.0090)
        assertEquals(TextResolutionGuidance.Readiness.READY, estimate.readiness)
    }

    @Test
    fun `READY is not a claim that recognition will succeed`() {
        // Measured: kinder at ratio 0.80 has the largest text of any kinder rendering (0.01823) and
        // still returns NotFound, because surrounding package prose competes. This test exists to
        // pin that READY says nothing about the outcome — if it ever starts implying success, the
        // guidance has overreached beyond what it can measure.
        val estimate = TextResolutionGuidance.estimate(document(height = 28, frameHeight = 1536))
        assertEquals(
            "large text is READY even though this exact framing fails in production",
            TextResolutionGuidance.Readiness.READY,
            estimate.readiness,
        )
    }
}
