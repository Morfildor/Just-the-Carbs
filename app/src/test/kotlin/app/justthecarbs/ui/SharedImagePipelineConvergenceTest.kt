package app.justthecarbs.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A shared image uses the photo pipelines that already exist, and adds none.**
 *
 * The claim this whole feature rests on, and the one a future change is most likely to break
 * without noticing — by adding a `readSharedBarcode()` that seems more direct than routing through
 * the scanner, or a share-specific OCR entry point, or a second URI-copy that re-derives the size
 * ceiling. Each produces a parallel path, and a parallel path is somewhere the two can disagree
 * about what the user is about to be shown.
 *
 * These read the production sources, following
 * [app.justthecarbs.ocr.ImportedPhotoPipelineConvergenceTest] and its barcode sibling: the property
 * is *structural* — "no such code exists" — and no behavioural test can establish the absence of a
 * branch it does not happen to exercise.
 */
class SharedImagePipelineConvergenceTest {

    private val manifest = sourceFile("app/src/main/AndroidManifest.xml")
    private val activity = sourceFile("app/src/main/kotlin/app/justthecarbs/MainActivity.kt")
    private val navHost = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/JustTheCarbsNavHost.kt")
    private val chooser =
        sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/SharedImageChooserScreen.kt")
    private val intake = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/SharedImageIntake.kt")
    private val scanner = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/ScannerScreen.kt")
    private val labelScanner =
        sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt")

