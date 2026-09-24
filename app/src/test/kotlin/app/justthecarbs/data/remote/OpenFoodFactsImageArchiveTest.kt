package app.justthecarbs.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A search result's front photo from Open Food Facts' S3 archive, and when not to use it.
 *
 * Measured 2026-09-23: `images.openfoodfacts.org` took 4 to 34 s to open a connection, the archive
 * 0.12 s median (0.17 s worst) and held every front photo of 100 search results. The archive holds
 * the photos as uploaded, not the cropped, rotated or recoloured pictures Open Food Facts shows, so
 * only a front photo shown unmodified may come from it. Every other case must return null, which
 * keeps the result on the address it has always used.
 *
 * The entries below are live ones (2026-09-23), trimmed to the fields the rule reads.
 */
class OpenFoodFactsImageArchiveTest {

    /** Nutella-Muffin, 8000500392935: the upload shown as it is. */
    private val muffinCode = "8000500392935"
    private val muffinFrontUrl =
        "https://images.openfoodfacts.org/images/products/800/050/039/2935/front_de.74.400.jpg"
    private val muffinFront = """
        {"angle":0,"coordinates_image_size":"full","geometry":"0x0--1--1","imgid":"19",
         "normalize":null,"rev":"74","sizes":{"400":{"h":248,"w":400}},"white_magic":null,
         "x1":"-1","x2":"-1","y1":"-1","y2":"-1"}
    """.trimIndent()
    private val muffinUpload = """{"sizes":{"400":{"h":248,"w":400}}}"""
    private val muffinArchiveUrl =
        "https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/800/050/039/2935/19.400.jpg"

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun images(vararg entries: Pair<String, String>): JsonObject =
        json(entries.joinToString(prefix = "{", postfix = "}") { (key, value) -> "\"$key\":$value" })

    /** The muffin's front entry with [overrides], next to its unchanged upload entry. */
    private fun muffin(vararg overrides: Pair<String, String>): JsonObject {
        val front = json(muffinFront).toMutableMap()
        overrides.forEach { (key, value) -> front[key] = Json.parseToJsonElement(value) }
        return images("front_de" to JsonObject(front).toString(), "19" to muffinUpload)
    }

    private fun archiveUrl(code: String, frontUrl: String?, images: JsonObject?) =
        OpenFoodFactsImageArchive.urlFor(code, frontUrl, images)

    // ---- mapping -------------------------------------------------------------------------------

    @Test
    fun `an unmodified front photo maps to its archive original at 400 px`() {
        assertEquals(muffinArchiveUrl, archiveUrl(muffinCode, muffinFrontUrl, muffin()))
    }

    /**
     * The trap found while measuring: for an 8-digit barcode the photo host's folder is the bare
     * code (`80809180/`) and the archive's is the code padded to 13 digits. The unpadded folder is a
     * 404 on the archive for every short code tried (6, 8 and 11 digits).
     */
    @Test
    fun `a short barcode is padded to 13 digits for the archive folder`() {
        val frontUrl = "https://images.openfoodfacts.org/images/products/80809180/front_en.96.400.jpg"
        val front = """{"angle":0,"geometry":"0x0--1--1","imgid":"14","rev":"96","sizes":{"400":{"h":400,"w":220}}}"""

        assertEquals(
            "https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/000/008/080/9180/14.400.jpg",
            archiveUrl("80809180", frontUrl, images("front_en" to front, "14" to """{"sizes":{"400":{"h":400,"w":220}}}""")),
        )
    }

    @Test
    fun `a 12-digit code is padded and a 14-digit code is split as it is`() {
        assertEquals(
            "https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/001/234/567/8905/19.400.jpg",
            archiveUrl("012345678905", muffinFrontUrl, muffin()),
        )
        assertEquals(
            "https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/520/266/716/46291/19.400.jpg",
            archiveUrl("52026671646291", muffinFrontUrl, muffin()),
        )
    }

    /** The front URL names the selected language's picture; another language's may be modified. */
    @Test
    fun `the entry is the one the front URL names, not another front picture`() {
        val croppedFront = JsonObject(
            json(muffinFront).toMutableMap().apply { put("geometry", Json.parseToJsonElement("\"1405x2015-291-207\"")) },
        ).toString()

        assertNull(
            archiveUrl(
                muffinCode,
                muffinFrontUrl,
                images("front_en" to muffinFront, "front_de" to croppedFront, "19" to muffinUpload),
            ),
        )
    }

    @Test
    fun `the small front URL names the same picture as the large one`() {
        assertEquals(muffinArchiveUrl, archiveUrl(muffinCode, muffinFrontUrl.replace(".400.jpg", ".200.jpg"), muffin()))
    }

    /** Numbers arrive as JSON strings on some records and as numbers on others (both seen live). */
    @Test
    fun `numeric fields are read whether they arrive as strings or numbers`() {
        val mixed = muffin("imgid" to "19", "rev" to "74", "angle" to "0.0", "x1" to "-1", "x2" to "0")

        assertEquals(muffinArchiveUrl, archiveUrl(muffinCode, muffinFrontUrl, mixed))
    }

    // ---- eligibility: anything Open Food Facts changed stays on its own picture ----------------

    @Test
    fun `a rotated picture is not taken from the archive`() {
        listOf("90", "90.0", "\"270\"", "180").forEach { angle ->
            assertNull("angle $angle", archiveUrl(muffinCode, muffinFrontUrl, muffin("angle" to angle)))
        }
    }

