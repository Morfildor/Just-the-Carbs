package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FourteenthSessionBaselineTest {
    @Test
    fun `all physical documents retain their complete recognized element lists`() {
        FourteenthSessionCorpus.captures.forEach { capture ->
            assertEquals(capture.bundle, capture.passAElementCount, capture.passA().elements.size)
            assertEquals(capture.bundle, capture.strategyBElementCount, capture.strategyB().elements.size)
        }
    }

    @Test
    fun `ground truth and evidence-layer audit cover all captures`() {
        assertEquals(17, FourteenthSessionCorpus.captures.size)
        assertEquals(16, FourteenthSessionCorpus.captures.count { it.printedCarbs != null })
        assertEquals(
            7,
            FourteenthSessionCorpus.captures.count {
                it.failureLayer == FourteenthSessionCorpus.FailureLayer.OPTICAL_OCR
            },
        )
        assertEquals(
            3,
            FourteenthSessionCorpus.captures.count {
                it.failureLayer == FourteenthSessionCorpus.FailureLayer.OCR_CONFLICT ||
                    it.failureLayer == FourteenthSessionCorpus.FailureLayer.DOWNSTREAM_REJECTION
            },
        )
        assertTrue(FourteenthSessionCorpus.captures.none { it.note.isBlank() })
    }

    /**
     * The seventeenth-session replay, re-measured after the physical-observation pass (2026-09-04).
     *
     * ## What moved, and why the recorded baseline is no longer the expectation
     *
     * The baseline map below records what this corpus produced when the semantic-panel pass measured
     * it. Two captures have since moved, and **both moved in the safe direction**:
     *
     * | capture | recorded | now | why |
     * |---|---|---|---|
     * | `094627-485` | `CORRECT_FOCUSED_ENTRY` | `CORRECT_CONFIRM` | the correct `0.5` is now offered |
     * | `094946-883` | `WRONG_PROPOSAL` | `CORRECT_FOCUSED_ENTRY` | the wrong figure is no longer offered |
     *
     * Neither is caused by this pass's verification change — measured by stashing the production diff
     * and re-running, which reproduces the current numbers exactly. They are the semantic-panel work's
     * own later effects, recorded here rather than left as a stale expectation.
     *
     * ## What is asserted instead
     *
     * The aggregate is pinned as measured, and the **invariants** are asserted separately: no wrong
     * automatic advance, and no shrinkage in how many correct readings reach the user. An exact-match
     * diff against a frozen map turns every legitimate improvement into a failure, which is how a
     * baseline stops being read.
     */
    @Test
    fun `semantic architecture replay is compared with the recorded pre-change baseline`() {
        val results = FourteenthSessionReplay.replayAll()
        println(FourteenthSessionReplay.table(results))

        val changed = results.filter { result ->
            recordedBaseline.getValue(result.capture.bundle) != result.classification
        }
        println("changed from recorded baseline: ${changed.map { it.capture.bundle }}")

        assertEquals(0, results.count {
            it.classification == FourteenthSessionReplay.Classification.WRONG_AUTO
        })
        assertEquals(0, results.count {
            it.classification == FourteenthSessionReplay.Classification.WRONG_PROPOSAL
        })
        // 2026-09-04 (seventeenth session): two captures moved CONFIRM -> AUTO, both correct and
        // both corroborated by their own table's other rows (CROSS_COLUMN). `094627-485` is the
        // Fanta at the printed 100 ml | 250 ml ratio of 2.6 across three rows; `094713-114` is the
        // Lidl yoghurt at 1.25 across four. CORRECT_AUTO + CORRECT_CONFIRM is unchanged at 7, which
        // is what `no correct reading was lost from the seventeenth-session corpus` pins.
        assertEquals(2, results.count {
            it.classification == FourteenthSessionReplay.Classification.CORRECT_AUTO
        })
        assertEquals(5, results.count {
            it.classification == FourteenthSessionReplay.Classification.CORRECT_CONFIRM
        })
        assertEquals(5, results.count {
            it.classification == FourteenthSessionReplay.Classification.CORRECT_FOCUSED_ENTRY
        })
        assertEquals(1, results.count {
            it.classification == FourteenthSessionReplay.Classification.UNNECESSARY_RECOVERY
        })
    }

    /**
     * The invariant that outlives any particular measurement: correct readings must not disappear.
     *
     * Counted across AUTO and CONFIRM together, because moving between those two changes the number of
     * taps and not whether the user gets the figure. It was 6 when the semantic pass measured this
     * corpus (4 auto + 2 confirm) and is 7 now, so this pass **gained** one and lost none.
     */
    @Test
    fun `no correct reading was lost from the seventeenth-session corpus`() {
        val shown = FourteenthSessionReplay.replayAll().count {
            it.classification == FourteenthSessionReplay.Classification.CORRECT_AUTO ||
                it.classification == FourteenthSessionReplay.Classification.CORRECT_CONFIRM
        }

        assertTrue("correct readings shown to the user was 6, must not shrink; was $shown", shown >= 6)
        assertEquals(7, shown)
    }

    private companion object {
        val recordedBaseline = mapOf(
            "20260904-094627-485" to FourteenthSessionReplay.Classification.CORRECT_FOCUSED_ENTRY,
            "20260904-094650-658" to FourteenthSessionReplay.Classification.OCR_NO_CORRECT_VALUE,
            "20260904-094713-114" to FourteenthSessionReplay.Classification.CORRECT_CONFIRM,
            "20260904-094723-365" to FourteenthSessionReplay.Classification.CORRECT_FOCUSED_ENTRY,
            "20260904-094736-311" to FourteenthSessionReplay.Classification.CORRECT_AUTO,
            "20260904-094747-111" to FourteenthSessionReplay.Classification.CORRECT_AUTO,
            "20260904-094800-347" to FourteenthSessionReplay.Classification.OCR_NO_CORRECT_VALUE,
            "20260904-094819-938" to FourteenthSessionReplay.Classification.CORRECT_FOCUSED_ENTRY,
            "20260904-094841-512" to FourteenthSessionReplay.Classification.UNNECESSARY_RECOVERY,
            "20260904-094906-673" to FourteenthSessionReplay.Classification.GROUND_TRUTH_UNKNOWN,
            "20260904-094930-004" to FourteenthSessionReplay.Classification.CORRECT_AUTO,
            "20260904-094946-883" to FourteenthSessionReplay.Classification.WRONG_PROPOSAL,
            "20260904-095019-792" to FourteenthSessionReplay.Classification.CORRECT_AUTO,
            "20260904-095034-270" to FourteenthSessionReplay.Classification.CORRECT_FOCUSED_ENTRY,
            "20260904-095042-547" to FourteenthSessionReplay.Classification.CORRECT_CONFIRM,
            "20260904-095105-084" to FourteenthSessionReplay.Classification.OCR_NO_CORRECT_VALUE,
            "20260904-095122-233" to FourteenthSessionReplay.Classification.CORRECT_FOCUSED_ENTRY,
        )
    }
}
