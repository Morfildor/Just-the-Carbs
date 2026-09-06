package app.justthecarbs.ocr.fixtures

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Machine-readable ground truth for every optical fixture this plan's Phase 5/10 tests assert
 * against. Each case names the printed value/basis from the physical package (not from what any
 * pass of ML Kit returned), the final UI actions that are acceptable outcomes, and the values that
 * must never be displayed regardless of which path is taken.
 *
 * A capture with no established ground truth (nobody has read the physical package and recorded
 * the printed figure) must not appear here — an unverified guess about "what the label probably
 * says" would let a wrong recognizer output silently pass as correct.
 */
object GroundTruthManifest {

    data class GroundTruthCase(
        val captureId: String,
        val printedCarbValue: BigDecimal,
        val printedBasis: NutritionBasis,
        /** Final actions acceptable for this capture — e.g. AUTO_ADVANCE, CONFIRM_ON_CAPTURE, FOCUSED_AMOUNT_ENTRY. */
        val allowedFinalActions: Set<String>,
        /** Values that must never be shown or prefilled for this capture, under any path. */
        val forbiddenDisplayedValues: Set<BigDecimal>,
        val hasServingFacts: Boolean = false,
    )

    /**
     * Seeded from CLAUDE.md's documented session fixtures with an established printed ground
     * truth. Extend this list as new physical captures are verified against their package — do not
     * add a case whose printed value has not actually been read off the physical label.
     */
    val CASES: List<GroundTruthCase> = listOf(
        // Hellmann's bottle, docs/Scan Evidence new structure/20260904-113950-065: prints
        // "1,3 g / 100 ml"; every recognition view reads "13g". This is the case Task 4 exists to
        // close: 13 must never be prefilled for one-tap acceptance.
        //
        // CONFIRM_UNVERIFIED added to the allowed set (twentieth session): the task's own explicit
        // policy boundary states that a reading indistinguishable from a legitimate integer "may
        // appear only in the clearly unverified visual-confirmation state" — never auto-accepted,
        // never one-tap. [FifteenthSessionReplay.Result.offeredValue] stays null for
        // CONFIRM_UNVERIFIED (it is deliberately excluded from `presents`, alongside AUTO_ADVANCE/
        // CONFIRM_ON_CAPTURE/CONFIRM), so `forbiddenDisplayedValues` below is unaffected: `13` is
        // still never counted as an app-asserted, prefilled value under any action.
        GroundTruthCase(
            captureId = "20260904-113950-065",
            printedCarbValue = BigDecimal("1.3"),
            printedBasis = NutritionBasis.PER_100_ML,
            allowedFinalActions = setOf("RECOVERY", "FOCUSED_AMOUNT_ENTRY", "CONFIRM_UNVERIFIED"),
            forbiddenDisplayedValues = setOf(BigDecimal("13"), BigDecimal("13.0")),
        ),
    )
}

class GroundTruthManifestTest {
    @Test fun `manifest is non-empty and every forbidden value differs from the printed value`() {
        assertTrue(GroundTruthManifest.CASES.isNotEmpty())
        GroundTruthManifest.CASES.forEach { case ->
            case.forbiddenDisplayedValues.forEach { forbidden ->
                assertTrue(
                    "case ${case.captureId}: forbidden value $forbidden must differ from printed ${case.printedCarbValue}",
                    forbidden.compareTo(case.printedCarbValue) != 0,
                )
            }
        }
    }
}
