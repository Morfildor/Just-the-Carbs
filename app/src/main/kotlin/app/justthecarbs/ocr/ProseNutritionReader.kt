package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.NutritionValueValidator
import java.math.BigDecimal
import kotlin.math.abs

/**
 * Reads a total carbohydrate figure from a label with no tabular structure — a run-on multilingual
 * sentence such as `Voedingswaarde per 100 g: ... koolhydraten 46 g, waarvan suikers 1,0 g, ...`.
 *
 * This is a recognizer of one printed form, NOT a scoring model. It has no notion of a best
 * candidate: it either finds a span matching the required shape or yields nothing. There is nothing
 * here to tune, which is what keeps it from becoming a second scoring path of the kind the
 * geometry-first rewrite removed.
 *
 * It runs only on tabular `NotFound` and only when [isProseLabel] holds — see
 * [NutritionTableInterpreter]. Both conditions independently exclude every tabular fixture.
 */
object ProseNutritionReader {

    /**
     * Whether this label is prose, stated positively — both conditions required.
     *
     * 1. Some **declaration** reads as a sentence: a carbohydrate term, then a numeric value, then a
     *    child term, then a numeric value, in that order.
     * 2. No **usable** basis column was resolved anywhere in the document — see [isUsable].
     *
     * Bare co-occurrence of a total term and a child term on one row is deliberately NOT the
     * predicate. That is the signature of a *merged table row* — the failure mode the geometry-first
     * rewrite was built to fix — and using it here would hand the prose reader the tables it must
     * never touch. Condition 1's ordering requirement separates a sentence from a merged row (whose
     * values sit adjacent in one value column rather than interleaved with their nutrient terms), and
     * condition 2 independently refuses any document where the table stage found real column
     * structure and failed for some other reason.
     */
    fun isProseLabel(rows: List<LogicalRow>, columns: List<NutritionColumn>, documentWidth: Int): Boolean {
        if (hasUsableBasisColumn(rows, columns, documentWidth)) return false
        // The SAME assembly [read] uses, not a second token stream built for the predicate. If the
        // two could drift apart, the gate would be deciding about a document the reader never sees.
        return declarationsIn(flatten(rows)).any { hasNutrientValueChildValueSequence(wordsOf(it)) }
    }

    /**
     * Condition 2, exposed on its own so a test can assert the **reason** a document is or is not
     * prose rather than only the verdict.
     *
     * That distinction is load-bearing here. The merged-table guard and the resolved-but-unusable
     * case both end in `isProseLabel == false` / `true` for their own reasons, and a test asserting
     * only the verdict cannot tell a guard that still works from one that has silently eroded into
     * being carried by the other condition.
     */
    internal fun hasUsableBasisColumn(
        rows: List<LogicalRow>,
        columns: List<NutritionColumn>,
        documentWidth: Int,
    ): Boolean = columns.any { it.kind in BASIS_KINDS && isUsable(it, columns, rows, documentWidth) }

    private val BASIS_KINDS = setOf(
        NutritionColumnKind.PER_100_G,
        NutritionColumnKind.PER_100_ML,
        NutritionColumnKind.PER_SERVING,
    )

