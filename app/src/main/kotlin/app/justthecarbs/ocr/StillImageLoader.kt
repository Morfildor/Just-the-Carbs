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
 * 2. **Region.** Cropping to what the user framed (§10).
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
    fun load(file: File, region: NormalizedRegion?): Bitmap? {
        val decoded = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }.getOrNull() ?: return null

        val upright = applyExifRotation(file, decoded)
        val crop = region?.let { ScanRegionMapper.toPixels(it, upright.width, upright.height) }
            ?: return upright

        return runCatching {
            Bitmap.createBitmap(upright, crop.left, crop.top, crop.width, crop.height)
        }.getOrNull()?.also { cropped ->
            if (cropped !== upright) upright.recycle()
        } ?: upright
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            when (
                ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap

        val rotated = runCatching {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(degrees) },
                true,
            )
        }.getOrNull() ?: return bitmap

        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
