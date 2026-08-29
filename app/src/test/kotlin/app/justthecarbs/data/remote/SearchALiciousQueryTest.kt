package app.justthecarbs.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plain-text search input hardening for the Search-a-licious provider.
 *
 * The service parses `q` as a Lucene query. The app's search field is a product-name box, so
 * ordinary punctuation in an ordinary product name would otherwise be read as operators — measured
 * against the live service on 2026-08-28, six representative inputs returned **zero results** as
 * typed, and one returned a *wrong* result set. [SearchALiciousQuery] carries the full table.
 *
 * The cases below are split deliberately:
 *
 * - **Measured regressions** — inputs whose live behaviour was wrong before escaping. Each names
 *   what the service actually did, so a future reader can tell a real requirement from a guess.
 * - **Must not be touched** — characters that are not Lucene metacharacters and appear constantly in
 *   real product names. These pin the *absence* of over-escaping, which is the failure mode of the
 *   obvious fix ("escape everything") and would be just as invisible.
 */
class SearchALiciousQueryTest {

    private fun escaped(input: String) = SearchALiciousQuery.escape(input)

    // ---------------------------------------------------------------- measured live regressions

    /** Live: 0 hits raw; 10000 and the correct "Kinder bueno white" once escaped. */
    @Test
    fun `parentheses are escaped`() {
        assertEquals("""Kinder Bueno \(White\)""", escaped("Kinder Bueno (White)"))
    }

    /** Live: 0 hits raw — a leading `+` is Lucene's required-term operator. */
    @Test
    fun `plus is escaped`() {
        assertEquals("""milk \+ chocolate""", escaped("milk + chocolate"))
    }

    /** Live: 0 hits raw — `:` makes a field query against a field that does not exist. */
    @Test
    fun `colon is escaped`() {
        assertEquals("""product\:name""", escaped("product:name"))
    }

    /** Live: 0 hits raw — a quoted phrase over fields that do not tokenise that way. */
    @Test
    fun `double quotes are escaped`() {
        assertEquals("""\"chocolate milk\"""", escaped("\"chocolate milk\""))
    }

    /** Live: 0 hits raw — `^` is the boost operator. */
    @Test
    fun `caret is escaped`() {
        assertEquals("""chocolate\^2 milk""", escaped("chocolate^2 milk"))
    }

    /** Live: 0 hits raw — `~` is the fuzzy/proximity operator. */
    @Test
    fun `tilde is escaped`() {
        assertEquals("""chocolate\~2 milk""", escaped("chocolate~2 milk"))
    }

    /**
     * The worst case in the set, and the reason this is not merely about empty result lists.
     *
     * Live, `milk -chocolate` returned 10000 hits either way — but not the same hits. Unescaped, the
     * leading `-` is the NOT operator, so the search *excluded* chocolate and led with "Lait De Coco
     * Nature"; escaped, it leads with "Tony's Chocolonely milk chocolate". A silently wrong result
     * set is harder for a user to notice than an empty one, because there is nothing to notice.
     */
    @Test
    fun `a leading minus is escaped so it cannot act as NOT`() {
        assertEquals("""milk \-chocolate""", escaped("milk -chocolate"))
    }

    @Test
    fun `boolean operator punctuation is escaped`() {
        assertEquals("""milk \&\& chocolate""", escaped("milk && chocolate"))
        assertEquals("""milk \|\| chocolate""", escaped("milk || chocolate"))
        assertEquals("""chocolate\! milk""", escaped("chocolate! milk"))
    }

    @Test
    fun `wildcards are escaped so they cannot expand the search`() {
        assertEquals("""choco\*""", escaped("choco*"))
        assertEquals("""choco\?ate""", escaped("choco?ate"))
    }

    @Test
    fun `range and grouping brackets are escaped`() {
        assertEquals("""chocolate \[milk\]""", escaped("chocolate [milk]"))
        assertEquals("""chocolate \{milk\}""", escaped("chocolate {milk}"))
    }