    /**
     * Whether [column] actually participates in the table's structure, rather than merely having been
     * resolved from a basis phrase that happened to appear in running text.
     *
     * **The existence of the [NutritionColumn] object proves nothing on its own**, which is exactly
     * what the un-amended predicate wrongly trusted. On a prose label the basis phrase is embedded in
     * a sentence, so [ColumnClassifier] resolves a column whose x-position corresponds to no value
     * column at all; on a dense label, ingredient prose ("waarvan toegevoegde suikers 0 g per 100 g")
     * resolves one too. Measured on this repo's real corpus, every one of the three prose fixtures
     * resolves at least one basis column, so "was a column resolved" can never be satisfied by a real
     * prose label.
     *
     * A column is usable when at least [MIN_ALIGNED_VALUE_CELLS] value cells that the interpreter
     * would bind to it are also **mutually aligned with each other** at one x-position. Two
     * independent things are required and neither alone suffices:
     *
     * - *Binding* reuses the interpreter's own cell-to-column rule ([columnForCell], the same nearest
     *   -within-tolerance test with the same `verticalExtent` respect that [NutritionTableInterpreter]
     *   applies), so there is one alignment rule in the package rather than two that can drift apart.
     *   That tolerance is deliberately generous — it exists to tolerate photographic skew — which is
     *   why binding alone is not evidence: on running text several unrelated numbers fall inside it.
     * - *Mutual alignment* is what "column" actually means. Cells printed one under another in a
     *   table share an x-centre to within about a character; numbers scattered through a sentence do
     *   not, however close some of them happen to land to a header's centre.
     *
     * Only cells on nutrient rows count. A page number, a batch code or a net weight is not a
     * nutrition-table value however neatly it lines up.
     *
     * The merged-table guard is untouched by this. A genuine table whose printed rows chained into one
     * logical row still prints its values one under another, so they remain mutually aligned and the
     * column stays usable — the reconstruction defect moved the rows, not the columns.
     */
    private fun isUsable(
        column: NutritionColumn,
        columns: List<NutritionColumn>,
        rows: List<LogicalRow>,
        documentWidth: Int,
    ): Boolean {
        val medianHeight = medianTextHeight(rows)
        val boundCenters = rows
            .filter { statesANutrientValue(it) }
            .flatMap { row -> valueCellsOf(row, documentWidth) }
            .filter { cell -> columnForCell(cell, columns, documentWidth) === column }
            .map { it.centerX }

        // The tightest cluster, not the total. A column with three cells spread across the sentence
        // is three coincidences, not a column.
        return boundCenters.any { anchor ->
            boundCenters.count { abs(it - anchor) <= medianHeight * MAX_CELL_ALIGNMENT_IN_HEIGHTS } >=
                MIN_ALIGNED_VALUE_CELLS
        }
    }

    /**
     * How many mutually aligned value cells make a column rather than a coincidence.
     *
     * Three, measured against this repo's nine real fixtures. Their tightest aligned-cell clusters
     * separate cleanly and with a whole cell of margin: the three prose labels reach at most **2**
     * (a stray "0" from an ingredient claim landing 11 px from a sugars figure on the next wrapped
     * line), while every genuine table reaches at least **3** on the column its values print under.
     *
     * It is deliberately one more than [ColumnClassifier]'s "two cells make a column, one is a
     * stray". That rule is applied to *percent-shaped* cells, whose shape already carries most of the
     * evidence; here the cells are ordinary numbers, and a wrapped paragraph produces coincidental
     * pairs readily enough that a pair cannot be trusted. Raising it further would start to refuse
     * short real tables, which print only three or four nutrient rows.
     */
    private const val MIN_ALIGNED_VALUE_CELLS = 3

    /**
     * How far two value cells' x-centres may sit apart and still count as one column, in text heights.
     *
     * One text height is roughly one character. Printed table cells are typically right- or
     * decimal-aligned and land well inside it (the real fixtures cluster within 2–10 px on 15–40 px
     * text); a sentence's numbers do not. Scaled by text height rather than by image width so it
     * means the same thing on a 900 px crop and an 8 MP capture.
     */
    private const val MAX_CELL_ALIGNMENT_IN_HEIGHTS = 1.0

    /** Rows whose numbers are nutrient quantities. A header or a marketing line states no cell. */
    private val NUTRIENT_ROW_KINDS = setOf(
        NutritionRowKind.TOTAL_CARBOHYDRATE,
        NutritionRowKind.CARBOHYDRATE_CHILD,
    )

