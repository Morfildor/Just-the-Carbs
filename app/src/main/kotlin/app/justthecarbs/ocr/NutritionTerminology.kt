package app.justthecarbs.ocr

import app.justthecarbs.domain.BasisUnitSpellings
import app.justthecarbs.domain.NutritionBasis
import java.text.Normalizer
import java.util.Locale

/** One language's printed nutrition-table terms. Add languages here, not in parser logic. */
data class NutritionTerms(
    val language: String,
    val carbohydrate: Set<String>,
    val exclusions: Set<String>,
    val serving: Set<String>,
)

/**
 * Latin-script nutrition terminology used on European packaging.
 *
 * Diacritics are retained here for reviewability, then normalized for OCR matching. Exclusions are
 * deliberately language-specific: a row containing any of them cannot become a total-carbohydrate
 * anchor even if it repeats the total label.
 */
object NutritionTerminology {
    val languages: List<NutritionTerms> = listOf(
        // The named sugars are the same words across these Latin-script languages, so they live in
        // the shared English set rather than being repeated in each one.
        // `total carb` is the US Nutrition Facts abbreviation, printed as `Total Carb.` — normalization
        // drops the period, so that is the form matched. Added 2026-09-01 from the recognized text of
        // a real Korean sauce panel (`docs/Scan Evidence 01-09-26/20260901-211550-678`), where its
        // absence meant the row stated no carbohydrate term at all and the printed 6 g was
        // unreachable.
        //
        // The two-word form is deliberate. A bare `carb` would match the first four letters of
        // nothing useful that `carbohydrate` does not already cover, while `total carb` is anchored
        // by a word that only introduces a nutrient total — and [NutritionTerminology.containsTerm]
        // matches whole space-delimited phrases, so it cannot fire inside a longer word.
        NutritionTerms("en", setOf("carbohydrate", "carbohydrates", "total carb", "total carbs"), setOf("of which sugars", "sugars", "sugar", "added sugars", "added sugar", "fibre", "fiber", "starch", "polyols", "polyol", "dextrose", "glucose", "fructose", "sucrose", "lactose", "maltose", "maltodextrin", "glucose syrup"), setOf("serving", "portion")),
        // Dutch is the app owner's own market and was extended on 2026-08-26 after measuring, not
        // after guessing — see DutchLabelDiagnosticTest, which prints what each printed form does.
        //
        // What the measurement showed: a merged total+child row whose child term is NOT listed here
        // comes back `Ambiguous [62.0, 35.0]`. That is not a confident-wrong — the architecture holds
        // — but it asks someone about to dose insulin to choose between the total and the sugars
        // figure with nothing on screen to tell them which is which. Listing the term turns that into
        // `NotFound`, which is the documented correct outcome for a row the parser cannot separate.
        //
        // The Dutch-specific sugar names are the reason the shared English list was not enough:
        // Dutch prints "sacharose" where English prints "sucrose", and uses transparent compounds —
        // melksuiker (lactose), druivensuiker (dextrose), vruchtensuiker (fructose) — that share no
        // stem with their Latin equivalents.
        NutritionTerms(
            "nl",
            setOf(
                "koolhydraten", "koolhydraat", "totale koolhydraten",
                // Abbreviated on small packs.
                "koolhydr",
                // Dutch hyphenates long compounds across a line; normalization turns the hyphen into
                // a space, so the printed "Kool-hydraten" arrives here as two words.
                "kool hydraten",
            ),
            setOf(
                "waarvan suikers", "suikers", "suiker",
                "sacharose", "saccharose", "melksuiker", "druivensuiker", "vruchtensuiker",
                "invertsuiker", "rietsuiker", "kristalsuiker", "glucosestroop",
                "vezels", "vezel", "voedingsvezels", "voedingsvezel", "vezelstoffen",
                "zetmeel", "zetmelen",
                "polyolen", "suikeralcoholen", "suikeralcohol", "meervoudige alcoholen",
            ),
            setOf("portie", "per portie"),
        ),
        NutritionTerms(
            "de",
            // Same hyphenation reasoning as Dutch: German splits "Kohlen-hydrate" across a line.
            setOf("kohlenhydrate", "kohlenhydrat", "kohlen hydrate"),
            setOf("davon zucker", "zucker", "ballaststoffe", "stärke", "mehrwertige alkohole", "polyole", "milchzucker", "traubenzucker", "fruchtzucker", "saccharose"),
            setOf("portion", "pro portion"),
        ),
        NutritionTerms("fr", setOf("glucides"), setOf("dont sucres", "sucres", "fibres alimentaires", "fibres", "amidon", "polyols"), setOf("portion", "par portion")),
        NutritionTerms("es", setOf("hidratos de carbono", "carbohidratos"), setOf("de los cuales azúcares", "azúcares", "fibra alimentaria", "fibra", "almidón", "polialcoholes", "polioles"), setOf("porción", "por porción")),
        NutritionTerms("it", setOf("carboidrati"), setOf("di cui zuccheri", "zuccheri", "fibre", "amido", "polioli"), setOf("porzione", "per porzione")),
        NutritionTerms("pt", setOf("hidratos de carbono", "carboidratos"), setOf("dos quais açúcares", "açúcares", "fibra", "amido", "polióis"), setOf("porção", "por porção")),
        NutritionTerms("tr", setOf("karbonhidrat"), setOf("şekerler", "şeker", "lif", "posa", "nişasta", "polioller"), setOf("porsiyon", "porsiyon başına")),
        NutritionTerms("pl", setOf("węglowodany"), setOf("w tym cukry", "cukry", "błonnik", "skrobia", "poliole"), setOf("porcja", "w porcji")),
        NutritionTerms("da", setOf("kulhydrat", "kulhydrater"), setOf("heraf sukkerarter", "sukkerarter", "kostfibre", "stivelse", "polyoler"), setOf("portion", "pr portion")),
        NutritionTerms("sv", setOf("kolhydrat", "kolhydrater"), setOf("varav sockerarter", "sockerarter", "fiber", "kostfiber", "stärkelse", "polyoler"), setOf("portion", "per portion")),
        NutritionTerms("no", setOf("karbohydrat", "karbohydrater"), setOf("hvorav sukkerarter", "sukkerarter", "kostfiber", "stivelse", "polyoler"), setOf("porsjon", "per porsjon")),
        NutritionTerms("fi", setOf("hiilihydraatti", "hiilihydraatit"), setOf("josta sokereita", "sokerit", "ravintokuitu", "kuitu", "tärkkelys", "polyolit"), setOf("annos", "annosta kohden")),
        NutritionTerms("cs", setOf("sacharidy"), setOf("z toho cukry", "cukry", "vláknina", "škrob", "polyoly"), setOf("porce", "na porci")),
        NutritionTerms("ro", setOf("glucide", "carbohidrați"), setOf("din care zaharuri", "zaharuri", "fibre", "amidon", "polioli"), setOf("porție", "per porție")),
        // Added 2026-08-16 by reading the actual ML Kit output for the real Kinder package, whose
        // panel carries all five of these alongside NL/FR/DE. Not speculative coverage: each term
        // below was observed in the recognized text of a photograph in this repo's test assets.
        //
        // The Croatian entry is already load-bearing rather than decorative. ML Kit merged
        // "od kojih šećeri" with "Kohlenhydrate" onto one recognized row on that photograph; without
        // "šećeri" as an exclusion that row types as TOTAL_CARBOHYDRATE on the strength of the
        // German word, which is a sugars-row-as-total waiting for a frame where it carries numbers.
        //
        // Diacritics are written out for reviewability and normalized away on both sides before
        // matching, so "šećeri" is compared as "seceri" and Macedonian "шеќери" as "шекери".
        // Estonian, Latvian and Lithuanian, added 2026-09-01 from the recognized text of a real
        // Nordic/Baltic package (`docs/Scan Evidence 01-09-26/20260901-211619-534`). Not speculative
        // coverage: every term below was read off that photograph.
        //
        // The measured consequence of their absence was a lost reading, not a wrong one, and the
        // mechanism is worth recording. That label prints its carbohydrate declaration across two
        // recognised rows — the Nordic names on the first, `Oglhidrāti/Angliavandeniai` and the
        // values on the second. The first row classified (Swedish `Kolhydrat` is listed), but the
        // row carrying the numbers named carbohydrate only in Latvian and Lithuanian, so it typed
        // OTHER and the printed 59,2 g was unreachable: `Total-carbohydrate row found but no usable
        // per-100 cell`.
        //
        // `süsivesikud` is Estonian; `ogļhidrāti` Latvian (OCR reliably drops the cedilla, so the
        // undiacriticked `oglhidrati` is what normalization compares); `angliavandeniai` Lithuanian.
        // The child terms come from the same panel's sugars and fibre rows.
        NutritionTerms(
            "et",
            setOf("süsivesikud", "susivesikud"),
            setOf("millest suhkrud", "suhkrud", "kiudained", "tärklis", "polüoolid"),
            setOf("portsjon", "portsjonit"),
        ),
        NutritionTerms(
            "lv",
            setOf("ogļhidrāti", "oglhidrati"),
            setOf("tostarp cukuri", "cukuri", "šķiedrvielas", "skiedrvielas", "ciete", "polioli"),
            setOf("porcija", "porcijas"),
        ),
        NutritionTerms(
            "lt",
            setOf("angliavandeniai"),
            setOf("iš kurių cukrų", "cukrų", "cukru", "skaidulinės medžiagos", "skaidulines medziagos", "krakmolas", "polioliai"),
            setOf("porcija", "porcijos"),
        ),
        NutritionTerms("hr", setOf("ugljikohidrati"), setOf("od čega šećeri", "od kojih šećeri", "šećeri", "šećer", "vlakna", "škrob", "polioli"), setOf("porcija", "po porciji")),
        NutritionTerms("sl", setOf("ogljikovi hidrati"), setOf("od tega sladkorji", "sladkorji", "sladkor", "vlaknine", "škrob", "polioli"), setOf("porcija", "na porcijo")),
        NutritionTerms("sr", setOf("ugljeni hidrati"), setOf("od kojih šećeri", "šećeri", "vlakna", "skrob"), setOf("porcija", "na porciju")),
        NutritionTerms("mk", setOf("јаглехидрати"), setOf("од кои шеќери", "шеќери", "шеќер"), setOf("порција")),
        NutritionTerms("sq", setOf("karbohidrate"), setOf("nga të cilat sheqerna", "sheqerna", "sheqer"), setOf("porcion")),
    )

