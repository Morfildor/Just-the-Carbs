package app.justthecarbs.domain

import java.util.Locale

/**
 * Which of an Open Food Facts record's names the app shows.
 *
 * One rule for every place a remote name enters the app — both search providers and the barcode
 * lookup — so a product found by search keeps the same name on the calculator that opens next.
 *
 * Open Food Facts carries a main `product_name`, in the language the product was entered in, and
 * optional translations (`product_name_nl`, `product_name_tr`, ...):
 *
 * - **Device set to Turkish**: the Turkish name, then the main name. Products sold in Turkey are
 *   usually entered in Turkish, so the main name is the one on the package; a Dutch translation is
 *   not.
 * - **Any other device language**: the Dutch name, then the main name — the app's behaviour since
 *   it first preferred Dutch names, kept unchanged for its existing users.
 *
 * Product data, not interface text: the app itself is always shown in English.
 */
object ProductNames {

    /** The translations to try, in order, on a device set to [language] (a language tag). */
    fun preferenceFor(language: String): List<String> =
        if (languageOf(language) == "tr") listOf("tr") else listOf("nl")

    /** The bare language of a tag: `tr` for `tr-TR`, `TR` or `tr_TR`. */
    fun languageOf(language: String): String =
        language.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)

    /**
     * The name to show: the first non-blank translation in [preferenceFor] order, then [generic].
     * Null when the record has no usable name at all.
     */
    fun choose(generic: String?, localized: Map<String, String?>, language: String): String? =
        (preferenceFor(language).map { localized[it] } + generic)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
}
