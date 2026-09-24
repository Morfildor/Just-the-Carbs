package app.justthecarbs.data.remote

import app.justthecarbs.domain.ProductImageUrlValidator
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A search result's front photo in Open Food Facts' image archive on Amazon S3.
 *
 * Measured 2026-09-23: `images.openfoodfacts.org` (one server) took 4 to 34 s to open a
 * connection and 0.2 to 3.6 s per photo on an open one; the archive answered in 0.12 s median,
 * 0.17 s worst, and held every front photo of 100 search results.
 *
 * The archive holds the photos **as uploaded** (`<image number>.400.jpg`), not the pictures Open
 * Food Facts derives from them, so an address is built only when the front picture is the upload
 * unmodified: no crop, no rotation, no contrast normalisation, no background whitening, and the
 * same 400 px size as the upload's own entry. The size check is not redundant: the index can omit
 * a rotation (Machandel tomatensoep, 8713938000357: a 400x300 upload shown as 300x400, no `angle`)
 * or a trim (Nutella 825 g, 3017620429484: a 400x400 upload shown as 372x400, `geometry` `0x0…`),
 * and without it the emulator benchmark showed the wrong picture. With it: 483 of 622 front photos
 * across 15 searches, and 39 of 40 sampled pairs matched to within JPEG noise (the 40th could not
 * be compared: Open Food Facts' own address for it was a 404). Anything else, or anything
 * unreadable, gives null and the row keeps Open Food Facts' own picture.
 *
 * The bucket is Open Food Facts' own (`openfoodfacts-images`, eu-west-3; "Managed By Open Food
 * Facts" on the AWS Open Data Registry, CC BY-SA like the photos themselves). Its documentation,
 * read 2026-09-23 (openfoodfacts.github.io/openfoodfacts-server/api/aws-images-dataset), says it
 * is synced monthly and "some recent images are likely missing", which is why the search row falls
 * back to Open Food Facts' own address on any failure. An upload's number never changes what it
 * shows, so a synced copy cannot be an older version of the same picture.
 */
internal object OpenFoodFactsImageArchive {

    /** The front URL Search-a-licious returns: folder, then `front[_lang].<rev>.<size>.jpg`. */
    private val FRONT_URL = Regex(
        """^https://images\.openfoodfacts\.org/images/products/[0-9/]+/(front(?:_[a-z]{2,3})?)\.(\d+)\.(?:\d+|full)\.jpg$""",
    )

    /** Coordinates Open Food Facts writes when nothing was cropped. */
    private val NO_CROP = setOf(-1.0, 0.0)

    /**
     * [code] is Open Food Facts' own `code`, the key its photos are filed under, not the app's
     * normalised barcode (which adds or drops a leading zero). [frontUrl] names which picture is
     * the front one and its revision; [images] is the hit's `images` object.
     */
    fun urlFor(code: String?, frontUrl: String?, images: JsonObject?): String? {
        val folder = folderFor(code) ?: return null
        val match = FRONT_URL.matchEntire(frontUrl?.trim().orEmpty()) ?: return null
        val (key, revision) = match.destructured
        val entry = images?.get(key) as? JsonObject ?: return null
        val imageNumber = entry.text("imgid")?.takeIf { it.all(Char::isDigit) } ?: return null
        val upload = images[imageNumber] as? JsonObject ?: return null
        if (!isUnmodifiedUpload(entry, upload, revision)) return null

        return ProductImageUrlValidator.validateArchive(
            "https://${ProductImageUrlValidator.ARCHIVE_HOST}/data/$folder/$imageNumber.400.jpg",
        )
    }

    /**
     * The archive files a code padded to 13 digits and split 3/3/3/rest. Checked 2026-09-23 on
     * codes of 6, 8, 11, 13 and 14 digits: the unpadded folder is a 404 for every short one, even
     * though the image host's own URLs use it (`80809180/front_en.96.400.jpg`).
     */
    private fun folderFor(code: String?): String? {
        val digits = code?.trim().orEmpty()
        if (digits.isEmpty() || digits.length > 14 || !digits.all(Char::isDigit)) return null
        val padded = digits.padStart(13, '0')
        return "${padded.substring(0, 3)}/${padded.substring(3, 6)}/${padded.substring(6, 9)}/${padded.substring(9)}"
    }

    private fun isUnmodifiedUpload(entry: JsonObject, upload: JsonObject, revision: String): Boolean {
        // The URL and the entry must describe the same selection.
        if (entry.text("rev") != revision) return false
        val angle = entry.text("angle")
        if (angle != null && angle.toDoubleOrNull() != 0.0) return false
        // A crop shows in the geometry ("1405x2015-291-207"), in the coordinates, or in both.
        if (entry.text("geometry")?.startsWith("0x0") == false) return false
        val cropped = listOf("x1", "y1", "x2", "y2").any { name ->
            val value = entry.text(name)
            value != null && value.toDoubleOrNull() !in NO_CROP
        }
        if (cropped) return false
        if (!entry.isOff("normalize") || !entry.isOff("white_magic")) return false
        // The shape the picture is shown at must be the upload's own: a rotation swaps width and
        // height and a trim changes them, and the index does not always record either.
        val shown = entry.size400() ?: return false
        return shown == upload.size400()
    }

    /** Width and height of the 400 px rendition, as numbers, or null if either is missing. */
    private fun JsonObject.size400(): Pair<Double, Double>? {
        val size = (get("sizes") as? JsonObject)?.get("400") as? JsonObject ?: return null
        val width = size.text("w")?.toDoubleOrNull() ?: return null
        val height = size.text("h")?.toDoubleOrNull() ?: return null
        return width to height
    }

    /** Every "off" form seen live: absent, null, empty, false and 0, as strings or JSON values. */
    private fun JsonObject.isOff(name: String): Boolean =
        text(name)?.lowercase().let { it == null || it == "false" || it == "0" || it == "null" }

    /** A primitive's trimmed content, or null for absent, JSON null, blank, arrays and objects. */
    private fun JsonObject.text(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }
}