    /**
     * Words that introduce a column header ("per 100 g", "par pièce", "na porciju").
     *
     * One list, because three stages need it — [ColumnClassifier] to find a serving column,
     * [RowClassifier] to recognise the row it sits on, and [InlineBasisSpans] to find a basis printed
     * inside a value row. They previously kept private copies with a comment asking the reader to
     * keep them in step, which is the arrangement that drifts.
     */
    internal val connectives = setOf("per", "pro", "par", "pr", "na", "w", "voor")

    /**
     * The basis-unit spellings, from the one place both layers can see
     * ([app.justthecarbs.domain.BasisUnitSpellings]).
     *
     * Re-exported here rather than imported at each call site so the four OCR stages that need it —
     * [ColumnClassifier], [RowClassifier], [InlineBasisSpans] and [ProseNutritionReader] — keep
     * reading their vocabulary from one object, which is what this file is for. Each of them held a
     * private `g|ml` literal until 2026-08-26, and fixing one of the four was not enough: a row must
     * be typed `HEADER` before the column vocabulary is ever consulted.
     */
    internal val millilitreUnits = BasisUnitSpellings.millilitre
    internal val basisUnitAlternation = BasisUnitSpellings.alternation

    internal fun basisUnitFor(normalizedWord: String): NutritionBasis? =
        BasisUnitSpellings.basisFor(normalizedWord)