    /**
     * Whether [row] states some nutrient's value — carbohydrate, one of its children, **or any other
     * declared nutrient** (fat, saturates, protein, salt, energy).
     *
     * ### Why the non-carbohydrate rows had to be counted (2026-08-25)
     *
     * Counting only carbohydrate rows made this gate blind to the commonest table on a European
     * package, and a real device produced a **confident wrong** because of it. On a trilingual
     * NL/FR/DE label the values `13g 12g 869 54g 0,99 007g` printed one under another at x≈818–940 —
     * six cells, unmistakably a column — but only two of their rows (`Kohlenhydrate` and its sugars
     * child) were carbohydrate rows. Two is below [MIN_ALIGNED_VALUE_CELLS], so the per-100 column
     * was judged unusable, the document was declared prose, and the prose reader bound the word
     * `Kohlenhydrate` to the **per-portion** cell `22 g` and reported it as per 100 g. The table was
     * never in doubt; only this predicate's view of it was.
     *
     * A nutrition table's defining structure is its *column of nutrient values*, and fat and protein
     * lines are part of that column just as carbohydrate is. Excluding them measured the wrong thing.
     *
     * ### And why they must also be short (measured the same day)
     *
     * Counting *any* row that names a nutrient went too far in the other direction and cost this
     * repo's bread fixture, which prints its whole declaration as one running sentence. Its rows name
     * `vetten`, `eiwitten` and `zout` too — inside sentences — so their scattered numbers were
     * suddenly counted, a coincidental column crossed [MIN_ALIGNED_VALUE_CELLS], the label was judged
     * a table, and a page that has no table at all lost its prose reading.
     *
     * A table's nutrient row is a *line*: a name, a value, perhaps a unit and a second column. A
     * prose declaration is a *sentence* that happens to contain nutrient names. [MAX_WORDS_IN_A_ROW]
     * separates them, and it is a statement about printed form rather than a tuned score — every
     * genuine table row in this repo's corpus is far below it and every prose row far above.
     *
     * Membership is by named nutrient **and** row shape, never by "row containing numbers". That is
     * what keeps genuine prose labels passing: their nutrient names sit in sentences, and ingredient
     * text, batch codes and marketing claims name no nutrient at all.
     */
    private fun statesANutrientValue(row: LogicalRow): Boolean {
        if (RowClassifier.classify(row) in NUTRIENT_ROW_KINDS) return true
        if (row.elements.size > MAX_WORDS_IN_A_ROW) return false
        val normalized = NutritionTerminology.normalize(row.text)
        return CarbohydrateTermAnchor.OTHER_NUTRIENT_TERMS.any {
            NutritionTerminology.containsTerm(normalized, NutritionTerminology.normalize(it))
        }
    }

    /**
     * How many recognized elements a *table* row may hold before it is read as running text.
     *
     * Eight. A printed nutrient line is a name and its cells — the widest real one in this corpus is
     * a trilingual `Vetten/Matieres grasses/Fett:` with two value columns and a percentage, which
     * lands well inside it. A prose declaration runs to dozens of elements. The gap between the two
     * populations is large, so the exact number is not delicate; it only has to sit inside the gap.
     */
    private const val MAX_WORDS_IN_A_ROW = 8

    /**
     * A cell a table would print in a column: a number standing on its own, optionally carrying a
     * unit or the punctuation a sentence wraps it in.
     *
     * Deliberately the *shape* test, not the full numeric-cell extraction
     * [NutritionTableInterpreter] performs — this stage is asking whether column structure exists at
     * all, not what any particular value means, so percentages count as evidence of a column just as
     * grams do.
     */
    private fun valueCellsOf(row: LogicalRow, documentWidth: Int): List<OcrBox> {
        val percentIndices = PercentAssociation.percentElementIndices(row, documentWidth)
        return row.elements.filterIndexed { index, element ->
            // A bare "%" marks its neighbour's cell rather than being one itself.
            index !in percentIndices || element.text.any { it.isDigit() }
        }.filter { VALUE_CELL.matches(it.text.trim()) }.map { it.box }
    }

