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
        NutritionTerms("en", setOf("carbohydrate", "carbohydrates"), setOf("of which sugars", "sugars", "sugar", "fibre", "fiber", "starch", "polyols"), setOf("serving", "portion")),
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
    )

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