    internal val carbohydrateTerms = languages.flatMap { it.carbohydrate }.distinct()
    internal val exclusionTerms = languages.flatMap { it.exclusions }.distinct()
    internal val servingTerms = languages.flatMap { it.serving }.distinct()

    /**
     * Every vocabulary term, pre-normalized and wrapped in the spaces [containsTerm] compares with.
     *
     * ### The measured defect (2026-09-01)
     *
     * `containsTerm` normalized its `term` argument on **every call**, and `term` is always one of
     * these compile-time constants. Profiling the 79-element capture from
     * `docs/Scan Evidence 01-09-26 2nd test/20260901-222212-563/` counted **225,360** calls to
     * [normalize], of which **222,076 (98.5%)** were re-normalizing vocabulary that had not changed
     * since the class loaded. Each call runs an NFD decomposition plus five regex replacements and
     * allocates six intermediate strings.
     *
     * On the JVM that cost 105 ms. On the device it was part of a **9278 ms** parse, against 341–505
     * ms measured for the same product before the vocabulary grew — mobile ART pays far more for
     * allocation-heavy regex work than a warm desktop JIT does, which is why a change that looked
     * free in the test suite was not free in the hand.
     *
     * ### Why a map rather than normalizing the lists in place
     *
     * Callers pass raw terms from several sources — the three vocabulary lists, plus
     * [NutrientRowSegments]'s own boundary set — so the cache has to key on the raw string rather
     * than assume the caller pre-normalized. Terms are added to this file by hand and the map is
     * built once on first use from those same lists, so a term missing from it is a term the
     * *caller* invented, which falls through to the uncached path and still works.
     *
     * ### Why it populates on demand rather than being built from the lists
     *
     * Vocabulary reaches [containsTerm] from more places than this file declares — the three lists
     * here, [NutrientRowSegments]'s boundary words, [CarbohydrateTermAnchor.OTHER_NUTRIENT_TERMS] —
     * and enumerating them all here would couple this object to every caller and go stale the first
     * time someone adds a set. Filling the map on first sight of a term keeps one cache without that
     * coupling: each distinct term is normalized exactly once for the life of the process, whoever
     * introduces it.
     *
     * The map is bounded because it is keyed on caller-supplied strings. Every real caller passes
     * compile-time constants, so it settles at a few hundred entries and never grows again; the
     * bound is what stops a future caller passing recognized *text* from turning a cache into a
     * leak. Clearing wholesale on overflow costs a re-normalization, never a wrong answer.
     */
    private val normalizedTerms = HashMap<String, String>(512)

