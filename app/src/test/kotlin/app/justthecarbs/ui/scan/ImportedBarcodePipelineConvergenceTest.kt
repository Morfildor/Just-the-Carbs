package app.justthecarbs.ui.scan

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **There is one barcode lookup path, and an imported photo uses it.**
 *
 * The claim the feature rests on, and the one a future change is most likely to break without
 * noticing — by adding a `photoBarcodeLookup()` that seems more convenient than threading a value
 * back to the existing callback, or by giving the photo path "just a quick" length check of its own
 * rather than routing through [app.justthecarbs.domain.BarcodeValidator]. Either produces a second
 * downstream path, and a second path is somewhere the two can disagree about which product the user
 * is about to be shown.
 *
 * It also pins the staging reuse: the barcode import must go through the **same**
 * [ImportedPhotoStaging] the nutrition-label import uses, rather than a second URI-copy
 * implementation that would have to re-derive the size ceiling, the cancellation poll and the
 * partial-file delete.
 *
 * These tests read the production sources, following
 * [app.justthecarbs.ocr.ImportedPhotoPipelineConvergenceTest]'s reasoning: the property is
 * *structural* — "no such code exists" — and no behavioural test can establish the absence of a
 * branch it does not happen to exercise.
 */
class ImportedBarcodePipelineConvergenceTest {

    private val scanner = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/ScannerScreen.kt")
    private val reader = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/ImportedBarcodeReader.kt")
    private val analyzer = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/BarcodeAnalyzer.kt")
    private val staging = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/ImportedPhotoStaging.kt")

    /**
     * The source with comments stripped.
     *
     * Every assertion here is about **code**. The sibling convergence test records why this has to
     * be enforced rather than assumed: its first run failed against prose in a KDoc that merely
     * *named* the symbol being counted. Match the code, never a bare substring of the file.
     */
    private fun codeOf(source: String): String = source.lineSequence()
        .map { it.substringBefore("//") }
        .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
        .joinToString("\n")

    private fun sourceFile(path: String): String {
        val candidates = listOf(File(path), File("../$path"), File(path.removePrefix("app/")))
        val found = candidates.firstOrNull { it.isFile }
        requireNotNull(found) { "could not locate $path from ${File(".").absolutePath}" }
        return found.readText()
    }

    @Test
    fun `both barcode inputs validate through the one frame reader`() {
        // The convergence itself. BarcodeFrameReader.read is where a raw ML Kit string becomes a
        // validated, normalised barcode; one call site in the live analyzer, one in the photo
        // reader, and nowhere else in the scanner package.
        val pattern = Regex("""BarcodeFrameReader\.read\(""")

        assertEquals(
            "the live analyzer must read exactly one raw detection per frame through the reader",
            1,
            pattern.findAll(codeOf(analyzer)).count(),
        )
        assertEquals(
            "the imported-photo reader must map its detections through the same function",
            1,
            pattern.findAll(codeOf(reader)).count(),
        )
    }

    @Test
    fun `the photo path has no validation of its own`() {
        // The forbidden shape: a second opinion about whether a number is a usable barcode. Every
        // such rule already lives behind BarcodeFrameReader, and a copy here is a copy that drifts.
        val forbidden = listOf(
            "BarcodeValidator",
            "checkDigit",
            "expandUpcE",
            ".length ==",
            "isDigit",
        )

        val readerCode = codeOf(reader)
        forbidden.forEach { symbol ->
            assertTrue(
                "$symbol appears in ImportedBarcodeReader. Validation belongs to " +
                    "BarcodeFrameReader alone, so a photo and a camera frame cannot be judged " +
                    "by two different rules.",
                !readerCode.contains(symbol),
            )
        }
    }

