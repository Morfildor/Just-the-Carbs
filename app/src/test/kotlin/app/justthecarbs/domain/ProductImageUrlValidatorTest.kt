package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A malicious or corrupt remote product record must not make the app load an image from an
 * arbitrary third-party host (countable-portions brief §15). Confirmed hosts per the OFF image
 * documentation, 2026-08-14: `images.openfoodfacts.org` is the live-verified CDN host;
 * `static.openfoodfacts.org` appears in OFF's own schema examples but was not independently
 * live-verified — included anyway, since rejecting a real OFF host costs nothing (falls back to
 * the monogram tile) while over-trusting an unlisted host is the actual risk.
 */
class ProductImageUrlValidatorTest {

    @Test
    fun `accepts an https images openfoodfacts url`() {
        val url = "https://images.openfoodfacts.org/images/products/301/762/042/2003/front_en.879.400.jpg"

        assertEquals(url, ProductImageUrlValidator.validate(url))
    }

    @Test
    fun `accepts an https static openfoodfacts url`() {
        val url = "https://static.openfoodfacts.org/images/products/example.jpg"

        assertEquals(url, ProductImageUrlValidator.validate(url))
    }

    @Test
    fun `rejects plain http even on an approved host`() {
        assertNull(ProductImageUrlValidator.validate("http://images.openfoodfacts.org/images/products/x.jpg"))
    }

    @Test
    fun `rejects an unapproved host`() {
        assertNull(ProductImageUrlValidator.validate("https://evil.example.com/images/products/x.jpg"))
    }

    @Test
    fun `rejects a host that merely contains the approved host as a substring`() {
        assertNull(ProductImageUrlValidator.validate("https://images.openfoodfacts.org.evil.com/x.jpg"))
    }

    @Test
    fun `rejects null, blank and malformed input`() {
        assertNull(ProductImageUrlValidator.validate(null))
        assertNull(ProductImageUrlValidator.validate(""))
        assertNull(ProductImageUrlValidator.validate("   "))
        assertNull(ProductImageUrlValidator.validate("not a url"))
    }

    // ---- Open Food Facts' S3 archive: search thumbnails only, one exact shape ------------------

    private val archiveUrl =
        "https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/301/762/042/9484/149.400.jpg"

    @Test
    fun `accepts an archive original in the data folder`() {
        assertEquals(archiveUrl, ProductImageUrlValidator.validateArchive(archiveUrl))
        val longCode = "https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/520/266/716/46291/8.400.jpg"
        assertEquals(longCode, ProductImageUrlValidator.validateArchive(longCode))
    }

    /** Products and Recents keep Open Food Facts' own picture; the archive is for search rows only. */
    @Test
    fun `the product image rule does not accept the archive`() {
        assertNull(ProductImageUrlValidator.validate(archiveUrl))
    }

    @Test
    fun `the archive rule does not accept Open Food Facts' own image host`() {
        assertNull(
            ProductImageUrlValidator.validateArchive(
                "https://images.openfoodfacts.org/images/products/301/762/042/9484/front_fr.409.400.jpg",
            ),
        )
    }

    @Test
    fun `rejects anything but the exact archive shape`() {
        listOf(
            archiveUrl.replace("https://", "http://"),
            // Another bucket, another region, or the same bucket under S3's other host forms.
            archiveUrl.replace("openfoodfacts-images.", "openfoodfacts-images2."),
            archiveUrl.replace("eu-west-3", "us-east-1"),
            archiveUrl.replace("openfoodfacts-images.s3.eu-west-3.amazonaws.com", "s3.eu-west-3.amazonaws.com/openfoodfacts-images"),
            archiveUrl.replace(".amazonaws.com", ".amazonaws.com.evil.com"),
            // Outside /data/, another size, the full upload, or a path that climbs out.
            archiveUrl.replace("/data/", "/"),
            archiveUrl.replace("/data/", "/other/"),
            archiveUrl.replace(".400.jpg", ".jpg"),
            archiveUrl.replace(".400.jpg", ".200.jpg"),
            archiveUrl.replace(".400.jpg", ".400.json"),
            archiveUrl.replace("/9484/", "/9484/../"),
            archiveUrl.replace("/301/762/042/9484/", "/301/762/9484/"),
            // Anything added to the request.
            "$archiveUrl?x=1",
            "$archiveUrl#x",
            archiveUrl.replace("amazonaws.com/", "amazonaws.com:8443/"),
            archiveUrl.replace("https://", "https://user@"),
            null,
            "",
            "not a url",
        ).forEach { url ->
            assertNull(url, ProductImageUrlValidator.validateArchive(url))
        }
    }
}
