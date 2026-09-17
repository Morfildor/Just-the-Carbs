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
 *
 * ## Coverage (2026-09-17)
 *
 * The EU languages written in Latin script, plus Turkish, Norwegian and the Balkan languages seen on
 * real packs. The EU names follow the nutrition declaration of Regulation (EU) No 1169/2011 (Annex XV)
 * as printed in each language, cross-checked against Open Food Facts' multilingual nutrient taxonomy,
 * and each addition was measured first in `EuropeanLabelDiagnosticTest` and then against every
 * committed device capture: a word that changed what a real capture did was left out.
 *
 * Not read: Hungarian-only tables (see the `hu` entry for why). Greek and Bulgarian are absent
 * because ML Kit's bundled recognizer reads Latin script only, so their words never reach this file.
 * Irish and Maltese packaging is printed in English.
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
        // European languages, extended on 2026-09-17 after measuring (EuropeanLabelDiagnosticTest).
        // A merged total+child row naming a child word that was not listed — the singular sugar
        // (`sucre`, `zucchero`, `cukier`, `cukr`, `zahăr`, `sukker`, `socker`, `azúcar`, `açúcar`),
        // the official Polish and Czech polyols, Lithuanian `cukrūs` — came back
        // `Ambiguous [62, 35]`, the total and the child figure side by side. Listing the word makes
        // it `NotFound`, the documented outcome for a row the parser cannot separate.
        NutritionTerms(
            "fr",
            setOf("glucides"),
            setOf("dont sucres", "sucres", "sucre", "fibres alimentaires", "fibres", "amidon", "fécule", "fécules", "polyols"),
            setOf("portion", "par portion"),
        ),
        NutritionTerms(
            "es",
            setOf("hidratos de carbono", "carbohidratos"),
            setOf(
                "de los cuales azúcares", "azúcares", "azúcar",
                "fibra alimentaria", "fibra", "fibras", "almidón", "polialcoholes", "polioles",
            ),
            // `ración` is the usual Spanish word over a per-portion column (`por ración`).
            setOf("porción", "por porción", "ración", "por ración"),
        ),
        NutritionTerms(
            "it",
            setOf("carboidrati"),
            setOf("di cui zuccheri", "zuccheri", "zucchero", "fibre", "fibra", "amido", "fecola", "polioli", "polialcoli"),
            setOf("porzione", "per porzione"),
        ),
        NutritionTerms(
            "pt",
            setOf("hidratos de carbono", "carboidratos"),
            setOf("dos quais açúcares", "açúcares", "açúcar", "fibra", "fibras", "amido", "polióis", "poliálcoois"),
            setOf("porção", "por porção"),
        ),
        // Turkish, extended on 2026-09-17 after measuring (TurkishLabelDiagnosticTest), the same way
        // Dutch was. A merged total+child row naming any of the added child words came back
        // `Ambiguous [62.0, 35.0]`: Turkish spells the sugars `sakaroz`, `laktoz`, `glikoz` rather than
        // like the shared English list, and inflects `lif` to `diyet lifi` and `lifler`, which a
        // whole-word match on `lif` does not see. `karbonhidratlar` is the plural some labels print;
        // it read nothing at all.
        NutritionTerms(
            "tr",
            setOf("karbonhidrat", "karbonhidratlar"),
            setOf(
                "şekerler", "şeker", "şekerleri",
                "sakaroz", "laktoz", "glikoz", "fruktoz", "maltoz", "dekstroz",
                "maltodekstrin", "glikoz şurubu",
                "lif", "lifler", "diyet lifi", "posa",
                "nişasta", "polioller",
            ),
            // `Porsiyonda (30 g)` ("in a portion") is how a Turkish per-portion column is headed.
            setOf("porsiyon", "porsiyon başına", "porsiyonda"),
        ),
        NutritionTerms(
            "pl",
            setOf("węglowodany"),
            // `alkohole wielowodorotlenowe` is the official Polish name for polyols.
            setOf("w tym cukry", "cukry", "cukier", "błonnik", "skrobia", "poliole", "alkohole wielowodorotlenowe"),
            // `w porcji` ("in a portion"): the word alone ends the header, so it must be a term alone.
            setOf("porcja", "porcji", "w porcji"),
        ),
        NutritionTerms(
            "da",
            setOf("kulhydrat", "kulhydrater"),
            setOf("heraf sukkerarter", "sukkerarter", "sukker", "kostfibre", "stivelse", "polyoler"),
            setOf("portion", "pr portion"),
        ),
        NutritionTerms(
            "sv",
            setOf("kolhydrat", "kolhydrater"),
            setOf("varav sockerarter", "sockerarter", "socker", "fiber", "kostfiber", "stärkelse", "polyoler"),
            setOf("portion", "per portion"),
        ),
        NutritionTerms(
            "no",
            setOf("karbohydrat", "karbohydrater"),
            setOf("hvorav sukkerarter", "sukkerarter", "sukker", "kostfiber", "stivelse", "polyoler"),
            setOf("porsjon", "per porsjon"),
        ),
        // Finnish prints the sugars in the partitive after `josta` / `joista` ("of which"), so
        // `sokereita` must be a term on its own; a row reading `joista sokereita` matched nothing.
        NutritionTerms(
            "fi",
            setOf("hiilihydraatti", "hiilihydraatit"),
            setOf(
                "josta sokereita", "joista sokereita", "sokereita", "sokerit", "sokeri",
                "ravintokuitu", "ravintokuidut", "kuitu", "kuidut", "tärkkelys", "polyolit",
            ),
            setOf("annos", "annosta kohden", "annoksessa"),
        ),
        NutritionTerms(
            "cs",
            setOf("sacharidy"),
            // `polyalkoholy` is the official Czech name for polyols.
            setOf("z toho cukry", "cukry", "cukr", "vláknina", "škrob", "polyoly", "polyalkoholy"),
            setOf("porce", "porci", "na porci", "v porci"),
        ),
        NutritionTerms(
            "sk",
            setOf("sacharidy"),
            setOf("z toho cukry", "cukry", "cukor", "vláknina", "škrob", "polyoly", "alkoholické cukry"),
            setOf("porcia", "porcii", "na porciu", "v porcii"),
        ),
        // Hungarian: the child and portion words only. `Szénhidrát` is deliberately NOT a carbohydrate
        // term, so a Hungarian-only table still reads nothing (2026-09-17, measured, not guessed).
        //
        // On the Indomie capture (`SeventeenthSessionFixtures.c20260904_134501_895`) the multilingual
        // carbohydrate line wraps, and its Hungarian word sits on the next line, which the row builder
        // merged with the start of the sugars clause and its `29g` (2,9 g sugars). As a term,
        // `szénhidrát` made that a second total row: the sugars figure read as `Confident 29.0`, and,
        // with that guarded, focused entry lost its target because two total rows now print figures.
        // The capture reached focused entry on the device. Multilingual packs sold in Hungary name
        // the row in other languages too, which is what still reads them.
        NutritionTerms(
            "hu",
            emptySet(),
            setOf("amelyből cukrok", "cukrok", "cukor", "rost", "élelmi rost", "keményítő", "poliolok"),
            setOf("adag", "adagonként"),
        ),
        NutritionTerms(
            "ro",
            setOf("glucide", "carbohidrați"),
            setOf("din care zaharuri", "zaharuri", "zahăr", "fibre", "amidon", "polioli"),
            setOf("porție", "per porție"),
        ),
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
            // `portsjoni kohta` ("per portion") heads a per-portion column.
            setOf("portsjon", "portsjonit", "portsjoni"),
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
            // `iš kurių cukrūs` is the official form; only the genitive `cukrų` was listed, and a
            // merged row naming `cukrūs` offered the sugars figure beside the total.
            setOf(
                "iš kurių cukrūs", "iš kurių cukrų", "cukrūs", "cukrų", "cukru",
                "skaidulinės medžiagos", "skaidulines medziagos", "skaidulinių medžiagų",
                "krakmolas", "krakmolo", "polioliai", "poliolių",
            ),
            setOf("porcija", "porcijos", "porcijoje"),
        ),
        NutritionTerms(
            "hr",
            setOf("ugljikohidrati"),
            setOf("od čega šećeri", "od kojih šećeri", "šećeri", "šećer", "vlakna", "škrob", "polioli"),
            setOf("porcija", "porciji", "po porciji"),
        ),
        NutritionTerms(
            "sl",
            setOf("ogljikovi hidrati"),
            setOf("od tega sladkorji", "sladkorji", "sladkorjev", "sladkor", "vlaknine", "vlaknin", "škrob", "polioli"),
            setOf("porcija", "porcijo", "na porcijo"),
        ),
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
    // `je`, `pour`, `por`, `ve`, `v`, `u` and `la` added 2026-09-17 (German, French, Spanish and
    // Portuguese, Czech and Slovak, Croatian, Romanian). Without them a nutrient row printing its own
    // basis — `Hidratos de carbono por 100 g 62,5 g` — had its `100` read as the carbohydrate figure:
    // `Confident 100.0` in Spanish and Portuguese, measured in `EuropeanLabelDiagnosticTest`. The
    // connective is what marks `100 g` as a basis rather than an amount ([InlineBasisSpans]).
    internal val connectives = setOf("per", "pro", "par", "pr", "na", "w", "voor", "je", "pour", "por", "ve", "v", "u", "la")

    /**
     * The [connectives] a column header's measured extent may include. The words added on
     * 2026-09-17 (`je` onwards) are left out: [ColumnClassifier] already reads `100 g` without them,
     * and taking one in moves the column — `Pour` on the Turkish rice-flour capture is 90 px wide and
     * moved that header 52 px (`CrossColumnEvidenceReachTest`).
     */
    internal val columnHeaderConnectives = setOf("per", "pro", "par", "pr", "na", "w", "voor")

    /**
     * Words that mark a basis **after** it — `100 g kohta` (Estonian), `100 g kohden` / `kohti`
     * (Finnish), `100 g için` (Turkish). The same job as [connectives] for languages whose word for
     * "per" follows the quantity. Normalized spellings.
     */
    internal val postpositions = setOf("kohta", "kohden", "kohti", "icin")

    /**
     * Whether [text] is a basis unit carrying a case suffix — `g'da`, `ml'de`, `g-ban`, `g:ssa` —
     * which, like a connective, can only mean "in 100 g", never an amount of 100 g. See
     * [UNIT_CASE_SUFFIX] for the suffixes.
     */
    internal fun carriesCaseSuffix(text: String): Boolean =
        CASE_SUFFIXED_UNIT.matches(
            Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT),
        )

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

    /**
     * How the quantity `100` can be spelled once a recognizer has seen it.
     *
     * ## Why a lowercase `l` is admitted for `1`
     *
     * Measured on `docs/Scan Evidence 03-09 2nd test/20260903-143036-432`: ML Kit returned the
     * printed `per 100 g` as **`per l00 g`**. That one glyph cost the whole label — the row matched
     * no per-100 vocabulary, typed `OTHER` instead of `HEADER`, and `ColumnClassifier` only ever
     * looks at header rows, so **zero** columns resolved. With no basis there is no reading, and
     * [FocusedAmountEntry] returns null too, so even the escape hatch was unavailable.
     *
     * `1` and `l` are the same printed shape in most sans-serif faces; this is the same family as
     * the `(g)` -> `(9)` and `Ø` -> `o` misreads already recorded here.
     *
     * ## Why this is not the numeric repair this repo has refused
     *
     * The refused repair is one that changes a **value** — trimming a digit from `790`, or turning
     * `72` into `7.2`. Those are unfalsifiable from the app's side and produce a plausible wrong
     * number the user has no reason to check.
     *
     * This admits an alternative spelling of a **fixed literal** in a header, and the literal is the
     * one quantity that names a basis this app has. It cannot alter any value: nothing downstream
     * reads a number out of this token, and the only outcome of a match is that a column is
     * classified per-100 rather than left unresolved. A false match would have to be a header
     * printing `l00` beside a basis unit and meaning something else, which is not a thing packages
     * print.
     *
     * `0` and `O` are deliberately **not** interchanged. That would make `lOO`, `100`, `l00` and
     * `1OO` all equivalent, and `OO` appears inside ordinary words in a way `00` does not — the
     * narrower rule covers the measured failure and nothing more.
     */
    internal const val PER_100_QUANTITY_PATTERN = "[1l]00"

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

    /**
     * Test-only: clears both term caches, so a test measuring "cold parse fills the cache, warm
     * parse hits it" (e.g. `SeventhSessionParseCostTest`) is deterministic regardless of what other
     * tests ran earlier in the same JVM fork and happened to fill or overflow-clear these caches
     * first. Production code never calls this — the caches are meant to live for the process's whole
     * lifetime, which is exactly what makes them order-sensitive from a test's point of view.
     */
    internal fun resetCachesForTesting() {
        normalizedTerms.clear()
        termWordLists.clear()
    }

    internal fun normalize(text: String): String {
        if (ParserWorkCounters.enabled) ParserWorkCounters.normalizeCalls++
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(UNIT_CASE_SUFFIX, "$1")
            .replace(NON_WORD, " ")
            .trim()
            .replace(MULTIPLE_SPACES, " ")
            .replace(RUN_TOGETHER_BASIS, "$0 ")
    }

    /**
     * A case suffix on a basis unit, in the three languages that attach one:
     *
     * - Turkish, after an apostrophe: `100 g'da` ("in 100 g"), `100 ml'de`, `100 gr'da`, `100 g'daki`,
     *   and `100 gramda`, where the unit is written out and takes the suffix directly.
     * - Hungarian, after a hyphen: `100 g-ban`, `100 ml-ben` ("in"), `100 g-ra` ("for"),
     *   `100 g-onként` ("per").
     * - Finnish, after a colon: `100 g:ssa`, `100 ml:ssä` ("in"), `100 g:aa kohden` ("per").
     *
     * Matched after diacritics are removed, so `ssä` is `ssa` and `ként` is `kent` here.
     *
     * ### The measured defect (2026-09-17)
     *
     * `100 g'da` is the usual Turkish per-100 header, and it read nothing: the apostrophe became a
     * space, so the header element was `g da`, and [ColumnClassifier] refuses a basis phrase that ends
     * in a word it does not know — the rule that keeps the Baltic `of` debris out. No per-100 column
     * was resolved and the scan returned `NotFound`. Hungarian `100 g-ban` and Finnish `100 g:ssa`
     * failed the same way. Measured in `TurkishLabelDiagnosticTest` and
     * `EuropeanLabelDiagnosticTest`, pinned in `TurkishNutritionTableTest` and
     * `EuropeanNutritionTableTest`.
     *
     * ### Why this does not loosen anything
     *
     * It removes a suffix; it does not create a basis. The unit must be a whole word that is already a
     * basis spelling, so `kcal'de` and `adet'te` are untouched, and the suffixes are only these
     * languages' own case endings.
     */
    private const val CASE_SUFFIX =
        "(?:(?:['’`´]|(?<=gram))[dt][ae](?:ki)?|-(?:ban|ben|ra|re|onkent|enkent)|:(?:ssa|aa|sta))"

    private val UNIT_CASE_SUFFIX = Regex(
        "(?<=^|[\\s\\d])(${BasisUnitSpellings.alternation})$CASE_SUFFIX(?![\\p{L}\\p{N}])",
    )

    /** A whole token that is a basis unit and its case suffix, with trailing punctuation allowed. */
    private val CASE_SUFFIXED_UNIT = Regex(
        "(?:${BasisUnitSpellings.alternation})$CASE_SUFFIX[^\\p{L}\\p{N}]*",
    )

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
