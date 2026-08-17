package app.justthecarbs.ocr

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
        NutritionTerms("nl", setOf("koolhydraten"), setOf("waarvan suikers", "suikers", "suiker", "vezels", "voedingsvezels", "zetmeel", "polyolen"), setOf("portie", "per portie")),
        NutritionTerms("de", setOf("kohlenhydrate"), setOf("davon zucker", "zucker", "ballaststoffe", "stärke", "mehrwertige alkohole", "polyole"), setOf("portion", "pro portion")),
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