    @Test
    fun `no second downstream lookup exists`() {
        // The brief's explicit prohibition. A barcode from a photograph reaches the caller's
        // ordinary onBarcode callback, which the nav host routes exactly as it routes a live scan
        // and a typed code; nothing in the scanner package may look a product up by itself.
        val forbidden = listOf(
            "photoBarcodeLookup",
            "ProductRepository",
            "ProductViewModel",
            "Routes.product",
            "navController",
        )

        listOf("ScannerScreen" to scanner, "ImportedBarcodeReader" to reader).forEach { (name, src) ->
            val code = codeOf(src)
            forbidden.forEach { symbol ->
                assertTrue(
                    "$symbol appears in $name. The scanner produces a validated barcode and " +
                        "nothing else — every lookup and every navigation is the caller's, and " +
                        "shared with the live scanner by construction.",
                    !code.contains(symbol),
                )
            }
        }
    }

    @Test
    fun `the imported barcode is staged by the same object a label import uses`() {
        // Staging reuse, asserted rather than claimed: one implementation of the URI copy, with
        // one size ceiling, one cancellation poll and one partial-file delete.
        val scannerCode = codeOf(scanner)
        assertTrue(
            "ScannerScreen must stage through ImportedPhotoStaging rather than copying a URI " +
                "itself — a second copy would have to re-derive every rule that one already holds.",
            scannerCode.contains("ImportedPhotoStaging.stage("),
        )
        assertTrue(
            "the barcode import must pass BARCODE_PREFIX, so its cache file is never mistaken " +
                "for a nutrition-label capture by the evidence recorder.",
            scannerCode.contains("ImportedPhotoStaging.BARCODE_PREFIX"),
        )
    }

    @Test
    fun `the scanner performs no URI copy of its own`() {
        // What a duplicated staging implementation would look like at the call site.
        val forbidden = listOf(
            "openInputStream",
            "contentResolver",
            "createTempFile",
            "takePersistableUriPermission",
        )

        val scannerCode = codeOf(scanner)
        forbidden.forEach { symbol ->
            assertTrue(
                "$symbol appears in ScannerScreen. The URI copy belongs to ImportedPhotoStaging; " +
                    "takePersistableUriPermission in particular must appear nowhere at all, since " +
                    "the picker's temporary grant is deliberately never persisted.",
                !scannerCode.contains(symbol),
            )
        }
    }

    @Test
    fun `the staging prefix defaults to the label prefix`() {
        // The generalisation is minimal by construction: the label path's behaviour is unchanged
        // to the byte because it does not pass a prefix at all.
        val stagingCode = codeOf(staging)
        assertTrue(
            "stage() must default its prefix to LABEL_PREFIX, so the nutrition-label import is " +
                "byte-identical in behaviour to before the barcode path existed.",
            stagingCode.contains("prefix: String = LABEL_PREFIX"),
        )
        assertTrue(
            "the label prefix must still be the literal a capture has always used.",
            stagingCode.contains("\"justthecarbs-label-\""),
        )
    }

    @Test
    fun `the photo path does not consult the live stability tracker`() {
        // The one deliberate divergence, pinned so it stays deliberate. The tracker's geometry and
        // hold gates infer intent from a live stream; a still photograph has no such evidence, and
        // applying them would refuse exactly the small, off-centre barcodes this feature exists to
        // read. See ImportedBarcodeConvergenceTest for the behavioural half of this claim.
        val readerCode = codeOf(reader)
        listOf("BarcodeStabilityTracker", "onFrame", "BarcodeAcceptance").forEach { symbol ->
            assertTrue(
                "$symbol appears in ImportedBarcodeReader's code. A photograph is not a frame " +
                    "sequence, and treating it as one would reintroduce an aim gate on an image " +
                    "nobody was aiming.",
                !readerCode.contains(symbol),
            )
        }
    }

    @Test
    fun `both inputs are configured for the same symbologies from one constant`() {
        // "A photograph is read for the same codes the camera is" must be a property of the code,
        // not a coincidence between two lists that can drift apart in one edit.
        val expected = "setBarcodeFormats(PRIMARY_BARCODE_FORMAT, *OTHER_BARCODE_FORMATS)"

        assertTrue(
            "the live analyzer must take its formats from the shared constant",
            codeOf(analyzer).contains(expected),
        )
        assertTrue(
            "the imported-photo reader must take its formats from the same shared constant",
            codeOf(reader).contains(expected),
        )
    }
}
