package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Adversarial mutations of the pickle jar, each removing **one** protective signal.
 *
 * ## Why replaying the archived capture is not enough
 *
 * `20260904-081151-032` reads correctly today: the label prints `koolhydraten 5,4 g` per 100 g and
 * `1,6 g` per 30 g serving, and the app offers `5.4 / PER_100_G`. Asserting that outcome proves the
 * pipeline works on the exact pixel arrangement that already works.
 *
 * The failure that matters is the one where the *correct* cell is missing and the *serving* cell
 * survives, because then nothing is left to out-compete a wrong assignment. The brief asks for that
 * case directly, and it does not appear in any archived bundle — so it is constructed here, from the
 * device's own geometry, by deletion only.
 *
 * ## Construction rule: every variant is the real document minus something
 *
 * No box is invented, moved or resized. Each variant is [ThirteenthSessionFixtures.pickleFivePointFour]
 * with specific elements removed, so a passing assertion is about the parser's reaction to a
 * degraded real label rather than to a hand-drawn one. That is the distinction this repo has
 * recorded twice — a fixture that models only the safe version of a hazard proves nothing.
 *
 * ## The invariant, in one sentence
 *
 * > **A serving-column figure must never be presented as a per-100 figure**, however much of the
 * > rest of the label is destroyed.
 *
 * Being unable to read the label is an acceptable outcome for every variant below. Reading it wrong
 * is not.
 */
class PickleAdversarialTest {

    private val perHundredValue = BigDecimal("5.4")
    private val servingValue = BigDecimal("1.6")

    private fun pickle() = ThirteenthSessionFixtures.pickleFivePointFour()

    /** The real document with every element whose text matches [texts] removed. */
    private fun without(vararg texts: String): OcrDocument {
        val doomed = texts.toSet()
        val original = pickle()
        return original.copy(
            elements = original.elements.filterNot { it.text.trim() in doomed },
        )
    }

    private fun offered(document: OcrDocument): Pair<BigDecimal, NutritionBasis?>? {
        val confident = NutritionTableParser.parseWithDiagnostics(document).reading
            as? LabelReading.Confident ?: return null
        return confident.candidate.value to confident.candidate.basis
    }

    private fun recoveryOffers(document: OcrDocument) =
        RecoveryCandidates.of(document).map { it.reading.amount to it.reading.basis }

    // ------------------------------------------------------------------ the baseline

    @Test
    fun `the unmodified capture still reads the per-100 figure`() {
        // The control. Without this the variants below could all "pass" on a fixture that had
        // stopped resolving anything at all.
        val (value, basis) = offered(pickle())!!
        assertEquals(0, value.compareTo(perHundredValue))
        assertEquals(NutritionBasis.PER_100_G, basis)
    }

    @Test
    fun `the fixture really contains both cells and both columns`() {
        // The precondition every variant depends on: there is genuinely a per-100 cell to delete
        // and a serving cell that could be mistaken for it.
        val document = pickle()
        val rows = LogicalRowBuilder.build(document)
        val kinds = ColumnClassifier.classify(rows, document.width).map { it.kind }
        assertTrue("expected a per-100 column, got $kinds", kinds.contains(NutritionColumnKind.PER_100_G))
        assertTrue("expected a serving column, got $kinds", kinds.contains(NutritionColumnKind.PER_SERVING))
        assertTrue(document.elements.any { it.text.trim() == "5,4g" })
        assertTrue(document.elements.any { it.text.trim() == "1,6" })
    }

    // ------------------------------------------------------------------ variant 1

    /**
     * **Variant 1 — the correct per-100 cell is missing; the serving cell remains.**
     *
     * The hazard: with nothing in the per-100 column, the surviving `1,6` is the only value on the
     * carbohydrate row, and a rule that binds "the nearest value" or "the only value" to the
     * resolved per-100 column would produce `1.6 g / 100 g` — a figure three and a half times too
     * small, on a dosing input, with nothing on screen to reveal it.
     *
     * The fifth session already measured the mirror image of this (a per-100 cell wearing the
     * serving column's basis) and fixed it with column ownership. This asserts the other direction.
     */
    @Test
    fun `variant 1 - a missing per-100 cell never promotes the serving figure`() {
        val document = without("5,4", "5,4g")
        assertTrue(
            "precondition: the serving figure must survive, or the variant tests nothing",
            document.elements.any { it.text.trim() == "1,6" },
        )

        offered(document)?.let { (value, basis) ->
            assertTrue(
                "the serving figure was presented as $value / $basis",
                !(value.compareTo(servingValue) == 0 && basis == NutritionBasis.PER_100_G),
            )
        }
        recoveryOffers(document).forEach { (value, basis) ->
            val isPerHundred = basis is app.justthecarbs.domain.CarbBasis.PerHundred
            assertTrue(
                "recovery offered the serving figure as a per-100 reading: $value / $basis",
                !(value.compareTo(servingValue) == 0 && isPerHundred),
            )
        }
    }