    /** See [normalizedTerms]. Comfortably above the ~400 terms every caller in this repo supplies. */
    private const val MAX_CACHED_TERMS = 4096

    internal fun normalize(text: String): String {
        if (ParserWorkCounters.enabled) ParserWorkCounters.normalizeCalls++
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(NON_WORD, " ")
            .trim()
            .replace(MULTIPLE_SPACES, " ")
            .replace(RUN_TOGETHER_BASIS, "$0 ")
    }

    /**
     * A basis unit immediately followed by a digit, with the space between two column headers lost.
     *
     * ### The measured defect (P1-3)
     *
     * Two captures of one Fanta Zero can, 36 seconds apart
     * (`docs/Scan evidence 31-08-26/`):
     *
     * ```
     * 140132-710:  '100 ml 250 m'  -> HEADER -> column PER_100_ML   (correct)
     * 140208-173:  '100 ml250 ml'  -> OTHER  -> no columns at all
     * ```
     *
     * The whitespace is the only difference. Both `RowClassifier.isHeaderLike` and
     * `ColumnClassifier`'s `PER_100` require the unit to be followed by end-of-string or whitespace,
     * so `ml250` matches neither, the row is typed `OTHER`, and no column is ever resolved. The user
     * is then asked "per what?" and offered `/100 g` on a drink whose only unit anywhere is `ml`.
     *
     * ### Why the repair lives here
     *
     * `normalize` is the one function both classifiers already route through, so fixing it here means
     * the row-level and column-level views of a header cannot disagree about where it ends — the
     * exact drift that cost the Kinder per-piece column once already.
     *
     * ### Why this does not loosen anything
     *
     * It inserts a space; it does not create a basis. The unit must already be spelled correctly and
     * already be attached to its quantity, and each resulting phrase must still be recognised on its
     * own by the ordinary vocabulary. `100250` gains nothing (no unit), `1081stoffen` gains nothing
     * (not a unit), and a prose row containing `100 g` is no more a header than it was.
     */
    private val RUN_TOGETHER_BASIS = Regex(
        "\\d{1,4}\\s*(?:${BasisUnitSpellings.alternation})(?=\\d)",
    )

    /**
     * Whether [normalizedText] states [term] as a whole space-delimited phrase.
     *
     * [term] is looked up in [NORMALIZED_TERM_CACHE] rather than normalized per call — see that
     * property for the measurement that made this necessary. A term not in the cache is normalized
     * on the spot, so behaviour is unchanged for any caller passing a string this file does not
     * declare; only the cost differs.
     *
     * The padding is part of the cached value because the whole-phrase comparison needs it on both
     * sides, and building `" $x "` per call was itself two of the allocations being paid for.
     */
    internal fun containsTerm(normalizedText: String, term: String): Boolean {
        var paddedTerm = normalizedTerms[term]
        if (paddedTerm == null) {
            if (ParserWorkCounters.enabled) ParserWorkCounters.termNormalizeMisses++
            paddedTerm = " ${normalize(term)} "
            if (normalizedTerms.size >= MAX_CACHED_TERMS) normalizedTerms.clear()
            normalizedTerms[term] = paddedTerm
        }
        return " $normalizedText ".contains(paddedTerm)
    }

    /**
     * [term] normalized and split into its space-delimited words, cached.
     *
     * The word-list form a phrase matcher needs, for the same reason and with the same bound as
     * [normalizedTerms]: the caller walks a token stream and asks about **every** vocabulary term at
     * **every** position, so normalizing the term inside that loop is quadratic in exactly the way
     * the 2026-09-01 regression was.
     *
     * Measured on `docs/Scan Evidence 02-09 4th test/20260902-141642-529` — a 289-element capture
     * whose multilingual ingredient panel reaches the prose reader — where
     * [ProseNutritionReader.longestTermAt] alone accounted for ~49,000 of the parse's 51,792
     * `normalize` calls, and the device spent **1432 ms** parsing it against 55–199 ms for every
     * other capture in the same session.
     *
     * Returned list is shared and must not be mutated by callers; every call site reads it only.
     */
    internal fun termWords(term: String): List<String> {
        var words = termWordLists[term]
        if (words == null) {
            if (ParserWorkCounters.enabled) ParserWorkCounters.termNormalizeMisses++
            words = normalize(term).split(' ').filter(String::isNotBlank)
            if (termWordLists.size >= MAX_CACHED_TERMS) termWordLists.clear()
            termWordLists[term] = words
        }
        return words
    }

    /** See [termWords]. Same bound and same overflow behaviour as [normalizedTerms]. */
    private val termWordLists = HashMap<String, List<String>>(512)

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val MULTIPLE_SPACES = Regex("\\s+")
}
