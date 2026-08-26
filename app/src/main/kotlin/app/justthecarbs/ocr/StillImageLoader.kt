package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Decodes a captured still into the upright, region-cropped bitmap OCR should actually read.
 *
 * Two jobs the previous `InputImage.fromFilePath` call could not do:
 *
 * 1. **Orientation.** `fromFilePath` applies EXIF itself, but a bitmap decoded for cropping does
 *    not — `BitmapFactory` ignores the orientation tag entirely. Cropping a sideways bitmap with an
 *    upright rectangle would take a region of the package nobody framed, so the rotation has to
 *    happen first and cannot be skipped.
 * 2. **Region.** Cropping to a region when one is asked for.
 *
 * Every failure path returns the widest thing that still works rather than nothing: an unreadable
 * EXIF tag gives the unrotated bitmap, a failed crop gives the whole image. The worst case is the
 * behaviour that shipped before this pass.
 */
internal object StillImageLoader {

    /**
     * The bitmap to recognise, or null if the file could not be decoded at all.
     *
     * Intermediate bitmaps are recycled as soon as they are superseded. A full-resolution capture is
     * tens of megabytes as ARGB_8888, and holding the source, the rotated copy and the crop at once
     * is how this would become an OutOfMemoryError on a mid-range phone.
     */
    fun load(file: File, region: NormalizedRegion?): Bitmap? = loadWithRotation(file, region).bitmap

    /**
     * The bitmap plus the EXIF rotation that was applied to get it upright.
     *
     * The rotation is reported because "rotation or decode damages the image" is a live hypothesis
     * whenever the device and the test harness disagree, and a rotated bitmap is indistinguishable
     * from a correct one when viewed alone — it is only wrong *relative to what the sensor captured*.
     * The debug evidence recorder writes this figure next to both images so the question is settled by
     * looking rather than by reasoning.
     */
    fun loadWithRotation(file: File, region: NormalizedRegion?, trace: ScanTrace? = null): Result {
        val decoded = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }.getOrNull().also { trace?.mark("jpeg-decode") } ?: return Result(null, 0)

        val degrees = exifRotationDegrees(file).also { trace?.mark("exif") }
        val upright = applyExifRotation(decoded, degrees).also { trace?.mark("rotate") }
        val crop = region?.let { ScanRegionMapper.toPixels(it, upright.width, upright.height) }
            ?: return Result(upright, degrees)

        val cropped = runCatching {
            Bitmap.createBitmap(upright, crop.left, crop.top, crop.width, crop.height)
        }.getOrNull()?.also { result ->
            if (result !== upright) upright.recycle()
        } ?: upright
        trace?.mark("crop")
        return Result(cropped, degrees)
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
