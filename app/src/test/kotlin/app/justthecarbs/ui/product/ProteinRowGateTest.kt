package app.justthecarbs.ui.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The protein row's field-first gate, driven through the same show/measure/hide cycle the screen
 * runs (2026-09-25 review). [settle] models one frame per step: the zone's room is what is left of
 * [freeRoomPx] once the row takes its height, and the row reports its height while composed.
 */
class ProteinRowGateTest {

    private val estimate = 51
    private val floor = 48

    /** Runs the cycle for [frames] frames and returns the shown/hidden sequence. */
    private fun settle(freeRoomPx: Int, rowPx: Int, frames: Int = 12): List<Boolean> {
        var shown = true
        var zoneRoom = ProteinRowGate.UNMEASURED
        var occupying = 0
        var last = 0
        val sequence = mutableListOf<Boolean>()
        repeat(frames) {
            shown = ProteinRowGate.fits(zoneRoom, occupying, last, estimate, floor)
            sequence += shown
            // Layout of this frame: the zone gets what the dock left, and the row reports its size.
            zoneRoom = freeRoomPx - if (shown) rowPx else 0
            occupying = if (shown) rowPx else 0
            if (shown) last = rowPx
        }
        return sequence
    }

    @Test
    fun `plenty of room keeps the row`() {
        assertTrue(settle(freeRoomPx = 400, rowPx = 51).all { it })
    }

    @Test
    fun `too little room hides the row for good`() {
        val sequence = settle(freeRoomPx = 80, rowPx = 51)
        assertTrue("from the first measurement on, hidden", sequence.drop(1).none { it })
    }

    /**
     * The defect: a row taller than the estimate (87px against 51px) with room between
     * `estimate + floor` and `row + floor` flipped every frame when the cost fell back to the
     * estimate as soon as the row left.
     */
    @Test
    fun `a row taller than the estimate never oscillates`() {
        for (free in 99..140) {
            val sequence = settle(freeRoomPx = free, rowPx = 87)
            val settled = sequence.drop(2)
            assertEquals("room $free px flipped: $sequence", 1, settled.toSet().size)
        }
    }

    @Test
    fun `a taller row that does not fit stays hidden`() {
        val sequence = settle(freeRoomPx = 120, rowPx = 87)
        assertFalse(sequence.last())
    }

    @Test
    fun `before the first measurement the row is not withheld`() {
        assertTrue(ProteinRowGate.fits(ProteinRowGate.UNMEASURED, 0, 0, estimate, floor))
    }
}
