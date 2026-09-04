package app.justthecarbs.ocr

/**
 * Splits a reconstructed row at the nutrient names printed on it, so a row naming several nutrients
 * can still yield the total-carbohydrate figure that belongs to one of them.
 *
 * ## The failure this exists for
 *
 * A US-style linear Nutrition Facts panel prints its whole declaration as running text. Measured on
 * a Sempio Korean sauce (`docs/Scan Evidence 01-09-26/20260901-211550-678`), ML Kit returned the
 * carbohydrate declaration as one row:
 *
 * ```
 * DV), Total Carb. 6g (2% DV), Fiber 1 g (4% DV),
 * ```
 *
 * [RowClassifier] reads a row as one unit, so it saw `fiber` — a child term — and typed the row
 * `CARBOHYDRATE_CHILD` **unconditionally**, which is the correct and load-bearing rule for a *merged
 * table row*. The consequence here is that the table had no total row at all and the printed `6 g`
 * was unreachable. The user was sent to manual selection, tapped the number themselves, and was then
 * offered only "/100 g" and "/100 ml" for a figure that is per 18 g serving.
 *
 * ## Why splitting is safe where relaxing the child rule would not be
 *
 * The child rule exists because a *merged table row* — one printed row swallowing the next through
 * row-reconstruction chaining — genuinely cannot be separated: `Koolhydraten: 0.59 13g` merged with
 * `Waarvan suikers: 0.59 13g` gives no way to know which number belongs to which nutrient. Relaxing
 * that would be exactly the sugars-as-total failure the geometry-first rewrite removed.
 *
 * A **linear panel is different in kind**: the nutrients are printed in sequence on one line, each
 * introduced by its own name, so reading order tells you where one nutrient's clause ends — the next
 * nutrient's name begins it. That is the same reading-order argument [CarbohydrateTermAnchor] already
 * makes for a wrapped prose row, applied to a whole row rather than to one anchor.
 *
 * So this does not weaken the child rule. It runs **before** classification and asks a narrower
 * question: *does this row state a total-carbohydrate term explicitly, with its own value, bounded by
 * the next nutrient name?* When the answer is no — which is every ordinary table row and every merged
 * row — nothing changes and the row classifies exactly as it did.
 *
 * ## The boundaries
 *
 * A segment runs from a nutrient term to the **next nutrient term of any kind**, exclusive. `Fiber`
 * ends the carbohydrate segment; so would `Protein`, `Sugars` or another `Total Carb.`. Everything
 * before the first nutrient term (here the trailing `DV),` of the previous line) belongs to no
 * segment and is discarded, which is what keeps the preceding sodium percentage out.
 *
 * ## What this deliberately does not do
 *
 * - It does not apply outside a nutrition-table context: [segmentsOf] requires the row to name a
 *   total-carbohydrate term *and* at least one other nutrient, which running prose and ingredient
 *   lists do not do. An ingredients row naming `sugar` names no total term and is untouched.
 * - It does not invent a basis. A segment carries a value and its geometry; what that value is
 *   measured *per* is still decided by column association, exactly as before.
 * - It does not repair numbers, and it never promotes a child segment. A `Fiber` segment stays a
 *   child and can never supply the total, which is asserted by its own negative control.
 */
internal object NutrientRowSegments {

    /**
     * One nutrient's clause within a row: the term that introduces it and the horizontal span its
     * values may occupy.
     *
     * [endX] is exclusive and is the left edge of the next nutrient term, or [Int.MAX_VALUE] for the
     * last segment on the row.
     */
    data class Segment(
        val kind: NutritionRowKind,
        val term: String,
        val startX: Int,
        val endX: Int,
    ) {
        /** Whether [box] sits inside this segment's horizontal span. */
        fun contains(box: OcrBox): Boolean = box.left >= startX && box.left < endX
    }

