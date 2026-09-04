package app.justthecarbs.ocr

import app.justthecarbs.domain.BasisUnitSpellings
import app.justthecarbs.domain.ServingSizeParser
import kotlin.math.abs

/** What one table column measures. */
enum class NutritionColumnKind {
    PER_100_G,
    PER_100_ML,
    PER_SERVING,

    /** A "%RI"/"%DV" column. Never a carbohydrate quantity, only a percentage of a daily reference. */
    REFERENCE_PERCENT,

    /** Position known, meaning not established. A cell here is never used for any figure. */
    UNKNOWN,
}

/** One classified column, positioned by the x-centre later cells are aligned against. */
data class NutritionColumn(
    val kind: NutritionColumnKind,
    /** Null when the column was recovered from its cells rather than from a header. */
    val headerBox: OcrBox?,
    val centerX: Double,
    /** The header text this column was read from, or "" when recovered from cell shape. */
    val headerText: String,
    /**
     * The band of rows this column is evidence about, or null when it applies document-wide.
     *
     * Non-null only for a column recovered from cell shape rather than read from a header. A stated
     * header is a claim about the table it heads, and a table's rows run below its header for an
     * unbounded distance — on the real packaging in this repo's fixtures a correct per-100 header
     * sits seven reconstructed rows above the carbohydrate row it governs. A cluster of
     * percent-shaped cells, by contrast, is evidence only about the rows it was found on: it says
     * "these particular rows have a percentage in this x-band", and nothing whatever about a table
     * printed elsewhere in the frame that it never touched.
     *
     * That distinction matters because a crop of a real package routinely retains a second printed
     * block — a reference-intake summary, a marketing paragraph, a neighbouring package. Its
     * percentages cluster on an x position of their own, the fallback recovers a column from them,
     * and that column then vetoes the real table's grams cells as "percentages". The reading is lost
     * to a panel it has no relationship with.
     *
     * Bounding a recovered column to its own rows is a narrowing of what it may *claim*. It is not a
     * preference for whichever column sits nearer the values — that would be a proximity score, and
     * proximity scoring is the mechanism the geometry-first rewrite removed.
     */
    val verticalExtent: IntRange? = null,
)

/**
 * Finds the table's columns and says what each one measures.
 *
 * Header text is the primary signal. The cell-shape fallback exists for exactly one job: a column
 * whose numbers all carry a percent sign is [NutritionColumnKind.REFERENCE_PERCENT] even when OCR
 * lost its header, so a "17%" can never be handed back as 17 g of carbohydrate. It deliberately does
 * NOT try to tell per-100 from per-serving by shape — both look like bare grams, and a wrong guess
 * there is a confident wrong answer rather than a refusal. Those need a header, or they stay
 * [NutritionColumnKind.UNKNOWN] and are refused downstream.
 */
object ColumnClassifier {

    fun classify(rows: List<LogicalRow>, documentWidth: Int): List<NutritionColumn> {
        // classifyAll: an address block or storage instruction past the declaration boundary must
        // not head a column. See [DeclarationBoundary].
        val kinds = RowClassifier.classifyAll(rows)
        val headerRows = rows.filterIndexed { index, _ -> kinds[index] == NutritionRowKind.HEADER }
        val fromHeaders = headerRows.flatMap { columnsIn(it) }

        val percentColumns = percentColumnsFromCells(rows, documentWidth).filter { candidate ->
            // A header already covering this x position wins; do not add a duplicate.
            fromHeaders.none { abs(it.centerX - candidate.centerX) <= documentWidth * NEAR_COLUMN_FRACTION }
        }

        // A serving column whose size is printed on the line below the header it belongs to. The
        // span walk above reads one row at a time, so a header split across two rows loses the half
        // that gives it meaning. See [ServingColumnHeaders] for the two captures this was measured
        // on and for why a printed quantity is not a vocabulary.
        val servingColumns = ServingColumnHeaders.recover(
            rows = rows,
            kinds = kinds,
            existing = fromHeaders + percentColumns,
            documentWidth = documentWidth,
        )

        return (fromHeaders + percentColumns + servingColumns).sortedBy { it.centerX }
    }

