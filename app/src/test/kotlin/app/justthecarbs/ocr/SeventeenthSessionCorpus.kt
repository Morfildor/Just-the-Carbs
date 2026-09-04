package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * The 29 captures from `docs/Scan Evidence 3rd testr`, with truth read from each photograph.
 *
 * ## Why this corpus exists separately
 *
 * It is the largest physical session so far — ~22 distinct products, Samsung SM-S928B, 13:41-13:49
 * — and the first taken after the owner described the app as *"much better, still not amazing"*.
 * Both halves of that are visible in the bundles and both matter:
 *
 * - **Zero wrong values reached the user across 29 captures.** The safety architecture holds, and
 *   six bundles carry the physical-observation rejection text, so that P0 is confirmed on hardware.
 * - **Zero captures advanced automatically.** Every one cost at least one interaction: 14
 *   `CONFIRM_ON_CAPTURE`, 5 `RECOVERY`, 5 `FOCUSED_AMOUNT_ENTRY`, 5 `CROP_FALLBACK`.
 *
 * The cause is measurable rather than a matter of taste. `automatic-verification` is `NONE` on all
 * 29, and its cross-column half reports **`only 0 coherent row pairs; 3 needed`** on 19 of the 20
 * captures that reached it. Zero, not two — a near miss would be a threshold question, and zero
 * across the board is a structural one: [CrossColumnRatioCheck] never obtained two usable value
 * columns, so it could not form a single ratio. With [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT]
 * correctly closed by the physical-observation rule, both routes to an automatic advance were shut
 * and 0/29 is arithmetically forced.
 *
 * ## Truth
 *
 * [printedCarbs] and [printedBasis] are read from `capture.jpg` in each bundle — the photograph, not
 * the parser's output. Where the print is not legible in the capture the truth is left null and the
 * capture scores [SeventeenthSessionReplay.Classification.GROUND_TRUTH_UNKNOWN] rather than being
 * guessed from what the app happened to say.
 *
 * [deviceAction] is transcribed from each bundle's `selection.txt` `final UI action` line, and
 * [SeventeenthSessionReplayTest] asserts the replay reproduces every one of them before any other
 * claim is made. Without that the harness would be measuring the fixture rather than the app.
 */