    /** "2,3", "46g,", "(150", "<1%", "0,313" — a number and at most its unit and wrapping. */
    private val VALUE_CELL =
        Regex("""^[(\[<]?\d{1,4}(?:[.,]\d{1,3})?\s*(?:g|mg|ml|kj|kcal|%)?[)\],.;]?$""", RegexOption.IGNORE_CASE)

    /**
     * The column [NutritionTableInterpreter] would bind [cell] to — the same nearest-within-tolerance
     * rule, including its respect for a recovered column's [NutritionColumn.verticalExtent].
     *
     * Reimplemented over a box rather than shared as a function because the interpreter's version is
     * private to it and takes its own internal cell type; the rule, its tolerance and its band check
     * are identical, and a change to either must be made in both. Structuring it any other way would
     * mean widening the interpreter's private surface for a diagnostic question.
     */
    private fun columnForCell(
        cell: OcrBox,
        columns: List<NutritionColumn>,
        documentWidth: Int,
    ): NutritionColumn? {
        if (columns.isEmpty()) return null
        val loose = maxOf(
            NutritionParserThresholds.MIN_STRICT_COLUMN_PIXELS,
            documentWidth * NutritionParserThresholds.LOOSE_COLUMN_FRACTION,
        )
        return columns
            .filter { it.verticalExtent?.let { band -> cell.centerY.toInt() in band } ?: true }
            .map { it to abs(it.centerX - cell.centerX) }
            .filter { it.second <= loose }
            .minByOrNull { it.second }
            ?.first
    }

    private fun medianTextHeight(rows: List<LogicalRow>): Double {
        val heights = rows.flatMap { row -> row.elements.map { it.box.height } }.sorted()
        return if (heights.isEmpty()) 1.0 else heights[heights.size / 2].toDouble().coerceAtLeast(1.0)
    }

    /**
     * True when [words] — one declaration's normalized tokens, in reading order — match
     * `<carbohydrate term> … <number> … <child term> … <number>`.
     *
     * The ordering is the whole test. A merged table row produces
     * `<carbohydrate term> <number> <child term> <number>` only by coincidence of column layout; far
     * more often it produces the two values adjacent, or the child term before the total's value.
     * Requiring the interleaving is what makes this a statement about sentences rather than about
     * proximity.
     *
     * **The window is one declaration, not one reconstructed row** (owner amendment, 2026-08-17). ML
     * Kit wraps a printed sentence wherever the line ends, so on the real corpus the child clause
     * routinely lands on the *next* reconstructed row — `…Kulhydrat: 3g. dont` / `sures /waarvan
     * suikers … 2,5g`. A row-scoped window can therefore only ever fire when the wrap happened to fall
     * somewhere harmless, which is luck rather than structure. The predicate itself is unchanged; only
     * the window moved, and it moved to the span [read] already assembles rather than to the document.
     *
     * That declaration boundary is what stops this becoming document-wide chaining. A declaration runs
     * from one basis phrase to the next, so the walk **cannot** reach across into another nutrient
     * declaration to borrow the child clause it is missing, and tokens preceding the first basis phrase
     * belong to no declaration at all. Skipping across a *usable table structure* is refused
     * independently by condition 2, which is evaluated first and over the whole document.
     *
     * Walked over **words** rather than elements, for the same reason [CarbohydrateTermAnchor] does: a
     * term can span elements ("ogljikovi hidrati") and a single element can carry several words and a
     * fused number ("46g,waarvan") — the exact tokenization the real dense-prose fixture produces. A
     * word-level walk sees the sequence in either shape; an element-level walk sees it only when the
     * recognizer happened to split on spaces.
     */
    private fun hasNutrientValueChildValueSequence(words: List<String>): Boolean {
        if (words.isEmpty()) return false


        // Four states, advanced strictly left to right. Reaching PAST the last one is the match.
        var state = 0
        var index = 0
        while (index < words.size) {
            when (state) {
                // Seek the carbohydrate term. A child term reached first means the sentence's total
                // clause is not on this row, so nothing here can be a total-then-child sequence.
                0 -> {
                    val child = termLengthAt(words, index, NutritionTerminology.exclusionTerms)
                    if (child != null) return false
                    val carb = termLengthAt(words, index, NutritionTerminology.carbohydrateTerms)
                    if (carb != null) {
                        state = 1
                        index += carb
                        continue
                    }
                }
                // Seek the total's value. A child term before any number means the total clause
                // states no value here — a merged table row's usual shape.
                1 -> {
                    if (termLengthAt(words, index, NutritionTerminology.exclusionTerms) != null) return false
                    if (isNumberWord(words[index])) state = 2
                }
                // Seek the child term.
                2 -> if (termLengthAt(words, index, NutritionTerminology.exclusionTerms) != null) {
                    state = 3
                }
                // Seek the child's value.
                3 -> if (isNumberWord(words[index])) return true
            }
            index++
        }
        return false
    }