    /**
     * The source with comments stripped.
     *
     * Every assertion here is about **code**. The sibling convergence tests record why this has to
     * be enforced rather than assumed: a first run failed against prose in a KDoc that merely
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
    fun `sharing stages through the one staging implementation`() {
        // SharedImageIntake must delegate, not reimplement. A second copy would have to re-derive
        // the size ceiling, the cancellation poll and the partial-file delete, and would be free
        // to get any of them wrong.
        val code = codeOf(intake)
        assertTrue(
            "the share intake must delegate to ImportedPhotoStaging",
            code.contains(Regex("""ImportedPhotoStaging\.stage\(""")),
        )
        // No stream copying of its own anywhere in it.
        assertFalse(
            "the share intake must not copy bytes itself",
            code.contains("openInputStream") || code.contains("copyTo") || code.contains("ByteArray("),
        )
    }

    @Test
    fun `sharing declares no new size ceiling`() {
        // The ceiling lives in ImportedImageStaging and is inherited. A literal byte count here
        // would be a second, drifting bound.
        assertFalse(
            "the share path must not define its own byte ceiling",
            codeOf(intake).contains("MAX_BYTES") && codeOf(intake).contains("="),
        )
    }

    @Test
    fun `no share-specific barcode recognition exists`() {
        // ImportedBarcodeReader is the one ML Kit boundary for a still barcode. Nothing in the
        // share path may call a scanner client of its own.
        for ((name, source) in listOf("chooser" to chooser, "intake" to intake, "activity" to activity)) {
            val code = codeOf(source)
            assertFalse(
                "$name must not construct a barcode scanner",
                code.contains("BarcodeScanning.getClient") || code.contains("BarcodeScannerOptions"),
            )
            assertFalse("$name must not call BarcodeFrameReader", code.contains("BarcodeFrameReader"))
        }
    }

    @Test
    fun `no share-specific OCR exists`() {
        for ((name, source) in listOf("chooser" to chooser, "intake" to intake, "activity" to activity)) {
            val code = codeOf(source)
            assertFalse(
                "$name must not run still-image analysis",
                code.contains("analyzeStill") || code.contains("TextRecognition") ||
                    code.contains("NutritionTableParser") || code.contains("LabelAnalyzer"),
            )
        }
    }

    @Test
    fun `the chooser runs neither recognizer before the user answers`() {
        // The safety rule stated as structure. The chooser reaches a recognizer only by
        // navigating, and it navigates only from a card's onClick.
        val code = codeOf(chooser)
        assertFalse(
            "the chooser must not recognise anything itself",
            code.contains("ImportedBarcodeReader") || code.contains("Analyzer") ||
                code.contains("InputImage") || code.contains("ImportedPhotoStaging"),
        )
    }

    @Test
    fun `the chooser never inspects the image to guess what it contains`() {
        // No auto-classification, by decision. Nothing here may decode, sniff or measure the file.
        val code = codeOf(chooser)
        for (forbidden in listOf("BitmapFactory", "decodeFile", "decodeStream", "Bitmap", "exifinterface")) {
            assertFalse("the chooser must not inspect image content ($forbidden)", code.contains(forbidden))
        }
    }

    @Test
    fun `both scanners receive the share through their existing import parameter`() {
        // The convergence itself: one named parameter on each screen, and the nav host passes the
        // same in-flight staged file to both.
        //
        // The type is `ImportedImageSource`, not `Uri`, and that is the 1.0.8 ownership correction
        // rather than a rename. A `file://` URI said nothing about who owned the bytes, so both
        // scanners staged an already-owned cache file a second time and orphaned the first. A
        // `Staged` cannot be staged — there is no URI in it to stage.
        assertTrue(
            "ScannerScreen must accept a sharedImage as an owned source",
            codeOf(scanner).contains(Regex("""sharedImage:\s*ImportedImageSource\?""")),
        )
        assertTrue(
            "LabelScannerScreen must accept a sharedImage as an owned source",
            codeOf(labelScanner).contains(Regex("""sharedImage:\s*ImportedImageSource\?""")),
        )
        assertEquals(
            "the nav host must hand the staged share to exactly the two scanners",
            2,
            Regex("""sharedImage = shareForThisVisit""").findAll(codeOf(navHost)).count(),
        )
    }

    @Test
    fun `neither scanner stages an image itself, so a share cannot be copied twice`() {
        // The structural half of the ownership fix. Both scanners must go through
        // `ImportedImageResolver`, which is the single place that knows a share is already owned;
        // a direct `ImportedPhotoStaging.stage(` call from either screen is the exact shape of the
        // defect — it stages unconditionally, so it would copy a cache file into a second cache
        // file and abandon the first. Prefix constants are still referenced from both, which is why
        // this matches the call rather than the object's name.
        for ((name, file) in listOf("ScannerScreen" to scanner, "LabelScannerScreen" to labelScanner)) {
            assertEquals(
                "$name must resolve through ImportedImageResolver, never stage directly",
                0,
                Regex("""ImportedPhotoStaging\.stage\(""").findAll(codeOf(file)).count(),
            )
            assertEquals(
                "$name must resolve exactly once",
                1,
                Regex("""ImportedImageResolver\.resolve\(""").findAll(codeOf(file)).count(),
            )
        }
    }

    @Test
    fun `the nav host hands over an owned file rather than a URI`() {
        // `Uri.fromFile` here was how an owned cache file was disguised as something to stage.
        val code = codeOf(navHost)
        assertEquals(
            "the nav host must not turn the staged share back into a URI",
            0,
            Regex("""Uri\.fromFile\(""").findAll(code).count(),
        )
        assertEquals(
            "both chooser branches must hand over the staged file itself",
            2,
            Regex("""ImportedImageSource\.Staged\(""").findAll(code).count(),
        )
    }

    @Test
    fun `the label share enters the same importPhoto a picked photo does`() {
        // One import function, two callers (the picker effect and the share effect). A second
        // import path would be a second place the automatic-verification gate could be skipped.
        val code = codeOf(labelScanner)
        assertEquals(
            "importPhoto must be declared exactly once",
            1,
            Regex("""fun importPhoto\(""").findAll(code).count(),
        )
        assertEquals(
            "importPhoto must be called by the picker effect and the share effect, and nothing else",
            2,
            Regex("""[^n] importPhoto\(""").findAll(code).count(),
        )
    }

    @Test
    fun `the barcode share enters the same picked delivery the launcher writes`() {
        // Rather than a parallel state, the share is injected into `picked` — so everything below
        // it (intake rules, staging, recognition, the choice sheet, navigation) is one path.
        val code = codeOf(scanner)
        assertTrue(
            "the share must be delivered onto the same picked state",
            code.contains(Regex("""picked = deliveries to shared""")),
        )
        assertEquals(
            "there must be exactly one effect consuming a picked delivery",
            1,
            Regex("""LaunchedEffect\(picked\?\.first\)""").findAll(code).count(),
        )
    }

    @Test
    fun `the manifest asks for no storage or media permission`() {
        // A share hands over a URI with a temporary grant. Nothing about reading it needs a
        // standing claim on the user's library, and this is where such a claim would appear.
        for (permission in listOf(
            "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
            "READ_MEDIA_IMAGES",
            "READ_MEDIA_VISUAL_USER_SELECTED",
            "MANAGE_EXTERNAL_STORAGE",
        )) {
            assertFalse("the manifest must not request $permission", manifest.contains(permission))
        }
    }

    @Test
    fun `nothing takes a persistable uri grant`() {
        // Persisting would convert a one-read share into a standing claim on a photograph.
        for ((name, source) in listOf(
            "activity" to activity,
            "intake" to intake,
            "nav host" to navHost,
            "scanner" to scanner,
            "label scanner" to labelScanner,
        )) {
            assertFalse(
                "$name must not persist a URI grant",
                codeOf(source).contains("takePersistableUriPermission"),
            )
        }
    }

    @Test
    fun `the manifest accepts a single image share and nothing wider`() {
        assertTrue("ACTION_SEND must be declared", manifest.contains("android.intent.action.SEND\""))
        assertTrue("the filter must be scoped to images", manifest.contains("""android:mimeType="image/*""""))
        // Out of scope by decision, and absent rather than half-handled.
        assertFalse(
            "ACTION_SEND_MULTIPLE must not be declared",
            manifest.contains("android.intent.action.SEND_MULTIPLE"),
        )
    }

    @Test
    fun `the share is consumed exactly once from each terminal path`() {
        // Every route out of the chooser and the failure screen reports consumption, so nothing
        // can be replayed by a rotation or a later launch.
        val code = codeOf(navHost)
        assertEquals(
            "each of the four exits (barcode, label, cancel, failure-close) must consume the share",
            4,
            Regex("""onShareConsumed\(""").findAll(code).count(),
        )
    }

    @Test
    fun `the share decision is reached through the tested rule, never re-derived`() {
        // MainActivity must ask SharedImageRequest rather than comparing the action itself, which
        // is how the onboarding gate would quietly get a second, divergent copy.
        val code = codeOf(activity)
        assertTrue(
            "the activity must consult SharedImageRequest",
            code.contains(Regex("""SharedImageRequest\.from\(""")),
        )
        assertFalse(
            "the activity must not test the send action itself",
            code.contains("\"android.intent.action.SEND\""),
        )
    }

    @Test
    fun `onboarding release goes through the tested transition`() {
        // The held-share release is a rule, not an assignment. Re-deriving it in the Activity is
        // exactly how "a share never skips onboarding" would erode.
        assertTrue(
            "the activity must release a held share through SharedImageTransitions",
            codeOf(activity).contains(Regex("""SharedImageTransitions\.onboardingCompleted\(""")),
        )
    }
}