    /**
     * The nutrient clauses on [row], or an empty list when the row is not a multi-nutrient linear
     * declaration.
     *
     * Empty is the ordinary answer and means "classify this row exactly as before". It is returned
     * whenever the row does not carry a total-carbohydrate term followed by a different nutrient,
     * which is every table row this repo's corpus contains.
     */
    fun segmentsOf(row: LogicalRow): List<Segment> {
        if (ParserWorkCounters.enabled) ParserWorkCounters.segmentationCalls++
        val terms = nutrientTermsIn(row)
        if (terms.size < 2) return emptyList()

        // A total term must be present, and it must be *stated*, not merely implied by the row also
        // naming a child. Without this the rule would fire on an ordinary merged table row, which is
        // precisely the case it must not touch.
        if (terms.none { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE }) return emptyList()

        val segments = terms.mapIndexed { index, term ->
            Segment(
                kind = term.kind,
                term = term.text,
                startX = term.box.left,
                endX = terms.getOrNull(index + 1)?.box?.left ?: Int.MAX_VALUE,
            )
        }

        // **The merged-row guard, and the reason this rule is safe at all.**
        //
        // A linear Nutrition Facts panel states each nutrient *with its own value*: `Total Carb. 6g
        // ... Fiber 1 g`. A merged multilingual table row states one nutrient several times in
        // different languages and carries one set of values — ML Kit merged Croatian `od kojih
        // šećeri` with German `Kohlenhydrate` onto one row on a real photograph, and that row is a
        // sugars row wearing a carbohydrate word, not two clauses.
        //
        // Requiring every segment to carry its own value separates them, because it is the property
        // that actually differs: in the merged row the language variants sit adjacent with no number
        // between them, so all but the last segment are empty. Without this the merged row would
        // type TOTAL_CARBOHYDRATE on the strength of the German word — the exact sugars-as-total
        // failure the geometry-first rewrite removed, reintroduced through a side door.
        if (segments.any { segment -> !carriesAValue(row, segment) }) return emptyList()

        return segments
    }

    /**
     * Whether [segment] contains a numeric cell of its own.
     *
     * Percent-shaped tokens are excluded: `(2% DV)` is a reference figure that follows almost every
     * nutrient on a US panel, so counting it would make an empty segment look populated and defeat
     * the merged-row guard above.
     */
    private fun carriesAValue(row: LogicalRow, segment: Segment): Boolean =
        row.elements.any { element ->
            segment.contains(element.box) &&
                VALUE_TOKEN.containsMatchIn(element.text) &&
                !PERCENT_TOKEN.containsMatchIn(element.text)
        }

    /** A number, with or without a fused unit. */
    private val VALUE_TOKEN = Regex("\\d")

    /** A percentage, in either tokenization ML Kit produces. */
    private val PERCENT_TOKEN = Regex("%")

    /**
     * The total-carbohydrate segment on [row], or null when the row has no separable one.
     *
     * Null both when the row is not a linear declaration at all and when it names more than one
     * total term — two totals on one row is a shape nothing here can resolve, and picking either
     * would be the arbitrary choice this architecture refuses.
     */
    fun totalCarbohydrateSegment(row: LogicalRow): Segment? =
        segmentsOf(row).filter { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE }.singleOrNull()

