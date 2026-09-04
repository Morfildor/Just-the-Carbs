package app.justthecarbs.ocr

/**
 * The Strategy B readings of the ninth session, reconstructed — and the honest limits of that.
 *
 * ## Why these have to be derived rather than replayed
 *
 * `diagnostics.txt` records the **Pass A** document. Strategy B is a *separate* ML Kit recognition
 * of the user's crop, and the bundle preserves only its verdict (`Confident 2.8/PER_100_G`), never
 * the document that produced it. So there is nothing to replay, and the recorder gap that causes
 * that is fixed in this pass — but it is fixed for the *next* session, not this one.
 *
 * ## What is derived, and what is not
 *
 * Each document below is the real Pass A document with **one** change: the single element ML Kit
 * misread as a digit at the unit position is given the unit glyph the package prints. Nothing is
 * moved, nothing is added, no value is altered, and no other element is touched.
 *
 * That one change is not a guess about what Strategy B saw in general — it is the specific
 * difference the bundle proves existed. Pass A produced `NotFound` with
 * `unit-accompaniment: '2.8' states no unit`, Strategy B produced `Confident 2.8/PER_100_G` over the
 * same printed row, and a value cannot become accompanied by anything other than its unit being
 * recognised. The value `2.8` itself is already present in Pass A and is not synthesised here.
 *
 * ## The guard against the fixture trap
 *
 * `NinthSessionRegressionTest` asserts, as an explicit precondition, that the **real** interpreter
 * reads each document below as the device's Strategy B did. Without that a later assertion could
 * pass because the derivation happened to produce something else entirely — the trap this repo has
 * hit with the Dutch header fixture, the soft-keyboard geometry test and the fifth session's
 * serving-declaration fixture.
 *
 * These documents are used **only** to supply a `SELECTED_REGION_OCR` evidence entry with the
 * verdict the device recorded. Every rule under test then runs unmodified.
 */
object NinthSessionStrategyBDocuments {

    /**
     * `20260903-085019-213` — the white Dutch table, with the carbohydrate row's unit recognised.
     *
     * Device: `SELECTED_REGION_OCR [run=SELECTED_REGION] Confident 2.8/PER_100_G`.
     *
     * The element at `[1400,1517,1431,1575]` is the printed `g` after `2,8`; Pass A read it as `9`.
     * Note the *fat* row of the same capture suffered the identical misread (`4,8 9`), which is why
     * this is a property of the recognition rather than anything carbohydrate-specific.
     */
    fun whiteTableUnitRecognised(): OcrDocument = withUnitAt(
        NinthSessionFixtures.whiteTableFirst(),
        left = 1400,
        top = 1517,
    )

    /** `20260903-085032-269` — the same package and the same misread on the second attempt. */
    fun whiteTableSecondUnitRecognised(): OcrDocument = withUnitAt(
        NinthSessionFixtures.whiteTableSecond(),
        left = 1483,
        top = 1967,
    )

    /**
     * `20260903-084951-833` — the green drink, with the per-100 header's lost `l` restored.
     *
     * Device: `SELECTED_REGION_OCR [run=SELECTED_REGION] Confident 0.5/PER_100_ML`.
     *
     * ## Why the failure here is the *header*, not the value
     *
     * This capture's difference from the white table was measured rather than assumed, and it is not
     * the one the bundle's status text suggests. The green drink's value cell is **already clean** —
     * Pass A reads `0.5g`, unit and separator intact — so [UnitAccompanimentPolicy] never declines
     * it. What Pass A cannot do is *place* it: ML Kit read the printed `PER: 100 ml | 250 ml` as
     *
     * ```
     * 'PER:' 'm'   '|' '25d6'
     *        ^^^ the l of ml, lost
     * ```
     *
     * and `m` is not a unit spelling — deliberately, and it must never become one, because a bare
     * `m` is metres and that list is shared with `ServingSizeParser`. So [ColumnClassifier] resolves
     * **zero** columns, the interpreter rejects `0.5` with `no column`, and no pass is confident at
     * all. [EvidenceResolver] then returns `Nothing` from its `confident.isEmpty()` branch — which is
     * why this capture's bundle records `Nothing` where the white table's records
     * `NeedsVerification`, despite both showing `final UI action : RECOVERY`.
     *
     * Measured on the real classifier: with `m`, `columns=0` and `StatedBasis.of` is null; with `ml`,
     * `columns=1` (`PER_100_ML @ x=1163.5`) and the interpreter reads `Confident 0.5/PER_100_ML` —
     * the device's Strategy B verdict exactly.
     *
     * The one change is the same shape as the white table's: the single element whose glyph the
     * recognizer lost is given the glyph the package prints. The value `0.5` is already present in
     * Pass A and is not synthesised, and `25d6` — the *second* column's header, which Strategy B had
     * no more reason to read correctly — is deliberately left damaged.
     */
    fun greenDrinkUnitRecognised(): OcrDocument = replacingElementAt(
        NinthSessionFixtures.greenDrinkStrategyB(),
        left = 1208,
        top = 1347,
        text = "ml",
    )

    /** Replaces the element whose box starts at ([left], [top]) with the unit glyph `g`. */
    private fun withUnitAt(document: OcrDocument, left: Int, top: Int): OcrDocument =
        replacingElementAt(document, left, top, text = "g")

    /**
     * Replaces the text of the element whose box starts at ([left], [top]) with [text].
     *
     * Deliberately keyed on the **box** rather than on the text, so this cannot silently rewrite
     * some other `9` or `m` elsewhere on the label — several of which are legitimate. The `require`
     * makes a stale derivation fail loudly instead of quietly returning the unmodified document,
     * which would make every assertion built on it pass for the wrong reason.
     */
    private fun replacingElementAt(
        document: OcrDocument,
        left: Int,
        top: Int,
        text: String,
    ): OcrDocument {
        val replaced = document.elements.map { element ->
            if (element.box.left == left && element.box.top == top) {
                element.copy(text = text)
            } else {
                element
            }
        }
        require(replaced != document.elements) {
            "no element starts at ($left, $top); the fixture changed and this derivation is stale"
        }
        return document.copy(elements = replaced)
    }
}