    /**
     * How many words the first [terms] entry starting exactly at [index] occupies, or null if none
     * starts there. Multi-word terms ("waarvan suikers") must match across adjacent words.
     */
    private fun termLengthAt(words: List<String>, index: Int, terms: Collection<String>): Int? =
        terms.asSequence()
            .map { NutritionTerminology.normalize(it).split(' ').filter(String::isNotBlank) }
            .filter { it.isNotEmpty() && index + it.size <= words.size }
            .filter { term -> term.indices.all { words[index + it] == term[it] } }
            .maxOfOrNull { it.size }

    /**
     * A word that states a quantity: digits, optionally with a decimal separator, optionally fused to
     * its unit ("46g"). Normalization has already removed the separator, so "46,5" arrives as two
     * words — the leading one is enough to advance the sequence, which is all this predicate needs.
     */
    private fun isNumberWord(word: String): Boolean = NUMBER_WORD.matches(word)

    private val NUMBER_WORD = Regex("^\\d{1,4}[a-z]{0,4}$")

    // ------------------------------------------------------------------------------------------
    // The reader itself, plus the declaration assembly [isProseLabel]'s condition 1 shares with it.
    // Only [read] and what it alone calls run after the gate; the token stream and the declaration
    // split below are deliberately used by both, so the gate and the reader can never disagree about
    // where one declaration ends and the next begins.
    // ------------------------------------------------------------------------------------------

    /** The prose stage's answer plus, when it accepted one, the span that produced it. */
    data class ProseResult(
        val reading: LabelReading,
        val provenance: CandidateProvenance.FromProseSpan?,
    )

    /**
     * One token in the document's reading order, with the element it came from.
     *
     * A token is a *sub-word*, not an element and not an element's whole text. Real prose OCR welds a
     * value to the word after it — the dense-prose fixture emits `46g,waarvan` as one element — so an
     * element-level stream would hide both the value and the child term inside one opaque string.
     * Splitting on the punctuation between them, while keeping a decimal separator that sits between
     * two digits, recovers `46g` and `waarvan` as separate tokens without destroying `1,0`.
     *
     * [raw] is what a number is parsed from; [normalized] is what a term is matched against. They are
     * kept side by side because normalization deliberately destroys the decimal separator, and a
     * reader that matched terms on raw text or parsed numbers from normalized text would be wrong in
     * one direction or the other.
     */
    private data class Token(
        val index: Int,
        val raw: String,
        val normalized: String,
        val element: OcrElement,
        val elementIndexInRow: Int,
        val rowText: String,
    )

    /** A nutrient term found in the token stream, and whether it is a child. */
    private data class TermHit(val index: Int, val term: String, val isChild: Boolean)

    /** A value token: a number carrying a gram/millilitre unit, either joined or as the next token. */
    private data class ValueHit(val index: Int, val value: BigDecimal)

    /** A basis phrase and everything governed by it, up to the next basis phrase. */
    private data class Declaration(val basis: NutritionBasis, val tokens: List<Token>)

    /** One accepted total-carbohydrate interpretation, before aggregation decides what to do with it. */
    private data class ProseInterpretation(
        val value: BigDecimal,
        val basis: NutritionBasis,
        val term: TermHit,
        val valueToken: Token,
    )

