package app.justthecarbs.data.remote

/**
 * Turns what a user typed into a Search-a-licious `q` value that means the same thing.
 *
 * ## The problem this exists for
 *
 * The service documents `q` as accepting **Lucene query syntax**, with unrecognised words falling
 * through to full-text search. The Just the Carbs search field is not a query editor — it is a box
 * someone types a product name into — but the two are the same field on the wire, so ordinary
 * punctuation in an ordinary product name is parsed as operators.
 *
 * Measured against the live service on 2026-08-28. Every one of these returned **zero results** as
 * typed, and the correct products once escaped:
 *
 * | typed | raw | escaped |
 * |---|---|---|
 * | `Kinder Bueno (White)` | 0 hits | 10000, top hit *Kinder bueno white* |
 * | `milk + chocolate` | 0 hits | 10000, top hit *Tony's Chocolonely milk chocolate* |
 * | `product:name` | 0 hits | 10000, top hit *Product name* |
 * | `"chocolate milk"` | 0 hits | 10000 |
 * | `chocolate^2 milk` | 0 hits | 10000 |
 * | `chocolate~2 milk` | 0 hits | 10000 |
 *
 * **And one case that is worse than a zero.** `milk -chocolate` returned 10000 hits either way, but
 * they were not the same hits: unescaped, the leading `-` is Lucene's NOT operator, so the search
 * *excluded* chocolate and led with "Lait De Coco Nature". Escaped, it leads with "Tony's
 * Chocolonely milk chocolate". A wrong result set is harder to notice than an empty one — the user
 * sees a list and has no reason to think the app rewrote what they asked for.
 *
 * ## Why the whole set, when only some characters misbehave
 *
 * A narrower set was measured first and rejected as fragile rather than minimal. Whether a character
 * acts as an operator depends on **where it sits**, not merely on which character it is: `(`
 * embedded between letters (`chocolate(milk`) is inert, while the same character around a word
 * (`Kinder Bueno (White)`) opens a group. Likewise `-` is inert inside `Haagen-Dazs` and an operator
 * in `milk -chocolate`. A rule that escaped only the characters observed to break in one position
 * would be correct for the probe and wrong for the next product name.
 *
 * The cost of the wider rule was measured rather than assumed: escaping every character in the set
 * changed **no** query that already worked. `Nutella & Go!`, `M&M's`, `Coca-Cola Zero`,
 * `Ben & Jerry's`, `70% chocolate`, `Haagen-Dazs`, `7-Up`, `Uncle Ben's`, `Lay's`, `Cote d'Or`,
 * `Dr. Oetker`, `Milka Oreo` and `hagelslag` each returned an identical count and identical top hits
 * before and after. So the wider rule is not over-escaping in any way the service can observe — it
 * is the same behaviour with fewer edge cases.
 *
 * ## What is deliberately NOT escaped
 *
 * Apostrophes, `%`, `.`, `,`, `#`, `@` and every non-ASCII character. None is a Lucene
 * metacharacter, all appear constantly in real product names (`Lay's`, `70% chocolate`,
 * `Dr. Oetker`, `Côte d'Or`, `キットカット`), and escaping them would be noise. Spaces are untouched:
 * they separate terms, which is exactly what a multi-word product name means.
 *
 * ## Scope
 *
 * This applies to the Search-a-licious provider only. The legacy `cgi/search.pl` fallback takes a
 * plain-text `search_terms` parameter with no query language, so the same escaping there would send
 * literal backslashes into a search that would then match nothing — the precise bug this fixes,
 * inverted. Neither provider's escaping is the user's problem: the text field shows what they typed.
 */
internal object SearchALiciousQuery {

    /**
     * Lucene's reserved characters, as listed by the query-syntax documentation the service links.
     *
     * `&` and `|` are singly listed although only the doubled forms (`&&`, `||`) are operators —
     * escaping the single character covers both and costs nothing, verified against `M&M's` and
     * `Ben & Jerry's`, which are unchanged.
     */
    private val RESERVED = setOf(
        '+', '-', '&', '|', '!', '(', ')', '{', '}', '[', ']',
        '^', '"', '~', '*', '?', ':', '\\', '/',
    )

    /**
     * Prefixes every reserved character with a backslash, leaving everything else exactly as typed.
     *
     * Backslash is itself reserved and is handled by the same rule rather than by a separate pass,
     * so a typed `\` becomes `\\` and cannot escape the character that follows it. A separate
     * first pass over backslashes would double-escape the backslashes this function adds.
     */
    fun escape(terms: String): String = buildString(terms.length) {
        for (character in terms) {
            if (character in RESERVED) append('\\')
            append(character)
        }
    }
}
