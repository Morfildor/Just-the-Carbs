package app.justthecarbs.ocr

/**
 * Recognises a total-carbohydrate row whose **label** OCR damaged, using structural evidence from
 * the table around it rather than by loosening the carbohydrate vocabulary.
 *
 * ## The failure this exists for
 *
 * `docs/Scan Evidence 01-09-26 2nd test/20260901-222212-563/` is a clean, well-framed capture of a
 * drink. ML Kit read the table correctly — the header, the columns, the values, the sugars row — and
 * damaged one word:
 *
 * ```
 * printed         recognised
 * Koolhydraten:   laolhydraten:     (k -> l, o -> a)
 * ```
 *
 * The row reconstructs perfectly and carries both its values:
 *
 * ```
 * [OTHER]              'laolhydraten: 0.5g 13g'
 * [CARBOHYDRATE_CHILD] 'Waarvan suikers: 0.59 1.3'
 * ```
 *
 * Because the word matched no vocabulary the row typed `OTHER`, the table had no total row, and a
 * capture with everything else correct returned `NotFound` after the user had waited.
 *
 * ## Why this is not "loosening the vocabulary"
 *
 * The brief was explicit that arbitrary partial-word matching is forbidden, and it would be
 * dangerous: a fuzzy match on `koolhydraten` reaches `koolzaad`, and on a row that also names
 * sugars it would hand back the sugars figure as the total. That is the failure this whole
 * architecture exists to prevent.
 *
 * So the word alone is never enough here. Every one of the following must hold, and each is a fact
 * about the **table**, not about the string:
 *
 * 1. **The document is a confirmed nutrition table** — it resolved at least one per-100 basis
 *    column. Prose, ingredient lists and marketing copy resolve none, so they are unreachable.
 * 2. **The damaged row is immediately followed by a child row.** On every European label the sugars
 *    line is printed directly beneath the carbohydrate line and indented under it; nothing else
 *    occupies that position. This is the strongest single signal and it is positional, not textual.
 * 3. **The word ends in a carbohydrate suffix** — `hydraten`, `hydrate`, `hydrat`, `hidrati`. OCR
 *    damages the *start* of a word far more often than the end (the leading capital is where a
 *    clipped label loses characters), and these suffixes appear in no other word on a food package.
 * 4. **The row carries a value in an established column.** A row with no value in a resolved column
 *    cannot supply the total whatever it is called.
 * 5. **The row names no child term itself.** A row naming sugars is a child row, unconditionally and
 *    unchanged — this recovery can never override that rule, only reach a row it never covered.
 * 6. **No undamaged total row already exists.** If the table read normally, nothing here runs.
 *
 * ## What it deliberately will not do
 *
 * - It never substitutes the sugars row. Condition 2 requires the child row to be a *different,
 *   following* row, and condition 5 excludes the candidate itself naming a child.
 * - It never repairs the word or the number. It decides only whether a row may be *typed* as the
 *   total; which cell is read, and under which basis, stays with the unchanged column stages.
 * - It does not fire on `bydraten.` from `…222300-297`, and that is correct rather than a gap: the
 *   leading `koolhy` is gone, so no carbohydrate suffix survives, and the printed word is
 *   unrecoverable evidence of anything. That capture stays `NotFound` by design.
 */
internal object DamagedCarbohydrateLabel {

    /**
     * The index of a row that should be treated as the total-carbohydrate row despite its label not
     * matching the vocabulary, or null when no row qualifies.
     *
     * Only ever consulted after ordinary classification has found no total row — see
     * [NutritionTableInterpreter]. Returns null unless exactly one row qualifies: two candidates is
     * an ambiguity, and picking one would be the arbitrary choice this architecture refuses.
     */
    fun recoverTotalRowIndex(
        rows: List<LogicalRow>,
        kinds: List<NutritionRowKind>,
        columns: List<NutritionColumn>,
    ): Int? {
        // Condition 6: an undamaged total row means nothing here runs.
        if (kinds.any { it == NutritionRowKind.TOTAL_CARBOHYDRATE }) return null

        // Condition 1: a confirmed nutrition table. A per-100 column is the strongest available
        // evidence that this document is a table at all, and it is the column the recovered row's
        // value would have to be read under anyway.
        val basisColumns = columns.filter {
            it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
        }
        if (basisColumns.isEmpty()) return null

        val candidates = rows.indices.filter { index ->
            qualifies(rows, kinds, index, basisColumns)
        }

        return candidates.singleOrNull()
    }