    fun read(rows: List<LogicalRow>): ProseResult {
        // Rows top to bottom, elements left to right — the order a person reads the package in. A
        // declaration is a run of this stream, so it is free to cross row boundaries. It must be:
        // the baseline measured the basis phrase on a DIFFERENT row from the carbohydrate value in
        // every real prose fixture, so a row-scoped reader would never fire.
        val tokens = flatten(rows)

        val interpretations = declarationsIn(tokens).flatMap(::interpret)

        // Document-wide, never first-match. A first accepted result would silently prefer whichever
        // declaration sits highest on the package and hide any disagreement from the user.
        val distinct = interpretations.distinctBy { it.value.stripTrailingZeros() to it.basis }

        return when (distinct.size) {
            0 -> ProseResult(LabelReading.NotFound, null)
            1 -> {
                val only = distinct.single()
                ProseResult(
                    LabelReading.Confident(proseCandidate(only)),
                    CandidateProvenance.FromProseSpan(
                        nutrientTerm = only.term.term,
                        valueElementIndices = setOf(only.valueToken.elementIndexInRow),
                        rowText = only.valueToken.rowText,
                    ),
                )
            }
            // Competing eligible totals are an ambiguity, never a pick. No single span produced this,
            // so there is no provenance to report.
            else -> ProseResult(LabelReading.Ambiguous(distinct.map(::proseCandidate)), null)
        }
    }

    /** Every total-carbohydrate interpretation this declaration supports. Usually zero or one. */
    private fun interpret(declaration: Declaration): List<ProseInterpretation> {
        val terms = termsIn(declaration.tokens)
        if (terms.none { !it.isChild }) return emptyList()

        val values = valuesIn(declaration.tokens)

        // Each value binds to its nearest PRECEDING nutrient term, and a child term claims every
        // value up to the next term. Exclusion is a property of the binding, not a distance — which
        // is what stops a sugars figure being reachable from the total's term.
        return values.mapNotNull { value ->
            val owner = terms.lastOrNull { it.index < value.index } ?: return@mapNotNull null
            if (owner.isChild) return@mapNotNull null
            // The same ceiling the tabular path applies to a per-100 cell. A prose sentence states
            // plenty of numbers that are not carbohydrate grams — an energy figure in kJ is the
            // common one — and this refuses the impossible ones rather than ranking them.
            val validated = NutritionValueValidator.validateCarbsPer100(
                value.value.toDouble(),
                declaration.basis,
            ) ?: return@mapNotNull null
            val token = declaration.tokens.first { it.index == value.index }
            ProseInterpretation(validated, declaration.basis, owner, token)
        }
    }

    /**
     * The whole document as one reading-order token stream: rows top to bottom, elements left to
     * right within each row, each element split into sub-word tokens.
     */
    private fun flatten(rows: List<LogicalRow>): List<Token> = buildList {
        rows.forEach { row ->
            row.elements.forEachIndexed { elementIndex, element ->
                splitIntoWords(element.text).forEach { word ->
                    add(
                        Token(
                            index = size,
                            raw = word,
                            normalized = NutritionTerminology.normalize(word),
                            element = element,
                            elementIndexInRow = elementIndex,
                            rowText = row.text,
                        ),
                    )
                }
            }
        }
    }

    /**
     * One element's printed text as sub-words.
     *
     * Splits on any run of characters that is neither a letter nor a digit, **except** a single
     * decimal separator standing between two digits. That exception is the whole subtlety: without it
     * `1,0` becomes `1` and `0`; with it, `46g,waarvan` still becomes `46g` and `waarvan` because the
     * comma there is followed by a letter.
     */
    private fun splitIntoWords(text: String): List<String> =
        text.split(WORD_SEPARATOR).filter { it.isNotBlank() }