    /**
     * Where the total-carbohydrate clause on [row] **starts and stops**, for a tap that landed
     * somewhere on it. Null when the row states no total term at all.
     *
     * ## Why this is not [totalCarbohydrateSegment]
     *
     * That one answers *"may this row be read as a total-carbohydrate row?"* and is consulted by
     * [RowClassifier], the interpreter and [ScaleAmbiguity] — the automatic path. It is deliberately
     * conservative: its merged-row guard discards the whole row unless **every** clause on it
     * carries a value, because a multilingual table row repeating one nutrient in three languages
     * must never be read as three clauses.
     *
     * This one answers a different and strictly weaker question: *"the user pointed here — which
     * printed clause is that?"* It establishes no reading, promotes no row, and produces no value.
     * It is used only to route a tap, and every rule that decides whether a number may be **offered**
     * runs afterwards, unchanged.
     *
     * ## The failure it exists for
     *
     * `docs/Scan Evidence 02-09 4th test/20260902-141703-456`. ML Kit put the whole declaration on
     * one reconstructed row:
     *
     * ```
     * Zig lkg2%; Nolhydraten/Glucides Kohlenhydrate 8,9 a, 1.3q(<19; waarvan suikers/dant sucres/tavon Žucker
     *                                 ^^^ total clause ^^^^^^^^^^^^  ^^^^^^^^ child clause ^^^^^^^^
     * ```
     *
     * Segmentation finds a perfectly good total clause here — `Kohlenhydrate` at x=112 bounded by
     * `waarvan suikers` at x=900 — and then throws the row away, because the **trailing** French and
     * German language variants of the sugars clause (`sucres/tavon Žucker`, x=1426) carry no number
     * of their own. That trailing clause is exactly what the merged-row guard is for, and the guard
     * is right to distrust it. But its verdict was applied to the whole row, so a tap on `8,9` was
     * answered with a fact about a clause 500 px to its right, and the screen said *"This looks like
     * sugars or fibre"* about the carbohydrate value.
     *
     * ## Why a boundary is safe where a classification would not be
     *
     * The guard protects against *reading a value out of* a clause that may not be what it looks
     * like. This function reads no value. The strongest thing a caller can conclude from it is
     * "the finger was left of the sugars word", and being wrong about that costs a tap, not a
     * number — whereas the automatic path being wrong costs a carbohydrate figure.
     *
     * So the row's classification is untouched: on both truffle captures it stays
     * `CARBOHYDRATE_CHILD`, `8`/`13`/`8,9` stay non-automatic, and every suppression rule still runs
     * on anything the tap reaches.
     */
    fun totalCarbohydrateClause(row: LogicalRow): Segment? {
        if (ParserWorkCounters.enabled) ParserWorkCounters.segmentationCalls++

        // **Element-wise, not the greedy span walk [segmentsOf] uses**, and the difference is not an
        // optimisation — the span walk cannot answer this question at all.
        //
        // That walk tries the longest span first and tests exclusions before totals, so on
        // `Koolhydraten 12 g waarvan suikers 3 g` it matches the four-element span
        // `koolhydraten 12 g waarvan suikers` as one **child** term and consumes the carbohydrate
        // word inside it. Exactly one term is found, it is a child, and no total clause exists —
        // which is correct for the question that walk answers (*may this row be read as a total
        // row?*) and useless for this one (*which printed clause did the finger land on?*).
        //
        // Both properties are right where they belong: a merged row must not be *read* as a total,
        // and the words `Koolhydraten` and `suikers` are nonetheless printed in different places on
        // the package, which is what the user is pointing at. So the boundary is found by asking
        // which **element** names which nutrient, independently of how the spans group.
        val naming = namingElements(row)
            .filter { it.kind != NutritionRowKind.OTHER }
            .map { it.kind to it.element }

        // The **first** element naming the total opens the clause.
        //
        // Not `singleOrNull`: multilingual packaging routinely prints one nutrient several times in
        // a row, and `141703` names it twice — `Nolhydraten/Glucides` at x=333 and `Kohlenhydrate`
        // at x=649. Those are the same declaration in two languages, not two clauses, and refusing
        // the row over them would fail exactly the captures this exists for.
        //
        // Taking the first is what makes them one clause: the run of consecutive total-naming
        // elements is absorbed, because the boundary below is the first *differently*-named element
        // and no total-naming element can end a total clause.
        val total = naming
            .firstOrNull { it.first == NutritionRowKind.TOTAL_CARBOHYDRATE }
            ?.second
            ?: return null

        // A child named **before** the total means the row's own leading clause is a child one —
        // the merged Croatian/German shape where `od kojih šećeri` precedes `Kohlenhydrate`. There
        // the carbohydrate word belongs to the sugars declaration and no total clause opens at all.
        if (naming.any {
                it.first == NutritionRowKind.CARBOHYDRATE_CHILD && it.second.box.left < total.box.left
            }
        ) {
            return null
        }

        // The clause runs to the next nutrient-naming element, exclusive — the same boundary rule
        // [segmentsOf] uses, resolved to the printed word rather than to a span's start. On
        // `141703` the child span begins at the damaged unit glyph `a,` (x=900), which is the `g`
        // belonging to the total's own value `8,9` at x=851; taking the span's start would put that
        // value's own unit outside its own clause. The naming element (`suikers/dant`, x=1251) is
        // the boundary a person can actually see, which is the only one they can aim relative to.
        val boundary = naming
            .firstOrNull {
                it.first != NutritionRowKind.TOTAL_CARBOHYDRATE &&
                    it.second.box.left > total.box.left
            }
            ?.second?.box?.left

        return Segment(
            kind = NutritionRowKind.TOTAL_CARBOHYDRATE,
            term = NutritionTerminology.normalize(total.text),
            startX = total.box.left,
            endX = boundary ?: Int.MAX_VALUE,
        )
    }