    private fun qualifies(
        rows: List<LogicalRow>,
        kinds: List<NutritionRowKind>,
        index: Int,
        basisColumns: List<NutritionColumn>,
    ): Boolean {
        // Condition 5, first: a row naming a child is a child row, unconditionally. Checked before
        // anything else so this can never reach a row the child rule already governs.
        if (kinds[index] != NutritionRowKind.OTHER) return false

        val row = rows[index]
        val normalized = NutritionTerminology.normalize(row.text)

        // Condition 3: the word ends in a carbohydrate suffix.
        if (!statesADamagedCarbohydrateWord(normalized)) return false

        // Condition 5 again, explicitly: a row whose text names any child term is excluded even if
        // its kind came back OTHER for some other reason.
        if (NutritionTerminology.exclusionTerms.any { NutritionTerminology.containsTerm(normalized, it) }) {
            return false
        }

        // Condition 2: the next row is the child row. Positional and load-bearing — the sugars line
        // is printed directly beneath the carbohydrate line, and this is what distinguishes a
        // damaged total row from any other unreadable row on the label.
        if (kinds.getOrNull(index + 1) != NutritionRowKind.CARBOHYDRATE_CHILD) return false

        // Condition 4: the row carries a value under an established column.
        return row.elements.any { element ->
            NUMERIC.containsMatchIn(element.text) &&
                basisColumns.any { column -> covers(column, element) }
        }
    }

    /** Whether [element] sits under [column], by the same nearest-centre rule the interpreter uses. */
    private fun covers(column: NutritionColumn, element: OcrElement): Boolean {
        val headerWidth = column.headerBox?.width ?: 0
        val halfWidth = maxOf(headerWidth, element.box.width).coerceAtLeast(1)
        return kotlin.math.abs(element.box.centerX - column.centerX) <= halfWidth
    }

    /**
     * Whether [normalizedText] contains a word ending in a carbohydrate suffix but not matching the
     * vocabulary outright.
     *
     * The suffixes are the tails of the words this app already recognises, and they are chosen
     * because **OCR damages the start of a word far more often than the end**: a clipped or
     * shadowed label loses its leading capital, and the misreads observed on real packaging
     * (`laolhydraten`, `Kool-hydraten`, `bydraten`) are all head damage. A suffix match therefore
     * costs little and catches the observed failures.
     *
     * They are also long enough to be specific: no ingredient, additive or marketing word on a food
     * package ends in `hydraten` or `hidrati`. A short suffix like `raten` would not have that
     * property and is deliberately not used.
     */
    internal fun statesADamagedCarbohydrateWord(normalizedText: String): Boolean {
        val words = normalizedText.split(' ').filter { it.isNotBlank() }
        return words.any { word ->
            word.length >= MIN_WORD_LENGTH && SUFFIXES.any { word.endsWith(it) }
        }
    }

    /**
     * The tails of the carbohydrate words in [NutritionTerminology], across the languages whose
     * spelling actually ends this way.
     *
     * Deliberately not derived from the vocabulary automatically: `glucides` and `sacharidy` would
     * contribute tails that collide with ordinary words, and the point of this list is that every
     * entry is specific enough to stand alone as evidence.
     */
    private val SUFFIXES = listOf(
        "hydraten", "hydrate", "hydrates", "hydrat", "hydrati",
        "hidrati", "hidrate", "hydraat",
    )

    /**
     * How long a word must be before its suffix is evidence.
     *
     * Eight, so a suffix match always rests on at least one recognised character before the tail —
     * the whole word cannot *be* the suffix. Guards against a fragment like `hydrat` on its own line
     * qualifying a row.
     */
    private const val MIN_WORD_LENGTH = 8

    private val NUMERIC = Regex("\\d")
}
