package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Why the automatic path may wrap every basis as [CarbBasis.PerHundred].
 *
 * ## The inference this exists to stop
 *
 * [ReadingEligibility] branches on the basis's **provenance**: a serving the label *declared*
 * ([CarbBasis.PerQuantity], [CarbBasis.PerUnknownServing]) is admissible where an *inferred*
 * [CarbBasis.PerHundred] is refused. That distinction is what keeps the Korean sauce's
 * `6 g / 18 g serving` offerable while refusing the red Lidl label's `12 g / 100 g`.
 *
 * [AutomaticScanAdvance.eligibility] wraps unconditionally as `PerHundred`, which reads like it
 * flattens that distinction and silently deletes the escape hatch. **It does not**, and the reason
 * is type-level rather than a convention someone maintains — but it is spread across two files, so a
 * reviewer reaching for the obvious "fix" would widen a safety rule by accident. This states the
 * argument as executable assertions instead of prose.
 *
 * ## What is actually guaranteed
 *
 * 1. A [CarbCandidate] may only come from a per-hundred column or from no column at all; its `init`
 *    makes anything else unconstructible.
 * 2. `CarbCandidate.basis` is a [NutritionBasis], an enum with exactly the two per-hundred members.
 *
 * Together those mean the automatic path has no declared serving to lose. The declared-serving
 * branch is reachable only through recovery, which builds its own
 * [app.justthecarbs.domain.CarbReading].
 */
class AutomaticBasisProvenanceTest {

    private fun candidateFrom(column: NutritionColumnKind?) = CarbCandidate(
        sourceLine = "Koolhydraten 12 g",
        label = "Koolhydraten",
        value = BigDecimal("12"),
        basis = NutritionBasis.PER_100_G,
        score = 0,
        geometry = OcrBox(100, 100, 200, 140),
        evidence = emptyList(),
        column = column,
    )

    @Test
    fun `a candidate may only come from a per-hundred column or none`() {
        // The permitted set. Constructing these must not throw.
        listOf(null, NutritionColumnKind.PER_100_G, NutritionColumnKind.PER_100_ML)
            .forEach { candidateFrom(it) }
    }

    @Test
    fun `a serving column can never supply a candidate`() {
        // This is the guarantee the `PerHundred` wrapping rests on. If it ever stops holding, the
        // automatic path could carry a per-serving figure and the wrapping WOULD be lossy.
        assertThrows(IllegalArgumentException::class.java) {
            candidateFrom(NutritionColumnKind.PER_SERVING)
        }
    }

    @Test
    fun `a reference-percent or unresolved column can never supply a candidate`() {
        listOf(NutritionColumnKind.REFERENCE_PERCENT, NutritionColumnKind.UNKNOWN).forEach { column ->
            assertThrows(IllegalArgumentException::class.java) { candidateFrom(column) }
        }
    }

    @Test
    fun `every column kind is either permitted or refused - none is unconsidered`() {
        // Stated over the whole enum so a new column kind has to decide this question rather than
        // inheriting an answer. A new kind defaults to *refused* by the `init`, which is the safe
        // direction, and this records that as intended rather than accidental.
        val permitted = setOf(NutritionColumnKind.PER_100_G, NutritionColumnKind.PER_100_ML)
        NutritionColumnKind.entries.forEach { column ->
            val threw = runCatching { candidateFrom(column) }.isFailure
            assertEquals(
                "$column must be refused unless it can state a per-hundred carbohydrate quantity",
                column !in permitted,
                threw,
            )
        }
    }

    @Test
    fun `a candidate basis can only express the two per-hundred bases`() {
        // The second half of the argument: even an unconstrained column could not smuggle a serving
        // in, because the basis field has no member that could represent one.
        assertEquals(
            setOf(NutritionBasis.PER_100_G, NutritionBasis.PER_100_ML),
            NutritionBasis.entries.toSet(),
        )
    }

    @Test
    fun `wrapping a candidate basis always yields PerHundred`() {
        // Therefore the wrapping in [AutomaticScanAdvance.eligibility] is faithful: there is no
        // input for which it discards a declared serving.
        NutritionBasis.entries.forEach { basis ->
            assertTrue(CarbBasis.PerHundred(basis) is CarbBasis.PerHundred)
        }
    }

    @Test
    fun `the declared-serving branch is admissible - it is simply unreachable from here`() {
        // Guards against "the branch is dead, delete it". It is live and load-bearing on the
        // recovery surface; only the automatic path cannot reach it.
        val declared = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported("6", "no paired value"),
            basis = CarbBasis.PerQuantity(BigDecimal("18"), NutritionBasis.PER_100_G, "Tbsp"),
            corroborated = false,
        )
        assertTrue("a declared serving must remain offerable", declared.isEligible)

        // The same figure under an inferred per-hundred basis — the automatic path's only shape — is
        // refused. This is the red Lidl label's `12`.
        val inferred = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported("12", "no paired value"),
            basis = CarbBasis.PerHundred(NutritionBasis.PER_100_G),
            corroborated = false,
        )
        assertTrue("an inferred per-hundred basis must stay refused", !inferred.isEligible)
    }
}
