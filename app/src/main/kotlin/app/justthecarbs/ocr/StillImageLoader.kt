package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Decodes a captured still into the upright bitmap OCR should actually read.
 *
 * The one job `InputImage.fromFilePath` could not do is **orientation**: `fromFilePath` applies EXIF
 * itself, but a bitmap decoded here does not — `BitmapFactory` ignores the orientation tag entirely
 * — and a sideways bitmap is not what the parser's geometry stages expect.
 *
 * **This no longer crops, and must not start again.** It once took a `NormalizedRegion` and cut the
 * capture down to the scan overlay *before* recognition. The 2026-08-17 capture-first pass removed
 * that: cropping to the overlay discarded the basis header band on tall labels, so `ColumnClassifier`
 * reclassified the per-100 column and the interpreter correctly refused a value the user could
 * plainly see — it cost both canaries (sondey and kinder went `Confident` → `NotFound`). Recognition
 * now runs on the whole frame and the rectangle is applied *afterwards*, as relevance rather than as
 * a boundary (see `ScanRegionRelevance`, and `SelectedTableReader` for the user-confirmed crop).
 *
 * The parameter lingered as a dead `region = null` at every call site until it was removed in 1.0.3.
 *
 * Failure paths still return the widest thing that works: an unreadable EXIF tag gives the unrotated
 * bitmap rather than nothing.
 */
internal object StillImageLoader {

    /**
     * The bitmap plus the EXIF rotation that was applied to get it upright.
     *
     * Intermediate bitmaps are recycled as soon as they are superseded. A full-resolution capture is
     * tens of megabytes as ARGB_8888, and holding both the source and the rotated copy is how this
     * would become an OutOfMemoryError on a mid-range phone.
     *
     * The rotation is reported because "rotation or decode damages the image" is a live hypothesis
     * whenever the device and the test harness disagree, and a rotated bitmap is indistinguishable
     * from a correct one when viewed alone — it is only wrong *relative to what the sensor captured*.
     * The debug evidence recorder writes this figure next to both images so the question is settled by
     * looking rather than by reasoning.
     */
    fun loadWithRotation(file: File, trace: ScanTrace? = null): Result {
        val decoded = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }.getOrNull().also { trace?.mark("jpeg-decode") } ?: return Result(null, 0)

        val degrees = exifRotationDegrees(file).also { trace?.mark("exif") }
        val upright = applyExifRotation(decoded, degrees).also { trace?.mark("rotate") }
        return Result(upright, degrees)
    }

    /** The decoded bitmap and the EXIF rotation applied to it, in degrees. */
    data class Result(val bitmap: Bitmap?, val rotationDegrees: Int)

    private fun exifRotationDegrees(file: File): Int = runCatching {
        when (
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun applyExifRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap

        val rotated = runCatching {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(degrees.toFloat()) },
                true,
            )
        }.getOrNull() ?: return bitmap

        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