    /**
     * Walks a header row's elements, growing a span while it stays unrecognized and emitting a
     * column as soon as the span matches a known header vocabulary. This is what separates
     * "per 100 g" from "per serving" when both sit on one row.
     */
    private fun columnsIn(row: LogicalRow): List<NutritionColumn> {
        val columns = mutableListOf<NutritionColumn>()
        val elements = splitRunTogetherBases(row.elements)
        var spanStart = 0

        // Whether an off-basis quantity column (`250 ml`, `9 g`) may be recognised on this row at
        // all.
        //
        // Only on a row that also states a **per-100** basis. That is what makes the row a genuine
        // multi-column header rather than a value row that happens to contain a `<quantity><unit>`
        // token: a US-style linear panel prints `Protein 2g, Vit. D ...` on one recognised row, and
        // without this gate every such value would invent a column of its own. Measured on the
        // Korean sauce, where it produced three junk UNKNOWN columns from `0g,`, `(18 g)` and `2g,`.
        //
        // The per-100 column is the anchor the off-basis column sits beside; with no per-100 column
        // on the row there is no two-column header to protect, and the pre-existing behaviour — the
        // tokens simply head no column — is correct.
        val statesPerHundred = PER_100.containsMatchIn(
            NutritionTerminology.normalize(elements.joinToString(" ") { it.text }),
        )

        while (spanStart < elements.size) {
            // A lone "%" heading its own column, which is how a reference-intake column is printed
            // on a dense multilingual label. Handled before the span walk so it can never be
            // absorbed into a neighbouring header: on a reconstruction of the Kinder table the
            // greedy pass matched "per stuk %" as one PER_SERVING span, which both dragged that
            // column's centre 38 px toward the percentages and left no percent column at all. It
            // also destroyed the serving descriptor, since "stuk %" parses as no unit word.
            if (isBarePercent(elements[spanStart].text)) {
                // Subject to the same inline-annotation test as a longer percent span: on a linear
                // panel the `%` of `(22 % DV)` is its own element, so this branch was emitting one
                // phantom column per clause exactly as the span walk was. A bare `%` heads a column
                // only on a row that is a header — one naming no nutrient and printing no amount.
                if (!InlinePercentAnnotation.isInlineAnnotation(row, listOf(elements[spanStart]))) {
                    val box = elements[spanStart].box
                    columns += NutritionColumn(
                        kind = NutritionColumnKind.REFERENCE_PERCENT,
                        headerBox = box,
                        centerX = box.centerX,
                        headerText = elements[spanStart].text,
                    )
                }
                spanStart++
                continue
            }

            // A quantity-and-unit header naming a quantity that is NOT 100 — `250 ml`, `9 g`, `30 g`.
            //
            // Such a column is real and is printed on the label, but it is **not a per-100 column**
            // and there is no [app.justthecarbs.domain.NutritionBasis] member that means "per 250 ml".
            // It is therefore emitted as [NutritionColumnKind.UNKNOWN], whose entire purpose is to be
            // a position whose meaning is not established — a cell there is refused downstream rather
            // than being read under some other column's basis.
            //
            // **This is the fix for the 2.6x error.** The green drink prints `100 ml` and `250 ml`
            // side by side. Without this the `250 ml` tokens were absorbed into the per-100 span, one
            // column was emitted covering both, and the 250 ml figure (`13g`) bound to it and reached
            // the user as `1.3 g/100 ml`. Claiming the position without claiming a meaning is what
            // keeps the two cells apart.
            //
            // Checked before the vocabulary walk so the quantity cannot be swallowed by a longer
            // span, exactly as the bare `%` case above is.
            val offBasis = if (statesPerHundred) offBasisQuantitySpan(elements, spanStart) else null
            if (offBasis != null) {
                val box = offBasis.drop(1).fold(offBasis.first().box) { acc, e -> acc.union(e.box) }
                columns += NutritionColumn(
                    kind = NutritionColumnKind.UNKNOWN,
                    headerBox = box,
                    centerX = box.centerX,
                    headerText = offBasis.joinToString(" ") { it.text },
                )
                spanStart += offBasis.size
                continue
            }

            var matched = false
            // Longest span first: "per 100 ml" must beat a bare "100" prefix.
            for (length in minOf(MAX_HEADER_SPAN, elements.size - spanStart) downTo 1) {
                val span = elements.subList(spanStart, spanStart + length)
                // A span ending on a connective has over-reached into the next column: "per 100 g
                // per" is the grams column plus the start of another one, and consuming that
                // trailing word both widens this column's centre and hides the column it belongs
                // to. A bare "%" is over-reach of the same kind — normalization strips the sign, so
                // "per stuk %" would otherwise still match the serving vocabulary. Shorter spans
                // are tried instead.
                if (isConnective(span.last().text) || isBarePercent(span.last().text)) continue
                // A span may not reach across the start of a *different* column's basis statement.
                //
                // The green drink's header splits into `100` `ml` `250` `ml`, and the greedy walk
                // matched all four as one span: `PER_100.findAll` finds only `100 ml` in the joined
                // text (there is no `per 250` vocabulary), so [kindOf] saw exactly one kind, emitted
                // one PER_100_ML column, and silently absorbed the 250 ml header into it. Both value
                // cells then bound to that column and the 250 ml figure reached the user as
                // `/100 ml`.
                //
                // Stopping the span at the second quantity is what makes the off-basis check below
                // reachable at all — without it the walk consumes the tokens before that check is
                // ever asked about them.
                // Evaluated against the FULL element list, never the truncated span: the token that
                // begins the next column may be the span's own last element, whose unit sits just
                // beyond the span's end. Asking the sub-list would return null there and the guard
                // would silently never fire — which is exactly what left the drink's header as one
                // column reading `100 ml 250` after the split was already working.
                if (statesPerHundred &&
                    (spanStart + 1 until spanStart + length).any { offBasisQuantitySpan(elements, it) != null }
                ) {
                    continue
                }
                val text = span.joinToString(" ") { it.text }
                val kind = kindOf(NutritionTerminology.normalize(text)) ?: continue
                // A **basis** span may not end on a token that states no part of a basis.
                //
                // Same over-reach as the connective case above, in the shape a *damaged* connective
                // leaves behind. On the Baltic table (`225829-154`) the split of `of9g` yields the
                // debris `of` followed by `9g`; the greedy walk matched `o/100g| of` as one span —
                // the trailing `of` contributes nothing but drags the union, and with it the anchor,
                // 48 px right, from x=1306 to x=1354, toward the 9 g portion column whose values it
                // must stay clear of. Shorter spans are tried instead, which is how `o/100g|` alone
                // becomes the column.
                //
                // Applied **after** [kindOf] rather than before, and only to per-100 and serving
                // spans. A `REFERENCE_PERCENT` span legitimately ends on its own percent token —
                // this table's is `%Rí` — which states no basis by definition; checking before the
                // kind was known deleted every percent column on the label, and with it the
                // protection that keeps a `%RI` figure from being read as grams.
                if (kind != NutritionColumnKind.REFERENCE_PERCENT && !statesBasis(span.last().text)) {
                    continue
                }
                // An inline `% DV` annotation is not a column header.
                //
                // A US linear Nutrition Facts panel states each nutrient and its own reference
                // percentage in a running clause: `Total Fat 0.5 g (1 % DV), Sat. Fat 0 g (0 % D)`.
                // Every recognised row of such a panel therefore contains the reference-intake
                // vocabulary, and the span walk emitted a REFERENCE_PERCENT column for each one —
                // **eight** of them on the Korean sauce (`085611-201`), scattered at x=351, 524,
                // 738, 744, 817, 1078, 1232 and 1452 across a panel that has no columns at all.
                //
                // The consequence was not cosmetic. `Total Carb. 6g` sat nearest one of those
                // phantom anchors, so the printed carbohydrate figure was bound to a
                // reference-percent column and refused: `rejected: 6: REFERENCE_PERCENT column`.
                // The panel was unreadable because of columns the panel does not have.
                //
                // A genuine percent column heads a column — it stands alone at the top of one, as
                // `%RI` or a bare `%` does. An annotation is attached to a nutrient's own value
                // inside a clause. [InlinePercentAnnotation] tells them apart structurally, by
                // whether the span shares its row with a nutrient name and a value, and never by
                // position on the page.
                if (kind == NutritionColumnKind.REFERENCE_PERCENT &&
                    InlinePercentAnnotation.isInlineAnnotation(row, span)
                ) {
                    continue
                }
                val box = span.drop(1).fold(span.first().box) { acc, element -> acc.union(element.box) }
                // Anchored on the BASIS TOKENS, not on the union of the whole matched span.
                //
                // Measured on the Sondey pack (`docs/Scan evidence 31-08-26/20260831-135943-434`):
                // the per-100 header is printed as `Voedingswaarde/Valeur nutritionnelle/ 100g`, so
                // the union ran x=172..1181 and its centre landed at 676.5 — in the middle of the
                // label column, where nothing is printed. The `100g` token alone sits at centre
                // 1125.5, and the value it heads (`72,0`) at 1108.0. The interpreter binds cells by
                // distance to `centerX`, so the union anchor put the correct value 432 px away and
                // the whole table failed with "no usable per-100 cell".
                //
                // A leading noun ("Voedingswaarde", "Nutritional value") sits in the label column and
                // says nothing about where the values are. The basis token is what is printed over
                // the column it describes, which is why it — and only it — positions the column.
                //
                // `headerBox` keeps the full span, because it describes what was *read*; only the
                // anchor narrows to what states the basis.
                val anchorBox = basisAnchorBox(span) ?: box
                columns += NutritionColumn(kind, box, anchorBox.centerX, text)
                spanStart += length
                matched = true
                break
            }
            if (!matched) spanStart++
        }

        return columns
    }

