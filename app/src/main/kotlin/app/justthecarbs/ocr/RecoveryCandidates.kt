package app.justthecarbs.ocr

import app.justthecarbs.domain.BasisProvenance
import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.CarbReading
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.ResultFormatter
import java.math.BigDecimal
import kotlin.math.abs

/**
 * Builds the choices the recovery screen offers, each already carrying the basis it was printed
 * under.
 *
 * ## The defect this replaces
 *
 * Recovery used to work in two steps: *pick a number*, then *pick a basis*. A device recording
 * showed the consequence. On a green drink printing `0,5 g / 100 ml` and `1,3 g / 250 ml`, the user
 * tapped the `1.3` — the larger, better-formed number, in the column the eye lands on — and the
 * screen then offered `/100 ml`, because [StatedBasis] had correctly established that the *label*
 * states per 100 ml. Quick Calculation showed **`1.3 g carbs / 100 ml`**: the original 2.6x error,
 * reproduced through the manual path after the automatic path had been fixed.
 *
 * Nothing in that flow was individually wrong. `StatedBasis` reported a true fact about the label;
 * the tap reported a true fact about which glyphs the user meant. The defect is in the composition:
 * *the label's basis is not the tapped cell's basis*, and a two-step flow has no way to know that.
 *
 * ## The rule
 *
 * > A number becomes selectable only together with the basis of the column it sits in. There is no
 * > step at which a bare number exists and a basis is chosen for it.
 *
 * So the choices read `0.5 g / 100 ml` and `1.3 g / 250 ml`, and picking the second one yields
 * `0.52 g / 100 ml` — normalized by [CarbReading.normalizedToPerHundred], never relabelled.
 *
 * ## What is excluded, and why here
 *
 * Percentages are removed at **this** boundary rather than at each call site, because this is the one
 * place every recovery route passes through. A `%RI` figure is not a carbohydrate quantity under any
 * basis, and a device recording showed `9%` and `2` (from `2% DV`) offered as things the user might
 * have meant. Three independent signals are used, because ML Kit tokenizes percentages three ways —
 * see [isPercentage].
 *
 * Cells in [NutritionColumnKind.REFERENCE_PERCENT] columns are excluded for the same reason, and
 * cells in [NutritionColumnKind.UNKNOWN] columns are excluded because an unknown position states no
 * basis at all — there is nothing to label such a candidate with, and offering it unlabelled is the
 * defect above.
 */
object RecoveryCandidates {

    /**
     * One selectable choice: a reading that already knows its own basis, and where it sits.
     *
     * [box] is kept so the screen can highlight the cell on the frozen photograph, which is what
     * lets the user check that the app understood which number they meant.
     */
    data class Candidate(
        val reading: CarbReading,
        val box: OcrBox,
        /** The printed token, so the choice can echo what is actually on the package. */
        val rawText: String,
        /** The row this came from, for the child-row correction — see [childRowMessageFor]. */
        val rowText: String,
    ) {
        /** `0.5 g / 100 ml`, `1.3 g / 250 ml`, `6 g / 18 g serving`. Never a bare number. */
        val label: String
            get() = "${reading.amount.stripTrailingZeros().toPlainString()} g / ${reading.basis.label}"
    }