    /**
     * Machandel tomatensoep (8713938000357), live: the entry has no `angle` at all and a `0x0`
     * geometry, yet the 400x300 upload is shown as 300x400. The emulator benchmark showed the
     * sideways upload in the row until the size check existed.
     */
    @Test
    fun `a rotation the index does not record is caught by the size`() {
        val frontUrl = "https://images.openfoodfacts.org/images/products/871/393/800/0357/front_nl.6.400.jpg"
        val front = """{"geometry":"0x0-0-0","imgid":"2","normalize":"false","rev":"6","sizes":{"400":{"h":400,"w":300}},"white_magic":"false"}"""
        val upload = """{"sizes":{"400":{"h":300,"w":400}}}"""

        assertNull(archiveUrl("8713938000357", frontUrl, images("front_nl" to front, "2" to upload)))
    }

    /** Nutella 825 g (3017620429484), live: a `0x0` geometry and -1 coordinates, yet 400x400 → 372x400. */
    @Test
    fun `a trim the index does not record is caught by the size`() {
        val frontUrl = "https://images.openfoodfacts.org/images/products/301/762/042/9484/front_fr.409.400.jpg"
        val front = """{"angle":0,"coordinates_image_size":"400","geometry":"0x0--3--3","imgid":"149","normalize":"false","rev":"409","sizes":{"400":{"h":400,"w":372}},"white_magic":"false","x1":"-1","x2":"-1","y1":"-1","y2":"-1"}"""
        val upload = """{"sizes":{"400":{"h":400,"w":400}}}"""

        assertNull(archiveUrl("3017620429484", frontUrl, images("front_fr" to front, "149" to upload)))
    }

    @Test
    fun `without the upload's own entry or sizes there is no archive address`() {
        assertNull("no upload entry", archiveUrl(muffinCode, muffinFrontUrl, images("front_de" to muffinFront)))
        assertNull(
            "upload has no sizes",
            archiveUrl(muffinCode, muffinFrontUrl, images("front_de" to muffinFront, "19" to "{}")),
        )
        assertNull("front has no sizes", archiveUrl(muffinCode, muffinFrontUrl, muffin("sizes" to "{}")))
    }

    /** The live cropped case (80051428) has every coordinate empty; only the geometry shows it. */
    @Test
    fun `a crop shown only by the geometry is not taken from the archive`() {
        val cropped = muffin(
            "geometry" to "\"1405x2015-291-207\"",
            "x1" to "null", "y1" to "null", "x2" to "null", "y2" to "null",
        )

        assertNull(archiveUrl(muffinCode, muffinFrontUrl, cropped))
    }

    @Test
    fun `a crop shown only by the coordinates is not taken from the archive`() {
        val cropped = muffin("x1" to "\"-1\"", "y1" to "\"-1\"", "x2" to "\"1500\"", "y2" to "\"1500\"")

        assertNull(archiveUrl(muffinCode, muffinFrontUrl, cropped))
    }

    /** Recoloured pictures are not the upload either: normalised contrast, or a whitened background. */
    @Test
    fun `a normalised or background-whitened picture is not taken from the archive`() {
        listOf("\"true\"", "\"checked\"", "true").forEach { flag ->
            assertNull("normalize $flag", archiveUrl(muffinCode, muffinFrontUrl, muffin("normalize" to flag)))
            assertNull("white_magic $flag", archiveUrl(muffinCode, muffinFrontUrl, muffin("white_magic" to flag)))
        }
    }

    /** Every live unmodified picture used one of these forms for "off". */
    @Test
    fun `the filters count as off when false, zero, null or absent`() {
        listOf("\"false\"", "\"0\"", "0", "false", "null", "\"\"").forEach { off ->
            assertEquals(
                "filter $off",
                muffinArchiveUrl,
                archiveUrl(muffinCode, muffinFrontUrl, muffin("normalize" to off, "white_magic" to off)),
            )
        }
    }

    /** The URL and the entry disagree about which revision is selected: the entry is not that picture. */
    @Test
    fun `a revision that differs from the front URL is not taken from the archive`() {
        assertNull(archiveUrl(muffinCode, muffinFrontUrl, muffin("rev" to "\"75\"")))
    }

    // ---- anything unreadable leaves the result on its usual address -----------------------------

    @Test
    fun `missing or unreadable input gives no archive address`() {
        assertNull("no images", archiveUrl(muffinCode, muffinFrontUrl, null))
        assertNull("no front url", archiveUrl(muffinCode, null, muffin()))
        assertNull("no entry for the key", archiveUrl(muffinCode, muffinFrontUrl, images("front_en" to muffinFront, "19" to muffinUpload)))
        assertNull("entry is not an object", archiveUrl(muffinCode, muffinFrontUrl, images("front_de" to "\"x\"", "19" to muffinUpload)))
        assertNull("no imgid", archiveUrl(muffinCode, muffinFrontUrl, muffin("imgid" to "null")))
        assertNull("imgid not a number", archiveUrl(muffinCode, muffinFrontUrl, muffin("imgid" to "\"1/../2\"")))
        assertNull("code not digits", archiveUrl("80005003/2935", muffinFrontUrl, muffin()))
        assertNull("code too long", archiveUrl("800050039293512", muffinFrontUrl, muffin()))
        assertNull("no code", OpenFoodFactsImageArchive.urlFor(null, muffinFrontUrl, muffin()))
    }

    @Test
    fun `only a front picture on Open Food Facts' own image host is mapped`() {
        listOf(
            muffinFrontUrl.replace("https://images.", "https://static."),
            muffinFrontUrl.replace("https://", "http://"),
            muffinFrontUrl.replace("https://images.openfoodfacts.org", "https://evil.example"),
            muffinFrontUrl.replace("front_de.74", "ingredients_de.74"),
            muffinFrontUrl.replace("front_de.74.400", "19.400"),
        ).forEach { url ->
            assertNull(url, archiveUrl(muffinCode, url, muffin()))
        }
    }
}