    /**
     * Splits an element whose text runs two basis phrases together, so the boundary between two
     * adjacent column headers survives a missing space (P1-3).
     *
     * ### The measured defect
     *
     * Two captures of one Fanta Zero can, 36 seconds apart
     * (`docs/Scan evidence 31-08-26/`):
     *
     * ```
     * 140132-710:  '100 ml 250 m'  -> HEADER -> column PER_100_ML   (correct)
     * 140208-173:  '100 ml250 ml'  -> OTHER  -> no columns at all
     * ```
     *
     * The whitespace is the only difference. Without it the span walk sees one token that matches
     * two per-100 vocabularies at once, [kindOf] correctly refuses the ambiguity, no shorter span
     * can be tried inside a single element, and the row resolves nothing. The user is then asked
     * "per what?" and offered `/100 g` on a drink whose only unit anywhere is `ml`.
     *
     * ### Why this is a tokenising fix and not a loosening
     *
     * It splits only where a **complete** basis phrase is immediately followed by the start of
     * another one — `100 ml` then `250 ml`. Every resulting piece must still be recognised on its own
     * by the ordinary vocabulary; nothing is accepted here that would not have been accepted had ML
     * Kit emitted the space. A row of prose that merely contains `100 g` gains no column from this,
     * because one prose row still yields no aligned value cells.
     *
     * Geometry is apportioned across the split by character position. That is an approximation of
     * where the glyphs sit, and it is sound for the only thing the boxes are used for here: two
     * adjacent headers get two distinct anchors on the correct sides of the original box, instead of
     * one anchor spanning both columns.
     */
    /** Test-only window onto [splitRunTogetherBases], so a fixture can show what tokenisation it gets. */
    internal fun debugSplit(elements: List<OcrElement>): List<OcrElement> = splitRunTogetherBases(elements)

    private fun splitRunTogetherBases(elements: List<OcrElement>): List<OcrElement> {
        // Tested against RAW text, deliberately. `NutritionTerminology.normalize` now repairs this
        // boundary itself for the text-matching stages, so a normalized string never contains the
        // pattern any more — checking the normalized form here would mean this split silently never
        // fired, and two adjacent headers would keep sharing one anchor spanning both columns.
        if (elements.none {
                RUN_TOGETHER_BASES.containsMatchIn(it.text) ||
                    UNIT_THEN_QUANTITY.containsMatchIn(it.text) ||
                    DAMAGED_CONNECTIVE.containsMatchIn(it.text) ||
                    DAMAGED_CONNECTIVE_LETTER.containsMatchIn(it.text)
            }
        ) {
            return elements
        }

        return elements.flatMap { element ->
            val match = RUN_TOGETHER_BASES.find(element.text)
                ?: UNIT_THEN_QUANTITY.find(element.text)
                ?: DAMAGED_CONNECTIVE.find(element.text)
                ?: DAMAGED_CONNECTIVE_LETTER.find(element.text)
                ?: return@flatMap listOf(element)

            // The boundary sits immediately after the first complete basis phrase.
            val fraction = (match.range.last + 1).toDouble() / element.text.length
            val cut = (match.range.last + 1).coerceIn(1, element.text.length - 1)

            val width = element.box.right - element.box.left
            val boundary = element.box.left + (width * fraction).toInt().coerceIn(1, width - 1)

            listOf(
                element.copy(
                    text = element.text.substring(0, cut).trim(),
                    box = element.box.copy(right = boundary),
                ),
                element.copy(
                    text = element.text.substring(cut).trim(),
                    box = element.box.copy(left = boundary),
                ),
            ).filter { it.text.isNotBlank() }
        }
    }

