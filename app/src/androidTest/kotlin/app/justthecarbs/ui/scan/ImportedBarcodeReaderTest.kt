package app.justthecarbs.ui.scan

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.ImportedBarcodeSelection
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The end-to-end proof, on a device, through the real ML Kit recognizer: **a barcode in a photo
 * file reaches the same validated value a camera scan produces.**
 *
 * Everything below drives [ImportedBarcodeReader] itself — the production ML Kit boundary — over a
 * real JPEG on disk, exactly as an import does after staging. Nothing is faked: the image is
 * encoded, written to a cache file, decoded by `InputImage.fromFilePath`, recognised, mapped
 * through `BarcodeFrameReader` and collapsed by `ImportedBarcodeSelection`.
 *
 * The fixture is generated rather than committed, and [Ean13Fixture]'s KDoc records why: a
 * committed PNG cannot state which digits it encodes, so the assertion would rest on a claim
 * nobody can check. `theFixtureItselfIsReadable` is the control that keeps the rest honest — if
 * the generator were wrong, every other case here would pass or fail for a reason that has nothing
 * to do with the app.
 */
@RunWith(AndroidJUnit4::class)
class ImportedBarcodeReaderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Real, valid EAN-13 codes, each with its check digit computed rather than typed. */
    private val cocaCola = Ean13Fixture.complete("400638133393")
    private val otherProduct = Ean13Fixture.complete("871210084906")

    private fun stage(bitmap: Bitmap): File {
        // The same shape staging produces: a JPEG in cacheDir carrying the barcode prefix.
        val file = File.createTempFile(
            ImportedPhotoStaging.BARCODE_PREFIX,
            ".jpg",
            context.cacheDir,
        )
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return file
    }

    private fun read(vararg barcodes: String): ImportedBarcodeSelection.Outcome {
        val file = stage(Ean13Fixture.bitmap(barcodes.toList()))
        return try {
            runBlocking { ImportedBarcodeReader.read(context, file) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun theFixtureItselfIsReadable() {
        // The control. Without it, a broken generator would make every case below pass vacuously
        // (by finding nothing where nothing was expected) or fail for the wrong reason.
        assertEquals(
            "the generated fixture must encode the digits it was asked for — if this fails, " +
                "Ean13Fixture is wrong and nothing else in this class means anything",
            ImportedBarcodeSelection.Outcome.Single(cocaCola),
            read(cocaCola),
        )
    }

    @Test
    fun anImportedPhotoWithOneBarcodeReachesTheNormalBarcodeValue() {
        // Required test 1: the value that comes back is exactly what the caller hands to
        // `onBarcode`, which is the same callback and the same route a live scan takes.
        val outcome = read(otherProduct)

        assertEquals(ImportedBarcodeSelection.Outcome.Single(otherProduct), outcome)
    }

    @Test
    fun theImportedValueIsTheSameNormalisedFormTheLiveScannerProduces() {
        // The convergence, observed rather than argued: the string returned here is what
        // BarcodeValidator.normalize produces, because BarcodeFrameReader is the only way a raw
        // detection becomes a value on either path.
        val outcome = read(cocaCola) as ImportedBarcodeSelection.Outcome.Single

        assertEquals(13, outcome.value.length)
        assertTrue("the value must be digits only", outcome.value.all { it.isDigit() })
        assertEquals(cocaCola, outcome.value)
    }

    @Test
    fun aPhotoWithTwoDistinctBarcodesAsksRatherThanPicking() {
        // Required test 5, on real recognition: the app must never resolve a two-product
        // photograph to one of them silently.
        val outcome = read(cocaCola, otherProduct)

        assertTrue(
            "two distinct codes must produce a choice, not a silent pick — was $outcome",
            outcome is ImportedBarcodeSelection.Outcome.Choice,
        )
        val choice = outcome as ImportedBarcodeSelection.Outcome.Choice
        assertEquals(2, choice.values.size)
        assertEquals(setOf(cocaCola, otherProduct), choice.values.toSet())
    }

    @Test
    fun duplicateDetectionsOfOneCodeCollapse() {
        // Required test 6, end to end: the same printed code twice in one photograph is one
        // product, and must not ask the user to choose between two identical numbers.
        val outcome = read(cocaCola, cocaCola)

        assertEquals(ImportedBarcodeSelection.Outcome.Single(cocaCola), outcome)
    }

    @Test
    fun aPhotoWithNoBarcodeFailsGracefully() {
        // Required test 4: a photograph with nothing to read reports no barcode rather than
        // throwing, and rather than inventing one.
        val blank = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        val file = stage(blank)

        val outcome = try {
            runBlocking { ImportedBarcodeReader.read(context, file) }
        } finally {
            file.delete()
        }

        assertEquals(ImportedBarcodeSelection.Outcome.None, outcome)
    }

    @Test
    fun anUnreadableFileReportsNoBarcodeRatherThanThrowing() {
        // A truncated or non-image file: the reader must degrade to "nothing usable here", which
        // is the same statement the user gets for a photograph with no barcode in it.
        val file = File.createTempFile(ImportedPhotoStaging.BARCODE_PREFIX, ".jpg", context.cacheDir)
        file.writeText("this is not a JPEG")

        val outcome = try {
            runBlocking { ImportedBarcodeReader.read(context, file) }
        } finally {
            file.delete()
        }

        assertEquals(ImportedBarcodeSelection.Outcome.None, outcome)
    }

    @Test
    fun aBarcodeSmallAndOffCentreIsStillRead() {
        // The case the live path's geometry gate refuses and an import must not: a barcode
        // occupying a small corner of a much larger image, which is what a screenshot or a photo
        // of a whole packet looks like.
        val barcode = Ean13Fixture.bitmap(listOf(cocaCola), moduleWidth = 3, barHeight = 140)
        val canvasBitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        android.graphics.Canvas(canvasBitmap).drawBitmap(barcode, 40f, 40f, null)

        val file = stage(canvasBitmap)
        val outcome = try {
            runBlocking { ImportedBarcodeReader.read(context, file) }
        } finally {
            file.delete()
        }

        assertEquals(
            "an imported photo is deliberately not subject to the live aim and size gates",
            ImportedBarcodeSelection.Outcome.Single(cocaCola),
            outcome,
        )
    }
}
