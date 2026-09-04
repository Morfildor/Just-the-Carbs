package app.justthecarbs.ocr

/**
 * Documents rebuilt from the eleventh phone session, `docs/Scan Evidence 03-09 2nd test/`.
 *
 * That session was the first taken on physical hardware (Samsung SM-S928B, API 36) against a build
 * carrying the tenth pass's corrections. It produced **no wrong value through any route** — the
 * eighth session's `12` recurred on `20260903-142937-283` and was refused as a cross-run dispute —
 * and exposed the opposite failure twice: a correct reading the app held and the user could not get
 * to.
 *
 * ## Provenance
 *
 * Each document below is transcribed from its bundle's own `diagnostics.txt` element list, so the
 * geometry is the device's and not a construction of mine. Only the elements that bear on the
 * question are kept — a full 90-element transcription would obscure which boxes are load-bearing —
 * and every fixture carries a precondition test asserting the real classifier and column classifier
 * still reach the state the bundle recorded. Without that precondition a fixture whose header the
 * classifier stopped recognising would make every case pass for the wrong reason, which is the trap
 * this repo has recorded three times (the Dutch header fixture, the soft-keyboard geometry test, and
 * the `StatedBasis` fixtures).
 */
internal object EleventhSessionFixtures {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) = OcrElement(
        text = text,
        box = OcrBox(left = left, top = top, right = right, bottom = bottom),
        blockId = 0,
        lineId = 0,
    )

    /**
     * `20260903-142926-419` — the Lidl cracker, read off its **Spanish** row.
     *
     * The package prints `72,0 g / 100 g`, and three sibling captures of it in the same session read
     * exactly that from the Dutch/French row (`dont/Carbohydrate, of which: 72,0 g`). Here ML Kit
     * landed on `Hidratos de carbono` and returned the value as the separatorless **`72g`**.
     *
     * The state the bundle records, and that this fixture reproduces:
     *
     * ```
     * resolver.verdict: Resolved
     * automatic-verification: NONE — only one recognition run (PASS_A)
     * scale evidence  : UNSUPPORTED — candidate '72g'; no paired value in this clause
     * final UI action : RECOVERY
     * recovery        : suppressed '72g' @x=1405 — the scale is not established
     * ```
     *
     * Every one of those is correct in isolation. `72` really is a lone separatorless integer under
     * an inferred per-hundred basis, so [ReadingEligibility] refuses it, and the refusal is the same
     * rule that keeps the red label's `12` out. What is wrong is only what the user is offered next.
     */
    fun crackerSpanishRowSeparatorless(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            // The per-100 header. `o/100 g` is the printed `Ø/100 g` with the slashed O misread —
            // the column classifier resolves PER_100_G from it regardless, which is what makes the
            // basis a *fact the label stated* rather than a guess.
            element("o/100", 1282, 1033, 1445, 1122),
            element("g", 1435, 1033, 1460, 1122),
            // Two nutrient rows above the carbohydrate declaration, so the document has the shape of
            // a table rather than a lone row.
            element("Energie/Energie/Energie/", 168, 1147, 799, 1238),
            element("168", 1290, 1147, 1390, 1238),
            element("kJ", 1400, 1147, 1460, 1238),
            element("Vetten/Fett/Grasas", 170, 1450, 700, 1530),
            element("1,5", 1330, 1450, 1430, 1530),
            element("g", 1435, 1450, 1465, 1530),
            // The carbohydrate declaration, printed across two recognised rows in four languages.
            element("Koolhydraten/Glucides/", 170, 1788, 764, 1875),
            element("Kohlenhydrate/", 783, 1788, 1154, 1875),
            element("Hidratos", 188, 1881, 384, 1957),
            element("de", 396, 1881, 465, 1957),
            element("carbono/Hidratos", 477, 1881, 911, 1957),
            element("de", 923, 1881, 993, 1957),
            element("carbono", 1005, 1881, 1200, 1957),
            // The value, in the per-100 column, with its decimal separator lost.
            element("72g", 1340, 1879, 1471, 1968),
            // The sugars row beneath it, so the total row is not the last thing on the label.
            element("Waarvan suikers/dont sucres", 170, 2000, 900, 2080),
            element("6,1", 1330, 2000, 1430, 2080),
            element("g", 1435, 2000, 1465, 2080),
        ),
    )

    /**
     * `20260903-143023-402` — the Jumbo energy gel, whose second column header fused its connective.
     *
     * The package prints two columns, `per 100g` and `per 45g`, and states
     * `Koolhydraten 67,0 g` / `30,2 g` under them. ML Kit read the second header as the single
     * token **`per45`** followed by `g` — the space between connective and quantity lost.
     *
     * ## Why that one fused token loses the whole column
     *
     * [ColumnClassifier]'s off-basis rule exists for exactly this shape: a `<quantity><unit>` header
     * whose quantity is not 100 is emitted as `UNKNOWN`, so its cells are refused rather than being
     * relabelled per-100. It was written for the third session's `per 250 ml` and handles both
     * printed forms — split (`per` `45` `g`) and quantity-fused (`45g`).
     *
     * `per45` is neither. The connective is welded to the quantity, so the token is not all digits
     * and carries no unit, and the rule never fires. The `45 g` column is therefore never created,
     * and both printed values fall to the one surviving column:
     *
     * ```
     * COLUMNS (1): PER_100_G 'voedingswaarde per 100 g' @ x=1152
     * reading    : Ambiguous [67.0, 30.2]  — both labelled PER_100_G
     * ```
     *
     * **`30,2` is the per-45-g figure wearing the per-100-g basis.** That is the fifth session's
     * `72 g / serving` fabrication arriving through a different door: no stage is individually
     * wrong, and a value ends up under a basis the label does not give it. It reaches the user as
     * one of two choices with nothing on screen to say which is which.
     *
     * The device's own document is larger and its rows reconstruct slightly differently; this keeps
     * the header band, the carbohydrate row and its two cells, which is the geometry the defect
     * lives in. `EleventhSessionBaselineTest` pins that the real classifier reproduces it.
     */
    fun gelFusedConnectiveHeader(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            // The header band. The leading noun sits far left, in the label column.
            element("Gemiddelde", 444, 1530, 685, 1598),
            element("voedingswaarde", 689, 1538, 1010, 1609),
            element("per", 1058, 1551, 1126, 1613),
            element("100", 1135, 1553, 1212, 1615),
            element("g", 1220, 1556, 1246, 1616),
            // The fused second header — the defect's origin.
            element("per45", 1305, 1559, 1423, 1622),
            element("g", 1434, 1563, 1452, 1623),
            element("Energie", 422, 1597, 576, 1662),
            element("kcal", 1078, 1674, 1156, 1724),
            element("269", 1160, 1674, 1240, 1724),
            element("kcal", 1317, 1679, 1392, 1730),
            element("121", 1403, 1679, 1448, 1730),
            // The carbohydrate row, with a cell under each printed column.
            element("Koolhydraten", 418, 1839, 683, 1910),
            element("67,0", 1120, 1864, 1210, 1921),
            element("g", 1220, 1864, 1245, 1921),
            element("30,2g", 1348, 1864, 1443, 1921),
            element("waarvan", 468, 1910, 646, 1959),
            element("suikers", 655, 1910, 794, 1959),
        ),
    )

    /**
     * The same gel header and cells, with the sugars clause on its **own** reconstructed row.
     *
     * Two independent defects met on `20260903-143023-402`, and a fixture carrying both cannot show
     * what either one costs. This isolates the column half: identical header band, identical value
     * cells, with `waarvan suikers` moved down by one row pitch — which is how the same package
     * reconstructed on `20260903-142955-804`, the capture of this product that *did* read.
     *
     * Before the fused-connective fix this reads `Ambiguous [67.0, 30.2]`, offering the per-45-g
     * figure under a per-100-g basis. After it, `30,2` sits in an `UNKNOWN` column and cannot be
     * offered at all, and the printed `67,0 g / 100 g` reads confidently.
     *
     * **Not a sanitised version of the hazard.** The merged shape is kept and asserted separately,
     * because modelling only the safe version of a hazard proves nothing — this repo has recorded
     * that trap for the Dutch header fixture, the soft-keyboard geometry test and the
     * cancellation-honouring search fake.
     */
    fun gelUnmergedCarbohydrateRow(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("Gemiddelde", 444, 1530, 685, 1598),
            element("voedingswaarde", 689, 1538, 1010, 1609),
            element("per", 1058, 1551, 1126, 1613),
            element("100", 1135, 1553, 1212, 1615),
            element("g", 1220, 1556, 1246, 1616),
            element("per45", 1305, 1559, 1423, 1622),
            element("g", 1434, 1563, 1452, 1623),
            element("Energie", 422, 1597, 576, 1662),
            element("kcal", 1078, 1674, 1156, 1724),
            element("269", 1160, 1674, 1240, 1724),
            element("kcal", 1317, 1679, 1392, 1730),
            element("121", 1403, 1679, 1448, 1730),
            element("Koolhydraten", 418, 1839, 683, 1910),
            element("67,0", 1120, 1864, 1210, 1921),
            element("g", 1220, 1864, 1245, 1921),
            element("30,2g", 1348, 1864, 1443, 1921),
            // The sugars clause, one row pitch lower so it reconstructs separately.
            element("waarvan", 468, 1990, 646, 2049),
            element("suikers", 655, 1990, 794, 2049),
            element("12,6", 1120, 1990, 1210, 2049),
            element("g", 1220, 1990, 1245, 2049),
            element("5,4g", 1348, 1990, 1443, 2049),
        ),
    )

    /**
     * `20260903-143036-432` — the same gel, whose per-100 header lost its `1` to a lowercase `l`.
     *
     * ML Kit returned the header as **`per l00 g`**. The row therefore matched no per-100
     * vocabulary, typed `OTHER` rather than `HEADER`, and the device resolved **zero** columns:
     *
     * ```
     * --- columns (0) ---
     * result: Total-carbohydrate row found but no usable per-100 cell
     * reason: total row found but no per-100 column was resolved
     * ```
     *
     * This is a *recognition* failure of one glyph, in the same family as the `(g)` -> `(9)` and
     * `Ø/100 g` -> `o/100 g` misreads this repo already records. Unlike those, it costs the whole
     * basis rather than one cell, because the header is what every column resolution rests on.
     *
     * Geometry is transcribed from that bundle's own element list. The value cells are added at the
     * positions the same package prints them, so the fixture can show what the lost header costs;
     * the device's own capture failed before reaching them.
     */
    fun gelHeaderWithLetterEllForOne(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("Gemiddelde", 484, 1436, 743, 1511),
            element("voedingswaarde", 770, 1442, 1132, 1519),
            element("per", 1186, 1450, 1266, 1521),
            // The defect: a lowercase L where the package prints a 1.
            element("l00", 1275, 1450, 1390, 1521),
            element("g", 1400, 1452, 1428, 1521),
            element("per", 1477, 1456, 1557, 1526),
            element("45g", 1566, 1457, 1648, 1528),
            element("Energie", 462, 1520, 616, 1585),
            element("Koolhydraten", 458, 1760, 723, 1831),
            element("67,0", 1180, 1785, 1270, 1842),
            element("g", 1280, 1785, 1305, 1842),
            element("30,2g", 1520, 1785, 1615, 1842),
            element("waarvan", 508, 1900, 686, 1959),
            element("suikers", 695, 1900, 834, 1959),
            element("12,6", 1180, 1900, 1270, 1959),
            element("g", 1280, 1900, 1305, 1959),
            element("5,4g", 1520, 1900, 1615, 1959),
        ),
    )
}
