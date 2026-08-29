package app.justthecarbs.data.remote

import app.justthecarbs.domain.ProductSearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import android.util.Log

/**
 * Live feasibility bench for the primary search provider (§27 of the 2026-08-28 migration brief).
 *
 * **This test talks to the real internet and is a diagnostic, not a gate.** It exists so a human can
 * see, on a device, which provider answers a representative query and how long it takes — the
 * measurement that decided the migration and the one that has to be re-taken on real hardware.
 *
 * It deliberately asserts almost nothing. A network test that fails the build on a flaky connection
 * teaches the team to ignore it; this one prints, and asserts only the single property whose failure
 * would mean the integration itself is broken rather than the network being unavailable — that at
 * least one representative query produced a *usable* answer or an honest failure, never a crash.
 *
 * The same reasoning as `DutchLabelDiagnosticTest`: measure and print, assert only what cannot be
 * explained away by the environment.
 *
 * Run it alone:
 * ```
 * gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=app.justthecarbs.data.remote.SearchALiciousLiveDiagnosticTest
 * ```
 * Then read the results with `adb logcat -s JtcSearchBench`.
 */
class SearchALiciousLiveDiagnosticTest {

    private val source = SearchALiciousDataSource(
        NetworkModule.searchALiciousApi(NetworkModule.okHttpClient()),
    )

    @Test
    fun representativeQueriesAgainstTheLiveService() = runBlocking {
        val queries = listOf("chocolate", "gouda", "nutella", "milk", "pasta", "bread", "hagelslag")
        var usable = 0

        Log.i(TAG, "query        outcome        hits  withNumber  ms")
        for (query in queries) {
            val startedAt = System.currentTimeMillis()
            val result = source.search(query)
            val elapsed = System.currentTimeMillis() - startedAt

            val line = when (result) {
                is ProductSearchResult.Found -> {
                    usable++
                    // How many hits can show a carbohydrate figure. This is the measured cost of
                    // the missing `product_quantity_unit` field, and it is the number to watch: a
                    // hit without one is still selectable and still resolves correctly through the
                    // canonical barcode lookup.
                    val withNumber = result.hits.count { it.carbsPer100 != null }
                    "Found          ${result.hits.size}     $withNumber"
                }
                ProductSearchResult.NoMatches -> {
                    usable++
                    "NoMatches      0     0"
                }
                is ProductSearchResult.Failed -> "FAILED(${result.error})  -     -"
            }
            Log.i(TAG, "${query.padEnd(12)} $line  ${elapsed}ms")
        }

        // The only assertion: the integration produced controlled outcomes throughout. Zero usable
        // answers across seven queries means the wiring is broken, not that a connection blipped.
        assertTrue(
            "no representative query produced a usable answer — check the provider wiring",
            usable > 0,
        )
    }

    /**
     * Why phrase boosting is **not** enabled, kept re-runnable rather than only written down.
     *
     * The 2026-08-28 accuracy brief asked for a phrase-boost comparison. There is nothing to
     * compare, and the reason is a property of this deployment rather than a judgement call:
     *
     * 1. **No `boost_phrase` parameter exists.** The service's own OpenAPI document contains zero
     *    occurrences of "boost" or "phrase". Sending it anyway is accepted with HTTP 200 and
     *    changes nothing — same count, same hits, same order — so it is silently ignored, which is
     *    the most misleading of the three possible answers: it would have looked enabled.
     * 2. **Free-text Lucene phrase syntax does not work here either.** Measured against the raw
     *    HTTP endpoint with *unescaped* queries: `"nutella"` — a one-word phrase, which cannot
     *    legitimately fail — returns **0 hits**; `(coca cola)` returns 0; `coca^2 cola` returns 0;
     *    and `coca OR cola` returns **HTTP 500**. Meanwhile `brands:"coca-cola"` returns 3283 and
     *    the service's own documented example returns 5, so quoting is honoured **only** as a
     *    field-filter value, never as a free-text phrase.
     *
     * There is therefore no route to phrase boosting, and the honest response is to leave ranking to
     * the provider (§6) rather than to build a client-side ranker.
     *
     * ## Read this test's own output correctly — it does NOT reproduce those zeros
     *
     * The probes below go through [SearchALiciousDataSource], so [SearchALiciousQuery] escapes them
     * first. Every form therefore comes back **Found**, because the metacharacters arrive as literal
     * text and the query degrades to an ordinary word search — which is exactly the escaping working
     * as designed. `explicit OR` is the one that still fails, because `OR` is a bare word rather
     * than a metacharacter and nothing escapes it.
     *
     * So this test measures **what the app can actually send**, and the answer is "no phrase query,
     * by construction". The 0-hit measurements above required bypassing the app entirely. Do not
     * read a `Found` line here as evidence that phrase syntax works.
     *
     * Printed, not asserted, for the same reason as the bench above — the day this deployment gains
     * a working phrase mode, this should show it rather than fail a build.
     */
    @Test
    fun phraseSyntaxSupportOnTheLiveService() = runBlocking {
        Log.i(TAG, "form                       outcome")
        for ((label, query) in PHRASE_PROBES) {
            val outcome = when (val result = source.search(query)) {
                is ProductSearchResult.Found -> "Found ${result.hits.size}"
                ProductSearchResult.NoMatches -> "NoMatches (0 hits)"
                is ProductSearchResult.Failed -> "FAILED(${result.error})"
            }
            Log.i(TAG, "${label.padEnd(26)} $outcome")
        }

        // Asserts only the control: plain text must work. If even that fails the device has no
        // network and the rest of the output means nothing.
        assertTrue(
            "plain-text search failed — the network, not the phrase syntax, is the problem",
            source.search("chocolate") is ProductSearchResult.Found,
        )
    }