    /**
     * A complete per-100 basis phrase immediately followed by a digit that begins another one.
     *
     * Deliberately narrow. It requires the *unit* to be present before the boundary, so `100250` (a
     * bare number pair, stating no basis) and `1081stoffen` (an ingredient token) never match. The
     * trailing digit is what makes it a run-together *pair* rather than a single header with a
     * suffix.
     */
    private val RUN_TOGETHER_BASES = Regex(
        "\\d{1,4}\\s*(?:${BasisUnitSpellings.alternation})(?=\\d)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A unit that **begins** an element and is immediately followed by the quantity of the next
     * column's header — the other half of the run-together case, where ML Kit put the boundary
     * inside an element but the preceding quantity in the element before it.
     *
     * ### The measured shape
     *
     * The green drink's header (`docs/Scan Evidence 01-09-26/20260901-211417-935`) came back as
     * three elements:
     *
     * ```
     * '100'   [852..935]
     * 'ml250' [961..1172]   <- the first column's unit welded to the second column's quantity
     * 'ml'    [1177..1222]
     * ```
     *
     * [RUN_TOGETHER_BASES] cannot see this: it needs `<digits><unit>` inside one element, and here
     * the digits (`100`) live in the element *before* the unit. So the split never fired, the span
     * walk matched all three elements as one header, and one column was emitted spanning both
     * printed columns.
     *
     * Splitting `ml250` into `ml` + `250` restores the two-column structure the label prints, and
     * every resulting piece is still recognised by the ordinary vocabulary — `100 ml` and `250 ml`
     * are both complete basis phrases. Nothing is accepted here that would not have been accepted
     * had ML Kit emitted the space.
     *
     * Deliberately requires the unit at the **start** of the element and a digit immediately after
     * it, so an ordinary value cell (`13g`), a fused word, or a unit with trailing punctuation is
     * untouched.
     */
    private val UNIT_THEN_QUANTITY = Regex(
        "^(?:${BasisUnitSpellings.alternation})(?=\\d)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A one- or two-character prefix, ending in a separator, immediately followed by the quantity of
     * a basis phrase — the shape a damaged `per` takes when OCR keeps only its punctuation.
     *
     * ### The measured shape
     *
     * The Nordic/Baltic table (`docs/Scan Evidence 01-09-26/20260901-211619-534`) prints
     * `per 100 g` and `per 9 g` above its two value columns. ML Kit returned
     *
     * ```
     * 'o/100'  [1240..1343]
     * 'g|'     [1351..1402]
     * 'o/9g'   [1434..1528]
     * ```
     *
     * — the `per` reduced to `o/` in both. The quantity is welded to that debris, so `100` is not a
     * standalone token, the second column's `9g` is invisible to the quantity walk, and the span
     * matched one header reaching across both columns. The per-100 anchor landed at x=1439.5, over
     * the *9 g portion* values, and the printed 59,2 g cell bound to nothing.
     *
     * ### Why this is safe, and deliberately not a general repair
     *
     * It only ever **splits** an element, and every resulting piece must still be recognised by the
     * ordinary vocabulary — `100 g` and `9 g` are read exactly as they would have been had ML Kit
     * emitted the space. No basis is created: the `o/` debris is discarded as the unrecognised token
     * it is, not rewritten into `per`.
     *
     * It is reachable only from [splitRunTogetherBases], which runs on rows already classified
     * `HEADER`, so it cannot fire on prose, an ingredient list or a value row. The prefix is bounded
     * to two characters ending in a separator, so an ordinary word followed by a number
     * (`Energie1454`) does not match.
     */
    private val DAMAGED_CONNECTIVE = Regex("^\\p{L}{0,2}[/|:.]+(?=\\d)", RegexOption.IGNORE_CASE)

    /**
     * The same damaged `per`, in the capture where the separator itself was misread as a letter.
     *
     * ### The measured shape
     *
     * `docs/Scan evidence 01-09-26 3rd testr/20260901-225829-154/` is the same Baltic table one
     * capture later. Its header came back as
     *
     * ```
     * 'o/100g|'  [1225,1423,1388,1481]
     * 'of9g'     [1436,1423,1530,1481]   <- 'o/9g' with the slash read as an 'f'
     * '%Rí'      [1554,1423,1624,1481]
     * ```
     *
     * [DAMAGED_CONNECTIVE] requires an actual separator character, so `of9g` matched nothing, the
     * `9 g` portion column was invisible, and the span walk emitted **one** `PER_100_G` column at
     * x=1377.5 spanning both. The right answer still came out — `59,2g` at x=1308.5 happens to be
     * nearer that midpoint than `5,4g` at x=1485.5 — which is precisely why this had to be fixed: a
     * reading that is correct because one cell was closer than another is correct by luck, and the
     * luck runs out on the capture where the framing shifts.
     *
     * ### Why a bare two-letter prefix is safe here
     *
     * The prefix must be **at most two letters** and the remainder must be a *complete* basis phrase
     * — a quantity fused to a unit this app knows. That is a narrow shape: an ordinary word ending in
     * digits (`Energie1454`, `E471`) has a longer prefix, and a two-letter word followed by a bare
     * number (`kj 129`) is not fused. Every piece is still read by the ordinary vocabulary, so
     * nothing is accepted here that a spaced header would not have been.
     *
     * Kept as a separate pattern rather than relaxing [DAMAGED_CONNECTIVE] so the two shapes stay
     * separately readable and separately testable: one is punctuation surviving, the other is
     * punctuation being replaced.
     */
    private val DAMAGED_CONNECTIVE_LETTER = Regex(
        "^\\p{L}{1,2}(?=\\d{1,4}\\s*(?:${BasisUnitSpellings.alternation})(?:$|\\s))",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The elements at [start] forming a `<quantity> <unit>` header whose quantity is not 100, or
     * null when the position does not begin one.
     *
     * ### Why a non-100 quantity column must be named rather than ignored
     *
     * `NutritionBasis` has two members, per 100 g and per 100 ml. A label printing a `250 ml` or
     * `9 g` column is stating a real basis this app cannot represent. The only safe handling is to
     * record that a column exists at that position with an unestablished meaning, so its cells are
     * refused — the same treatment [NutritionColumnKind.UNKNOWN] already gives a position whose
     * header did not resolve.
     *
     * Ignoring it instead is what produced the measured 2.6x error: the tokens were absorbed into
     * the neighbouring per-100 span, and the 250 ml value inherited the 100 ml basis.
     *
     * ### Deliberately narrow
     *
     * Requires a quantity immediately followed by a basis unit, optionally introduced by a
     * connective (`per 250 ml`). A bare number heads no column, and a quantity followed by anything
     * other than a unit is not a basis statement — so an ordinary value cell, a percentage and a
     * serving word are all untouched. `100` itself is excluded, since that is the per-100 case the
     * ordinary vocabulary handles.
     */
    private fun offBasisQuantitySpan(elements: List<OcrElement>, start: Int): List<OcrElement>? {
        var index = start
        val span = mutableListOf<OcrElement>()

        // A quantity introduced by a serving word belongs to that serving header — `per portie 50 g`
        // is one column whose header states its own weight, not a serving column followed by a
        // separate 50 g column. [NutritionTableInterpreter.headerWeightDescriptor] reads exactly that
        // shape, so claiming the weight here would destroy the serving candidate it builds.
        //
        // Measured: without this, the grated-cheese fixture's `per portie 50 g` lost its serving
        // column entirely and the printed per-portion figure went with it.
        if (isServingWord(elements.getOrNull(index - 1)?.text.orEmpty())) return null

        if (isConnective(elements.getOrNull(index)?.text.orEmpty())) {
            span += elements[index]
            index++
        }

        val quantityElement = elements.getOrNull(index) ?: return null
        val rawQuantityText = NutritionTerminology.normalize(quantityElement.text)

        // ## The connective may be fused to its own quantity (eleventh session)
        //
        // `20260903-143023-402` prints `per 100g` and `per 45g`, and ML Kit returned the second
        // header as the single token **`per45`** followed by `g` — the space lost, exactly as
        // `PourPerlPro 100g:` and `o/9g` were welded in earlier sessions. That is a spelling of the
        // shape this function exists for, not a different shape.
        //
        // Without recognising it the token is neither all-digits nor quantity-fused, so the rule
        // returned null, no column was created at x=1378, and both printed cells fell to the single
        // surviving per-100 column. Measured on the fixture: one column `PER_100_G @ 1152` covering
        // both, and the label read `NotFound` — the printed `67,0 g / 100 g` lost. On a
        // differently-reconstructed capture of the same package the same geometry instead produced
        // `Ambiguous [67.0, 30.2]`, offering the per-45-g figure under a per-100-g basis.
        //
        // **Only the connective is stripped, and only when a real connective is what precedes the
        // digits.** `45g` keeps its existing fused handling, `100` is still the per-100 case, and a
        // token that merely starts with letters (`E202`, `b12`) matches nothing here because the
        // prefix must be a connective this classifier already recognises on its own.
        val fusedConnective = FUSED_CONNECTIVE_QUANTITY.matchEntire(rawQuantityText)
            ?.takeIf { isConnective(it.groupValues[1]) }
        val quantityText = fusedConnective?.groupValues?.get(2) ?: rawQuantityText

        // The quantity and its unit may be fused into one token ("250ml", "9g") or split across two.
        val fused = OFF_BASIS_FUSED.matchEntire(quantityText)
        if (fused != null) {
            if (isPerHundredQuantity(fused.groupValues[1])) return null
            return (span + quantityElement).takeIf { it.isNotEmpty() }
        }

        // `l00` must be excluded here for the same reason `100` is, now that [RowClassifier] and
        // [ColumnClassifier] both read it as the per-100 quantity. Without this the off-basis rule
        // would claim a header the per-100 vocabulary is about to claim, and emit it as UNKNOWN —
        // turning the letter-ell recovery into a different way of losing the same column.
        // `l00` must be excluded here for the same reason `100` is, now that [RowClassifier] and
        // [ColumnClassifier] both read it as the per-100 quantity. Asked **before** the all-digits
        // test, because `l00` is not all digits: without this the token would fall through, head no
        // column at all, and the letter-ell recovery would be undone by a different route.
        if (isPerHundredQuantity(quantityText)) return null
        if (!quantityText.all { it.isDigit() } || quantityText.isEmpty()) return null

        // The **quantity** was recognised twice, overlapping — the same duplication the unit shows
        // below, one token earlier.
        //
        // Measured on `225530-249`, whose header row reconstructs as `100 ml 250 250 m ml9`:
        //
        // ```
        // '250' [1099,1331,1184,1409]   b18
        // '250' [1102,1334,1193,1416]   b19    <- the same printed glyphs, read again
        // 'm'   [1183,1338,1242,1413]
        // ```
        //
        // Without skipping the repeat, the element after the quantity is another quantity rather than
        // a unit, every check below fails, and the span walk swallows both `250`s into the per-100
        // column — which is what left that capture with a single `PER_100_ML '100 ml 250 250'`
        // spanning both printed columns after the truncated-unit fix was already working.
        //
        // Same test as the unit case, and the same reasoning: two *columns* are horizontally
        // separated, so an overlapping element carrying identical text is one printed token read
        // twice. Identical text is required, so two genuinely different quantities side by side are
        // untouched.
        var quantityEnd = index
        while (true) {
            val repeat = elements.getOrNull(quantityEnd + 1) ?: break
            if (NutritionTerminology.normalize(repeat.text) != quantityText) break
            if (!overlapsHorizontally(elements[quantityEnd].box, repeat.box)) break
            quantityEnd++
        }
        val quantitySpan = elements.subList(index, quantityEnd + 1)

        val unitElement = elements.getOrNull(quantityEnd + 1) ?: return null
        val unitText = NutritionTerminology.normalize(unitElement.text)
        if (unitText in BasisUnitSpellings.gram || unitText in BasisUnitSpellings.millilitre) {
            return span + quantitySpan + unitElement
        }

        // The unit was truncated — `250 m` where the package prints `250 ml`.
        //
        // ### The measured shape, and why an earlier fix was not enough
        //
        // Two captures of the same drink, both with the second column's unit damaged:
        //
        // ```
        // 225654-501:  '250' [1297,1432,1373,1512]   'm' [1384,1439,1412,1515]     <- nothing follows
        // 222300-297:  '250' [915,1415,1006,1496]    'm' [1023,1418,1047,1497]
        //                                            'ml' [1023,1426,1074,1510]    <- read twice
        // ```
        //
        // The 2026-09-01 fix required the *second* shape: a full unit recognised again beside the
        // fragment. On `225654-501` there is no second recognition — the `l` is simply gone — so the
        // guard never fired, the per-100 span swallowed `250`, and **one** column was emitted
        // (`PER_100_ML 'PER: 100 ml 250' @ x=1254`) covering both printed columns. The 250 ml cell
        // `13g` then bound to it and the assisted screen offered it as `/100 ml` — the 2.6x error,
        // reproduced through the recovery path.
        //
        // ### The rule, and why a fragment alone is sufficient evidence
        //
        // Terminating the previous basis span does not require knowing what this column *is*. It
        // requires knowing that a **different** column starts here — and a quantity that is not 100,
        // followed by something that begins like a unit, is that. The column is then emitted
        // [NutritionColumnKind.UNKNOWN], which claims a position and no meaning, so nothing can be
        // read from it either way. A refusal costs a lost cell; the alternative cost a wrong basis.
        //
        // `m` is **not** added to the unit spellings and must never be: a bare `m` is metres, that
        // list is shared with every stage, and `ServingSizeParser` would inherit it. This asks a
        // narrower question — "is this token a proper prefix of a unit this app knows?" — in the one
        // place where the answer only ever produces a refusal.
        //
        // Requires a *proper* prefix (`m` of `ml`, `g` is already a whole unit and handled above), so
        // an unrelated token cannot terminate a span: `250` `x` is not a basis statement and the
        // walk continues, exactly as before.
        if (!isUnitFragment(unitText)) return null
        if (!adjacentOnTheRight(quantitySpan.last().box, unitElement.box)) return null

        // A second, complete recognition of the same printed unit sits on top of the fragment
        // (`222300-297` above). Consuming it too keeps it out of the following span, where it would
        // otherwise start a new walk of its own.
        val secondUnit = elements.getOrNull(quantityEnd + 2)
        val secondText = secondUnit?.let { NutritionTerminology.normalize(it.text) }.orEmpty()
        val isRepeatOfSameToken = secondUnit != null &&
            (secondText in BasisUnitSpellings.gram || secondText in BasisUnitSpellings.millilitre) &&
            secondText.startsWith(unitText) &&
            overlapsHorizontally(unitElement.box, secondUnit.box)

        return if (isRepeatOfSameToken) {
            span + quantitySpan + unitElement + secondUnit!!
        } else {
            span + quantitySpan + unitElement
        }
    }

    /**
     * Whether [normalizedText] is a **proper** prefix of some unit spelling this app knows — the
     * shape a unit takes when OCR clipped its final glyphs.
     *
     * Deliberately not part of [BasisUnitSpellings]. That list answers "is this a unit?" for every
     * stage in the app, including [app.justthecarbs.domain.ServingSizeParser] and the value-cell
     * accompaniment check, and a bare `m` is metres. This answers the narrower question "does this
     * token *begin* like a unit?", and it is asked in exactly one place — deciding that a new column
     * starts here, which only ever produces an [NutritionColumnKind.UNKNOWN] column whose cells are
     * refused. A false positive costs a lost cell; there is no path by which it can name a basis.
     *
     * Proper prefix, so a complete unit is not matched here (the caller has already handled those)
     * and the empty string is not a prefix of everything.
     */
    private fun isUnitFragment(normalizedText: String): Boolean {
        if (normalizedText.isEmpty()) return false
        val all = BasisUnitSpellings.gram + BasisUnitSpellings.millilitre
        if (normalizedText in all) return false
        return all.any { it.length > normalizedText.length && it.startsWith(normalizedText) }
    }

    /**
     * Whether [unit] sits immediately right of [quantity], close enough to be its unit rather than a
     * token from the next column along.
     *
     * Required only on the truncated-unit path. A complete unit spelling is its own evidence — `250`
     * followed by `ml` states a basis wherever the `ml` sits on the row — but a one-character
     * fragment is weak enough that adjacency has to carry part of the claim, or a stray `m` anywhere
     * to the right of a quantity would terminate a span.
     *
     * The bound is in text heights, the convention the row builder and
     * [CarbUnitAccompaniment.isAccompanied] both use, so it survives every capture resolution.
     * Measured on `225654-501`: `250` ends at 1373 and `m` begins at 1384, an 11 px gap against an
     * 80 px glyph height — about 0.14, far inside the bound and far below any column gap.
     */
    private fun adjacentOnTheRight(quantity: OcrBox, unit: OcrBox): Boolean {
        val height = quantity.height.toDouble()
        if (height <= 0.0) return false
        // The unit must **begin** to the right of where the quantity begins — that is the reading
        // order this whole rule rests on — but the two boxes may overlap slightly. ML Kit's boxes are
        // glyph hulls, not typographic advances, and on `225530-249` the duplicated `250`
        // [1102..1193] is followed by `m` starting at 1183: an 11 px overlap between two tokens the
        // package prints side by side. Requiring a non-negative gap rejected that.
        if (unit.left <= quantity.left) return false
        val gap = unit.left - quantity.right
        return gap <= height * MAX_UNIT_FRAGMENT_GAP_IN_HEIGHTS
    }

    /** See [adjacentOnTheRight]. Well above the measured 0.14, well below a column gap. */
    private const val MAX_UNIT_FRAGMENT_GAP_IN_HEIGHTS = 1.2

    /**
     * Whether two boxes occupy overlapping horizontal space.
     *
     * Kept local to this file rather than added to [OcrBox]: it exists for one question — "are these
     * two recognitions of the same printed token, or the start of a different column?" — and a
     * general geometry helper would invite use where the columns-are-separated reasoning above does
     * not apply.
     */
    private fun overlapsHorizontally(a: OcrBox, b: OcrBox): Boolean =
        minOf(a.right, b.right) > maxOf(a.left, b.left)

    /**
     * Whether [text] names a serving — `portie`, `stuk`, `serving`, `portion`.
     *
     * Uses the same two vocabularies the serving-column classifier does, so a word that heads a
     * serving column and a word that shields a following weight cannot drift apart.
     */
    private fun isServingWord(text: String): Boolean {
        val normalized = NutritionTerminology.normalize(text).trim()
        if (normalized.isEmpty()) return false
        if (NutritionTerminology.servingTerms.any { NutritionTerminology.containsTerm(normalized, it) }) {
            return true
        }
        return normalized.split(' ').lastOrNull()?.let { ServingSizeParser.kindForWord(it) != null } == true
    }

    /** A quantity fused to its unit: `250ml`, `9g`. */
    private val OFF_BASIS_FUSED =
        Regex("(\\d{1,4})\\s*(?:${BasisUnitSpellings.alternation})", RegexOption.IGNORE_CASE)

    /**
     * A connective fused to its quantity: `per45`, `pro250`.
     *
     * The letters are captured rather than matched against a fixed list so the caller can check them
     * with [isConnective], which is the same test the split spelling already passes through — one
     * definition of what introduces a basis phrase rather than two that can drift. Requiring the
     * whole token to be `<letters><digits>` keeps ordinary label text out: `E202` fails because
     * `e` is not a connective, and a value like `0,5` has no leading letters at all.
     */
    private val FUSED_CONNECTIVE_QUANTITY = Regex("([a-z]{2,6})(\\d{1,4})", RegexOption.IGNORE_CASE)

    /**
     * Whether [quantityText] is the per-100 quantity, in any spelling this parser accepts.
     *
     * Delegates to [NutritionTerminology.PER_100_QUANTITY_PATTERN] rather than restating it, so the
     * off-basis rule's *exclusion* cannot drift from what the per-100 vocabulary *includes*. If
     * those two disagreed, a header would be claimed by neither and the column would be lost — the
     * same shape as the defect this recovery exists for.
     */
    private fun isPerHundredQuantity(quantityText: String): Boolean =
        PER_100_QUANTITY_SPELLINGS.matches(quantityText)

    private val PER_100_QUANTITY_SPELLINGS =
        Regex(NutritionTerminology.PER_100_QUANTITY_PATTERN, RegexOption.IGNORE_CASE)

    /**
     * The box of the tokens within [span] that actually state the basis, or null when none can be
     * isolated and the caller should keep the whole span.
     *
     * ### What counts as a basis token
     *
     * The trailing run of elements that carries the quantity and its unit — `100g`, `100 ml`,
     * `portie/`, `serving` — rather than the descriptive noun that introduces it. Found by walking
     * **backwards** from the end of the span, because that is where the basis is printed in every
     * layout this repo has measured: a header reads `Voedingswaarde/Valeur nutritionnelle/ 100g`,
     * never `100g Voedingswaarde`. Walking backwards also means a long multilingual preamble of
     * unknown length costs nothing — it is simply never reached.
     *
     * The walk stops at the first element that contributes nothing to the basis, so a connective is
     * *included* (`per 100 g` anchors across all three, since `per` sits over the same column) while
     * a descriptive noun ends it.
     *
     * ### Why null rather than a guess
     *
     * When no trailing element is recognisable as part of a basis — a serving header spelled with a
     * word this table does not list, say — the honest answer is that this function cannot narrow the
     * anchor, and the caller keeps the span union it already had. That is the pre-existing behaviour,
     * so a failure here can only ever be as bad as before the fix, never worse.
     */
    private fun basisAnchorBox(span: List<OcrElement>): OcrBox? {
        var index = span.size - 1
        var box: OcrBox? = null
        // Set once the walk has consumed the quantity of a per-100 phrase. Reaching a *second*
        // quantity means the walk has stepped out of this column's basis phrase and into the
        // neighbouring column's, so it stops there.
        //
        // **Measured, and the reason for this bound.** The green drink's header
        // (`docs/Scan Evidence 01-09-26/20260901-211417-935`) is three elements — `100`, `ml250`,
        // `ml` — every one of which states a basis on its own. The unbounded walk therefore took all
        // three and anchored the column at x=1199.5, the centre of the *250 ml* values, so the
        // 100 ml cell at x=941 and the 250 ml cell at x=1171 both bound to one column and the parser
        // reported them as two competing readings of the same basis. The 250 ml figure then reached
        // the user labelled `/100 ml` — wrong by a factor of 2.6.
        //
        // The same shape defeats the multilingual table, whose header `o/100 g| o/9g RE` put the
        // per-100 anchor at x=1439.5, over the 9 g portion column.
        var consumedQuantity = false

        while (index >= 0) {
            val element = span[index]
            if (!statesBasis(element.text)) break

            val quantity = statesQuantity(element.text)
            if (quantity && consumedQuantity) break

            // A token separated from what it introduces by a wide gap is not printed over the same
            // column, whatever it says.
            //
            // **Measured on `225617-066`.** That drink's header row is `PER: 100 ml 250 ml`, where
            // `PER:` sits at x=263 in the *label* column and `100` begins at x=984 — a 619 px gap
            // across a 1684 px frame. `per` is a connective, so the walk accepted it, and the anchor
            // landed at **x=707**, midway between the label column and the values. The printed
            // `0.5g` cell at x=1076.5 was then 369 px from its own column and only 222 px from the
            // 250 ml column, so the interpreter bound the correct value to the **wrong** column and
            // rejected it; the reading survived only because the prose fallback happened to catch it.
            //
            // Bounded in text heights rather than pixels, the convention every geometric rule in
            // this parser uses, so it survives each capture resolution. A genuine `per 100 g` printed
            // over its column has its words a fraction of a height apart and is unaffected.
            if (box != null && !withinOneWordGap(element.box, box)) break

            box = box?.union(element.box) ?: element.box
            if (quantity) consumedQuantity = true
            index--
        }

        return box
    }

    /**
     * Whether [earlier] sits close enough to the left of [later] to be part of the same printed
     * phrase rather than a word in a different column.
     *
     * See [basisAnchorBox] for the measurement that set this. The bound is generous — several words'
     * worth — because a basis phrase legitimately contains a connective and a quantity with ordinary
     * word spacing between them; it exists only to reject a gap the width of a table column.
     */
    private fun withinOneWordGap(earlier: OcrBox, later: OcrBox): Boolean {
        val height = maxOf(earlier.height, later.height).toDouble()
        if (height <= 0.0) return true
        val gap = later.left - earlier.right
        return gap <= height * MAX_BASIS_PHRASE_GAP_IN_HEIGHTS
    }

    /** See [withinOneWordGap]. Several word spaces, far below a column gap. */
    private const val MAX_BASIS_PHRASE_GAP_IN_HEIGHTS = 2.0

    /**
     * Whether [text] carries the numeric quantity of a basis phrase — the `100` of `per 100 g`, or
     * the `9g` of a `9 g` portion.
     *
     * A bare unit (`g`, `ml`), a connective (`per`) and a serving word (`portie`) all state a basis
     * without stating a quantity, so they may be walked over freely; a second quantity is what marks
     * the boundary between two adjacent column headers.
     */
    private fun statesQuantity(text: String): Boolean =
        NutritionTerminology.normalize(text).any { it.isDigit() }

    /**
     * Whether one element is part of a basis statement rather than the prose introducing it.
     *
     * Accepts a quantity (`100`, `100g`), a bare unit (`g`, `ml`), a connective (`per`, `pour`), or a
     * serving word (`portie`, `serving`, `stuk`) — the vocabularies already used to classify the span
     * itself, so this cannot recognise a basis the classifier would not.
     */
    private fun statesBasis(text: String): Boolean {
        val normalized = NutritionTerminology.normalize(text).trim()
        if (normalized.isEmpty()) return false
        if (normalized in CONNECTIVES) return true
        if (PER_100.containsMatchIn(normalized)) return true
        if (NutritionTerminology.servingTerms.any { NutritionTerminology.containsTerm(normalized, it) }) {
            return true
        }
        val words = normalized.split(' ').filter { it.isNotBlank() }
        // A bare quantity, a bare unit, or a countable serving word ("portie", "stuk"). Anything
        // else — a descriptive noun like "voedingswaarde" — ends the backward walk.
        // The shared spelling table, so a unit this app recognises in a `serving_size` string and a
        // unit it recognises in a column header can never drift apart.
        return words.all { word ->
            word.all { it.isDigit() } ||
                word in BasisUnitSpellings.gram ||
                word in BasisUnitSpellings.millilitre ||
                ServingSizeParser.kindForWord(word) != null
        }
    }

    /** A word that only ever introduces a header, never ends one. */
    private fun isConnective(text: String): Boolean =
        NutritionTerminology.normalize(text) in CONNECTIVES

    /**
     * An element that is a percent sign and nothing else, in the tokenizations ML Kit produces for a
     * percent-column header: "%", "% RI", "%RI*". A token carrying a digit is a cell, not a header.
     */
    private fun isBarePercent(text: String): Boolean = BARE_PERCENT_HEADER.matches(text.trim())

    /**
     * The kind this span names, or null if it names none — or more than one.
     *
     * Ambiguity must be a rejection, not a first-match win. A greedy longest-span pass offers
     * "per 100 g %RI" as one span, which contains both vocabularies; picking whichever was checked
     * first silently merged two real columns into one and lost the grams column entirely. Returning
     * null makes the caller try shorter spans, which is what separates them.
     */
    private fun kindOf(normalizedSpan: String): NutritionColumnKind? {
        val matches = buildList {
            if (REFERENCE_PERCENT.containsMatchIn(normalizedSpan)) add(NutritionColumnKind.REFERENCE_PERCENT)
            // Every per-100 match, not just the first: a span reading "per 100 g per 100 ml" covers
            // two real columns, and taking the first match would emit one column spanning both —
            // losing the ml column entirely and putting its centre between the two.
            PER_100.findAll(normalizedSpan).forEach { match ->
                add(
                    if (match.groupValues[1] in NutritionTerminology.millilitreUnits) {
                        NutritionColumnKind.PER_100_ML
                    } else {
                        NutritionColumnKind.PER_100_G
                    },
                )
            }
            if (isServingSpan(normalizedSpan)) add(NutritionColumnKind.PER_SERVING)
        }
        return matches.singleOrNull()
    }

    /**
     * A per-serving column header: the generic "per serving"/"per portion", or a countable unit
     * ("per slice", "per 2 slices").
     *
     * The countable case reuses [ServingSizeParser]'s own unit-word table rather than a second list,
     * so the vocabulary a `serving_size` string is parsed with and the vocabulary a column header is
     * recognised with cannot drift apart.
     */
    private fun isServingSpan(normalizedSpan: String): Boolean {
        if (NutritionTerminology.servingTerms.any { NutritionTerminology.containsTerm(normalizedSpan, it) }) {
            return true
        }
        val words = normalizedSpan.split(' ').filter { it.isNotBlank() }
        // Only a "per <unit>" shape counts. A bare unit word elsewhere in the table is not a header.
        if (words.size < 2 || words.first() !in CONNECTIVES) return false
        return ServingSizeParser.kindForWord(words.last()) != null
    }

    /**
     * Recovers a percent column from the cells themselves when its header is missing or unreadable.
     * Requires at least two percent-shaped cells sharing an x position, so a single stray "17%" in
     * running text cannot invent a column.
     */
    private fun percentColumnsFromCells(rows: List<LogicalRow>, documentWidth: Int): List<NutritionColumn> {
        val percentCells = rows
            .filter { RowClassifier.classify(it) != NutritionRowKind.HEADER }
            .flatMap { row ->
                // Both tokenizations, decided once in PercentAssociation rather than re-tested here
                // against the element's own text. A split "3" + "%" is the shape a bare-text filter
                // misses, and it is the shape that leaves a percentage column unrecovered — exactly
                // when its header was also lost, which is the only case this fallback exists for.
                val indices = PercentAssociation.percentElementIndices(row, documentWidth)
                indices.mapNotNull { index ->
                    row.elements.getOrNull(index)?.takeIf { cell -> cell.text.any { it.isDigit() } }
                }
            }

        if (percentCells.size < MIN_PERCENT_CELLS) return emptyList()

        val tolerance = documentWidth * NEAR_COLUMN_FRACTION
        val clusters = mutableListOf<MutableList<OcrElement>>()
        percentCells.sortedBy { it.box.centerX }.forEach { cell ->
            val cluster = clusters.lastOrNull()
            if (cluster != null && abs(cluster.last().box.centerX - cell.box.centerX) <= tolerance) {
                cluster += cell
            } else {
                clusters += mutableListOf(cell)
            }
        }

        return clusters.filter { it.size >= MIN_PERCENT_CELLS }.map { cluster ->
            // The band is grown by one row pitch either side of the cells themselves, so a table
            // whose percentages OCR'd on only some of its rows still protects the rows between and
            // immediately around them. A pitch is measured from the cells' own text height, so it
            // scales with the image rather than assuming a resolution.
            val margin = cluster.map { it.box.height }.average() * PERCENT_BAND_MARGIN_IN_HEIGHTS
            val top = cluster.minOf { it.box.top } - margin
            val bottom = cluster.maxOf { it.box.bottom } + margin
            NutritionColumn(
                kind = NutritionColumnKind.REFERENCE_PERCENT,
                headerBox = null,
                centerX = cluster.map { it.box.centerX }.average(),
                headerText = "",
                verticalExtent = top.toInt()..bottom.toInt(),
            )
        }
    }

    /** Longest header phrase worth trying, e.g. "per 100 ml". */
    private const val MAX_HEADER_SPAN = 4

    /** Two cells make a column; one is a stray. */
    private const val MIN_PERCENT_CELLS = 2

    /** How close two x-centres must be to count as the same column. */
    private const val NEAR_COLUMN_FRACTION = 0.08

    /**
     * How far beyond its own cells a recovered percent column still claims, in text heights.
     *
     * One and a half heights is about one row pitch, which is what lets a column recovered from the
     * rows above and below a gap keep covering the row in between when its own percentage failed to
     * recognise. It is not enough to reach a separate printed block, which is the whole point.
     */
    private const val PERCENT_BAND_MARGIN_IN_HEIGHTS = 1.5

    /** Words that introduce a column header. One shared list — see NutritionTerminology. */
    private val CONNECTIVES = NutritionTerminology.connectives

    /**
     * `100 g`, `100g`, `100 gram`, `100 ml`, `100 milliliter` — every spelling
     * [NutritionTerminology.basisUnitAlternation] carries, longest-first so `gram` is preferred over
     * the `g` that prefixes it. The trailing boundary is what stops `g` matching inside `gram`.
     */
    /**
     * A per-100 basis phrase.
     *
     * The trailing boundary is a **lookahead**, not a consuming match, so two adjacent phrases are
     * both found. Consuming the separator meant `100 g 100 ml` reported only its first match — the
     * space after `g` was eaten, so the following `100 ml` could no longer satisfy `(?:^|\s)100` —
     * and [kindOf] therefore saw one basis where two are printed. That is the opposite of the
     * ambiguity refusal it exists to perform: instead of declining, it emitted a single PER_100_G
     * column spanning both, which would bind a millilitre value to a gram basis.
     *
     * Only reachable once run-together headers are split apart; on a spaced header the two phrases
     * were always separate elements. Pinned by `RunTogetherHeaderTest`.
     */
    // The quantity spelling is shared with [RowClassifier]'s own PER_100 rather than written out
    // here. Two stages must agree that a row is a header before either can act on it, so a spelling
    // added to one and not the other silently does nothing — measured in the Dutch pass, where
    // fixing this regex alone changed no outcome at all.
    private val PER_100 = Regex(
        "(?:^|\\s)${NutritionTerminology.PER_100_QUANTITY_PATTERN}" +
            "\\s*(${NutritionTerminology.basisUnitAlternation})(?=$|\\s)",
    )
    private val REFERENCE_PERCENT = Regex("(?:^|\\s)(?:ri|dv|gda|reference intake|daily value)(?:$|\\s)")

    /** Runs against RAW element text — normalization would strip the "%" these depend on. */
    private val BARE_PERCENT_HEADER = Regex("^%\\s*(?:ri|dv|gda)?\\*?$", RegexOption.IGNORE_CASE)
}
