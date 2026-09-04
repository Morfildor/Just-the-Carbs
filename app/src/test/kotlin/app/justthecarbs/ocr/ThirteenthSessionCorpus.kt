package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * The thirteenth physical session as **data**: one row per capture, ground truth read off the
 * photograph, replayed through the real pipeline by [ThirteenthSessionReplay].
 *
 * ## Why a manifest instead of nineteen test classes
 *
 * This repo has accumulated one bespoke regression class per session, each asserting a handful of
 * literals. That shape made every new physical session cost a new file and made corpus-level
 * questions — *how many clear labels does the app actually complete?* — unanswerable without
 * reading them all. The acceptance standard for this pass is an aggregate, so the corpus is a list
 * and the tests fold over it.
 *
 * ## Ground truth
 *
 * [printedCarbs] and [printedBasis] were read from each bundle's `capture.jpg` by eye. That is the
 * brief's rule and it is load-bearing here: three captures in this session recognise a *confidently
 * wrong* number, and two of them (`12` for a printed `7,2`, `13` for a printed `1,3`) would look
 * like successes if the parser's own answer were taken as truth.
 *
 * [printedCarbs] is null only when the photograph genuinely cannot establish it — a blurred frame
 * with no label in it.
 */
internal object ThirteenthSessionCorpus {

    /** What the app did with a capture, judged against the printed truth. */
    enum class Outcome {
        /** Advanced automatically to the correct printed value. */
        CORRECT_AUTO,

        /** Offered the correct printed value for one-tap confirmation. */
        CORRECT_CONFIRM,

        /** Advanced or confirmed a value the package does not print. Release blocker. */
        WRONG_AUTO,

        /** Offered a value the package does not print for confirmation. Release blocker. */
        WRONG_CONFIRM,

        /**
         * At least one recognition run held the correct value and basis, and the app still sent the
         * user to crop or generic recovery.
         *
         * This is the recall metric the brief is about: *OCR knew the answer and the app withheld
         * it*, as distinct from [OCR_NO_EVIDENCE] below.
         */
        UNNECESSARY_RECOVERY,

        /**
         * No recognition run produced the printed value at all, so recovery is the honest outcome.
         *
         * Not a defect of the presentation layer. Separated from [UNNECESSARY_RECOVERY] because
         * conflating the two hides regressions inside "NotFound", which the brief forbids.
         */
        OCR_NO_EVIDENCE,

        /** The photograph cannot establish what was printed. */
        GROUND_TRUTH_UNKNOWN,
    }

    /**
     * One capture.
     *
     * [correctValueInEvidence] records whether any pass in the bundle read the printed figure. It is
     * taken from the bundle's own `passes contributing evidence:` line, not inferred, and it is what
     * separates a presentation failure from a recognition failure.
     */
    data class Capture(
        val bundle: String,
        val product: String,
        val document: () -> OcrDocument,
        val printedCarbs: BigDecimal?,
        val printedBasis: NutritionBasis?,
        val correctValueInEvidence: Boolean,
        /** What the shipped build did on the device, from `selection.txt`'s `final UI action`. */
        val deviceAction: String,
        /** What the device's resolver returned, from `selection.txt`. */
        val deviceVerdict: String,
        /**
         * What Strategy B read on the device, when it read anything.
         *
         * Taken verbatim from the bundle's `passes contributing evidence:` line. The JVM cannot run
         * a second ML Kit recognition, so the replay reproduces the device's evidence *set* by
         * replaying Pass A's parse under this pass's identity when the device recorded agreement.
         * Null means Strategy B contributed nothing, which is the majority.
         */
        val strategyBValue: String? = null,
        val note: String,
    )

    private fun bd(s: String) = BigDecimal(s)