internal object SeventeenthSessionCorpus {

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
            "20260904-134100-466", "Fanta green drink",
            SeventeenthSessionFixtures::c20260904_134100_466,
            SeventeenthSessionStrategyBFixtures::c20260904_134100_466,
            bd("0.5"), NutritionBasis.PER_100_ML, true,
            "CROP_FALLBACK", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "prints 0,5 g/100 ml beside 1,3 g/250 ml; the row read as 'Koolhydraten: 0.5g 1.3' " +
                "but the 'PER:' header was lost, so no basis was established",
        ),
        Capture(
            "20260904-134119-132", "Fanta green drink",
            SeventeenthSessionFixtures::c20260904_134119_132,
            SeventeenthSessionStrategyBFixtures::c20260904_134119_132,
            bd("0.5"), NutritionBasis.PER_100_ML, true,
            "CONFIRM_ON_CAPTURE", bd("0.5"), FifteenthSessionCorpus.FailureLayer.NONE,
            "read correctly; one confirmation tap. Only the 100 ml column resolved, so the table " +
                "could not corroborate it",
        ),
        Capture(
            "20260904-134135-198", "Fanta green drink",
            SeventeenthSessionFixtures::c20260904_134135_198,
            SeventeenthSessionStrategyBFixtures::c20260904_134135_198,
            bd("0.5"), NutritionBasis.PER_100_ML, true,
            "CONFIRM_ON_CAPTURE", bd("0.5"), FifteenthSessionCorpus.FailureLayer.NONE,
            "read correctly; the 250 ml column resolved as UNKNOWN, which is right for reading a " +
                "value and is why no ratio pair could form",
        ),
        Capture(
            "20260904-134147-327", "Lidl lemon drink",
            SeventeenthSessionFixtures::c20260904_134147_327,
            SeventeenthSessionStrategyBFixtures::c20260904_134147_327,
            bd("6.2"), NutritionBasis.PER_100_ML, true,
            "CONFIRM_ON_CAPTURE", bd("6.2"), FifteenthSessionCorpus.FailureLayer.NONE,
            "the same package whose glyphs were destroyed in the sixteenth session's 6.29 defect; " +
                "here the units survived and it reads cleanly. Single-column label",
        ),
        Capture(
            "20260904-134159-043", "multilingual carton",
            SeventeenthSessionFixtures::c20260904_134159_043,
            SeventeenthSessionStrategyBFixtures::c20260904_134159_043,
            null, null, false,
            "CROP_FALLBACK", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "reads '72g' on a total row with no per-100 column resolved; the print is not legible " +
                "in the capture, so the truth is left unknown rather than taken from the app",
        ),
        Capture(
            "20260904-134217-566", "multilingual carton",
            SeventeenthSessionFixtures::c20260904_134217_566,
            SeventeenthSessionStrategyBFixtures::c20260904_134217_566,
            null, null, false,
            "FOCUSED_AMOUNT_ENTRY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "row and basis established, value unreadable — focused entry is the correct outcome",
        ),
        Capture(
            "20260904-134233-470", "Lidl yoghurt 125 g",
            SeventeenthSessionFixtures::c20260904_134233_470,
            SeventeenthSessionStrategyBFixtures::c20260904_134233_470,
            bd("3.2"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("3.2"), FifteenthSessionCorpus.FailureLayer.NONE,
            "prints Ø/100 g and Ø/125 g. Read correctly. Its own table states the serving ratio on " +
                "three rows (energie 1.2505, vetten 1.2500, carb 1.2500) and the second column " +
                "resolves UNKNOWN, so that corroboration was unreachable",
        ),
        Capture(
            "20260904-134246-138", "gherkin jar",
            SeventeenthSessionFixtures::c20260904_134246_138,
            SeventeenthSessionStrategyBFixtures::c20260904_134246_138,
            bd("5.4"), NutritionBasis.PER_100_G, false,
            "FOCUSED_AMOUNT_ENTRY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "prints 5,4 g/100 g beside part (30 g) 1,6 g; the value was not recognised on this frame",
        ),
        Capture(
            "20260904-134257-624", "gherkin jar",
            SeventeenthSessionFixtures::c20260904_134257_624,
            SeventeenthSessionStrategyBFixtures::c20260904_134257_624,
            bd("5.4"), NutritionBasis.PER_100_G, true,
            "CROP_FALLBACK", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "the row read as 'koolhydraten, 5,4 g 1,69' — correct value, correct row. The device " +
                "sent this to the crop screen; the replay already offers 5.4, so the semantic " +
                "architecture fixed it after this session was recorded. The device baseline is " +
                "kept as recorded rather than rewritten to match",
        ),
        Capture(
            "20260904-134311-325", "Hellmann's mayonnaise",
            SeventeenthSessionFixtures::c20260904_134311_325,
            SeventeenthSessionStrategyBFixtures::c20260904_134311_325,
            bd("1.3"), NutritionBasis.PER_100_ML, false,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.SCALE_UNRESOLVED,
            "prints 1,3 g/100 ml; recognised as bare '13g' with the separator lost, correctly " +
                "withheld as an unsupported scale. Manufacturing 1.3 from 13 is prohibited",
        ),
        Capture(
            "20260904-134322-389", "Hellmann's mayonnaise",
            SeventeenthSessionFixtures::c20260904_134322_389,
            SeventeenthSessionStrategyBFixtures::c20260904_134322_389,
            bd("1.3"), NutritionBasis.PER_100_ML, false,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.SCALE_UNRESOLVED,
            "same package and same '13g' misread, correctly withheld",
        ),
        Capture(
            "20260904-134339-091", "AH bake mix",
            SeventeenthSessionFixtures::c20260904_134339_091,
            SeventeenthSessionStrategyBFixtures::c20260904_134339_091,
            bd("3.3"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("3.3"), FifteenthSessionCorpus.FailureLayer.NONE,
            "prose label read correctly. Three PER_100_G columns at x=75, x=92 and x=1205 — the " +
                "first two are debris from a neighbouring panel, so they are a genuine " +
                "disagreement and must NOT be collapsed",
        ),
        Capture(
            "20260904-134400-747", "Basak rice flour",
            SeventeenthSessionFixtures::c20260904_134400_747,
            SeventeenthSessionStrategyBFixtures::c20260904_134400_747,
            bd("80"), NutritionBasis.PER_100_G, true,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.SCALE_UNRESOLVED,
            "prints 80 g/100 g — a genuinely separatorless integer on a single-column label, so " +
                "nothing can establish its scale. Correctly withheld",
        ),
        Capture(
            "20260904-134420-616", "Basak rice flour",
            SeventeenthSessionFixtures::c20260904_134420_616,
            SeventeenthSessionStrategyBFixtures::c20260904_134420_616,
            bd("80"), NutritionBasis.PER_100_G, false,
            "CROP_FALLBACK", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "the nutrient name wraps across four rows in six languages, each typing " +
                "TOTAL_CARBOHYDRATE, so FocusedAmountEntry's singleOrNull refused a recoverable " +
                "capture",
        ),
        Capture(
            "20260904-134428-088", "Basak rice flour",
            SeventeenthSessionFixtures::c20260904_134428_088,
            SeventeenthSessionStrategyBFixtures::c20260904_134428_088,
            bd("80"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("80"), FifteenthSessionCorpus.FailureLayer.NONE,
            "six PER_100_G columns spanning 91 px of a 1684 px frame — one printed column read in " +
                "six languages. Single-column label, so no ratio is available even so",
        ),
        Capture(
            "20260904-134501-895", "Indomie noodles",
            SeventeenthSessionFixtures::c20260904_134501_895,
            SeventeenthSessionStrategyBFixtures::c20260904_134501_895,
            null, null, false,
            "FOCUSED_AMOUNT_ENTRY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "five spurious PER_SERVING columns recovered from ingredient prose; value unreadable",
        ),
        Capture(
            "20260904-134520-722", "peanut spread",
            SeventeenthSessionFixtures::c20260904_134520_722,
            SeventeenthSessionStrategyBFixtures::c20260904_134520_722,
            bd("9.6"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("9.6"), FifteenthSessionCorpus.FailureLayer.NONE,
            "the only capture reaching support=2. Its serving-column cells were genuinely not " +
                "recognised — a recognition limit, NOT a threshold to lower",
        ),
        Capture(
            "20260904-134539-493", "Indomie noodles + bouillon",
            SeventeenthSessionFixtures::c20260904_134539_493,
            SeventeenthSessionStrategyBFixtures::c20260904_134539_493,
            bd("27"), NutritionBasis.PER_100_G, true,
            "FOCUSED_AMOUNT_ENTRY", null, FifteenthSessionCorpus.FailureLayer.OCR_CONFLICT,
            "two side-by-side per-100 g tables for DIFFERENT products (noodles 27 g, bouillon " +
                "2,7 g). Confident passes disagreed and it correctly refused to choose",
        ),
        Capture(
            "20260904-134552-198", "Indomie noodles + bouillon",
            SeventeenthSessionFixtures::c20260904_134552_198,
            SeventeenthSessionStrategyBFixtures::c20260904_134552_198,
            bd("27"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("27"), FifteenthSessionCorpus.FailureLayer.NONE,
            "three PER_100_G columns spanning 443 px — a REAL disagreement between two products' " +
                "tables. This is the control that any column-collapsing rule must not merge",
        ),
        Capture(
            "20260904-134626-725", "German fish product",
            SeventeenthSessionFixtures::c20260904_134626_725,
            SeventeenthSessionStrategyBFixtures::c20260904_134626_725,
            null, null, false,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "candidate recognised as '00G'; nothing usable, correctly withheld",
        ),
        Capture(
            "20260904-134701-478", "AH cucumber salad",
            SeventeenthSessionFixtures::c20260904_134701_478,
            SeventeenthSessionStrategyBFixtures::c20260904_134701_478,
            bd("5.2"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("5.2"), FifteenthSessionCorpus.FailureLayer.NONE,
            "prose label read correctly by the prose reader",
        ),
        Capture(
            "20260904-134719-477", "coconut water",
            SeventeenthSessionFixtures::c20260904_134719_477,
            SeventeenthSessionStrategyBFixtures::c20260904_134719_477,
            bd("3.2"), NutritionBasis.PER_100_ML, true,
            "CONFIRM_ON_CAPTURE", bd("3.2"), FifteenthSessionCorpus.FailureLayer.NONE,
            "prints 3,2 g/100 ml beside 8,1 g/250 ml — a genuine two-column label with a resolved " +
                "PER_SERVING column",
        ),
        Capture(
            "20260904-134738-628", "organic spread",
            SeventeenthSessionFixtures::c20260904_134738_628,
            SeventeenthSessionStrategyBFixtures::c20260904_134738_628,
            bd("11"), NutritionBasis.PER_100_G, true,
            "RECOVERY", null, FifteenthSessionCorpus.FailureLayer.SCALE_UNRESOLVED,
            "prose row 'ogische vetzuren 9,5 g, koolhydraten 11 g.' — the separator-bearing 9,5 g " +
                "is the FAT value to the LEFT, correctly excluded by the right-of-candidate rule",
        ),
        Capture(
            "20260904-134750-370", "McDonald's fritessaus",
            SeventeenthSessionFixtures::c20260904_134750_370,
            SeventeenthSessionStrategyBFixtures::c20260904_134750_370,
            bd("13.2"), NutritionBasis.PER_100_ML, true,
            "CONFIRM_ON_CAPTURE", bd("13.2"), FifteenthSessionCorpus.FailureLayer.NONE,
            "single-column label, read correctly",
        ),
        Capture(
            "20260904-134822-485", "green olives",
            SeventeenthSessionFixtures::c20260904_134822_485,
            SeventeenthSessionStrategyBFixtures::c20260904_134822_485,
            bd("1.6"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("1.6"), FifteenthSessionCorpus.FailureLayer.NONE,
            "prose label read correctly with no columns resolved at all",
        ),
        Capture(
            "20260904-134850-256", "tahini",
            SeventeenthSessionFixtures::c20260904_134850_256,
            SeventeenthSessionStrategyBFixtures::c20260904_134850_256,
            bd("14.5"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("14.5"), FifteenthSessionCorpus.FailureLayer.NONE,
            "single-column Turkish label, read correctly",
        ),
        Capture(
            "20260904-134917-744", "Lidl Baltic crispbread",
            SeventeenthSessionFixtures::c20260904_134917_744,
            SeventeenthSessionStrategyBFixtures::c20260904_134917_744,
            bd("59.2"), NutritionBasis.PER_100_G, true,
            "CONFIRM_ON_CAPTURE", bd("59.2"), FifteenthSessionCorpus.FailureLayer.NONE,
            "prints Ø/100 g and Ø/9 g. Read correctly. Its table states the ratio on three rows " +
                "(energy 0.0887, kcal 0.0899, carb 0.0912) and the 9 g column resolves UNKNOWN",
        ),
        Capture(
            "20260904-134939-263", "Dutch spread",
            SeventeenthSessionFixtures::c20260904_134939_263,
            SeventeenthSessionStrategyBFixtures::c20260904_134939_263,
            null, null, false,
            "CROP_FALLBACK", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "reads '524g' on the total row and the same token on the sugars row; no basis",
        ),
        Capture(
            "20260904-134951-455", "vanilla dessert sauce",
            SeventeenthSessionFixtures::c20260904_134951_455,
            SeventeenthSessionStrategyBFixtures::c20260904_134951_455,
            null, null, false,
            "FOCUSED_AMOUNT_ENTRY", null, FifteenthSessionCorpus.FailureLayer.OPTICAL_OCR,
            "row and basis established, value unreadable — focused entry is the correct outcome",
        ),
    )
}