    /** Any non-alphanumeric run that is not a decimal point between two digits. */
    private val WORD_SEPARATOR = Regex("(?<![0-9])[^\\p{L}\\p{N}]+|(?<=[0-9])[^\\p{L}\\p{N}]+(?![0-9])")

    /**
     * Each basis phrase in the stream and the tokens it governs, running to the next basis phrase or
     * the end of the document.
     *
     * Tokens preceding the first basis phrase belong to no declaration and are dropped: a value with
     * no governing basis is unplaceable, and the app refuses rather than assuming grams.
     */
    private fun declarationsIn(tokens: List<Token>): List<Declaration> {
        val openings = mutableListOf<Triple<Int, NutritionBasis, Int>>()
        var index = 0
        while (index < tokens.size) {
            val phrase = basisPhraseAt(tokens, index)
            if (phrase == null) {
                index++
                continue
            }
            openings += Triple(index, phrase.first, phrase.second)
            index += phrase.second
        }

        return openings.mapIndexed { position, (start, basis, length) ->
            val end = openings.getOrNull(position + 1)?.first ?: tokens.size
            Declaration(basis, tokens.subList(start + length, end))
        }
    }

    /**
     * The declaration's tokens as normalized words, in reading order.
     *
     * [NutritionTerminology.normalize] both strips punctuation and splits on it, so the raw token
     * "1,0" arrives here as the two words "1" and "0" and "46g" stays one — which is what lets the
     * sequence walk recognize a decimal value by its leading digits without a second number grammar.
     * Splitting on the space normalization introduces is therefore required, not incidental.
     */
    private fun wordsOf(declaration: Declaration): List<String> = declaration.tokens
        .flatMap { it.normalized.split(' ') }
        .filter { it.isNotBlank() }

    /**
     * How many declarations [rows] splits into. Exposed only so the separate-declarations negative
     * test can assert its fixture really produces two — without that check the test would pass
     * vacuously if the fixture collapsed into one declaration and failed the sequence for some other
     * reason, pinning nothing.
     */
    internal fun declarationCountForTest(rows: List<LogicalRow>): Int =
        declarationsIn(flatten(rows)).size

    /**
     * Whether the sequence would be satisfied if the window were the whole document rather than one
     * declaration — i.e. the behaviour the declaration boundary exists to refuse.
     *
     * Exposed for the same reason: the negative test must show the four tokens ARE present in correct
     * order, so the refusal is demonstrably the boundary doing its job and not the fixture simply
     * lacking a sequence.
     */
    internal fun documentWideSequenceForTest(rows: List<LogicalRow>): Boolean =
        hasNutrientValueChildValueSequence(
            flatten(rows).flatMap { it.normalized.split(' ') }.filter { it.isNotBlank() },
        )

    /**
     * A basis phrase starting exactly at [index], as `basis to length in tokens`.
     *
     * The same shape [InlineBasisSpans.spanAt] recognizes — connective, literal `100`, unit — rather
     * than a second vocabulary that could drift from it. The unit may be fused to the `100`
     * (`100g:`), which is how every real prose label in this corpus prints it.
     */
    private fun basisPhraseAt(tokens: List<Token>, index: Int): Pair<NutritionBasis, Int>? {
        if (tokens.getOrNull(index)?.normalized !in NutritionTerminology.connectives) return null

        val hundred = tokens.getOrNull(index + 1) ?: return null
        FUSED_HUNDRED.matchEntire(hundred.normalized)?.let { fused ->
            return basisFor(fused.groupValues[1])?.let { it to 2 }
        }
        if (hundred.normalized != "100") return null

        val unit = tokens.getOrNull(index + 2) ?: return null
        return basisFor(unit.normalized)?.let { it to 3 }
    }

    /** One shared unit vocabulary, so this cannot drift from [InlineBasisSpans]. */
    private fun basisFor(unit: String): NutritionBasis? = NutritionTerminology.basisUnitFor(unit)

