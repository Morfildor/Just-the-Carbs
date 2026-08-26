package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.IOException

/**
 * RESEARCH — asserts nothing. Measures whether 2-D clustering of RAW elements can isolate a table.
 *
 * ## RESULT: connected-component clustering is NOT a viable localisation primitive. Measured.
 *
 * Run 2026-08-17 against the four canary fixtures at gaps of 0.5/1.0/2.0/4.0 text heights:
 *
 * ```
 *                 gap=0.5h        gap=1.0h        gap=2.0h            gap=4.0h
 * kinder      no basis        no basis        USABLE x0.13..1.00  USABLE x0.13..1.00
 * sondey      no basis        USABLE          USABLE              USABLE
 * stokbrood   USABLE          USABLE          whole document      whole document
 * yoghurt     no basis        USABLE          whole document      whole document
 * ```
 *
 * Three independent reasons this fails, none of which is a tuning problem:
 *
 * 1. **No gap threshold works across the corpus.** sondey and yoghurt need 1.0h; kinder needs 2.0h;
 *    at 2.0h stokbrood and yoghurt collapse into a single cluster covering the whole document. Any
 *    fixed threshold that rescues one fixture destroys another, and the threshold cannot be derived
 *    from the image because the required value differs per package.
 * 2. **Kinder's "USABLE" cluster spans x 0.13..1.00 — the full frame width.** It did *not* separate
 *    the horizontally adjacent panel that motivated this experiment; it merely rediscovered the same
 *    vertical band that already failed as Pass B. The one case this was built for is the one case it
 *    does not solve.
 * 3. **Text on a package is spatially connected.** The premise check shows 22 of kinder's 35 rows
 *    spanning gaps of 7-9 text heights, and prose sits close enough to a table to join it at any gap
 *    generous enough to hold a table's own column spacing together. Whitespace does not separate the
 *    two things that need separating.
 *
 * **A locator must key on table STRUCTURE — repeated aligned value columns, consistent row pitch —
 * not on spatial connectivity.** That is a different experiment; do not spend time re-tuning this one.
 *
 * ## The question
 *
 * Vertical banding over reconstructed rows failed on Kinder because that fixture contains a second
 * package's ingredient panel *horizontally adjacent* to the nutrition table: [LogicalRowBuilder] has
 * already merged both panels into single rows before any locator sees them, and no vertical band can
 * separate two things occupying the same vertical extent.
 *
 * Clustering **raw elements**, before row reconstruction, is the obvious next candidate — it is the
 * only stage at which the two panels are still separate objects. Before building that as production
 * code, this measures whether the separation is actually visible in the raw geometry, and what it
 * would cost.
 *
 * ## What it reports
 *
 * For each real fixture: how many spatially connected element clusters exist at several gap
 * thresholds, how large each is, whether it contains a carbohydrate term, and whether it contains a
 * basis header. A usable localisation signal requires a threshold at which the nutrition table lands
 * in exactly one cluster **carrying both** — if no such threshold exists, connected-component
 * clustering is the wrong primitive and the next session should not spend time on it.
 *
 * Read the tag `JustTheCarbsCluster`.
 */
class RawElementClusteringDiagnosticTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private fun assetBitmap(name: String): Bitmap {
        val stream = try {
            testContext.assets.open("ocr_real/$name")
        } catch (e: IOException) {
            throw AssertionError("ocr_real/$name missing", e)
        }
        return stream.use { requireNotNull(BitmapFactory.decodeStream(it)) }
    }

    private fun documentOf(bitmap: Bitmap): OcrDocument {
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS,
        )
        return try {
            val text = com.google.android.gms.tasks.Tasks.await(
                recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)),
                60,
                java.util.concurrent.TimeUnit.SECONDS,
            )
            MlKitOcrMapper.toDocument(text, bitmap.width, bitmap.height)
        } finally {
            recognizer.close()
        }
    }

    /**
     * Connected components over element boxes, joined when they sit within [gapInHeights] text
     * heights of each other in BOTH axes.
     *
     * Union-find rather than a growing-boundary walk: the running-boundary version is the
     * single-linkage rule that made row reconstruction chain down a tilted table, and it would chain
     * across panels here for exactly the same reason.
     */
    private fun cluster(document: OcrDocument, gapInHeights: Double): List<List<OcrElement>> {
        val elements = document.elements
        if (elements.isEmpty()) return emptyList()
        val medianHeight = elements.map { it.box.height }.sorted().let { it[it.size / 2] }
            .coerceAtLeast(1)
        val gap = medianHeight * gapInHeights

        val parent = IntArray(elements.size) { it }
        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        elements.indices.forEach { i ->
            val a = elements[i].box
            (i + 1 until elements.size).forEach { j ->
                val b = elements[j].box
                // Gap between boxes on each axis; negative means they overlap on that axis.
                val dx = maxOf(a.left - b.right, b.left - a.right, 0).toDouble()
                val dy = maxOf(a.top - b.bottom, b.top - a.bottom, 0).toDouble()
                if (dx <= gap && dy <= gap) union(i, j)
            }
        }
        return elements.indices.groupBy { find(it) }.values.map { group -> group.map { elements[it] } }
    }

    private fun boxOf(elements: List<OcrElement>): OcrBox =
        elements.drop(1).fold(elements.first().box) { box, e -> box.union(e.box) }

    /** Whether this cluster names total carbohydrate, using the parser's own vocabulary. */
    private fun namesCarbohydrate(elements: List<OcrElement>): Boolean {
        val text = NutritionTerminology.normalize(elements.joinToString(" ") { it.text })
        return NutritionTerminology.carbohydrateTerms.any { term ->
            text.contains(NutritionTerminology.normalize(term))
        }
    }

    /** Whether this cluster carries a per-100 basis phrase — the thing a crop must never lose. */
    private fun namesBasis(elements: List<OcrElement>): Boolean {
        val text = NutritionTerminology.normalize(elements.joinToString(" ") { it.text })
        return BASIS_HINTS.any { text.contains(it) }
    }

    @Test
    fun measureWhetherRawElementClustersSeparateTheTable() {
        val fixtures = listOf(
            "kinder_multicolumn_piece.jpg",
            "sondey_multilingual_100g.jpg",
            "real_stokbrood_prose_dense_06.jpg",
            "real_yoghurt_serving_column_07.jpg",
        )
        val gaps = listOf(0.5, 1.0, 2.0, 4.0)

        Log.i(TAG, "=".repeat(96))
        Log.i(TAG, "RAW-ELEMENT 2-D CLUSTERING — can a table be isolated before row reconstruction?")
        Log.i(TAG, "USABLE means: exactly one cluster holds a carbohydrate term AND a basis phrase,")
        Log.i(TAG, "and it is not simply the whole document.")
        Log.i(TAG, "=".repeat(96))

        fixtures.forEach { name ->
            val bitmap = assetBitmap(name)
            try {
                val document = documentOf(bitmap)
                Log.i(TAG, "")
                Log.i(
                    TAG,
                    "--- $name  ${document.width}x${document.height}  ${document.elements.size} elements ---",
                )
                gaps.forEach { gapInHeights ->
                    val clusters = cluster(document, gapInHeights)
                        .sortedByDescending { it.size }
                    val carbClusters = clusters.filter { namesCarbohydrate(it) }
                    val both = clusters.filter { namesCarbohydrate(it) && namesBasis(it) }
                    val largestFraction = clusters.firstOrNull()
                        ?.let { it.size.toDouble() / document.elements.size } ?: 0.0

                    val verdict = when {
                        both.size == 1 && largestFraction < 0.9 -> "USABLE"
                        both.size == 1 -> "one cluster, but it is the whole document"
                        both.isEmpty() -> "no cluster carries both carb term and basis"
                        else -> "${both.size} competing clusters"
                    }
                    Log.i(
                        TAG,
                        "  gap=%.1fh  clusters=%-4d largest=%.0f%%  carbClusters=%-3d both=%-3d  %s"
                            .format(
                                gapInHeights, clusters.size, largestFraction * 100,
                                carbClusters.size, both.size, verdict,
                            ),
                    )
                    both.forEach {
                        val box = boxOf(it)
                        Log.i(
                            TAG,
                            "        candidate region [%d,%d,%d,%d] = x %.2f..%.2f y %.2f..%.2f, %d elements"
                                .format(
                                    box.left, box.top, box.right, box.bottom,
                                    box.left.toDouble() / document.width,
                                    box.right.toDouble() / document.width,
                                    box.top.toDouble() / document.height,
                                    box.bottom.toDouble() / document.height,
                                    it.size,
                                ),
                        )
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
        Log.i(TAG, "=".repeat(96))
    }

    @Test
    fun reportWhetherTheCorpusActuallyContainsSideBySidePanels() {
        // The premise check. If no fixture has horizontally disjoint text blocks sharing a vertical
        // extent, then the side-by-side problem is not represented in the corpus at all and any
        // locator built against it would be untestable here.
        val fixtures = listOf(
            "kinder_multicolumn_piece.jpg",
            "sondey_multilingual_100g.jpg",
            "real_stokbrood_prose_dense_06.jpg",
            "real_yoghurt_serving_column_07.jpg",
        )
        Log.i(TAG, "=".repeat(96))
        Log.i(TAG, "SIDE-BY-SIDE PREMISE CHECK — do rows span horizontally separated blocks?")
        Log.i(TAG, "=".repeat(96))

        fixtures.forEach { name ->
            val bitmap = assetBitmap(name)
            try {
                val document = documentOf(bitmap)
                val rows = LogicalRowBuilder.build(document)
                val medianHeight = document.elements.map { it.box.height }.sorted()
                    .let { it[it.size / 2] }.coerceAtLeast(1)

                // A row "spans a gap" when two consecutive elements in it are separated by much more
                // than ordinary word spacing — the signature of one row covering two panels.
                val spanning = rows.filter { row ->
                    row.elements.zipWithNext().any { (a, b) ->
                        b.box.left - a.box.right > medianHeight * 6
                    }
                }
                Log.i(
                    TAG,
                    "%-38s rows=%-4d spanning a large horizontal gap: %d"
                        .format(name.take(38), rows.size, spanning.size),
                )
                spanning.take(4).forEach {
                    val widest = it.elements.zipWithNext()
                        .maxByOrNull { (a, b) -> b.box.left - a.box.right }
                    val gap = widest?.let { (a, b) -> b.box.left - a.box.right } ?: 0
                    Log.i(
                        TAG,
                        "      gap=%4dpx (%.1f heights) '%s'"
                            .format(gap, gap.toDouble() / medianHeight, it.text.take(64)),
                    )
                }
            } finally {
                bitmap.recycle()
            }
        }
        Log.i(TAG, "=".repeat(96))
    }

    private companion object {
        const val TAG = "JustTheCarbsCluster"

        /** Basis phrases in the languages this corpus actually prints. Diagnostic use only. */
        val BASIS_HINTS = listOf("100 g", "100g", "100 ml", "100ml")
    }
}
