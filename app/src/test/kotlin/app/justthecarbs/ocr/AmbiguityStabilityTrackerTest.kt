package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal

// Suite: live-frame ambiguity stability
// Invariant: Confident emits immediately; NotFound never emits; Ambiguous only emits once the same
// interpretation has repeated for enough frames or persisted long enough, so a single incomplete
// frame cannot strand the user on a result the next frame might have resolved.
class AmbiguityStabilityTrackerTest {

    @Test
    fun `confident reading surfaces immediately`() {
        val tracker = AmbiguityStabilityTracker(stableFrameCount = 3)
        val confident = confident("48.2")

        assertSame(confident, tracker.onFrame(confident, nowNanos = 0L))
    }

    @Test
    fun `not found never surfaces`() {
        val tracker = AmbiguityStabilityTracker(stableFrameCount = 3)

        assertNull(tracker.onFrame(LabelReading.NotFound, nowNanos = 0L))
    }

    @Test
    fun `a single ambiguous frame does not surface`() {
        val tracker = AmbiguityStabilityTracker(stableFrameCount = 3, stableDurationNanos = Long.MAX_VALUE)
        val ambiguous = ambiguous("48.2", "51.0")

        assertNull(tracker.onFrame(ambiguous, nowNanos = 0L))
    }

    @Test
    fun `the same ambiguous interpretation repeated for stableFrameCount frames surfaces`() {
        val tracker = AmbiguityStabilityTracker(stableFrameCount = 3, stableDurationNanos = Long.MAX_VALUE)
        val ambiguous = ambiguous("48.2", "51.0")

        assertNull(tracker.onFrame(ambiguous, nowNanos = 0L))
        assertNull(tracker.onFrame(ambiguous, nowNanos = 1L))
        assertEquals(ambiguous, tracker.onFrame(ambiguous, nowNanos = 2L))
    }

    @Test
    fun `a changed ambiguous interpretation resets the frame count`() {
        val tracker = AmbiguityStabilityTracker(stableFrameCount = 3, stableDurationNanos = Long.MAX_VALUE)

        assertNull(tracker.onFrame(ambiguous("48.2", "51.0"), nowNanos = 0L))
        assertNull(tracker.onFrame(ambiguous("48.2", "51.0"), nowNanos = 1L))
        // A different interpretation arrives on the third frame instead of confirming the first —
        // an early incomplete frame must not have permanently biased the outcome.
        assertNull(tracker.onFrame(ambiguous("60.0"), nowNanos = 2L))
        assertNull(tracker.onFrame(ambiguous("60.0"), nowNanos = 3L))
        assertEquals(ambiguous("60.0"), tracker.onFrame(ambiguous("60.0"), nowNanos = 4L))
    }

    @Test
    fun `ambiguity surfaces once the stable duration elapses even with few frames`() {
        val tracker = AmbiguityStabilityTracker(stableFrameCount = 100, stableDurationNanos = 500L)
        val ambiguous = ambiguous("48.2", "51.0")

        assertNull(tracker.onFrame(ambiguous, nowNanos = 0L))
        assertEquals(ambiguous, tracker.onFrame(ambiguous, nowNanos = 500L))
    }

    @Test
    fun `confident reading resets tracking so a later ambiguous frame starts over`() {
        val tracker = AmbiguityStabilityTracker(stableFrameCount = 2, stableDurationNanos = Long.MAX_VALUE)
        val ambiguous = ambiguous("48.2", "51.0")

        assertNull(tracker.onFrame(ambiguous, nowNanos = 0L))
        tracker.onFrame(confident("60.0"), nowNanos = 1L)
        // Without the reset, this single frame would already meet stableFrameCount = 2.
        assertNull(tracker.onFrame(ambiguous, nowNanos = 2L))
    }

    @Test
    fun `not found resets tracking so a later ambiguous frame starts over`() {
        val tracker = AmbiguityStabilityTracker(stableFrameCount = 2, stableDurationNanos = Long.MAX_VALUE)
        val ambiguous = ambiguous("48.2", "51.0")

        assertNull(tracker.onFrame(ambiguous, nowNanos = 0L))
        tracker.onFrame(LabelReading.NotFound, nowNanos = 1L)
        assertNull(tracker.onFrame(ambiguous, nowNanos = 2L))
    }

    @Test
    fun `explicit reset clears tracking`() {
        val tracker = AmbiguityStabilityTracker(stableFrameCount = 2, stableDurationNanos = Long.MAX_VALUE)
        val ambiguous = ambiguous("48.2", "51.0")

        assertNull(tracker.onFrame(ambiguous, nowNanos = 0L))
        tracker.reset()
        assertNull(tracker.onFrame(ambiguous, nowNanos = 1L))
    }

    private fun confident(value: String): LabelReading.Confident =
        LabelReading.Confident(carbCandidate(value, NutritionBasis.PER_100_G))

    private fun ambiguous(vararg values: String): LabelReading.Ambiguous =
        LabelReading.Ambiguous(values.map { carbCandidate(it, NutritionBasis.PER_100_G) })

    private fun carbCandidate(value: String, basis: NutritionBasis?) = CarbCandidate(
        sourceLine = "Carbohydrate $value g",
        label = "Carbohydrate",
        value = BigDecimal(value),
        basis = basis,
        score = 999,
        geometry = OcrBox(0, 0, 10, 10),
        evidence = emptyList(),
    )
}