    @Test
    fun `slash is escaped so it cannot open a regex`() {
        assertEquals("""chocolate\/milk""", escaped("chocolate/milk"))
    }

    /**
     * A typed backslash must escape itself, not the character after it.
     *
     * Handled in the same single pass as every other reserved character rather than in a separate
     * first pass — a separate pass would double-escape the backslashes the function itself adds.
     */
    @Test
    fun `a typed backslash escapes itself and does not escape the next character`() {
        assertEquals("""back\\slash""", escaped("""back\slash"""))
        // The `(` still gets its own escape; the backslash before it does not consume it.
        assertEquals("""a\\\(b""", escaped("""a\(b"""))
    }

    // ------------------------------------------------------------------- must NOT be touched

    /**
     * Apostrophes are not Lucene metacharacters and are everywhere in real brand names.
     *
     * Verified live: identical count and identical top hits raw and escaped, for each of these.
     */
    @Test
    fun `apostrophes are left alone`() {
        assertEquals("Lay's", escaped("Lay's"))
        assertEquals("Uncle Ben's", escaped("Uncle Ben's"))
        assertEquals("Cote d'Or", escaped("Cote d'Or"))
    }

    /** Live: `70% chocolate` returned an identical 10000 and identical top hits either way. */
    @Test
    fun `percent, period and comma are left alone`() {
        assertEquals("70% chocolate", escaped("70% chocolate"))
        assertEquals("Dr. Oetker", escaped("Dr. Oetker"))
        assertEquals("milk, dark", escaped("milk, dark"))
    }

    /** Spaces separate terms — which is exactly what a multi-word product name means. */
    @Test
    fun `spaces are never escaped`() {
        assertEquals("Milka Oreo", escaped("Milka Oreo"))
        assertEquals("  padded  ", escaped("  padded  "))
    }

    /**
     * Non-ASCII text passes through untouched.
     *
     * The app's whole reason for sending `langs=nl,en` is that the owner scans Dutch packaging;
     * mangling accented or non-Latin names here would defeat that.
     */
    @Test
    fun `unicode is preserved exactly`() {
        assertEquals("Côte d'Or", escaped("Côte d'Or"))
        assertEquals("hagelslag", escaped("hagelslag"))
        assertEquals("キットカット", escaped("キットカット"))
        assertEquals("Müsli", escaped("Müsli"))
    }

    /** An ordinary query with no reserved characters must be byte-identical. */
    @Test
    fun `plain text is unchanged`() {
        assertEquals("chocolate", escaped("chocolate"))
        assertEquals("", escaped(""))
    }

    /**
     * The hyphen inside a word is the same character as the NOT operator above, and must still be
     * escaped — the point being that escaping it costs nothing.
     *
     * Verified live: `Haagen-Dazs` and `7-Up` each returned an identical count and identical top
     * hits before and after escaping. This is the evidence that the wider rule is not over-escaping:
     * position decides whether a character acts as an operator, so a rule that escaped `-` only when
     * it looked like an operator would be one product name away from being wrong.
     */
    @Test
    fun `an in-word hyphen is escaped and that is harmless`() {
        assertEquals("""Haagen\-Dazs""", escaped("Haagen-Dazs"))
        assertEquals("""7\-Up""", escaped("7-Up"))
        assertEquals("""Coca\-Cola Zero""", escaped("Coca-Cola Zero"))
    }

    /** Ampersands in brand names: escaped, and measured to be behaviour-neutral. */
    @Test
    fun `ampersands in brand names are escaped without changing results`() {
        assertEquals("""Ben \& Jerry's""", escaped("Ben & Jerry's"))
        assertEquals("""M\&M's""", escaped("M&M's"))
        assertEquals("""Nutella \& Go\!""", escaped("Nutella & Go!"))
    }
}
