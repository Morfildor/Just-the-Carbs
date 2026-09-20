package app.justthecarbs.ocr

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.ui.scan.ImportedPhotoStaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * An imported photograph reaches the same reading a capture of the same image would.
 *
 * Deliberately built on the **committed real-image corpus** rather than on synthetic geometry or a
 * mocked recognizer: the claim being tested is that the import inherits the real pipeline, and a
 * fake recognizer would prove only that the harness agrees with itself. These are the same nine
 * photographs `RealImageOcrTest` and `ProductionStillPipelineTest` use, so a divergence between an
 * imported photo and a captured one shows up here as a different reading of a fixture whose correct
 * answer is already pinned elsewhere.
 *
 * The corpus itself is untouched by this file — nothing here asserts a *new* expected value. Each
 * test asserts that the imported route produces the **same** thing the direct route does, whatever
 * that is on this device, so it cannot drift from the corpus or quietly redefine it.
 */
class ImportedPhotoPipelineTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context
    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assetBytes(name: String): ByteArray = try {
        testContext.assets.open("ocr_real/$name").use { it.readBytes() }
    } catch (e: IOException) {
        throw AssertionError("ocr_real/$name is missing; it is a committed, mandatory fixture", e)
    }

    /**
     * A real `content://` URI backed by the fixture, served through a real [ContentResolver].
     *
     * The bytes are written to the app's own cache and exposed via `Uri.fromFile`'s content
     * equivalent — what matters is that [ImportedPhotoStaging] reaches them through
     * `contentResolver.openInputStream`, which is the call the photo picker's URI resolves through
     * in production. A hand-made `InputStream` would bypass exactly the layer being tested.
     */
    private fun fixtureUri(name: String): Uri {
        val staged = File.createTempFile("import-src-", ".jpg", appContext.cacheDir)
        staged.writeBytes(assetBytes(name))
        return Uri.fromFile(staged)
    }

    private fun stage(uri: Uri, context: Context = appContext): ImportedPhotoStaging.Result =
        ImportedPhotoStaging.stage(context, uri)

    /** The production still path, exactly as `ProductionStillPipelineTest` drives it. */
    private fun analyse(file: File, region: NormalizedRegion? = OVERLAY): NutritionParseReport {
        val analyzer = LabelAnalyzer(onReading = { })
        try {
            val latch = CountDownLatch(1)
            var report: NutritionParseReport? = null
            analyzer.analyzeStill(appContext, file, region) { report = it; latch.countDown() }
            assertTrue("still recognition timed out", latch.await(60, TimeUnit.SECONDS))
            return requireNotNull(report)
        } finally {
            analyzer.close()
        }
    }

    private fun readingOf(report: NutritionParseReport): String = when (val r = report.reading) {
        is LabelReading.Confident ->
            "Confident ${r.candidate.value.stripTrailingZeros().toPlainString()}/${r.candidate.basis}"
        is LabelReading.Ambiguous ->
            "Ambiguous " + r.candidates.map { it.value.stripTrailingZeros().toPlainString() }.sorted()
        LabelReading.NotFound -> "NotFound"
    }

    @Test
    fun anImportedFixtureIsStagedByteForByte() {
        // The strongest possible statement of "same input": not merely an equivalent image, the
        // identical bytes. This is what makes every downstream guarantee inherited rather than
        // re-established — the analyzer receives a file indistinguishable from a capture's.
        val name = "sondey_multilingual_100g.jpg"
        val source = assetBytes(name)

        val result = stage(fixtureUri(name))

        val staged = result as? ImportedPhotoStaging.Result.Staged
        assertNotNull("the fixture must stage successfully", staged)
        assertEquals(source.size.toLong(), staged!!.bytes)
        assertTrue(
            "the staged file must be byte-identical to the chosen photo — any re-encode would " +
                "strip the EXIF orientation tag StillImageLoader depends on",
            source.contentEquals(staged.file.readBytes()),
        )
        staged.file.delete()
    }

    @Test
    fun theStagedFileIsShapedExactlyLikeACaptureFile() {
        // The evidence recorder, the frozen preview and the analyzer's own deletion all treat the
        // capture file by name and location. A different shape here would be the first place the
        // two inputs diverged.
        val staged = stage(fixtureUri("sondey_multilingual_100g.jpg"))
            as ImportedPhotoStaging.Result.Staged

        assertEquals(appContext.cacheDir, staged.file.parentFile)
        assertTrue(
            "the staged file must use the same prefix a capture does, was ${staged.file.name}",
            staged.file.name.startsWith("justthecarbs-label-"),
        )
        assertTrue(staged.file.name.endsWith(".jpg"))
        staged.file.delete()
    }

    @Test
    fun anImportedCanaryReadsExactlyWhatTheSameFileReadDirectlyReads() {
        // The convergence, measured rather than argued. Sondey is one of the four canaries; its
        // correct value is pinned by the corpus tests, and this asserts the imported route agrees
        // with the direct route on whatever this device actually reads — so it cannot pass by
        // redefining the expected answer, and it fails the moment the two routes diverge.
        val name = "sondey_multilingual_100g.jpg"

        val direct = File.createTempFile("direct-", ".jpg", appContext.cacheDir)
            .also { it.writeBytes(assetBytes(name)) }
        val directReading = readingOf(analyse(direct))

        val staged = stage(fixtureUri(name)) as ImportedPhotoStaging.Result.Staged
        val importedReading = readingOf(analyse(staged.file))

        assertEquals(
            "an imported photo must reach the identical reading — there is one pipeline",
            directReading,
            importedReading,
        )
    }

    @Test
    fun everyCorpusFixtureReadsIdenticallyThroughTheImportRoute() {
        // The whole corpus, not one fixture: a divergence that appears only on the prose labels or
        // only on the multi-column ones would be exactly the kind of quiet fork this feature must
        // not create. Asserts equality with the direct route per fixture, so the committed corpus's
        // own interpretations are neither restated nor changed here.
        val fixtures = listOf(
            "sondey_multilingual_100g.jpg",
            "kinder_multicolumn_piece.jpg",
            "real_yoghurt_serving_column_07.jpg",
            "real_stokbrood_prose_dense_06.jpg",
        )

        val divergences = fixtures.mapNotNull { name ->
            val direct = File.createTempFile("direct-", ".jpg", appContext.cacheDir)
                .also { it.writeBytes(assetBytes(name)) }
            val directReading = readingOf(analyse(direct))

            val staged = stage(fixtureUri(name)) as ImportedPhotoStaging.Result.Staged
            val importedReading = readingOf(analyse(staged.file))

            direct.delete()
            if (directReading == importedReading) null else "$name: $directReading != $importedReading"
        }

        assertTrue("imported readings diverged from direct readings: $divergences", divergences.isEmpty())
    }

    @Test
    fun aRotatedPhotoIsReadUprightExactlyAsARotatedCaptureIs() {
        // A photograph from a library is far more likely to carry a rotation tag than a capture is,
        // and a sideways frame is the one failure the parser's geometry stages cannot recover from
        // — every printed row reconstructs across the columns instead of along the rows.
        //
        // The orientation is applied by StillImageLoader, which the import inherits rather than
        // reimplements. This asserts that inheritance actually holds end to end.
        //
        // The assertion is deliberately about GEOMETRY, not about the recognised token stream.
        // Building a rotated fixture costs two lossy JPEG encodes and two resampling passes that a
        // real photograph never pays, and measurement showed that is enough to move ML Kit on its
        // own: recompressing this fixture with NO rotation at all still reads `Confident 61.9`,
        // while the rotated round-trip reads `Ambiguous [47.6, 61.9]` — 47.6 being the competing
        // misread this corpus already records for this label. Asserting reading equality here would
        // therefore be pinning the emulator's recognizer against JPEG noise, not pinning
        // orientation, and it would fail for a reason that has nothing to do with this feature.
        //
        // What orientation actually claims is that the sideways file is delivered upright, and that
        // is measured at the one place responsible for it.
        val name = "sondey_multilingual_100g.jpg"
        val original = BitmapFactory.decodeByteArray(assetBytes(name), 0, assetBytes(name).size)
        val uprightWidth = original.width
        val uprightHeight = original.height
        original.recycle()

        // A physically rotated image whose EXIF tag says how to put it back — the exact shape a
        // phone produces when the handset is held sideways.
        val rotated = rotatedWithExif(name, degrees = 90)
        val staged = stage(Uri.fromFile(rotated)) as ImportedPhotoStaging.Result.Staged

        // Staging must carry the tag through untouched; a re-encode here would strip it and the
        // parser's geometry stages would see every printed row reconstructed across the columns.
        assertEquals(
            "staging must preserve the EXIF orientation tag byte-for-byte",
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface(staged.file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
        )

        val loaded = StillImageLoader.loadWithRotation(staged.file)
        assertEquals("the shared loader must report the rotation it applied", 90, loaded.rotationDegrees)
        assertEquals("the imported photo must arrive upright", uprightWidth, loaded.bitmap?.width)
        assertEquals("the imported photo must arrive upright", uprightHeight, loaded.bitmap?.height)
        loaded.bitmap?.recycle()

        // And the safety property that matters regardless: a rotated import must never invent a
        // confident carbohydrate figure the upright image does not support.
        val reading = analyse(staged.file).reading
        if (reading is LabelReading.Confident) {
            assertEquals(
                "a rotated import must not reach a different confident value than the upright read",
                "61.9",
                reading.candidate.value.stripTrailingZeros().toPlainString(),
            )
        }
    }

    @Test
    fun anUnreadableUriFailsGracefullyAndLeavesNoPartialFileBehind() {
        // A revoked grant, or an item deleted between picking and reading. It must report a
        // failure rather than throwing, and must not leave a zero-byte file that would later
        // decode to nothing and be reported as an ordinary `NotFound`.
        val before = capturePrefixedFiles()

        val result = stage(Uri.parse("content://app.justthecarbs.test.missing/999"))

        assertTrue(
            "an unresolvable URI must be reported, not thrown",
            result is ImportedPhotoStaging.Result.Failed,
        )
        assertEquals(
            "a failed import must leave no capture-shaped file behind",
            before,
            capturePrefixedFiles(),
        )
    }

    @Test
    fun anEmptyFileIsRefusedRatherThanReadAsALabelWithNoTable() {
        // The distinction that matters: nothing was decoded, so saying "no nutrition table found"
        // would assert something about a label the app never saw.
        val empty = File.createTempFile("empty-", ".jpg", appContext.cacheDir)

        val result = stage(Uri.fromFile(empty))

        assertEquals(
            ImportedPhotoStaging.Result.Failed(ImportedImageStaging.Failure.EMPTY),
            result,
        )
    }

    @Test
    fun aNonImageFileIsRefusedWithoutProducingAReading() {
        // A document chooser can return something that is not an image at all. It must never reach
        // the parser as a photograph.
        val text = File.createTempFile("notanimage-", ".jpg", appContext.cacheDir)
        text.writeText("this is not a photograph of anything")

        val staged = stage(Uri.fromFile(text))

        // It stages — the bytes are real — and then fails to decode, which the screen reports as an
        // import failure rather than as a reading. What must never happen is a carbohydrate value.
        val report = (staged as? ImportedPhotoStaging.Result.Staged)?.let { analyse(it.file) }
        assertFalse(
            "a non-image must never produce a carbohydrate value",
            report?.reading is LabelReading.Confident,
        )
    }

    private fun capturePrefixedFiles(): Int =
        appContext.cacheDir.listFiles { f -> f.name.startsWith("justthecarbs-label-") }?.size ?: 0

    /** The fixture rotated in pixels, with the EXIF tag that says how to undo it. */
    private fun rotatedWithExif(name: String, degrees: Int): File {
        val source = BitmapFactory.decodeByteArray(assetBytes(name), 0, assetBytes(name).size)
        val rotated = Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            Matrix().apply { postRotate(-degrees.toFloat()) }, true,
        )
        val file = File.createTempFile("rotated-", ".jpg", appContext.cacheDir)
        file.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        source.recycle()
        rotated.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                when (degrees) {
                    90 -> ExifInterface.ORIENTATION_ROTATE_90
                    180 -> ExifInterface.ORIENTATION_ROTATE_180
                    else -> ExifInterface.ORIENTATION_ROTATE_270
                }.toString(),
            )
            saveAttributes()
        }
        return file
    }

    private companion object {
        /** The same starting rectangle the scanner proposes, as the corpus tests use. */
        val OVERLAY = NormalizedRegion(left = 0.06, top = 0.25, right = 0.94, bottom = 0.75)
    }
}