    /**
     * Which nutrient's clause a tap at [tappedX] landed in, or null when the row names none.
     *
     * The companion of [totalCarbohydrateClause], for the mirror-image defect. That one stops a
     * merged row's *total* clause being answered "this looks like sugars". This one lets a caller
     * see that the finger was in some **other** nutrient's clause entirely — the fat line, the salt
     * line — on a row that merely happens to name a child somewhere along it.
     *
     * ### The measured failure
     *
     * `20260903-212700-478` reconstructs the pickle's fat row together with a slice of the
     * ingredient list printed beside it:
     *
     * ```
     * aDin, suiker, zout   vetten,   0,2 g   0,06 g
     *        ^x=213         ^x=602    ^1075   ^1313
     * ```
     *
     * The word `suiker` belongs to the ingredient list; `vetten` (fat) and both values belong to the
     * table. [RowClassifier] types the whole row `CARBOHYDRATE_CHILD`, which is correct and
     * unchanged — the row genuinely cannot be *read* as a total. But every tap on it, including a
     * tap on the fat figures 800 px away from the word `suiker`, was answered *"This looks like
     * sugars or fibre."* That statement is simply false about where the finger was, and it is the
     * message the user is given instead of a way forward.
     *
     * ### The rule
     *
     * A tap belongs to the clause of the **last nutrient named at or before it**, which is reading
     * order — the same argument [CarbohydrateTermAnchor] makes about which nutrient owns a number. A
     * tap before every nutrient name on the row belongs to the first clause, so the bullet of a
     * `- suikers 2,3 g` row is still a sugars tap.
     *
     * This decides only which **message** is shown. It reads no value, promotes no row and relaxes
     * no suppression: [RecoveryCandidates] still offers nothing from a child row, so a tap here can
     * never produce a number it could not produce before.
     */
    fun nutrientClauseKindAt(row: LogicalRow, tappedX: Int): NutritionRowKind? {
        val naming = namingElements(row)
        if (naming.isEmpty()) return null
        val owner = naming.lastOrNull { it.element.box.left <= tappedX } ?: naming.first()
        return owner.kind
    }

    /** A nutrient name printed on the row, attributed to the element that begins it. */
    private data class NamingElement(
        val kind: NutritionRowKind,
        val term: String,
        val element: OcrElement,
    )

    /**
     * Every nutrient name printed on [row], attributed to the element that **begins** it.
     *
     * ### One element at a time, deliberately
     *
     * This is the walk [totalCarbohydrateClause] has always used, extracted so
     * [nutrientClauseKindAt] asks the same question of the same words rather than restating it.
     *
     * It is **not** the greedy span walk [nutrientTermsIn] uses, and the difference is the point:
     * that walk tries the longest span first and would match the four-element child term
     * `koolhydraten 12 g waarvan suikers` starting at the carbohydrate word, recording it as naming
     * a *child*. Right for its own question (*may this row be read as a total row?*), useless for
     * this one (*which printed clause did the finger land on?*).
     *
     * Multi-element nutrient names — `Hidratos de carbono`, the hyphenated `Kool-hydraten` — are
     * therefore invisible here, and that is a known limit rather than an oversight. A two-pass
     * variant that fills the gaps with spans was written and measured against this repo's whole
     * corpus: it changed no outcome anywhere, and it moved the Dutch `waarvan suikers` boundary from
     * the printed word `suikers` back to `waarvan`, failing [MergedRowTapBoundTest] and
     * [SeventhSessionRegressionTest] — device-measured behaviour from the seventh session. It is not
     * kept, because a capability with no measured beneficiary and a measured cost is not a
     * capability. Add it when a merged row on such a label actually appears in a session.
     */
    private fun namingElements(row: LogicalRow): List<NamingElement> {
        if (ParserWorkCounters.enabled) ParserWorkCounters.segmentationCalls++
        return row.elements.mapNotNull { element ->
            val text = NutritionTerminology.normalize(element.text)
            if (text.isEmpty()) return@mapNotNull null
            kindOfTerm(text)?.let { NamingElement(it, text, element) }
        }
    }

