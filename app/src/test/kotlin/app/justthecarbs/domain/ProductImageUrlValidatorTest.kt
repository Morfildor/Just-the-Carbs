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
}
