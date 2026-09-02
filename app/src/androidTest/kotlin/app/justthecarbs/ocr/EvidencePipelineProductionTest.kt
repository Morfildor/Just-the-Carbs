package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

/**
 * The multi-strategy evidence pipeline, measured on the real corpus with the real recognizer.
 *
 * This is the suite that answers "did the architecture change actually help, and did it cost
 * anything". It runs [SelectedTableResolution] — Pass A + Strategy A + Strategy B + the resolver —
 * exactly as `LabelScannerScreen` does, against real photographs.
 *
 * ## Read this before adjusting anything here
 *
 * The headline safety property is **zero confident-wrong**, and the measured data says a second
 * recognition can absolutely produce one: on grated cheese the full frame reads `2.09`, a 5% crop
 * reads `2` and the production-overlay crop reads `2.04`, for a package printed `2,0`. The resolver
 * refuses that conflict rather than picking, so a disagreement surfaces as `Conflicted` and the user
 * is asked to point at the figure. A test that "fixed" such a case by making one source win would be
 * reintroducing exactly the failure this pass exists to prevent.
 */
class EvidencePipelineProductionTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private fun asset(name: String): Bitmap {
        val stream = try {
            testContext.assets.open("ocr_real/$name")
        } catch (e: IOException) {
            throw AssertionError("ocr_real/$name is missing; it is a committed, mandatory fixture", e)
        }
        return stream.use {
            requireNotNull(BitmapFactory.decodeStream(it)) { "ocr_real/$name did not decode" }
        }
    }

    /** Runs the whole evidence pipeline the way the scanner does. */
    private fun resolve(
        name: String,
        region: NormalizedRegion = OVERLAY,
        live: RecognitionEvidence? = null,
    ): SelectedTableResolution.Result {
        val bitmap = asset(name)
        return try {
            val text = RealRecognition.recognise(bitmap)
            val document = MlKitOcrMapper.toDocument(text, bitmap.width, bitmap.height)
            val passA = PassAResult(
                sessionId = 1L,
                document = document,
                report = NutritionTableParser.parseWithDiagnostics(document),
                bitmap = bitmap,
                evidence = null,
                recognitionMs = 0,
            )
            SelectedTableResolution.resolve(passA, region, bitmap, live)
        } finally {
            bitmap.recycle()
        }
    }

    private fun resolvedValue(outcome: EvidenceResolver.Outcome): BigDecimal? = when (outcome) {
        is EvidenceResolver.Outcome.Resolved ->
            (outcome.reading as? LabelReading.Confident)?.candidate?.value
        is EvidenceResolver.Outcome.NeedsVerification -> outcome.reading.candidate.value
        else -> null
    }

    /** Every value the pipeline would put in front of the user, for the never-offered assertions. */
    private fun offered(outcome: EvidenceResolver.Outcome): List<BigDecimal> = when (outcome) {
        is EvidenceResolver.Outcome.Resolved -> when (val r = outcome.reading) {
            is LabelReading.Confident -> listOf(r.candidate.value)
            is LabelReading.Ambiguous -> r.candidates.map { it.value }
            LabelReading.NotFound -> emptyList()
        }
        is EvidenceResolver.Outcome.NeedsVerification -> listOf(outcome.reading.candidate.value)
        // A conflict deliberately offers NOTHING; that is the whole point of the state.
        is EvidenceResolver.Outcome.Conflicted -> emptyList()
        // An ambiguity with nothing to corroborate it. Like a conflict, it offers nothing to
        // advance on — the automatic gate requires `Resolved` *and* `Confident`, and this is
        // neither. Enumerated rather than folded into an `else` so a future outcome type has to be
        // considered here rather than silently defaulting to "offers nothing".
        is EvidenceResolver.Outcome.Unresolved -> emptyList()
        EvidenceResolver.Outcome.Nothing -> emptyList()
    }

    private fun assertNeverOffered(name: String, outcome: EvidenceResolver.Outcome, vararg forbidden: String) {
        val values = offered(outcome)
        forbidden.forEach { bad ->
            assertTrue(
                "$name: $bad must never be offered as total carbohydrate (offered: $values)",
                values.none { it.compareTo(BigDecimal(bad)) == 0 },
            )
        }
    }

    // ---- canaries must survive the architecture change ------------------------------------------

    @Test
    fun sondeyStillResolvesThroughTheEvidencePipeline() {
        val result = resolve(SONDEY)
        assertEquals(BigDecimal("61.9"), resolvedValue(result.outcome)?.stripTrailingZeros())
        assertNeverOffered(SONDEY, result.outcome, "47.6")
    }

    @Test
    fun kinderStillResolvesThroughTheEvidencePipeline() {
        val result = resolve(KINDER)
        assertEquals(BigDecimal("53.5"), resolvedValue(result.outcome)?.stripTrailingZeros())
        assertNeverOffered(KINDER, result.outcome, "3", "7", "53.3", "9")
    }

    @Test
    fun yoghurtStillResolvesThroughTheEvidencePipeline() {
        val result = resolve(YOGHURT)
        assertEquals(BigDecimal("5"), resolvedValue(result.outcome)?.stripTrailingZeros())
        assertNeverOffered(YOGHURT, result.outcome, "7.5", "150")
    }

    @Test
    fun stokbroodStillResolvesThroughTheEvidencePipeline() {
        val result = resolve(STOKBROOD)
        assertEquals(BigDecimal("46"), resolvedValue(result.outcome)?.stripTrailingZeros())
        assertNeverOffered(STOKBROOD, result.outcome, "1.0", "4.7")
    }

    // ---- the automatic fast path, measured on the real corpus (1.0.3 P3) ------------------------

    /**
     * Which real photographs would skip the crop-confirmation step, and — far more importantly —
     * that none of them skips it wrongly.
     *
     * This is the only place the *advance* half of the fast path can be measured without a phone in
     * hand: it runs the production resolution over the committed photographs at the shipped starting
     * rectangle, then asks [AutomaticScanAdvance] the same question the scanner asks.
     *
     * It prints its table and asserts the **safety** property rather than a pass rate, deliberately.
     * How many of nine labels advance is a property of nine particular photographs and would make
     * this a brittle scoreboard; that nothing unsafe advances is the claim the feature rests on.
     */
    @Test
    fun theFastPathAdvancesOnlyOnConfidentlyResolvedFixtures() {
        val fixtures = listOf(
            SONDEY, KINDER, YOGHURT, STOKBROOD, WITTE_KAAS, GRATED_CHEESE, JAR, LID,
        )

        var advanced = 0
        fixtures.forEach { name ->
            val result = resolve(name)
            val mayAdvance = AutomaticScanAdvance.mayAdvance(result.outcome)
            if (mayAdvance) advanced++

            println(
                "FAST-PATH $name -> ${result.outcome::class.simpleName} " +
                    "value=${resolvedValue(result.outcome)?.stripTrailingZeros()?.toPlainString()} " +
                    "advance=$mayAdvance",
            )

            // The safety claim, asserted per fixture: advancing implies a confidently resolved
            // reading carrying a basis. Anything else must have gone to the crop screen.
            if (mayAdvance) {
                val resolved = result.outcome as EvidenceResolver.Outcome.Resolved
                val confident = resolved.reading as? LabelReading.Confident
                assertTrue("$name advanced without a Confident reading", confident != null)
                assertTrue(
                    "$name advanced without stating what its value is per",
                    confident!!.candidate.basis != null,
                )
            }
        }

        println("FAST-PATH summary: $advanced of ${fixtures.size} fixtures skip the crop step")
    }

    /**
     * The fixture that must never advance, named explicitly.
     *
     * Grated cheese is the corpus's live hazard: three recognitions of the same photograph produce
     * three different numbers, so the resolver refuses. If a future change ever lets this one through
     * the fast path, a known-wrong carbohydrate value would reach the user with one tap fewer than
     * before — which is the exact failure this whole architecture exists to prevent.
     */
    @Test
    fun theKnownConflictFixtureNeverSkipsTheCropStep() {
        val result = resolve(GRATED_CHEESE)

        assertTrue(
            "grated cheese must not skip the crop step (outcome=${result.outcome::class.simpleName})",
            !AutomaticScanAdvance.mayAdvance(result.outcome),
        )
    }

    // ---- the safety property that matters most --------------------------------------------------

    /**
     * No fixture may produce a confidently-resolved value that is wrong.
     *
     * Grated cheese is the live hazard: three recognitions of the same photograph produce three
     * different numbers. Whatever the pipeline does there, it must not *resolve* to a wrong one — a
     * conflict, a verification prompt or nothing are all acceptable; a silent wrong answer is not.
     */
    @Test
    fun noFixtureResolvesToAKnownWrongValue() {
        val forbidden = mapOf(
            KINDER to listOf("9", "3", "7", "53.3"),
            SONDEY to listOf("47.6"),
            YOGHURT to listOf("7.5"),
            STOKBROOD to listOf("1.0"),
            JAR to listOf("20", "500"),
            LID to listOf("2.5", "19", "125"),
        )

        val failures = mutableListOf<String>()
        forbidden.forEach { (fixture, bad) ->
            val outcome = resolve(fixture).outcome
            // Only a RESOLVED value is the app answering. A verification prompt is explicitly
            // labelled unconfirmed and a conflict offers nothing, so neither can mislead silently.
            val resolved = (outcome as? EvidenceResolver.Outcome.Resolved)?.let { r ->
                (r.reading as? LabelReading.Confident)?.candidate?.value
            }
            if (resolved != null && bad.any { resolved.compareTo(BigDecimal(it)) == 0 }) {
                failures += "$fixture resolved to $resolved, which is a forbidden value"
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The disagreement case, asserted as a refusal rather than as a value.
     *
     * If a future change makes this fixture resolve confidently, that is not automatically progress —
     * it means one recognition started winning, and the reason must be understood before it ships.
     */
    @Test
    fun gratedCheeseNeverSilentlyResolvesTheDisputedValue() {
        val outcome = resolve(GRATED_CHEESE).outcome

        val resolved = (outcome as? EvidenceResolver.Outcome.Resolved)?.let {
            (it.reading as? LabelReading.Confident)?.candidate?.value
        }
        // 2.09 and 2.04 are both recognition artefacts; the package prints 2,0.
        assertTrue(
            "grated cheese must not resolve to a disputed artefact (got $resolved from $outcome)",
            resolved == null ||
                resolved.compareTo(BigDecimal("2.09")) != 0 && resolved.compareTo(BigDecimal("2.04")) != 0,
        )
    }

    // ---- the recovery the pass exists for -------------------------------------------------------

    /**
     * The measured recovery: the full frame cannot read witte kaas, a native-resolution crop can.
     *
     * The correct value is `2.3`. It must reach the user *somehow* — resolved or proposed — rather
     * than the scanner reporting nothing while the number is legible on screen. It must NOT be
     * presented as corroborated when only one pass found it.
     */
    @Test
    fun witteKaasIsRecoveredByTheSelectedRegionPass() {
        // A tighter selection than the default overlay, which is what the crop UI now asks for.
        val result = resolve(WITTE_KAAS, region = NormalizedRegion(0.10, 0.10, 0.90, 0.90))

        val value = resolvedValue(result.outcome)
        assertTrue(
            "witte kaas should surface 2.3 through some evidence path, got ${result.outcome}",
            value != null && value.compareTo(BigDecimal("2.3")) == 0,
        )
    }

    /** Live consensus must corroborate a still reading rather than being discarded (§9). */
    @Test
    fun liveConsensusCorroboratesTheStillReading() {
        val liveCandidate = CarbCandidate(
            sourceLine = "live",
            label = "Koolhydraten",
            value = BigDecimal("61.9"),
            basis = app.justthecarbs.domain.NutritionBasis.PER_100_G,
            score = 120,
            geometry = OcrBox(0, 0, 10, 10),
            evidence = emptyList(),
        )
        val live = RecognitionEvidence(
            source = EvidenceSource.LIVE_STABLE_FRAME,
            report = NutritionParseReport(LabelReading.Confident(liveCandidate), emptyList()),
            document = null,
        )

        val result = resolve(SONDEY, live = live)

        val resolved = result.outcome as? EvidenceResolver.Outcome.Resolved
            ?: throw AssertionError("expected Resolved, got ${result.outcome}")
        assertTrue(
            "the live frame should appear among the agreeing sources",
            resolved.agreeingSources.contains(EvidenceSource.LIVE_STABLE_FRAME),
        )
    }

    private companion object {
        val OVERLAY = NormalizedRegion(left = 0.08, top = 0.20, right = 0.92, bottom = 0.80)

        const val SONDEY = "sondey_multilingual_100g.jpg"
        const val KINDER = "kinder_multicolumn_piece.jpg"
        const val YOGHURT = "real_yoghurt_serving_column_07.jpg"
        const val STOKBROOD = "real_stokbrood_prose_dense_06.jpg"
        const val WITTE_KAAS = "real_witte_kaas_single_column_05.jpg"
        const val GRATED_CHEESE = "real_grated_cheese_multicolumn_02.jpg"
        const val JAR = "real_jar_prose_multilingual_03.jpg"
        const val LID = "real_lid_prose_curved_04.jpg"
    }
}