    /**
     * `100g` / `100ml` / `100gram` as one token, the shape a printed `per 100g:` produces after
     * splitting. Longest-first alternation, anchored at both ends, so `g` cannot match inside `gram`.
     */
    private val FUSED_HUNDRED = Regex("^100(${NutritionTerminology.basisUnitAlternation})$")

    /**
     * Every nutrient term in [tokens], in reading order.
     *
     * **Exclusions are checked first**, so a phrase that is both — a child term containing a
     * carbohydrate word — is a child. That is the same precedence [RowClassifier] applies, and it is
     * the correctness claim this reader inherits rather than re-decides.
     */
    private fun termsIn(tokens: List<Token>): List<TermHit> {
        val words = tokens.map { it.normalized }
        val hits = mutableListOf<TermHit>()
        var index = 0
        while (index < words.size) {
            val child = longestTermAt(words, index, NutritionTerminology.exclusionTerms)
            if (child != null) {
                hits += TermHit(tokens[index].index, child.first, isChild = true)
                index += child.second
                continue
            }
            val carb = longestTermAt(words, index, NutritionTerminology.carbohydrateTerms)
            if (carb != null) {
                hits += TermHit(tokens[index].index, carb.first, isChild = false)
                index += carb.second
                continue
            }
            index++
        }
        return hits
    }

    /** The longest [terms] entry starting exactly at [index], as `term to length in words`. */
    private fun longestTermAt(
        words: List<String>,
        index: Int,
        terms: Collection<String>,
    ): Pair<String, Int>? = terms.asSequence()
        .map { it to NutritionTerminology.normalize(it).split(' ').filter(String::isNotBlank) }
        .filter { (_, parts) -> parts.isNotEmpty() && index + parts.size <= words.size }
        .filter { (_, parts) -> parts.indices.all { words[index + it] == parts[it] } }
        .maxByOrNull { (_, parts) -> parts.size }
        ?.let { (term, parts) -> term to parts.size }

    /**
     * Every value token in [tokens]: a number carrying a gram unit, either fused (`46g`) or stated by
     * the token after it (`46` `g`).
     *
     * The unit requirement is a refusal, not a preference. A prose sentence is full of bare numbers —
     * a kJ figure, a batch code, a percentage — and a number with no unit states no quantity this
     * reader can place. Values inside a basis phrase never reach here: [declarationsIn] consumes
     * those tokens, so the literal `100` in `per 100 g` is not in any declaration's token list.
     */
    private fun valuesIn(tokens: List<Token>): List<ValueHit> = buildList {
        tokens.forEachIndexed { position, token ->
            val fused = FUSED_VALUE.matchEntire(token.raw)
            val number = when {
                fused != null -> fused.groupValues[1]
                BARE_NUMBER.matches(token.raw) &&
                    tokens.getOrNull(position + 1)?.normalized in GRAM_UNITS -> token.raw
                else -> null
            } ?: return@forEachIndexed
            number.replace(',', '.').toBigDecimalOrNull()?.let { add(ValueHit(token.index, it)) }
        }
    }

    /** A number welded to its unit: "46g", "1,0g". */
    private val FUSED_VALUE = Regex("^(\\d{1,4}(?:[.,]\\d{1,3})?)(?:g|mg)$", RegexOption.IGNORE_CASE)

    /** A number on its own, whose unit must then be the next token. */
    private val BARE_NUMBER = Regex("^\\d{1,4}(?:[.,]\\d{1,3})?$")

    /** Units a carbohydrate quantity is printed in. Never kJ, kcal or a bare percentage. */
    private val GRAM_UNITS = setOf("g", "mg")

    private fun proseCandidate(interpretation: ProseInterpretation) = CarbCandidate(
        sourceLine = interpretation.valueToken.rowText,
        label = interpretation.term.term,
        value = interpretation.value,
        basis = interpretation.basis,
        // There is no scoring on this path; the field exists for the shared type.
        score = 0,
        geometry = interpretation.valueToken.element.box,
        evidence = listOf(
            CandidateEvidence("prose declaration basis ${interpretation.basis.name}", 0),
        ),
    )
}
