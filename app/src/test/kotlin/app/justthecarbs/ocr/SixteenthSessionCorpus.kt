package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * The 16 captures from `docs/Scan Evidence 04-09 2nd test`, with truth read from each photograph.
 *
 * ## Why this corpus exists separately
 *
 * This is the **first physical session run against the physical-observation build** (Samsung
 * SM-S928B, 12:45-12:49), taken after the P0 in [FifteenthSessionCorpus] was closed. It is the
 * device's own verdict on that fix, and it confirms it: three bundles carry the new rejection text
 * *"all 2 recognition runs read one physical observation"*, and **no capture auto-advanced**.
 *
 * It also exposes the next defect, which the earlier corpora could not: `20260904-124935-320`
 * photographs a Lidl drink printing `6,2 g / 100 ml`, and the device offered **`6.29`** for one-tap
 * confirmation. Unlike the Fanta's `0.59`, that token carries a decimal separator, so
 * [ScaleAmbiguity] reports `Established` and nothing downstream questions it.
 *
 * The controlled comparison is inside this session and is what makes the cause unambiguous:
 * `20260904-124924-679` is the **same physical package seconds earlier**, where the `g` glyphs
 * survived — and there the label is correctly judged to print units on its values and `6.2` is read.
 * Same package, same typesetting, opposite verdicts, decided only by how thoroughly ML Kit destroyed
 * the unit glyphs. See [UnitConventionSemanticsTest].
 *
 * ## Truth
 *
 * [printedCarbs] and [printedBasis] are read from `capture.jpg` in each bundle by cropping the
 * carbohydrate row from the recorded element geometry and reading the print, never from parser
 * output. Where the row is not legible the truth is left null and the capture scores
 * [SixteenthSessionReplay.Classification.GROUND_TRUTH_UNKNOWN] rather than being guessed.
 *
 * [deviceAction] is transcribed from each bundle's `selection.txt` `final UI action` line. It is the
 * baseline: a move off it must be justified in the replay assertions, never absorbed silently.
 */
