package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * The 21 captures from `docs/Scan Evidence new structure`, with truth read from each photograph.
 *
 * ## Why this corpus exists separately from [FourteenthSessionCorpus]
 *
 * This is a **later physical session on the same device** (Samsung SM-S928B, 11:36–11:43) than the
 * 17 captures the semantic-panel pass replayed (09:46–09:51). It is not a re-export of those: no
 * bundle id overlaps.
 *
 * It matters because it contains the app's first **confident-wrong automatic advance** measured on
 * hardware. `20260904-113653-044` photographs a Fanta bottle printing `0,5 g / 100 ml`, and the
 * device advanced to Quick Calculation showing `0.59 / 100 ml` — ten times the printed figure, with
 * no confirmation step — because two OCR runs over *the same JPEG* agreed on the same corrupted
 * glyph. See [PhysicalObservationProvenanceTest].
 *
 * ## Truth
 *
 * [printedCarbs] and [printedBasis] are read from `capture.jpg` in each bundle, not from any parser
 * output. Where the photograph does not show the carbohydrate row legibly the truth is left null and
 * the capture is scored [FifteenthSessionReplay.Classification.GROUND_TRUTH_UNKNOWN] rather than
 * guessed.
 *
 * [deviceAction] is what the device actually did, transcribed from each bundle's `selection.txt`
 * `final UI action` line. It is the **baseline**: this pass may move a capture off it, and every such
 * move has to be justified in the replay assertions rather than absorbed silently.
 */
internal object FifteenthSessionCorpus {

    /** Which layer failed, for a capture that did not reach a correct automatic reading. */
    enum class FailureLayer {
        NONE,

        /** The glyphs the value needs are absent or corrupted in every recognition. */
        OPTICAL_OCR,

        /** A unit glyph was recognised as a digit — `0,5 g` -> `0.59`, `14,5 g` -> `14.59`. */
        UNIT_GLYPH,

        /** Two recognitions of the frame disagreed and neither could be preferred. */
        OCR_CONFLICT,

        /** The value was read but its decimal scale could not be established. */
        SCALE_UNRESOLVED,

        /** A correct reading existed and downstream rules withheld it. */
        DOWNSTREAM_REJECTION,
    }

    data class Capture(
        val bundle: String,
        val product: String,
        val passA: () -> OcrDocument,
        val strategyB: () -> OcrDocument,
        val printedCarbs: BigDecimal?,
        val printedBasis: NutritionBasis?,
        /** Whether any recognition in the bundle contains the correct printed value. */
        val correctValueInEvidence: Boolean,
        /** What the device did, from `selection.txt`. The baseline this pass must not regress. */
        val deviceAction: String,
        /** The device's `automatic-verification` route, verbatim. */
        val deviceVerification: String,
        val failureLayer: FailureLayer,
        val note: String,
    ) {
        /**
         * Whether the device's automatic advance rested on same-frame agreement.
         *
         * These are the captures this pass deliberately moves to confirmation: the value stays on
         * screen, the user gains one tap, and the wrong-auto route closes.
         */
        val autoRestedOnSameFrameAgreement: Boolean
            get() = deviceAction == "AUTO_ADVANCE" && deviceVerification == "DISTINCT_OCR_AGREEMENT"
    }

    private fun bd(value: String) = BigDecimal(value)