    /** A nutrient name found on the row, with where it starts. */
    private data class Term(val kind: NutritionRowKind, val text: String, val box: OcrBox)

    /**
     * Every nutrient name printed on [row], in reading order.
     *
     * Matching walks element spans rather than the joined row text because a term's *position* is
     * what bounds a segment, and a match against the joined string cannot be mapped back to a box.
     * Child terms are checked before total terms at each position for the same reason
     * [RowClassifier] checks them first: `Total Sugars` names a child, and must not be read as a
     * total because it happens to contain the word `total`.
     */
    private fun nutrientTermsIn(row: LogicalRow): List<Term> {
        val found = mutableListOf<Term>()
        val elements = row.elements
        var index = 0

        while (index < elements.size) {
            var matched = false
            // Longest span first so "Total Sugars" is preferred over a bare "Sugars" starting one
            // element later, and "Total Carb." over "Carb.".
            for (length in minOf(MAX_TERM_SPAN, elements.size - index) downTo 1) {
                val span = elements.subList(index, index + length)
                val text = NutritionTerminology.normalize(span.joinToString(" ") { it.text })
                if (text.isEmpty()) continue

                val kind = kindOfTerm(text) ?: continue
                val box = span.drop(1).fold(span.first().box) { acc, e -> acc.union(e.box) }
                found += Term(kind, text, box)
                index += length
                matched = true
                break
            }
            if (!matched) index++
        }

        return found
    }

    /**
     * Which nutrient a span names, or null for anything else.
     *
     * Children are tested first, exactly as [RowClassifier] does, so a child term containing a total
     * term's word cannot be read as a total.
     */
    private fun kindOfTerm(normalizedSpan: String): NutritionRowKind? {
        if (NutritionTerminology.exclusionTerms.any { NutritionTerminology.containsTerm(normalizedSpan, it) }) {
            return NutritionRowKind.CARBOHYDRATE_CHILD
        }
        if (NutritionTerminology.carbohydrateTerms.any { NutritionTerminology.containsTerm(normalizedSpan, it) }) {
            return NutritionRowKind.TOTAL_CARBOHYDRATE
        }
        val padded = " $normalizedSpan "
        if (OTHER_NUTRIENTS_PADDED.any { padded.contains(it) }) {
            return NutritionRowKind.OTHER
        }
        return null
    }

    /**
     * Nutrient names that are neither the total nor one of its children, but which still **end** a
     * segment.
     *
     * They matter only as boundaries: a `Protein 2g` following the carbohydrate clause must not have
     * its value fall inside the carbohydrate segment. Kept deliberately short and confined to the
     * words that appear on a nutrition panel — this is not a general nutrient vocabulary, and adding
     * to it widens only where a segment *stops*, never what may be read as carbohydrate.
     */
    private val OTHER_NUTRIENTS = setOf(
        "protein", "proteins", "eiwitten", "proteines", "eiweiss",
        "fat", "total fat", "sat fat", "saturated fat", "trans fat", "vetten", "matieres grasses",
        "sodium", "salt", "zout", "sel", "cholest", "cholesterol",
        "calories", "energie", "energy",
        "vit", "vitamin", "calcium", "iron", "potas", "potassium",
    )

    /**
     * [OTHER_NUTRIENTS] pre-normalized and space-padded, for the same reason
     * [NutritionTerminology.NORMALIZED_TERM_CACHE] exists: this set is a compile-time constant
     * consulted once per candidate span, and normalizing it per comparison was part of the 98.5% of
     * parser time spent re-normalizing unchanging strings.
     *
     * Kept here rather than added to that cache because these words are segment *boundaries*, not
     * nutrition vocabulary — they must never be reachable from the carbohydrate or exclusion lists.
     */
    private val OTHER_NUTRIENTS_PADDED: List<String> by lazy {
        OTHER_NUTRIENTS.map { " ${NutritionTerminology.normalize(it)} " }
    }

    /** Longest nutrient phrase worth trying, e.g. "of which saturated fat". */
    private const val MAX_TERM_SPAN = 4
}