internal object SixteenthSessionCorpus {

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
        /** What the device offered, when it offered anything. */
        val deviceOffered: BigDecimal?,
        val failureLayer: FifteenthSessionCorpus.FailureLayer,
        val note: String,
    )

    private fun bd(value: String) = BigDecimal(value)

    val captures = listOf(
        Capture(
            "20260904-124526-937", "Lidl green drink",
            SixteenthSessionFixtures::c20260904_124526_937,
            SixteenthSessionStrategyBFixtures::c20260904_124526_937,
            bd("0.5"), NutritionBasis.PER_100_ML, false,
            "CROP_FALLBACK", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "print is 0.5 g / 100 ml beside 1.3 g / 250 ml; the value cell was not recognised",
        ),
        Capture(
            "20260904-124538-378", "Lidl green drink",
            SixteenthSessionFixtures::c20260904_124538_378,
            SixteenthSessionStrategyBFixtures::c20260904_124538_378,
            bd("0.5"), NutritionBasis.PER_100_ML, true,
            "CONFIRM_ON_CAPTURE", bd("0.5"), FifteenthSessionCorpus.FailureLayer.NONE,
            "print is 0.5 g / 100 ml; read correctly, one confirmation tap",
        ),
        Capture(
            "20260904-124552-632", "Lidl green drink",
            SixteenthSessionFixtures::c20260904_124552_632,
            SixteenthSessionStrategyBFixtures::c20260904_124552_632,
            bd("0.5"), NutritionBasis.PER_100_ML, false,
            "FOCUSED_AMOUNT_ENTRY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "print is 0.5 g / 100 ml; the row was found, the number was not readable",
        ),
        Capture(
            "20260904-124609-416", "Lidl green drink",
            SixteenthSessionFixtures::c20260904_124609_416,
            SixteenthSessionStrategyBFixtures::c20260904_124609_416,
            bd("0.5"), NutritionBasis.PER_100_ML, true,
            "CONFIRM_ON_CAPTURE", bd("0.5"), FifteenthSessionCorpus.FailureLayer.NONE,
            "print is 0.5 g / 100 ml; read correctly, one confirmation tap",
        ),
        Capture(
            "20260904-124620-112", "red Lidl carton",
            SixteenthSessionFixtures::c20260904_124620_112,
            SixteenthSessionStrategyBFixtures::c20260904_124620_112,
            bd("7.2"), NutritionBasis.PER_100_G, false,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "print is 7,2 g; recognised as '12g' and correctly withheld on unsupported scale",
        ),
        Capture(
            "20260904-124643-457", "red Lidl carton",
            SixteenthSessionFixtures::c20260904_124643_457,
            SixteenthSessionStrategyBFixtures::c20260904_124643_457,
            bd("7.2"), NutritionBasis.PER_100_G, false,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "print is 7,2 g; recognised as '12g' and correctly withheld on unsupported scale",
        ),
        Capture(
            "20260904-124711-287", "Lidl dairy tub",
            SixteenthSessionFixtures::c20260904_124711_287,
            SixteenthSessionStrategyBFixtures::c20260904_124711_287,
            bd("3.2"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("3.2"), FifteenthSessionCorpus.FailureLayer.NONE,
            "print is 3,2 g / 100 g beside 4,0 g / 125 g; read correctly",
        ),
        Capture(
            "20260904-124724-066", "yellow tub",
            SixteenthSessionFixtures::c20260904_124724_066,
            SixteenthSessionStrategyBFixtures::c20260904_124724_066,
            bd("5.4"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("5.4"), FifteenthSessionCorpus.FailureLayer.NONE,
            "print is 5,4 g / 100 g; read correctly",
        ),
        Capture(
            "20260904-124747-884", "multilingual carton",
            SixteenthSessionFixtures::c20260904_124747_884,
            SixteenthSessionStrategyBFixtures::c20260904_124747_884,
            null, null, false,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.SCALE_UNRESOLVED,
            "carbohydrate row unreadable in the photograph; '13g' withheld on unsupported scale",
        ),
        Capture(
            "20260904-124806-597", "blue crisps tube",
            SixteenthSessionFixtures::c20260904_124806_597,
            SixteenthSessionStrategyBFixtures::c20260904_124806_597,
            bd("72"), NutritionBasis.PER_100_G, false,
            "FOCUSED_AMOUNT_ENTRY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "print is 72 g / 100 g; the row was found, the number was not readable",
        ),
        Capture(
            "20260904-124822-392", "blue crisps tube",
            SixteenthSessionFixtures::c20260904_124822_392,
            SixteenthSessionStrategyBFixtures::c20260904_124822_392,
            bd("72"), NutritionBasis.PER_100_G, true,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.SCALE_UNRESOLVED,
            "print is 72 g / 100 g; read correctly but bare integer, so scale is UNSUPPORTED",
        ),
        Capture(
            "20260904-124835-611", "blue crisps tube",
            SixteenthSessionFixtures::c20260904_124835_611,
            SixteenthSessionStrategyBFixtures::c20260904_124835_611,
            bd("72"), NutritionBasis.PER_100_G, true,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.SCALE_UNRESOLVED,
            "print is 72 g / 100 g; read correctly but bare integer, so scale is UNSUPPORTED",
        ),
        Capture(
            "20260904-124856-544", "unreadable frame",
            SixteenthSessionFixtures::c20260904_124856_544,
            SixteenthSessionStrategyBFixtures::c20260904_124856_544,
            null, null, false,
            "CROP_FALLBACK", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "no total-carbohydrate row in any recognition",
        ),
        Capture(
            "20260904-124910-555", "Lidl mint drink",
            SixteenthSessionFixtures::c20260904_124910_555,
            SixteenthSessionStrategyBFixtures::c20260904_124910_555,
            bd("6.2"), NutritionBasis.PER_100_ML, false,
            "FOCUSED_AMOUNT_ENTRY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "print is 6,2 g / 100 ml; the row was found, the number was not readable",
        ),
        Capture(
            "20260904-124924-679", "Lidl mint drink",
            SixteenthSessionFixtures::c20260904_124924_679,
            SixteenthSessionStrategyBFixtures::c20260904_124924_679,
            bd("6.2"), NutritionBasis.PER_100_ML, true,
            "CONFIRM_ON_CAPTURE", bd("6.2"), FifteenthSessionCorpus.FailureLayer.NONE,
            "print is 6,2 g / 100 ml; unit glyphs survived, read correctly. The control for 124935",
        ),
        Capture(
            "20260904-124935-320", "Lidl mint drink",
            SixteenthSessionFixtures::c20260904_124935_320,
            SixteenthSessionStrategyBFixtures::c20260904_124935_320,
            bd("6.2"), NutritionBasis.PER_100_ML, false,
            "CONFIRM_ON_CAPTURE", bd("6.29"), FifteenthSessionCorpus.FailureLayer.UNIT_GLYPH,
            "THE DEFECT. print is 6,2 g; every unit glyph became a 9, so the label read as " +
                "header-only and the corrupted '6,29' was offered for one-tap confirmation",
        ),
    )
}