    val captures: List<Capture> = listOf(
        Capture(
            bundle = "20260904-080926-938",
            product = "green drink (Fanta-style can)",
            document = ThirteenthSessionFixtures::greenDrinkUnitLostZeroFiveNine,
            printedCarbs = bd("0.5"),
            printedBasis = NutritionBasis.PER_100_ML,
            // Pass A read `0.59` — the printed `g` as a `9`. The value is wrong as recognised.
            correctValueInEvidence = false,
            deviceAction = "CROP_FALLBACK",
            deviceVerdict = "Nothing",
            note = "unit glyph lost into the value; `0,5 g` -> `0.59`",
        ),
        Capture(
            bundle = "20260904-080948-287",
            product = "green drink (Fanta-style can)",
            document = ThirteenthSessionFixtures::greenDrinkConflictedZeroFive,
            printedCarbs = bd("0.5"),
            printedBasis = NutritionBasis.PER_100_ML,
            correctValueInEvidence = true,
            deviceAction = "CROP_FALLBACK",
            deviceVerdict = "Conflicted",
            note = "Pass A read 0.5 correctly; Strategy B read 0.59 — a genuine cross-run conflict",
        ),
        Capture(
            bundle = "20260904-081003-678",
            product = "Lidl carton (red)",
            document = ThirteenthSessionFixtures::lidlCartonStrategyBTwo,
            printedCarbs = bd("7.2"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = false,
            deviceAction = "RECOVERY",
            deviceVerdict = "NeedsVerification",
            note = "Strategy B read `2` for a printed `7,2`",
        ),
        Capture(
            bundle = "20260904-081018-275",
            product = "Lidl carton (red)",
            document = ThirteenthSessionFixtures::lidlCartonTwelve,
            printedCarbs = bd("7.2"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = false,
            deviceAction = "RECOVERY",
            deviceVerdict = "Resolved",
            note = "`7,2 g` recognised as `12g`; the scale rule correctly withheld it",
        ),
        Capture(
            bundle = "20260904-081039-484",
            product = "cracker pack",
            document = ThirteenthSessionFixtures::crackersSeventyTwoNothing,
            printedCarbs = bd("72"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = false,
            deviceAction = "CROP_FALLBACK",
            deviceVerdict = "Nothing",
            note = "two-column table; the per-100 carbohydrate cell was not recognised",
        ),
        Capture(
            bundle = "20260904-081055-219",
            product = "yoghurt tub",
            document = ThirteenthSessionFixtures::yoghurtThreePointTwo,
            printedCarbs = bd("3.2"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = true,
            deviceAction = "AUTO_ADVANCE",
            deviceVerdict = "Resolved",
            strategyBValue = "3.2",
            note = "clean read, verified by distinct runs, separator intact",
        ),
        Capture(
            bundle = "20260904-081108-784",
            product = "yoghurt tub (2 kg)",
            document = ThirteenthSessionFixtures::yoghurtTubNothing,
            printedCarbs = bd("4.5"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = false,
            deviceAction = "CROP_FALLBACK",
            deviceVerdict = "Nothing",
            note = "very small dot-matrix multilingual print; the row was not reconstructed",
        ),
        Capture(
            bundle = "20260904-081129-886",
            product = "prose label (Albert Heijn)",
            document = ThirteenthSessionFixtures::proseThreePointThree,
            printedCarbs = bd("3.3"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = true,
            deviceAction = "AUTO_ADVANCE",
            deviceVerdict = "Resolved",
            strategyBValue = "3.3",
            note = "prose declaration read correctly and corroborated",
        ),
        Capture(
            bundle = "20260904-081141-102",
            product = "fritessaus bottle",
            document = ThirteenthSessionFixtures::fritessausThirteenPointTwo,
            printedCarbs = bd("13.2"),
            printedBasis = NutritionBasis.PER_100_ML,
            correctValueInEvidence = true,
            deviceAction = "AUTO_ADVANCE",
            deviceVerdict = "Resolved",
            strategyBValue = "13.2",
            note = "clean read, separator intact",
        ),
        Capture(
            bundle = "20260904-081151-032",
            product = "pickle jar",
            document = ThirteenthSessionFixtures::pickleFivePointFour,
            printedCarbs = bd("5.4"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = true,
            deviceAction = "CONFIRM_ON_CAPTURE",
            deviceVerdict = "Resolved",
            note = "per-100 and serving columns both present; the per-100 cell won correctly",
        ),
        Capture(
            bundle = "20260904-081213-403",
            product = "coconut water carton",
            document = ThirteenthSessionFixtures::coconutWaterNothingOne,
            printedCarbs = bd("3.2"),
            printedBasis = NutritionBasis.PER_100_ML,
            correctValueInEvidence = false,
            deviceAction = "CROP_FALLBACK",
            deviceVerdict = "Nothing",
            note = "two-column 100ml/250ml table; carbohydrate cell not recognised as a value",
        ),
        Capture(
            bundle = "20260904-081226-187",
            product = "coconut water carton",
            document = ThirteenthSessionFixtures::coconutWaterNothingTwo,
            printedCarbs = bd("3.2"),
            printedBasis = NutritionBasis.PER_100_ML,
            correctValueInEvidence = false,
            deviceAction = "CROP_FALLBACK",
            deviceVerdict = "Nothing",
            note = "same package, closer framing, same failure",
        ),
        Capture(
            bundle = "20260904-081244-479",
            product = "(blurred, no label in frame)",
            document = ThirteenthSessionFixtures::blurredNonLabel,
            printedCarbs = null,
            printedBasis = null,
            correctValueInEvidence = false,
            deviceAction = "CROP_FALLBACK",
            deviceVerdict = "Nothing",
            note = "zero elements recognised; nothing to read",
        ),
        Capture(
            bundle = "20260904-081251-955",
            product = "sunflower oil bottle",
            document = ThirteenthSessionFixtures::sunflowerOilZero,
            printedCarbs = bd("0.0"),
            printedBasis = NutritionBasis.PER_100_ML,
            correctValueInEvidence = true,
            deviceAction = "CONFIRM_ON_CAPTURE",
            deviceVerdict = "Resolved",
            note = "single-column label; correct, unverified, separator intact",
        ),
        Capture(
            bundle = "20260904-081307-240",
            product = "Lidl drink carton (green)",
            document = ThirteenthSessionFixtures::lidlDrinkSixPointTwoUnitLost,
            printedCarbs = bd("6.2"),
            printedBasis = NutritionBasis.PER_100_ML,
            // `6,2 g` arrived as the single token `6,20` — the DIGITS are right and the unit glyph
            // became a `0`. The correct value is present in the recognition; only its unit is not.
            correctValueInEvidence = true,
            deviceAction = "CROP_FALLBACK",
            deviceVerdict = "Nothing",
            note = "`6,2 g` -> `6,20`; declined as unit-less and suppressed in recovery too",
        ),
        Capture(
            bundle = "20260904-081335-279",
            product = "cocoa powder tin",
            document = ThirteenthSessionFixtures::cocoaTwelvePointSix,
            printedCarbs = bd("12.6"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = true,
            deviceAction = "CONFIRM_ON_CAPTURE",
            deviceVerdict = "Resolved",
            note = "prose declaration, correct, unverified, separator intact",
        ),
        Capture(
            bundle = "20260904-081407-814",
            product = "peanut butter jar",
            document = ThirteenthSessionFixtures::peanutButterNothing,
            printedCarbs = bd("11"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = false,
            deviceAction = "CROP_FALLBACK",
            deviceVerdict = "Nothing",
            note = "prose declaration; `11 g` arrived as `t1`",
        ),
        Capture(
            bundle = "20260904-081421-421",
            product = "peanut butter jar",
            document = ThirteenthSessionFixtures::peanutButterEleven,
            printedCarbs = bd("11"),
            printedBasis = NutritionBasis.PER_100_G,
            correctValueInEvidence = true,
            deviceAction = "AUTO_ADVANCE",
            deviceVerdict = "Resolved",
            strategyBValue = "11.0",
            note = "genuine printed integer; advanced on cross-run agreement with scale UNSUPPORTED",
        ),
        Capture(
            bundle = "20260904-081435-300",
            product = "mayonnaise bottle",
            document = ThirteenthSessionFixtures::mayonnaiseThirteen,
            printedCarbs = bd("1.3"),
            printedBasis = NutritionBasis.PER_100_ML,
            correctValueInEvidence = false,
            deviceAction = "RECOVERY",
            deviceVerdict = "Resolved",
            note = "`1,3 g` recognised as `13g` — a decimal collapse; correctly withheld",
        ),
    )

    /** Captures whose printed value the photograph establishes. */
    val withGroundTruth: List<Capture> get() = captures.filter { it.printedCarbs != null }
}