    // ------------------------------------------------------------------ variant 2

    /**
     * **Variant 2 — the serving header `30 g` is destroyed; both value cells remain.**
     *
     * The hazard: with the serving column unresolved, its cell is orphaned. A rule that assigns an
     * orphaned cell to the nearest surviving column would hand `1,6` to the per-100 column — and
     * because `5,4` is also there, the label would then carry two per-100 carbohydrate figures and
     * could resolve to either.
     *
     * The correct behaviour is that `5,4` wins its own column (it is the cell printed under it) and
     * `1,6` is either unplaced or refused. It must never be *offered as per-100*.
     */
    /**
     * **Measured note, recorded rather than dressed up.** Deleting the `30` token does *not* actually
     * dissolve the serving column on this document — the classifier still resolves `PER_SERVING`
     * from the surrounding header text (`part`, `per`). So this variant is a weaker mutation than
     * its name suggests, and the reading is unchanged at `5.4 / PER_100_G`.
     *
     * It is kept because the assertion it makes is still worth holding — no route may present `1,6`
     * as per-100 — and because the honest record of what a mutation did and did not remove is more
     * useful than a variant renamed to match its effect. Variants 1 and 3 are the ones that
     * genuinely degrade the document, and both change the outcome to `NotFound`.
     */
    @Test
    fun `variant 2 - a damaged serving header does not hand its cell to the per-100 column`() {
        val document = without("30")
        assertTrue(
            "precondition: both value cells must survive",
            document.elements.any { it.text.trim() == "5,4g" } &&
                document.elements.any { it.text.trim() == "1,6" },
        )

        offered(document)?.let { (value, basis) ->
            assertNotEquals(
                "the serving figure must not become a per-100 reading",
                0,
                value.compareTo(servingValue).takeIf { basis == NutritionBasis.PER_100_G } ?: 1,
            )
        }
        recoveryOffers(document).forEach { (value, basis) ->
            val isPerHundred = basis is app.justthecarbs.domain.CarbBasis.PerHundred
            assertTrue(
                "recovery offered $value as a per-100 reading with the serving header destroyed",
                !(value.compareTo(servingValue) == 0 && isPerHundred),
            )
        }
    }

    // ------------------------------------------------------------------ variant 3

    /**
     * **Variant 3 — the per-100 cell is missing AND the serving header is destroyed.**
     *
     * The hardest negative, and the one the brief names as such. Every signal that would normally
     * out-compete a wrong assignment is gone at once: there is one value on the carbohydrate row,
     * one resolved column, and no printed evidence tying them apart.
     *
     * **Recovery — or reading nothing at all — is the correct outcome.** What is forbidden is
     * presenting `1,6` as `1.6 g / 100 g`, and the assertion is written that way round: the test
     * does not require any particular refusal, only that no wrong figure appears through any route.
     */
    @Test
    fun `variant 3 - neither signal survives and no wrong per-100 figure is produced`() {
        val document = without("5,4", "5,4g", "30")

        offered(document)?.let { (value, basis) ->
            assertTrue(
                "with every distinguishing signal removed the app still claimed $value / $basis",
                !(value.compareTo(servingValue) == 0 && basis == NutritionBasis.PER_100_G),
            )
        }
        recoveryOffers(document).forEach { (value, basis) ->
            val isPerHundred = basis is app.justthecarbs.domain.CarbBasis.PerHundred
            assertTrue(
                "recovery offered $value / $basis with nothing left to justify it",
                !(value.compareTo(servingValue) == 0 && isPerHundred),
            )
        }
    }

    // ------------------------------------------------------------------ derived-value provenance

    /**
     * A converted figure keeps its provenance (brief section L).
     *
     * `1,6 g / 30 g` normalises to `5.333…`, which is arithmetically valid and is **not** what the
     * package prints (`5,4`). Whenever recovery offers a converted reading it must carry
     * `derivedFrom`, so the screen can show the printed figure beside the computed one rather than
     * presenting the computation as an OCR result.
     */
    @Test
    fun `a converted recovery offer always records what it was derived from`() {
        RecoveryCandidates.of(pickle()).forEach { candidate ->
            val normalized = candidate.reading.normalizedToPerHundred() ?: return@forEach
            val wasConverted = normalized.amount.compareTo(candidate.reading.amount) != 0
            if (wasConverted) {
                assertTrue(
                    "a converted offer (${candidate.reading.amount} -> ${normalized.amount}) " +
                        "must record its origin",
                    normalized.derivedFrom != null,
                )
            }
        }
    }
}
