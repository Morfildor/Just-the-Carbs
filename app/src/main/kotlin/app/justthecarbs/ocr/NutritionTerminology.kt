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
        NutritionTerms("en", setOf("carbohydrate", "carbohydrates"), setOf("of which sugars", "sugars", "sugar", "added sugars", "added sugar", "fibre", "fiber", "starch", "polyols", "polyol", "dextrose", "glucose", "fructose", "sucrose", "lactose", "maltose", "maltodextrin", "glucose syrup"), setOf("serving", "portion")),
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

    internal fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_WORD, " ")
        .trim()
        .replace(MULTIPLE_SPACES, " ")

    internal fun containsTerm(normalizedText: String, term: String): Boolean {
        val normalizedTerm = normalize(term)
        return " $normalizedText ".contains(" $normalizedTerm ")
    }

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val MULTIPLE_SPACES = Regex("\\s+")
}