    /**
     * Why partial-query **prefix recall** is not implemented, kept re-runnable rather than only
     * written down.
     *
     * The 2026-08-28 recall brief asked whether a provider-generated trailing wildcard (`nutel*`)
     * could rescue the incomplete queries that return nothing. It cannot, and the reason is a
     * property of this deployment's index rather than a judgement call: **the `*` is silently
     * discarded**, exactly like `boost_phrase` before it.
     *
     * Measured over 28 partial queries, baseline versus final-token wildcard: **0 differed**.
     * Identical counts, identical ranks, identical top hits, on every one.
     *
     * Two probes below are the ones that settle it, and the first is a trap worth knowing about:
     *
     * 1. **`nut*` returns 10000 hits and looks like a working wildcard.** It is not — bare `nut`
     *    returns the same 10000 with the same top hits, because `nut` is simply a real word. Read
     *    alone, that row would have "confirmed" wildcard support.
     * 2. **`choc*late` returns *Late*.** The `*` is acting as a token separator, splitting the query
     *    into `choc` + `late` — the opposite of a prefix expansion. A working wildcard could not do
     *    this, and no amount of query construction turns it into one.
     *
     * Fuzzy forms (`nutel~`, `~1`, `~2`) are also 0, and field-scoped forms (`product_name:nutel`)
     * return **HTTP 500** because `product_name` is language-subfielded on this index. So the finding
     * is about the capability, not about one syntax.
     *
     * The index appears to hold analyzed whole tokens with no edge-ngram expansion: `pindakaa`
     * returns 60 hits while `pindaka` returns 0. That is a token boundary, not a ranking cliff —
     * **and no client-side ranking can reorder an empty result set**, which is why this was measured
     * before anything was built rather than after.
     *
     * ## This probe deliberately bypasses [SearchALiciousDataSource]
     *
     * Unlike [phraseSyntaxSupportOnTheLiveService], it calls the API directly. It has to: the app's
     * escaper escapes `*`, so a wildcard can only be produced by appending it *after* escaping — the
     * provider-generated construction the brief requires. Going through the data source would send a
     * literal backslash-star and measure the escaper instead of the service, which is the same class
     * of mistake as the phrase probe that once measured its own string construction.
     *
     * Printed, not asserted, for the same reason as the benches above — the day this index gains
     * prefix expansion, this should show it rather than fail a build.
     */
    @Test
    fun prefixWildcardSupportOnTheLiveService() = runBlocking {
        val api = NetworkModule.searchALiciousApi(NetworkModule.okHttpClient())

        Log.i(TAG, "sent q                  status  count   top hit")
        for (query in PREFIX_PROBES) {
            val response = api.search(SearchALiciousRequest(q = query))
            val body = response.body()
            val top = body?.hits?.firstOrNull()
                ?.let { it.productNameNl ?: it.productName }
                ?: "-"
            Log.i(
                TAG,
                "${query.padEnd(22)} ${response.code()}     ${(body?.count ?: -1).toString().padEnd(7)} $top",
            )
        }

        // Asserts only the control: a full word must return matches. If `nutella` comes back empty
        // the index or the network is the problem, and the zeros above mean nothing.
        val control = api.search(SearchALiciousRequest(q = "nutella"))
        assertTrue(
            "the full-word control returned nothing — the service, not the wildcard, is the problem",
            (control.body()?.count ?: 0) > 0,
        )
    }

    private companion object {
        const val TAG = "JtcSearchBench"

        /**
         * Sent **raw**, bypassing the escaper — see [prefixWildcardSupportOnTheLiveService].
         *
         * Ordered as pairs so the output is self-checking: each wildcard form sits next to the plain
         * form it must be compared against. Measured 2026-08-28: every pair identical, `choc*late`
         * returning *Late*, and every canary 0 with and without the wildcard.
         */
        val PREFIX_PROBES = listOf(
            // The canaries: all 0 today, all fine as full words.
            "nutel", "nutel*", "nutella",
            "pindak", "pindak*", "pindakaas",
            // The trap: `nut*` == `nut` because `nut` is a word, not because `*` expanded.
            "nut", "nut*",
            // The proof: `*` splits the token instead of extending it.
            "choc*late", "late", "chocolate",
            // Other provider-generated forms, for completeness.
            "nutel~", "nutel~2",
        )

        /**
         * These go through [SearchALiciousDataSource], so the escaper applies — which is the point:
         * they measure what the *app* can send, not what a hand-built request could. Measured
         * outcome (2026-08-28, on device): every form **Found**, except `explicit OR` which fails
         * with SERVER — see the class KDoc for why that is the correct result and not a
         * contradiction of the raw-endpoint zeros.
         */
        val PHRASE_PROBES = listOf(
            "plain (control)" to "coca cola",
            "quoted phrase" to "\"coca cola\"",
            "quoted single word" to "\"nutella\"",
            "grouping" to "(coca cola)",
            "term boost caret" to "coca^2 cola",
            "explicit OR" to "coca OR cola",
        )
    }
}
