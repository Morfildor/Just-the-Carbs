package app.justthecarbs.data.remote

import app.justthecarbs.domain.BarcodeValidator
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Replays captured Search-a-licious pages through the production data source and scores the list a
 * user would see. Fixtures and query lists: `src/test/resources/search/benchmark/`, captured live on
 * 2026-09-17 with `tools/search-benchmark/capture.py`.
 *
 * Each query carries a relevance key (see the query files). For every list the app would show:
 *
 * - **top1**: the first result is relevant; **top1WithFigure**: and it shows a carbohydrate figure;
 * - **top5**: a relevant result is among the first five; **figuresInTop5**: how many of those show one;
 * - **leaks**: results matching none of the key's words — the "unrelated Albert Heijn product" kind;
 * - **answerable**: queries whose captured page holds any relevant result at all. The live service
 *   does not fold Turkish letters, so "Pinar sut" and "Icim yogurt" come back without one.
 *
 * ## Measured, 2026-09-17
 *
 * The same fixtures, before and after the ranking of that date (`SearchResultRanking`), with the
 * languages each app sends:
 *
 * ```
 *                            top1   top1WithFigure  figuresInTop5  leaks (top 5)  relevant shown
 * Turkish, before (nl-first)  60/63       55            310/318       102 (29)          460
 * Turkish, after  (tr-first)  63/63       58            244/331        22 (3)           931
 * Dutch,   before             46/46       45            228/230        56 (10)          716
 * Dutch,   after              46/46       45            211/229         3 (0)           857
 * ```
 *
 * Fewer of the top five show a figure because exact matches without one are no longer dropped in
 * favour of unrelated products that have one; the first result shows a figure as often as before.
 * Turkish goes first on a device set to Turkish because it found the most: 63 top results and 931 relevant
 * rows, against 62 and 877 for the Dutch-first list and 61 and 839 for Turkish and English alone.
 *
 * The numbers are asserted so a change to mapping or ranking cannot move them silently. A change
 * made on purpose re-reads them here, from the printed report.
 */
class SearchBenchmarkTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun source(language: String, country: String?): SearchALiciousDataSource {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SearchALiciousApi::class.java)
        return SearchALiciousDataSource(api, preferredLanguage = { language }, deviceCountryTag = { country })
    }

    @Test
    fun turkishBenchmark() {
        val scores = listOf("nl-en-de-fr", "tr-en-nl-de-fr", "tr-en").associateWith { config ->
            score("turkish", config, source("tr-TR", "en:turkey")).also {
                println(it.report("turkish / $config / country TR"))
            }
        }
        val shipped = scores.getValue(SearchALiciousApi.searchLanguagesFor("tr-TR").joinToString("-"))

        assertEquals(68, shipped.scores.size)
        assertEquals(63, shipped.answerable)
        assertEquals(63, shipped.top1)
        assertEquals(58, shipped.top1WithFigure)
        assertEquals(63, shipped.top5)
        assertEquals(3, shipped.leaksInTop5)
        for (alternative in scores.values - shipped) {
            assertTrue(shipped.top1 > alternative.top1)
            assertTrue(shipped.relevantShown > alternative.relevantShown)
        }
    }

    @Test
    fun dutchControl() {
        val scores = listOf("nl-en-de-fr", "nl-en-de-fr-tr").associateWith { config ->
            score("dutch", config, source("en-NL", "en:netherlands")).also {
                println(it.report("dutch / $config / country NL"))
            }
        }
        val shipped = scores.getValue(SearchALiciousApi.searchLanguagesFor("en-NL").joinToString("-"))

        assertEquals(46, shipped.top1)
        assertEquals(45, shipped.top1WithFigure)
        assertEquals(0, shipped.leaksInTop5)
        assertEquals(3, shipped.leaks)
    }

    /** The on-device report that started the 2026-09-17 pass, on the page the service returned. */
    @Test
    fun theAlbertHeijnPizzaSearchShowsNoUnrelatedAlbertHeijnProducts() = runBlocking {
        val query = "krokante pizza Albert heijn"
        server.enqueue(response(page("dutch", "nl-en-de-fr", query)))

        val shown = (source("en-NL", "en:netherlands").search(query) as ProductSearchResult.Found).hits

        assertEquals(listOf("Pizza krokante", "Pizza Krokante Margherita"), shown.take(2).map { it.name })
        for (hit in shown) {
            val name = fold(hit.name)
            assertTrue(hit.name, "pizza" in name || "krokant" in name)
        }
    }

    // ------------------------------------------------------------------------------ scoring

    private class QueryScore(
        val query: String,
        val shown: List<ProductSearchHit>,
        val relevant: List<Boolean>,
        val leaked: List<Boolean>,
        val relevantInPage: Int,
    ) {
        val top1 get() = relevant.firstOrNull() == true
        val top1WithFigure get() = top1 && shown.first().carbsPer100 != null
        val top5 get() = relevant.take(5).any { it }
        val figuresInTop5 get() = shown.take(5).count { it.carbsPer100 != null }
        val relevantInTop5 get() = relevant.take(5).count { it }
        val relevantShown get() = relevant.count { it }
        val relevantWithFigure get() = shown.indices.count { relevant[it] && shown[it].carbsPer100 != null }
        val leaks get() = leaked.count { it }
        val leaksInTop5 get() = leaked.take(5).count { it }
    }

    private class Score(val scores: List<QueryScore>) {
        val top1 get() = scores.count { it.top1 }
        val top1WithFigure get() = scores.count { it.top1WithFigure }
        val top5 get() = scores.count { it.top5 }
        val figuresInTop5 get() = scores.sumOf { it.figuresInTop5 }
        val top5Slots get() = scores.sumOf { minOf(5, it.shown.size) }
        val answerable get() = scores.count { it.relevantInPage > 0 }
        val relevantShown get() = scores.sumOf { it.relevantShown }
        val relevantWithFigure get() = scores.sumOf { it.relevantWithFigure }
        val leaks get() = scores.sumOf { it.leaks }
        val leaksInTop5 get() = scores.sumOf { it.leaksInTop5 }
        val empty get() = scores.count { it.shown.isEmpty() }

        fun report(title: String) = buildString {
            appendLine("== $title")
            for (s in scores) {
                val marks = s.shown.indices.joinToString("") { i ->
                    when {
                        s.relevant[i] -> if (s.shown[i].carbsPer100 != null) "R" else "r"
                        s.leaked[i] -> "x"
                        else -> "."
                    }
                }
                appendLine(
                    String.format(
                        Locale.ROOT,
                        "%-22s top1=%-5s rel5=%d page=%2d shown=%2d leaks=%2d  %s | %s",
                        s.query, s.top1, s.relevantInTop5, s.relevantInPage, s.shown.size, s.leaks, marks,
                        s.shown.take(3).joinToString(" / ") { it.name },
                    ),
                )
            }
            appendLine(
                "TOTAL queries=${scores.size} answerable=$answerable top1=$top1 top1WithFigure=$top1WithFigure " +
                    "top5=$top5 figuresInTop5=$figuresInTop5/$top5Slots " +
                    "relevantShown=$relevantShown withFigure=$relevantWithFigure " +
                    "leaks=$leaks leaksTop5=$leaksInTop5 empty=$empty",
            )
        }
    }

    private fun page(set: String, config: String, query: String): JsonObject =
        Json.parseToJsonElement(fixture("$set-$config.json.gz")).jsonObject["responses"]!!
            .jsonObject.getValue(query).jsonObject

    private fun response(page: JsonObject) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(page.toString())

    private fun score(set: String, config: String, source: SearchALiciousDataSource): Score = runBlocking {
        val scores = queries(set).map { (query, key) ->
            val page = page(set, config, query)
            val words = wordsByBarcode(page)
            server.enqueue(response(page))
            val shown = (source.search(query) as? ProductSearchResult.Found)?.hits.orEmpty()
            QueryScore(
                query = query,
                shown = shown,
                relevant = shown.map { key.matchesAll(words[it.barcode].orEmpty()) },
                leaked = shown.map { !key.matchesAny(words[it.barcode].orEmpty()) },
                relevantInPage = words.values.count { key.matchesAll(it) },
            )
        }
        Score(scores)
    }

    /** Every word of every captured name and brand, keyed by the barcode the app would show. */
    private fun wordsByBarcode(page: JsonObject): Map<String, Set<String>> =
        page["hits"]!!.jsonArray.mapNotNull { element ->
            val hit = element.jsonObject
            val barcode = BarcodeValidator.normalize(hit.text("code") ?: return@mapNotNull null)
                ?: return@mapNotNull null
            val names = hit.keys.filter { it.startsWith("product_name") }.mapNotNull { hit.text(it) }
            val brands = (hit["brands"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                ?: listOfNotNull(hit.text("brands"))
            barcode to WORD.findAll(fold((names + brands).joinToString(" "))).map { it.value }.toSet()
        }.toMap()

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    // ------------------------------------------------------------------------------ fixtures

    /**
     * A relevance key from the query list: every token must match a word, as a prefix, or — with a
     * leading `=` — exactly, with `/` separating accepted spellings.
     */
    private class Key(text: String) {
        private val tokens: List<Pair<Boolean, List<String>>> = text.split(' ').filter { it.isNotBlank() }.map {
            it.startsWith("=") to it.removePrefix("=").split('/')
        }

        private fun matches(token: Pair<Boolean, List<String>>, words: Set<String>): Boolean {
            val (exact, spellings) = token
            return words.any { word -> spellings.any { if (exact) word == it else word.startsWith(it) } }
        }

        fun matchesAll(words: Set<String>) = tokens.all { matches(it, words) }
        fun matchesAny(words: Set<String>) = tokens.any { matches(it, words) }
    }

    private fun queries(set: String): List<Pair<String, Key>> =
        resource("search/benchmark/$set-queries.tsv").lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.split('\t').let { it[0] to Key(it[1]) } }

    private fun fixture(name: String): String =
        GZIPInputStream(javaClass.classLoader!!.getResourceAsStream("search/benchmark/$name")!!)
            .readBytes().toString(Charsets.UTF_8)

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes().toString(Charsets.UTF_8)

    private companion object {
        /**
         * The benchmark's own folding, deliberately not the app's: a change to the app's matching must
         * not silently change what the benchmark calls relevant.
         */
        fun fold(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
                .replace('ı', 'i')

        val WORD = Regex("[\\p{L}\\p{N}]+")
    }
}
