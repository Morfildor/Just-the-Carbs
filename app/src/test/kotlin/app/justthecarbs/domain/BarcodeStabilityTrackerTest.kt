package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: barcode acceptance
// Invariant: a lookup fires only for a barcode the user has deliberately presented — framed, close,
// and held. A single decodable frame is evidence that a barcode exists, not that it is the one they
// meant. Every gate here is a refusal; none of them can be traded off against another.
class BarcodeStabilityTrackerTest {

    private val validEan = "5000112637922"
    private val otherEan = "8710398515704"

    private fun centred(value: String, size: Double = 0.4, nanos: Long = 0L) = NormalizedBarcode(
        value = value,
        format = BarcodeFormat.EAN_13,
        box = NormalizedBox(
            left = 0.5 - size / 2,
            top = 0.5 - size / 8,
            right = 0.5 + size / 2,
            bottom = 0.5 + size / 8,
        ),
        timestampNanos = nanos,
    )

    private fun at(value: String, centerX: Double, centerY: Double, size: Double = 0.4) = NormalizedBarcode(
        value = value,
        format = BarcodeFormat.EAN_13,
        box = NormalizedBox(
            left = centerX - size / 2,
            top = centerY - size / 8,
            right = centerX + size / 2,
            bottom = centerY + size / 8,
        ),
        timestampNanos = 0L,
    )

    /** One frame every 33 ms, an ordinary 30 fps analyzer. */
    private fun frameNanos(index: Int) = index * 33_000_000L

    @Test
    fun `one qualified frame is never enough`() {
        val tracker = BarcodeStabilityTracker()
        assertEquals(
            BarcodeAcceptance.Stabilizing,
            tracker.onFrame(listOf(centred(validEan)), frameNanos(0)),
        )
    }

    @Test
    fun `a held barcode is accepted`() {
        val tracker = BarcodeStabilityTracker()
        tracker.onFrame(listOf(centred(validEan)), frameNanos(0))
        tracker.onFrame(listOf(centred(validEan)), frameNanos(1))
        assertEquals(
            BarcodeAcceptance.Accepted(validEan),
            tracker.onFrame(listOf(centred(validEan)), frameNanos(2)),
        )
    }

    @Test
    fun `a barcode is accepted exactly once`() {
        val tracker = BarcodeStabilityTracker()
        val results = (0..9).map { tracker.onFrame(listOf(centred(validEan)), frameNanos(it)) }
        assertEquals(
            "a streaming analyzer delivers the same code many times; one lookup must follow",
            1,
            results.count { it is BarcodeAcceptance.Accepted },
        )
    }

    @Test
    fun `a second barcode restarts the count rather than inheriting it`() {
        val tracker = BarcodeStabilityTracker()
        tracker.onFrame(listOf(centred(validEan)), frameNanos(0))
        tracker.onFrame(listOf(centred(validEan)), frameNanos(1))
        // The user swung the phone to the product next to it.
        assertEquals(
            BarcodeAcceptance.Stabilizing,
            tracker.onFrame(listOf(centred(otherEan)), frameNanos(2)),
        )
        assertEquals(
            BarcodeAcceptance.Stabilizing,
            tracker.onFrame(listOf(centred(otherEan)), frameNanos(3)),
        )
        assertEquals(
            BarcodeAcceptance.Accepted(otherEan),
            tracker.onFrame(listOf(centred(otherEan)), frameNanos(4)),
        )
    }

    @Test
    fun `losing sight of the barcode restarts the count`() {
        val tracker = BarcodeStabilityTracker()
        tracker.onFrame(listOf(centred(validEan)), frameNanos(0))
        tracker.onFrame(listOf(centred(validEan)), frameNanos(1))
        assertEquals(BarcodeAcceptance.Searching, tracker.onFrame(emptyList(), frameNanos(2)))
        // Two more frames must not be enough: stability means held, not seen this often in total.
        tracker.onFrame(listOf(centred(validEan)), frameNanos(3))
        assertEquals(
            BarcodeAcceptance.Stabilizing,
            tracker.onFrame(listOf(centred(validEan)), frameNanos(4)),
        )
    }

    @Test
    fun `a barcode outside the scan region is ignored however long it is held`() {
        val tracker = BarcodeStabilityTracker()
        repeat(20) { index ->
            assertEquals(
                "a barcode at the frame's edge is the shelf, not the product",
                BarcodeAcceptance.Searching,
                tracker.onFrame(listOf(at(validEan, centerX = 0.05, centerY = 0.5)), frameNanos(index)),
            )
        }
    }