    val captures = listOf(
        Capture(
            "20260904-113637-009", "Lidl green carton",
            FifteenthSessionFixtures::capture113637, FifteenthSessionStrategyBFixtures::capture113637,
            bd("6.2"), NutritionBasis.PER_100_ML, true,
            "CONFIRM_ON_CAPTURE", "NONE", FailureLayer.NONE,
            "print is 6,2 g per Ø/100 ml; Pass A read it cleanly, no second run agreed",
        ),
        Capture(
            "20260904-113653-044", "Fanta bottle",
            FifteenthSessionFixtures::capture113653, FifteenthSessionStrategyBFixtures::capture113653,
            bd("0.5"), NutritionBasis.PER_100_ML, false,
            "AUTO_ADVANCE", "DISTINCT_OCR_AGREEMENT", FailureLayer.UNIT_GLYPH,
            "THE P0. print is 0,5 g; both runs of the same JPEG read 0.59 and it auto-advanced",
        ),
        Capture(
            "20260904-113705-425", "multilingual carton",
            FifteenthSessionFixtures::capture113705, FifteenthSessionStrategyBFixtures::capture113705,
            null, null, false,
            "CROP_FALLBACK", "NONE", FailureLayer.OPTICAL_OCR,
            "carbohydrate row recognised with no value cell; basis missing",
        ),
        Capture(
            "20260904-113729-131", "red Lidl carton",
            FifteenthSessionFixtures::capture113729, FifteenthSessionStrategyBFixtures::capture113729,
            bd("7.2"), NutritionBasis.PER_100_G, false,
            "FOCUSED_AMOUNT_ENTRY", "NONE", FailureLayer.OCR_CONFLICT,
            "print is 7,2 g; Pass A read 72.0 and Strategy B read 12.0 — disputed, correctly withheld",
        ),
        Capture(
            "20260904-113756-978", "unreadable frame",
            FifteenthSessionFixtures::capture113756, FifteenthSessionStrategyBFixtures::capture113756,
            null, null, false,
            "CROP_FALLBACK", "NONE", FailureLayer.OPTICAL_OCR,
            "no total-carbohydrate row in any recognition",
        ),
        Capture(
            "20260904-113818-873", "chocolate spread jar",
            FifteenthSessionFixtures::capture113818, FifteenthSessionStrategyBFixtures::capture113818,
            bd("57"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", "DISTINCT_OCR_AGREEMENT", FailureLayer.NONE,
            "print is 57 g per 100 gram; bare integer, so scale is UNSUPPORTED and it confirms",
        ),
        Capture(
            "20260904-113832-090", "Lidl dairy tub",
            FifteenthSessionFixtures::capture113832, FifteenthSessionStrategyBFixtures::capture113832,
            bd("3.2"), NutritionBasis.PER_100_G, true,
            "AUTO_ADVANCE", "DISTINCT_OCR_AGREEMENT", FailureLayer.NONE,
            "print is 3,2 g / 100 g; correct, but auto rested on same-frame agreement",
        ),
        Capture(
            "20260904-113840-939", "AH prose carton",
            FifteenthSessionFixtures::capture113840, FifteenthSessionStrategyBFixtures::capture113840,
            bd("3.3"), NutritionBasis.PER_100_G, true,
            "AUTO_ADVANCE", "DISTINCT_OCR_AGREEMENT", FailureLayer.NONE,
            "prose label; koolhydraten 3,3 g and sugars also 3,3 g, so provenance is what separates them",
        ),
        Capture(
            "20260904-113900-255", "unreadable frame",
            FifteenthSessionFixtures::capture113900, FifteenthSessionStrategyBFixtures::capture113900,
            null, null, false,
            "CROP_FALLBACK", "NONE", FailureLayer.OPTICAL_OCR,
            "no total-carbohydrate row in any recognition",
        ),
        Capture(
            "20260904-113914-060", "pickle jar",
            FifteenthSessionFixtures::capture113914, FifteenthSessionStrategyBFixtures::capture113914,
            bd("5.4"), NutritionBasis.PER_100_G, true,
            "FOCUSED_AMOUNT_ENTRY", "CROSS_COLUMN", FailureLayer.DOWNSTREAM_REJECTION,
            "print is 5,4 g / 100 g; Strategy B read it and cross-column verified it, still withheld",
        ),
        Capture(
            "20260904-113936-038", "dense multilingual pack",
            FifteenthSessionFixtures::capture113936, FifteenthSessionStrategyBFixtures::capture113936,
            null, null, false,
            "CROP_FALLBACK", "NONE", FailureLayer.OPTICAL_OCR,
            "317 elements, no total-carbohydrate row recovered",
        ),
        Capture(
            "20260904-113950-065", "Hellmann's mayonnaise",
            FifteenthSessionFixtures::capture113950, FifteenthSessionStrategyBFixtures::capture113950,
            bd("1.3"), NutritionBasis.PER_100_ML, false,
            "CONFIRM_ON_CAPTURE", "DISTINCT_OCR_AGREEMENT", FailureLayer.UNIT_GLYPH,
            "print is 1,3 g / 100 ml; both runs read 13.0 — separator lost, scale UNSUPPORTED so it confirms",
        ),
        Capture(
            "20260904-114031-873", "multilingual pack",
            FifteenthSessionFixtures::capture114031, FifteenthSessionStrategyBFixtures::capture114031,
            null, null, false,
            "CROP_FALLBACK", "NONE", FailureLayer.OPTICAL_OCR,
            "carbohydrate term fused into neighbouring language clauses; no value",
        ),
        Capture(
            "20260904-114106-747", "cracker box",
            FifteenthSessionFixtures::capture114106, FifteenthSessionStrategyBFixtures::capture114106,
            bd("72"), NutritionBasis.PER_100_G, true,
            "RECOVERY", "NONE", FailureLayer.SCALE_UNRESOLVED,
            "print is 72 g / 100 g; only Strategy B read it, bare integer so scale is unsupported",
        ),
        Capture(
            "20260904-114134-776", "unreadable frame",
            FifteenthSessionFixtures::capture114134, FifteenthSessionStrategyBFixtures::capture114134,
            null, null, false,
            "CROP_FALLBACK", "NONE", FailureLayer.OPTICAL_OCR,
            "no total-carbohydrate row in any recognition",
        ),
        Capture(
            "20260904-114141-983", "Turkish tahini jar",
            FifteenthSessionFixtures::capture114141, FifteenthSessionStrategyBFixtures::capture114141,
            bd("14.5"), NutritionBasis.PER_100_G, false,
            "FOCUSED_AMOUNT_ENTRY", "NONE", FailureLayer.UNIT_GLYPH,
            "print is 14,5 g; recognised as 'Karbonhidrat 145G' — separator lost, correctly refused",
        ),
        Capture(
            "20260904-114150-536", "Turkish tahini jar",
            FifteenthSessionFixtures::capture114150, FifteenthSessionStrategyBFixtures::capture114150,
            bd("14.5"), NutritionBasis.PER_100_G, false,
            "CONFIRM_ON_CAPTURE", "NONE", FailureLayer.UNIT_GLYPH,
            "print is 14,5 g; Strategy B read 14.59 — the g became a 9, and 14.59 was offered",
        ),
        Capture(
            "20260904-114208-410", "Jumbo energy gel",
            FifteenthSessionFixtures::capture114208, FifteenthSessionStrategyBFixtures::capture114208,
            bd("67"), NutritionBasis.PER_100_G, true,
            "AUTO_ADVANCE", "DISTINCT_OCR_AGREEMENT", FailureLayer.NONE,
            "print is 67,0 g per 100 g; correct, and correctly not the 30,2 g serving column",
        ),
        Capture(
            "20260904-114241-317", "AH bread roll pack",
            FifteenthSessionFixtures::capture114241, FifteenthSessionStrategyBFixtures::capture114241,
            bd("47"), NutritionBasis.PER_100_G, true,
            "AUTO_ADVANCE", "CROSS_COLUMN", FailureLayer.NONE,
            "print is 47,0 g / 100 g; structurally verified by 4 supporting rows — this auto survives",
        ),
        Capture(
            "20260904-114311-968", "AH prose snack pack",
            FifteenthSessionFixtures::capture114311, FifteenthSessionStrategyBFixtures::capture114311,
            bd("35"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", "DISTINCT_OCR_AGREEMENT", FailureLayer.NONE,
            "prose label; koolhydraten 35 g, bare integer so scale is UNSUPPORTED and it confirms",
        ),
        Capture(
            "20260904-114331-943", "Lidl Baltic biscuit",
            FifteenthSessionFixtures::capture114331, FifteenthSessionStrategyBFixtures::capture114331,
            bd("59.2"), NutritionBasis.PER_100_G, true,
            "AUTO_ADVANCE", "DISTINCT_OCR_AGREEMENT", FailureLayer.NONE,
            "print is 59,2 g / 100 g; correct, but auto rested on same-frame agreement",
        ),
    )
}