    /**
     * Every basis-complete choice in [document].
     *
     * Empty when the document resolved no basis anywhere — which is honest: with no basis known
     * there is no labelled choice to offer, and the screen falls back to typing the value in, where
     * the user supplies both halves knowingly.
     */
    fun of(
        document: OcrDocument?,
        disputed: DisputedCandidates = DisputedCandidates.NONE,
    ): List<Candidate> {
        if (document == null || document.elements.isEmpty()) return emptyList()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)
        return contributingRows(rows, columns)
            .flatMap { candidatesOn(it, rows, columns, document, disputed) }
    }

    /**
     * What recovery would offer, and **why each rejected number was rejected**, for the bundle.
     *
     * Diagnostics only: it re-runs the same rules rather than instrumenting them, so nothing on the
     * answer path changes and this cannot alter what the user sees. It exists because a bundle
     * could previously say `weight=none` — a fact about the *parser's* serving candidate — beside a
     * screen showing `From 6 g per 18 g serving`, with nothing to reconcile the two, and because a
     * suppressed number left no trace at all.
     */
    fun explain(
        document: OcrDocument?,
        disputed: DisputedCandidates = DisputedCandidates.NONE,
    ): List<String> {
        if (document == null || document.elements.isEmpty()) return emptyList()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)
        val servingBasis = ServingDeclaration.of(rows)
        val lines = mutableListOf<String>()

        lines += "serving declaration: " + (servingBasis?.label ?: "none")

        contributingRows(rows, columns).forEach { row ->
            val mayDecline = UnitAccompanimentPolicy.mayDeclineBareValues(document, rows)
            val segment = NutrientRowSegments.totalCarbohydrateSegment(row)
            val anchors = CarbohydrateTermAnchor.nutrientAnchors(row)
            val percentIndices = PercentAssociation.percentElementIndices(row, document.width)

            row.elements.forEachIndexed { index, element ->
                val raw = element.text.trim()
                val reason = when {
                    index in percentIndices || isPercentage(raw) -> "percentage"
                    segment != null && !segment.contains(element.box) -> "outside the carbohydrate clause"
                    !CarbohydrateTermAnchor.isCarbohydrateValue(anchors, element.box.right) ->
                        "claimed by another nutrient to its left"
                    valueIn(raw) == null -> null // not value-shaped; not worth a line
                    mayDecline && !CarbUnitAccompaniment.isAccompanied(element, row.elements) ->
                        "states no unit on a label that prints them"
                    contradicted(document, row, element, valueIn(raw)!!) ->
                        "the table's own rows contradict it"
                    basisFor(element, columns, document.width, servingBasis, row.elements) == null ->
                        "no column claims it, so it states no basis"
                    disputed.disputes(
                        valueIn(raw)!!,
                        basisFor(element, columns, document.width, servingBasis, row.elements)
                            ?.let { basisEnumOf(it) },
                    ) -> "a distinct recognition run read it differently (${disputed.describe()})"
                    else -> {
                        // The centralized eligibility decision, reported in the words it was made in
                        // so a bundle says which evidence was missing rather than merely that the
                        // number was withheld. The basis is non-null here — the branch above returns
                        // for a cell no column claims — but it is read safely rather than asserted,
                        // because a reordering of these branches must not turn a diagnostic into a
                        // crash on the evidence path.
                        val value = valueIn(raw)
                        val basis =
                            basisFor(element, columns, document.width, servingBasis, row.elements)
                        if (value == null || basis == null) {
                            null
                        } else {
                            val probe = Candidate(
                                reading = CarbReading(value, basis, BasisProvenance.DECLARED),
                                box = element.box,
                                rawText = raw,
                                rowText = row.text,
                            )
                            (
                                ReadingEligibility.evaluate(document, probe)
                                    as? ReadingEligibility.Verdict.Refused
                                )?.reason
                        }
                    }
                }
                if (reason != null && valueIn(raw) != null) {
                    lines += "  suppressed '$raw' @x=${element.box.centerX.toInt()}: $reason"
                }
            }
        }

        of(document, disputed).forEach { candidate ->
            val reading = candidate.reading
            val normalized = reading.normalizedToPerHundred()
            lines += buildString {
                append("  offered '${candidate.rawText}' -> ${candidate.label}")
                append(" | displayed=${ResultFormatter.quantity(reading.amount)} per ${reading.basis.label}")
                append(" | amount=${reading.amount.toPlainString()} basis=${reading.basis}")
                append(" provenance=${reading.provenance}")
                if (normalized != null && normalized !== reading) {
                    append(" | normalized=${ResultFormatter.quantity(normalized.amount)} per ${normalized.basis.label}")
                    normalized.derivedFrom?.let {
                        append(" derivedFrom=${it.amount.toPlainString()} per ${it.basis.label}")
                    }
                }
                append(" | selectable (awaiting a tap)")
            }
        }
        return lines
    }

    /**
     * Whether a row may contribute a choice at all.
     *
     * Only a row that **names the total carbohydrate** does. Everything else on a package carries
     * numbers — an energy figure, a batch code, a postal address, `8 400 kJ/2 000 kcal` in the
     * reference-intake footnote, `0.12` in an ingredient percentage — and a probe over the nine
     * device captures offered every one of them.
     *
     * That is not merely untidy. The choices this screen shows are a claim that each is a plausible
     * reading of *the carbohydrate figure*, and a list containing `400 g / 100 ml` from a kilojoule
     * footnote makes the real answer harder to find in exactly the moment the user is already
     * struggling. Restricting to the carbohydrate row is the same restriction the "tap the row"
     * interaction rests on, applied to the list the screen opens with.
     *
     * The damaged-label recovery is consulted too, so a capture whose carbohydrate word OCR mangled
     * still offers its own row rather than nothing — that is the whole point of that recovery, and
     * it would be strange for the automatic path to find the row and the assisted path not to.
     */
    private fun contributingRows(
        rows: List<LogicalRow>,
        columns: List<NutritionColumn>,
    ): List<LogicalRow> {
        // classifyAll, so a row past the declaration boundary can never contribute a choice. The
        // sauce's ingredient list would otherwise offer its own numbers as carbohydrate readings.
        val kinds = RowClassifier.classifyAll(rows)
        val totals = rows.filterIndexed { index, _ -> kinds[index] == NutritionRowKind.TOTAL_CARBOHYDRATE }
        if (totals.isNotEmpty()) return totals

        return DamagedCarbohydrateLabel.recoverTotalRowIndex(rows, kinds, columns)
            ?.let { listOf(rows[it]) }
            ?: emptyList()
    }

    /**
     * The choices on the row containing [tappedY] — the "tap the carbohydrate row" interaction.
     *
     * Restricting to the tapped row is the safety property that interaction rests on: a figure from
     * the sugars row two lines below is not in this list, so confirming a value from it cannot
     * silently import an adjacent nutrient's number.
     */
    fun onRowAt(
        document: OcrDocument?,
        tappedY: Int,
        tappedX: Int? = null,
        disputed: DisputedCandidates = DisputedCandidates.NONE,
    ): List<Candidate> {
        if (document == null || document.elements.isEmpty()) return emptyList()
        val rows = LogicalRowBuilder.build(document)
        val row = rowAt(rows, tappedY, tappedX) ?: return emptyList()
        val columns = ColumnClassifier.classify(rows, document.width)
        return candidatesOn(row, rows, columns, document, disputed, tappedX)
    }

    /**
     * The row containing [tappedY], for echoing back what the user selected.
     */
    fun rowTextAt(document: OcrDocument?, tappedY: Int, tappedX: Int? = null): String? {
        if (document == null) return null
        return rowAt(LogicalRowBuilder.build(document), tappedY, tappedX)?.text
    }

    /**
     * Which reconstructed row a tap at [tappedY] means.
     *
     * ## Why first-match-on-the-union-box was wrong
     *
     * A reconstructed row's box is the union of its elements, and on a photographed label those
     * unions **overlap**. Measured on `docs/Scan Evidence 02-09/20260902-085453-023`:
     *
     * ```
     * recovered total row : y = 1772..1948
     * sugars child row    : y = 1865..1980     <- 83 px of overlap
     * ```
     *
     * A tap anywhere in 1865..1948 is inside both. The old rule took whichever came first in
     * document order — the total row — which happens to be the safe answer *here* and is the wrong
     * answer whenever the orders differ. Worse, the reverse case is the dangerous one: a tap the
     * user aimed at the carbohydrate label resolving to the sugars row, which then correctly refuses
     * and leaves the screen unchanged. The recording shows the user tapping again and again.
     *
     * ## The rule
     *
     * **Elements first, unions second.** A tap that lands on the vertical span of an actual
     * recognised element belongs to that element's row — that is what the user aimed at, and element
     * boxes are far tighter than row unions, so they overlap far less. Only when a tap lands on no
     * element at all (the whitespace between columns, say) does the union-box fallback apply, and
     * there the *nearest row centre* is used rather than the first match, so the answer does not
     * depend on document order.
     */
    fun rowAt(rows: List<LogicalRow>, tappedY: Int, tappedX: Int? = null): LogicalRow? {
        // ## Containment first: the box the finger is actually inside owns the tap
        //
        // Everything below this block tests `tappedY` alone, so a row qualified when *any* of its
        // elements spanned the tapped y — however far away in x. On a real label that is not hit
        // testing, and the thirteenth session measured the consequence.
        //
        // `20260904-081055-219` (yoghurt) reconstructs the child clause indented under its parent:
        //
        // ```
        // 'Koolhydraten/Glucides'  [297,1884,802,1988]   TOTAL_CARBOHYDRATE
        // 'waarvan'                [311,1948,486,2015]   CARBOHYDRATE_CHILD
        // ```
        //
        // The two overlap by 40 px vertically **and** 175 px horizontally. A tap at the centre of
        // `waarvan` is inside `waarvan` — and the y-only filter admitted both rows, after which the
        // nutrient-name preference found a carbohydrate word in each and handed the tap to the
        // **total**. So a user deliberately tapping *sugars* was offered the total row's candidates,
        // and `isChildRowAt` answered `false`, so the screen did not even say what had happened.
        //
        // ### Smallest containing box wins
        //
        // When boxes genuinely nest — an indented child label inside its parent's span — the tighter
        // box is the one the user aimed at. Ties (identical areas) fall through to the existing
        // rules rather than being decided arbitrarily by document order.
        //
        // ### Bounded to a located tap
        //
        // With no `tappedX` there is nothing to contain against, so this is skipped entirely and the
        // pre-existing behaviour stands unchanged for every caller that has no horizontal position.
        if (tappedX != null) {
            val containing = rows.mapNotNull { row ->
                row.elements
                    .filter { it.box.contains(tappedX, tappedY) }
                    .minByOrNull { it.box.area() }
                    ?.let { row to it }
            }
            if (containing.isNotEmpty()) {
                val smallest = containing.minOf { it.second.box.area() }
                val winners = containing.filter { it.second.box.area() == smallest }
                if (winners.size == 1) return winners.single().first
            }
        }

        // The elements the tap landed on vertically, across every row. Reached when the finger was
        // in whitespace, when no `tappedX` was supplied, or when two equally tight boxes contain it.
        val onElement = rows.filter { row ->
            row.elements.any { tappedY >= it.box.top && tappedY <= it.box.bottom }
        }
        if (onElement.size == 1) return onElement.single()

        if (onElement.size > 1) {
            // Two elements from different rows genuinely overlap this y — measured on the drink,
            // where `Kolhydraten:` spans 1772..1882 and `Waarvan` spans 1865..1947, a 17 px band in
            // which both are under the finger.
            //
            // **A nutrient-naming element wins.** That is the brief's rule and it is the right one:
            // the user was told to tap the carbohydrate *row*, so they aim at the word, and the word
            // is what disambiguates. A nearest-centre tiebreak was tried first and gets this exact
            // case wrong — at y=1881 the sugars row's `Waarvan` is 25 px away and `Kolhydraten:` is
            // 54 px away, so distance alone hands the tap to sugars, which then correctly refuses
            // and leaves the user tapping again. Distance measures which row's *box* is nearer;
            // it says nothing about which word the finger is on.
            //
            // When [tappedX] is known the horizontal position settles it, because the label column
            // and the value columns do not overlap.
            val onLabel = onElement.filter { row ->
                row.elements.any { element ->
                    tappedY >= element.box.top && tappedY <= element.box.bottom &&
                        (tappedX == null || (tappedX >= element.box.left && tappedX <= element.box.right)) &&
                        namesANutrient(element.text)
                }
            }
            if (onLabel.size == 1) return onLabel.single()

            val considered = onLabel.ifEmpty { onElement }
            return considered.minByOrNull { row ->
                row.elements
                    .filter { tappedY >= it.box.top && tappedY <= it.box.bottom }
                    .minOf { abs(it.box.centerY - tappedY) }
            }
        }

        // No element under the tap: fall back to row unions, nearest centre rather than first match.
        return rows
            .filter { tappedY >= it.box.top && tappedY <= it.box.bottom }
            .minByOrNull { abs(it.box.centerY - tappedY) }
    }

    /**
     * Whether [text] names a nutrient — the label column's own vocabulary.
     *
     * Both the total and the child vocabularies, deliberately. The question is "is this element a
     * nutrient *name*", which decides which row the finger is on; what that row then says is
     * [RowClassifier]'s decision and is unchanged. A tap resolving to the sugars row because the
     * user tapped the word "suikers" is correct behaviour — it is then refused, with the message
     * that says why.
     */
    private fun namesANutrient(text: String): Boolean {
        val normalized = NutritionTerminology.normalize(text)
        return NutritionTerminology.carbohydrateTerms.any {
            NutritionTerminology.containsTerm(normalized, it)
        } || NutritionTerminology.exclusionTerms.any {
            NutritionTerminology.containsTerm(normalized, it)
        } ||
            // A word OCR damaged at the head — `Kolhydraten:` for `Koolhydraten:`, which is exactly
            // the element the user taps on the drink. Reusing [DamagedCarbohydrateLabel]'s own
            // suffix test rather than restating it: the rule that decides a row is a damaged
            // carbohydrate row and the rule that decides a tap landed on its label must be the same
            // rule, or a row the app recovers becomes a row the user cannot select.
            DamagedCarbohydrateLabel.statesADamagedCarbohydrateWord(normalized)
    }

    /**
     * Whether the row containing [tappedY] is a child nutrient — sugars, fibre, polyols.
     *
     * The screen uses this to say *"This appears to be sugars. Tap total carbohydrate instead."*
     * rather than showing an empty list, which would read as the tap having missed. It offers no
     * values from that row, which is the unchanged rule: a child nutrient can never supply the
     * total, and the user tapping it does not change what the row says.
     */
    fun isChildRowAt(document: OcrDocument?, tappedY: Int, tappedX: Int? = null): Boolean {
        if (document == null) return false
        val rows = LogicalRowBuilder.build(document)
        val row = rowAt(rows, tappedY, tappedX) ?: return false
        if (RowClassifier.classify(row) != NutritionRowKind.CARBOHYDRATE_CHILD) return false
        if (tappedTheTotalClause(row, tappedX)) return false

        // Without a horizontal position there is nothing to locate the tap against, so the
        // row-level answer stands exactly as it did — the same confinement [tappedTheTotalClause]
        // already applies, and what keeps this change to a *located* tap.
        if (tappedX == null) return true

        // ## A nutrient row states a number; a paragraph that mentions sugar does not
        //
        // [RowClassifier] types a row `CARBOHYDRATE_CHILD` for naming a child nutrient anywhere
        // along it, unconditionally. That is the correct and load-bearing rule for deciding what a
        // row may be *read* as. It is the wrong basis for telling a user where their finger was.
        //
        // Measured on `20260903-212828-161`, where the tortilla's marketing paragraph reconstructs
        // as one row:
        //
        // ```
        // IStorbritannien. edetortila med fuldkom. Ingredienser: Contains stablser naturally
        // (EA15), occurring Room sugars. termperature.dced Packaged ina protective d package
        // ```
        //
        // Eighteen words, no numbers, and the single word `sugars.` types the whole thing a child
        // nutrient row. It occupies a wide band of the photograph, and **every** tap anywhere in it
        // was answered *"This looks like sugars or fibre. Tap the total carbohydrate row instead."*
        // — a correction that is not true and that points nowhere, on the screen the user reached
        // because nothing else had worked.
        //
        // A nutrition row states a quantity. Requiring one costs nothing on a real child row (every
        // one in this repo's corpus carries its value) and removes the false correction on prose.
        // Note what this does **not** do: it does not make the row selectable. [candidatesOn] still
        // returns nothing from a child row, so the tap yields no value either way. All that changes
        // is that the user is told the truth — the tap found nothing — which is what routes them to
        // focused entry instead of to a third identical attempt.
        if (ColumnOwnership.competingCells(row).isEmpty()) return false

        // ## And the message must be about the clause the finger was actually in
        //
        // `20260903-212700-478` reconstructs the pickle's fat line together with a slice of the
        // ingredient list printed beside it:
        //
        // ```
        // aDin, suiker, zout   vetten,   0,2 g   0,06 g
        //        ^x=213         ^x=602    ^1075   ^1313
        // ```
        //
        // `suiker` is in the ingredient list; `vetten` (fat) and both values are the table's. The
        // row types `CARBOHYDRATE_CHILD` — correctly, and unchanged — but a tap on the fat figures,
        // 800 px from the word `suiker`, was answered *"This looks like sugars or fibre"*.
        //
        // Narrowed deliberately to a clause owned by an unrelated nutrient (fat, salt, protein,
        // energy). A tap owned by the total's own clause is [tappedTheTotalClause]'s question and is
        // already answered above — and answered *negatively* on the merged Croatian shape, where a
        // child named before the total means no total clause opens and every tap stays a child tap.
        // That stance is deliberate and is left exactly as it was.
        if (NutrientRowSegments.nutrientClauseKindAt(row, tappedX) == NutritionRowKind.OTHER) {
            return false
        }
        return true
    }

    /**
     * Whether a tap at [tappedX] landed inside this row's **total-carbohydrate clause**.
     *
     * ## The failure this exists for
     *
     * On `docs/Scan Evidence 02-09 4th test/20260902-141703-456` ML Kit merged the carbohydrate
     * declaration and the `waarvan suikers` clause that follows it onto one reconstructed row. The
     * row therefore classifies `CARBOHYDRATE_CHILD` — correctly, and that classification is
     * unchanged — and every tap on it was answered *"This looks like sugars or fibre"*, including a
     * tap on the word `Kohlenhydrate` and on the printed value `8,9` half a screen to the left of
     * the sugars word.
     *
     * This is the same structural mistake the row-level hit-testing already avoids between rows:
     * *classifying a whole reconstructed unit when the user selected an element inside one of its
     * clauses.* The tap carries a horizontal position, which is information the automatic path does
     * not have and which is exactly what separates the two clauses on a printed label.
     *
     * ## What this does and does not license
     *
     * It routes a tap. It establishes no reading, promotes no row and produces no value — every
     * suppression rule in [candidatesOn] still runs on whatever the tap reaches, which is why both
     * truffle captures still offer nothing and route to focused entry instead. A tap inside the
     * sugars clause is still a child-clause tap and is still refused.
     *
     * Without [tappedX] there is nothing to compare, and the row-level answer stands — an unlocated
     * tap on a merged row is treated as before.
     */
    private fun tappedTheTotalClause(row: LogicalRow, tappedX: Int?): Boolean {
        if (tappedX == null) return false
        val clause = NutrientRowSegments.totalCarbohydrateClause(row) ?: return false
        return tappedX >= clause.startX && tappedX < clause.endX
    }

    private fun candidatesOn(
        row: LogicalRow,
        allRows: List<LogicalRow>,
        columns: List<NutritionColumn>,
        document: OcrDocument,
        disputed: DisputedCandidates = DisputedCandidates.NONE,
        tappedX: Int? = null,
    ): List<Candidate> {
        // A child row supplies nothing, whatever the user tapped. This is the same unconditional
        // exclusion [RowClassifier] applies in the automatic path, and it must hold identically here
        // — the tap tells the app which row the user meant, not what that row says.
        //
        // The one exception is a tap the user placed *inside this row's total-carbohydrate clause*
        // on a row that merged two clauses — see [tappedTheTotalClause]. Even then nothing is
        // relaxed: the clause bound below restricts the candidates to that clause, and every
        // suppression rule still runs, which is why the truffle captures still offer nothing.
        val tappedTotalClause = tappedTheTotalClause(row, tappedX)
        if (RowClassifier.classify(row) == NutritionRowKind.CARBOHYDRATE_CHILD && !tappedTotalClause) {
            return emptyList()
        }

        val percentIndices = PercentAssociation.percentElementIndices(row, document.width)
        val servingBasis = ServingDeclaration.of(allRows)

        // The same unit-accompaniment question the automatic path asks, asked once per document.
        //
        // Without it recovery re-offers exactly the value the parser refused. Measured on
        // `225632-622`, where the printed `0,5 g` came back as **`0.59`** — the unit glyph read as a
        // digit. The automatic path declined it (`'0.59' states no unit`) and the recovery list
        // offered `0.59 g / 100 ml`, one tap from storing a figure 18% too high with nothing on
        // screen to say the token was damaged.
        //
        // A recovery screen may show the user *more* than the parser accepted — a value under a
        // basis the app cannot store, say — but it must never present a token the parser identified
        // as corrupted as though it were a clean reading. The user cannot see the difference between
        // `0.5` and `0.59` on a phone screen; the parser can, and did.
        val mayDeclineBareValues = UnitAccompanimentPolicy.mayDeclineBareValues(document, allRows)

        // On a linear panel the carbohydrate clause is bounded by the next nutrient name, so only
        // numbers inside that span may be offered. This is what keeps `Fiber 1 g` — printed on the
        // same recognised row as `Total Carb. 6 g` — out of the list. Null on an ordinary table row,
        // where the whole row is the carbohydrate clause and nothing changes.
        //
        // When the user reached a merged child row by tapping its total clause, that clause is the
        // bound instead — and it is *required*, not optional. Without it a tap on the carbohydrate
        // value of a merged row would put the sugars figure printed further along the same row into
        // the list, which is the sugars-as-total failure this architecture exists to prevent.
        val segment = if (tappedTotalClause) {
            NutrientRowSegments.totalCarbohydrateClause(row)
        } else {
            NutrientRowSegments.totalCarbohydrateSegment(row)
        }

        // A different nutrient named to the left of a number claims it, by reading order — the same
        // rule the automatic path applies, so the two cannot disagree about which nutrient a figure
        // belongs to. It is what stops a wrapped prose row offering the saturated-fat figure.
        val anchors = CarbohydrateTermAnchor.nutrientAnchors(row)

        return row.elements.mapIndexedNotNull { index, element ->
            if (index in percentIndices) return@mapIndexedNotNull null
            if (isPercentage(element.text)) return@mapIndexedNotNull null
            if (segment != null && !segment.contains(element.box)) return@mapIndexedNotNull null
            if (!CarbohydrateTermAnchor.isCarbohydrateValue(anchors, element.box.right)) {
                return@mapIndexedNotNull null
            }

            val value = valueIn(element.text) ?: return@mapIndexedNotNull null
            if (mayDeclineBareValues && !CarbUnitAccompaniment.isAccompanied(element, row.elements)) {
                return@mapIndexedNotNull null
            }
            // A value the table's own other rows refute is not a choice. The automatic path already
            // vetoes it — that is the release-blocking fix from the fourth session — and offering it
            // here would hand the same contradicted figure back one tap later, on a screen whose
            // whole purpose is that the app was not sure. Measured on `085542-213`, where a misread
            // `12` was still listed as `12 g / 100 g` after the veto.
            //
            // Only a genuine contradiction suppresses. "The table could not answer" is the ordinary
            // case on a single-column label and must never remove a legitimate choice.
            if (contradicted(document, row, element, value)) return@mapIndexedNotNull null
            val basis = basisFor(element, columns, document.width, servingBasis, row.elements)
                ?: return@mapIndexedNotNull null

            // A value another *recognition run* read differently is not a choice either.
            //
            // The cross-column check above asks whether the label's own rows refute this cell; this
            // asks whether a second pair of eyes read it differently. They are different questions
            // with different evidence, and only the first was being asked here — which is how
            // `131511` refused `89` as `Conflicted` and then offered `89 g / 100 ml` anyway.
            //
            // Both sides of a dispute go, not just the loser: nothing available to the app says
            // which reading is the printed one.
            if (disputed.disputes(value, basisEnumOf(basis))) return@mapIndexedNotNull null

            val candidate = Candidate(
                reading = CarbReading(value, basis, BasisProvenance.DECLARED),
                box = element.box,
                rawText = element.text.trim(),
                rowText = row.text,
            )

            // The scale question, asked through the **same** object the automatic path asks.
            //
            // ## Why this is no longer an asymmetry
            //
            // This used to refuse only [ScaleAmbiguity.Verdict.Ambiguous], on the reasoning that a
            // human pointing at a number they can see needs no help from a rule about pairing. That
            // is half right: a tap establishes *which row the user meant*, and nothing at all about
            // whether the recognizer read the digits correctly. Measured on the red Lidl label —
            // printed `7,2 g`, recognised `12g` — recovery offered `12 g / 100 g` one tap from the
            // calculator, after the automatic path had already refused exactly that figure.
            //
            // The naive symmetry (refuse `Unsupported` here too) was measured and **deletes the
            // Korean sauce's `6 g / 18 g serving`**, a control that must keep working. What
            // separates the two is not the number but the *provenance of its basis*: the sauce's is
            // a serving the label printed, the red label's is a per-hundred the app inferred. That
            // distinction lives in [ReadingEligibility], which both surfaces now consult, so they
            // cannot disagree about the same candidate.
            if (!ReadingEligibility.evaluate(document, candidate).isEligible) {
                return@mapIndexedNotNull null
            }

            candidate
        }
    }

    /**
     * Whether the table's own other rows **refute** this cell as the carbohydrate figure.
     *
     * Asks [CrossColumnRatioCheck] the same question the automatic path asks, about the same cell,
     * so the two surfaces cannot disagree about which values the label contradicts. Only
     * [CrossColumnRatioCheck.Verdict.Conflicting] suppresses: `NotEnoughEvidence` means the table
     * could not answer, which is the ordinary case on any label printing one value column, and
     * treating it as a refusal would empty the recovery list on exactly the labels that need it.
     *
     * The candidate is constructed here rather than taken from the parser because the parser has no
     * candidate for this cell — that is why the user is on the recovery screen at all.
     */
    private fun contradicted(
        document: OcrDocument,
        row: LogicalRow,
        element: OcrElement,
        value: BigDecimal,
    ): Boolean =
        CrossColumnRatioCheck.check(document, probeFor(row, element, value)) is
            CrossColumnRatioCheck.Verdict.Conflicting

    /**
     * A candidate standing for this cell, so the checks the automatic path runs can be asked about
     * it.
     *
     * Built here rather than taken from the parser because the parser has no candidate for this
     * cell — that is why the user is on the recovery screen at all. One construction shared by every
     * check, so two surfaces cannot end up asking about subtly different candidates.
     */
    private fun probeFor(row: LogicalRow, element: OcrElement, value: BigDecimal) = CarbCandidate(
        sourceLine = row.text,
        label = row.text,
        value = value,
        basis = null,
        score = 0,
        geometry = element.box,
        evidence = emptyList(),
        column = null,
    )

    /** The stored basis enum behind a [CarbBasis], for comparing against a disputed reading. */
    private fun basisEnumOf(basis: CarbBasis): NutritionBasis? =
        (basis as? CarbBasis.PerHundred)?.basis

    /**
     * The basis of the column [element] sits in, or null when no basis is established there.
     *
     * Null — not a guess — for a [NutritionColumnKind.REFERENCE_PERCENT] or
     * [NutritionColumnKind.UNKNOWN] column, and for a cell aligned to no column at all. That is the
     * whole point: a candidate that cannot state its basis is not offered, because offering it
     * unlabelled is the defect this object exists to remove.
     *
     * [servingDeclaration] is the fallback for a **linear panel**, which has no columns: a US
     * Nutrition Facts label prints `Serv. size: 1 Tbsp (18 g)` and then every figure on the panel is
     * per that serving. It applies only when the document resolved no per-100 column anywhere, so a
     * real table's own headers always win.
     *
     * ## The fallback is a document-level statement and stays one
     *
     * A linear panel's declaration genuinely does apply to every figure on it — that is what "Amount
     * per serving:" means — so this branch is correct and is unchanged. It is *not* what produced
     * `72 g / serving` on `20260902-103936-423`: that capture's label prints no serving sentence at
     * all, so [ServingDeclaration.of] returns null there and this branch never ran. That defect was
     * a *column* being claimed by a cell too far from it, and is fixed in [nearestColumn].
     */
    private fun basisFor(
        element: OcrElement,
        columns: List<NutritionColumn>,
        documentWidth: Int,
        servingDeclaration: CarbBasis?,
        rowElements: List<OcrElement>,
    ): CarbBasis? {
        val hasPerHundredColumn = columns.any {
            it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
        }
        if (!hasPerHundredColumn && servingDeclaration != null) return servingDeclaration

        val column = nearestColumn(element, columns, documentWidth, rowElements) ?: return null
        return when (column.kind) {
            NutritionColumnKind.PER_100_G -> CarbBasis.PerHundred(NutritionBasis.PER_100_G)
            NutritionColumnKind.PER_100_ML -> CarbBasis.PerHundred(NutritionBasis.PER_100_ML)
            // A serving column may state its own size in its header ("per portie 50 g"). When it
            // does, that is a convertible quantity; when it does not, it is an honest unknown.
            NutritionColumnKind.PER_SERVING -> quantityInHeader(column.headerText)
                ?: CarbBasis.PerUnknownServing(null)
            // Position known, meaning not established, and a percentage is not a quantity at all.
            NutritionColumnKind.UNKNOWN, NutritionColumnKind.REFERENCE_PERCENT -> null
        }
    }

    /**
     * Nearest column within the same loose tolerance the interpreter binds cells with, so the
     * assisted path and the automatic path cannot disagree about which column a cell is in.
     *
     * ## Why a column must also *claim* the cell
     *
     * The loose tolerance is [NutritionParserThresholds.LOOSE_COLUMN_FRACTION] of the document
     * width — 370 px on a 1684-wide phone capture — and it is deliberately generous so that
     * photographic skew does not detach a cell from its own column. That is right when the cell's
     * column exists. It is dangerous when the cell's column has been **destroyed**.
     *
     * Measured on `docs/Scan Evidence 02-09 2nd test/20260902-103936-423`: ML Kit read the printed
     * `per 100 g` header as `1009`, so no per-100 column resolved at all. The per-100 value `72,0`
     * sits at x≈1082 and the surviving serving column's centre is x=1411 — 329 px away, inside the
     * tolerance. So the printed per-100 figure bound to the serving column and recovery offered
     * **`72 g / serving`**, a basis that number never had, one tap from the calculator.
     *
     * The general rule, keyed on nothing product-specific:
     *
     * > A column may claim a cell only if no **other cell on the same row** sits closer to that
     * > column's centre. A table column is the set of cells printed under it; when two cells on one
     * > row both reach the same column, at most one of them is actually in it, and the nearer one
     * > wins. The loser is claimed by nothing and states no basis.
     *
     * That is what separates the two cases without weakening either. On a healthy table each cell's
     * own column is nearest to it and every cell keeps its basis. On this capture `22,5` is 20 px
     * from the serving column and `72,0` is 329 px, so `22,5` claims it and `72,0` — whose column
     * OCR destroyed — is left honestly unresolved and is suppressed rather than relabelled.
     *
     * This is a *recovery-boundary* rule, not a parser change. The automatic path already refuses
     * this capture (`Total-carbohydrate row found but no usable per-100 cell`); the defect was that
     * recovery was more permissive than the parser it is the fallback for.
     */
    private fun nearestColumn(
        element: OcrElement,
        columns: List<NutritionColumn>,
        documentWidth: Int,
        rowElements: List<OcrElement>,
    ): NutritionColumn? {
        if (columns.isEmpty()) return null
        val loose = maxOf(
            NutritionParserThresholds.MIN_STRICT_COLUMN_PIXELS,
            documentWidth * NutritionParserThresholds.LOOSE_COLUMN_FRACTION,
        )
        val column = columns
            .filter { it.verticalExtent?.let { band -> element.box.centerY.toInt() in band } ?: true }
            .map { it to abs(it.centerX - element.box.centerX) }
            .filter { it.second <= loose }
            .minByOrNull { it.second }
            ?: return null

        // Another numeric cell on this row sitting closer to that column's centre owns it. Only
        // value-shaped elements compete: the nutrient name printed in the label column is not a
        // rival for a value column, and letting it compete would detach legitimate cells.
        //
        // Asked through [ColumnOwnership], which is the same object the automatic path now asks, so
        // the recovery screen and the parser cannot disagree about which cell occupies a column.
        return column.first.takeIf {
            ColumnOwnership.claims(it, element.box, ColumnOwnership.competingCells(rowElements), documentWidth)
        }
    }

    /** A `<quantity> <unit>` stated inside a serving column's own header — `per portie 50 g`. */
    private fun quantityInHeader(headerText: String): CarbBasis? {
        val match = HEADER_QUANTITY.find(NutritionTerminology.normalize(headerText)) ?: return null
        val amount = PortionParser.parse(match.groupValues[1]) ?: return null
        if (amount.signum() <= 0) return null
        val unit = NutritionTerminology.basisUnitFor(match.groupValues[2]) ?: return null
        return CarbBasis.PerQuantity(amount, unit)
    }

    private val HEADER_QUANTITY = Regex(
        "(?<!\\d)(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(${NutritionTerminology.basisUnitAlternation})(?:$|\\s)",
    )

    /** A number, optionally with a fused unit. Anchored, so a word containing digits is not a value. */
    private val VALUE_TOKEN = Regex(
        "^(\\d{1,3}(?:[.,]\\d{1,3})?)\\s*[.,]?\\s*(?:g|gr|gram|grammes?|ml)?[.,;:]?$",
        RegexOption.IGNORE_CASE,
    )

    private fun valueIn(text: String): BigDecimal? {
        val match = VALUE_TOKEN.find(text.trim()) ?: return null
        return match.groupValues[1].replace(',', '.').toBigDecimalOrNull()
    }

    /**
     * Whether a token is a percentage, in every tokenization ML Kit produces.
     *
     * Three shapes, all seen in this repo's evidence bundles: the sign fused to the number (`9%`),
     * the sign carried alone beside it (handled by [PercentAssociation], which is consulted
     * separately), and the reference-intake word without a sign at all (`2 DV`, `<1%`). The `<`
     * form matters because a Baltic table prints `<1%` and the leading character makes the token
     * neither a clean number nor a clean percentage.
     */
    private fun isPercentage(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.contains('%')) return true
        if (trimmed.startsWith('<') || trimmed.startsWith('>')) return true
        val normalized = NutritionTerminology.normalize(trimmed)
        return REFERENCE_WORDS.containsMatchIn(normalized)
    }

    private val REFERENCE_WORDS = Regex("(?:^|\\s)(?:ri|dv|gda)(?:$|\\s)")
}