    @Test
    fun `a barcode too small to be deliberate is ignored however long it is held`() {
        val tracker = BarcodeStabilityTracker()
        repeat(20) { index ->
            assertEquals(
                "the user is still approaching it",
                BarcodeAcceptance.Searching,
                tracker.onFrame(listOf(centred(validEan, size = 0.08)), frameNanos(index)),
            )
        }
    }

    @Test
    fun `a barcode that grows into range is then accepted normally`() {
        val tracker = BarcodeStabilityTracker()
        tracker.onFrame(listOf(centred(validEan, size = 0.08)), frameNanos(0))
        tracker.onFrame(listOf(centred(validEan, size = 0.12)), frameNanos(1))
        assertEquals(
            BarcodeAcceptance.Stabilizing,
            tracker.onFrame(listOf(centred(validEan, size = 0.45)), frameNanos(2)),
        )
        tracker.onFrame(listOf(centred(validEan, size = 0.45)), frameNanos(3))
        assertEquals(
            BarcodeAcceptance.Accepted(validEan),
            tracker.onFrame(listOf(centred(validEan, size = 0.45)), frameNanos(4)),
        )
    }

    @Test
    fun `reset allows the next barcode to be scanned`() {
        val tracker = BarcodeStabilityTracker()
        repeat(3) { tracker.onFrame(listOf(centred(validEan)), frameNanos(it)) }
        // Latched: without a reset the scanner cannot fire again, which is what stops a stream from
        // navigating twice.
        assertEquals(
            BarcodeAcceptance.Searching,
            tracker.onFrame(listOf(centred(otherEan)), frameNanos(3)),
        )

        tracker.reset()

        repeat(2) { tracker.onFrame(listOf(centred(otherEan)), frameNanos(10 + it)) }
        assertEquals(
            BarcodeAcceptance.Accepted(otherEan),
            tracker.onFrame(listOf(centred(otherEan)), frameNanos(12)),
        )
    }

    @Test
    fun `a slow analyzer accepts on elapsed time rather than making the user wait for frames`() {
        // 5 fps. Waiting for three frames would be 600 ms of nothing happening.
        val tracker = BarcodeStabilityTracker()
        tracker.onFrame(listOf(centred(validEan)), 0L)
        assertEquals(
            BarcodeAcceptance.Accepted(validEan),
            tracker.onFrame(listOf(centred(validEan)), 300_000_000L),
        )
    }

    @Test
    fun `elapsed time alone never accepts on a single frame`() {
        // A code seen once, then again a full second later, was not held — it was seen twice.
        val tracker = BarcodeStabilityTracker()
        assertEquals(
            BarcodeAcceptance.Stabilizing,
            tracker.onFrame(listOf(centred(validEan)), 0L),
        )
    }

    @Test
    fun `the larger of two visible barcodes wins`() {
        val tracker = BarcodeStabilityTracker()
        val frames = (0..2).map {
            tracker.onFrame(
                listOf(centred(otherEan, size = 0.22), centred(validEan, size = 0.55)),
                frameNanos(it),
            )
        }
        assertEquals(
            "the product the user moved closer to is the one they are pointing at",
            BarcodeAcceptance.Accepted(validEan),
            frames.last(),
        )
    }

    @Test
    fun `a barcode wider than the frame is still accepted`() {
        // Wrapped around a jar: the box exceeds the region on both sides. Requiring containment
        // would refuse exactly the packages that are hardest to scan.
        val tracker = BarcodeStabilityTracker()
        val wide = NormalizedBarcode(
            value = validEan,
            format = BarcodeFormat.EAN_13,
            box = NormalizedBox(left = 0.0, top = 0.45, right = 1.0, bottom = 0.55),
            timestampNanos = 0L,
        )
        repeat(2) { tracker.onFrame(listOf(wide), frameNanos(it)) }
        assertEquals(BarcodeAcceptance.Accepted(validEan), tracker.onFrame(listOf(wide), frameNanos(2)))
    }

    @Test
    fun `the tracker never sees an invalid check digit because reading refuses it first`() {
        // The gate order matters: validation happens before anything can accumulate stability, so a
        // misread digit cannot become a lookup for a different product no matter how steadily it is
        // held. Stated here as one pipeline rather than two unrelated units.
        val misread = BarcodeFrameReader.read(
            rawValue = "5000112637923", // last digit off by one
            format = BarcodeFormat.EAN_13,
            boxLeft = 300, boxTop = 700, boxRight = 780, boxBottom = 860,
            uprightWidth = 1080, uprightHeight = 1920,
            timestampNanos = 0L,
        )
        assertTrue("a bad check digit must not produce a candidate at all", misread == null)

        val tracker = BarcodeStabilityTracker()
        repeat(10) { index ->
            assertEquals(
                BarcodeAcceptance.Searching,
                tracker.onFrame(listOfNotNull(misread), frameNanos(index)),
            )
        }
    }
}
