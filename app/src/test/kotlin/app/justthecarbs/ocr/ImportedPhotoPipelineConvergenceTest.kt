package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **There is one nutrition-label interpretation pipeline, and an imported photo uses it.**
 *
 * This is the claim the photo-import feature rests on, and the one a future change is most likely
 * to break without noticing — by adding "just a small" imported-image branch to a parser stage, or
 * a second recognition entry point that seems more convenient for a `Uri`. Both would produce a
 * second interpretation path, and a second path is a place where the two can disagree about a
 * carbohydrate figure someone doses insulin from.
 *
 * The tests read the production sources. That is unusual, and it is deliberate: the property being
 * asserted is *structural* — "no such code exists" — and no behavioural test can establish the
 * absence of a branch it does not happen to exercise. This repo has twice shipped a safety rule
 * somewhere no test could reach; the answer recorded there is to put the rule where a test can see
 * it, which for an absence means looking at the source.
 */
class ImportedPhotoPipelineConvergenceTest {

    private val scanner = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt")
    private val staging = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/ImportedPhotoStaging.kt")
    private val analyzer = sourceFile("app/src/main/kotlin/app/justthecarbs/ocr/LabelAnalyzer.kt")
    private val loader = sourceFile("app/src/main/kotlin/app/justthecarbs/ocr/StillImageLoader.kt")

    /**
     * The source with comments stripped.
     *
     * Every assertion here is about **code**, and this file's own first run proved why that
     * distinction has to be enforced rather than assumed: two assertions failed against prose. The
     * scanner's KDoc names `analyzer.analyzeStillRetaining` while explaining the convergence, which
     * counted as a third call site; `StillImageLoader`'s KDoc names `NormalizedRegion` in the
     * sentence explaining that it no longer crops, which read as evidence that it does. Both are
     * the trap this repo already records for `mapping.txt` greps — match the definition, never a
     * bare substring.
     */
    private fun codeOf(source: String): String = source.lineSequence()
        .map { it.substringBefore("//") }
        .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
        .joinToString("\n")

    private fun sourceFile(path: String): String {
        // The unit test runs with a module-relative working directory on some invocations and a
        // repo-relative one on others; resolving both means this cannot fail for a reason that has
        // nothing to do with what it asserts.
        val candidates = listOf(File(path), File("../$path"), File(path.removePrefix("app/")))
        val found = candidates.firstOrNull { it.isFile }
        requireNotNull(found) { "could not locate $path from ${File(".").absolutePath}" }
        return found.readText()
    }

    @Test
    fun `the imported photo is handed to the same analyzer entry point a capture uses`() {
        // The convergence itself. Both inputs call `analyzeStillRetaining`, which is the single
        // door into the still pipeline — EXIF correction, whole-frame recognition, the parser, the
        // interpreter and every recovery route lie behind it.
        val calls = Regex("""analyzer\.analyzeStillRetaining\(""")
            .findAll(codeOf(scanner)).count()

        assertEquals(
            "exactly two call sites are expected — the camera capture and the photo import. " +
                "A third would be a new way into the still pipeline; zero or one means the two " +
                "inputs no longer converge here.",
            2,
            calls,
        )
    }

    @Test
    fun `no recognition or parsing entry point exists for imported images alone`() {
        // The forbidden shape: a parallel reader. If an import ever needs its own recognizer, its
        // own parse or its own interpretation, this is where that shows up first.
        val forbidden = listOf(
            "TextRecognition.getClient",
            "NutritionTableParser",
            "NutritionTableInterpreter",
            "InputImage.from",
        )

        val stagingCode = codeOf(staging)
        forbidden.forEach { symbol ->
            assertTrue(
                "$symbol appears in ImportedPhotoStaging. Staging copies bytes; it must never " +
                    "recognise, parse or interpret anything — that is the one pipeline's job.",
                !stagingCode.contains(symbol),
            )
        }
    }

    @Test
    fun `staging never decodes the image`() {
        // Two reasons, and both matter. Correctness: decoding here would drop the EXIF orientation
        // that StillImageLoader is relied on to apply, silently delivering sideways images to the
        // parser's geometry stages. Memory: a 50 MP photograph is ~200 MB as ARGB_8888, and the
        // pipeline already decodes exactly once, downstream.
        val stagingCode = codeOf(staging)
        listOf("BitmapFactory", "Bitmap.create", "ImageDecoder").forEach { symbol ->
            assertTrue(
                "$symbol appears in ImportedPhotoStaging — staging must copy bytes only, so the " +
                    "single decode stays StillImageLoader's and the orientation tag survives.",
                !stagingCode.contains(symbol),
            )
        }
    }

    @Test
    fun `the analyzer has no imported-image branch at all`() {
        // The analyzer must be unable to tell the two inputs apart. If it can, "one pipeline" has
        // become "two code paths that currently agree".
        listOf("import", "Imported", "gallery", "Gallery", "picker", "Picker")
            .forEach { term ->
                val inCode = codeOf(analyzer).lineSequence()
                    .filterNot { it.trimStart().startsWith("import ") } // Kotlin import statements
                    .any { it.contains(term) }
                assertTrue(
                    "'$term' appears in LabelAnalyzer's code. The analyzer must not know where " +
                        "its file came from — that is what makes the convergence structural.",
                    !inCode,
                )
            }
    }

    @Test
    fun `the still loader still corrects orientation and still does not crop`() {
        // Both guarantees are inherited by imported photos rather than reimplemented, so they are
        // pinned here too: a rotated photo from a library is far more common than a rotated
        // capture, and re-introducing a pre-recognition crop cost both canaries when it was tried.
        val loaderCode = codeOf(loader)
        assertTrue(
            "StillImageLoader must still read the EXIF orientation tag — an imported photo " +
                "depends on it more than a capture does.",
            loaderCode.contains("TAG_ORIENTATION"),
        )
        assertTrue(
            "StillImageLoader must not crop before recognition; cropping to an apparent table " +
                "removed the basis header band and cost both canaries.",
            !loaderCode.contains("NormalizedRegion") && !loaderCode.contains("PixelRegion"),
        )
    }

    @Test
    fun `the import reuses the same automatic read gate as a capture`() {
        // `readSelectedTable(..., automatic = true)` is the gate that decides whether a reading may
        // skip the crop step. An import that bypassed it — or called it with automatic = false —
        // would be accepting readings on terms a capture never gets.
        val automaticReads = Regex("""readSelectedTable\(proposed, automatic = true\)""")
            .findAll(codeOf(scanner)).count()

        assertEquals(
            "both the capture and the import must reach the automatic gate on identical terms",
            2,
            automaticReads,
        )
    }
}
