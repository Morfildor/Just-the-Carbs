package app.justthecarbs.domain

import app.justthecarbs.data.local.toDomain
import app.justthecarbs.data.local.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Hero-image selection (development-pass brief §5, §29).
 *
 * Two things must hold at once: prefer the larger image so the user can actually recognise their
 * package, and never let that preference become a way past the host allowlist.
 */
class ProductImageSelectorTest {

    private val large = "https://images.openfoodfacts.org/images/products/301/762/042/2003/front_en.879.400.jpg"
    private val small = "https://images.openfoodfacts.org/images/products/301/762/042/2003/front_en.879.200.jpg"

    private fun product(
        imageUrl: String? = null,
        largeImageUrl: String? = null,
        images: List<ProductImage> = emptyList(),
    ) = Product(
        barcode = "3017620422003",
        name = "Nutella",
        carbsPer100 = BigDecimal("57.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        imageUrl = imageUrl,
        largeImageUrl = largeImageUrl,
        images = images,
    )

    @Test
    fun `the hero prefers the larger front image`() {
        val hero = ProductImageSelector.heroImageUrl(product(imageUrl = small, largeImageUrl = large))
        assertEquals(large, hero)
    }

    @Test
    fun `the hero prefers a validated selected front image`() {
        val selected = ProductImage(ProductImageType.FRONT, "nl", "https://images.openfoodfacts.org/front-nl.400.jpg")

        val hero = ProductImageSelector.heroImageUrl(
            product(imageUrl = small, largeImageUrl = large, images = listOf(selected)),
        )

        assertEquals(selected.displayUrl, hero)
    }

    @Test
    fun `gallery metadata survives the Room entity mapping`() {
        val images = listOf(
            ProductImage(ProductImageType.FRONT, "nl", large),
            ProductImage(ProductImageType.NUTRITION, "en", "https://images.openfoodfacts.org/nutrition.400.jpg"),
        )

        val restored = product(images = images).toEntity().toDomain()

        assertEquals(images, restored.images)
    }

    @Test
    fun `unsafe cached gallery URLs are discarded when decoded`() {
        val unsafe = ProductImage(ProductImageType.FRONT, "en", "https://evil.example.com/front.jpg")

        val restored = product(images = listOf(unsafe)).toEntity().toDomain()

        assertTrue(restored.images.isEmpty())
    }

    @Test
    fun `the hero falls back to the small image when no large one exists`() {
        val hero = ProductImageSelector.heroImageUrl(product(imageUrl = small, largeImageUrl = null))
        assertEquals(small, hero)
    }

    @Test
    fun `the hero is null when the product has no image at all`() {
        assertNull(ProductImageSelector.heroImageUrl(product()))
    }

    /**
     * The security property: preferring a bigger image must not widen the allowlist. Each candidate
     * is validated separately, so an unsafe large URL falls through to a safe small one rather than
     * being used because it was "the better image".
     */
    @Test
    fun `an unsafe large image falls back to the safe small one`() {
        val hero = ProductImageSelector.heroImageUrl(
            product(imageUrl = small, largeImageUrl = "https://images.openfoodfacts.org.evil.com/front.400.jpg"),
        )
        assertEquals(small, hero)
    }

    @Test
    fun `a plain http large image is rejected even on an approved host`() {
        val hero = ProductImageSelector.heroImageUrl(
            product(imageUrl = null, largeImageUrl = large.replace("https://", "http://")),
        )
        assertNull(hero)
    }

    @Test
    fun `both candidates unsafe yields no image rather than a bad one`() {
        val hero = ProductImageSelector.heroImageUrl(
            product(
                imageUrl = "https://evil.example.com/small.jpg",
                largeImageUrl = "https://evil.example.com/large.jpg",
            ),
        )
        assertNull(hero)
    }

    // ---- thumbnails deliberately prefer the SMALL image (§33) ----------------------------------

    @Test
    fun `a thumbnail prefers the small image to avoid decoding oversized bitmaps`() {
        val thumb = ProductImageSelector.thumbnailUrl(product(imageUrl = small, largeImageUrl = large))
        assertEquals(small, thumb)
    }

    @Test
    fun `a thumbnail falls back to the large image when only that exists`() {
        val thumb = ProductImageSelector.thumbnailUrl(product(imageUrl = null, largeImageUrl = large))
        assertEquals(large, thumb)
    }

    @Test
    fun `a thumbnail is null when the product has no image`() {
        assertNull(ProductImageSelector.thumbnailUrl(product()))
    }

    // ---- gallery fallback (rebrand hardening pass §5): the gallery must open whenever the hero
    // photo can be shown, including for legacy/cached products with no structured selected_images.

    @Test
    fun `gallery synthesizes a front entry from the large image when no structured gallery exists`() {
        val images = ProductImageSelector.galleryImages(product(imageUrl = small, largeImageUrl = large))

        assertEquals(1, images.size)
        assertEquals(ProductImageType.FRONT, images.single().type)
        assertEquals(large, images.single().displayUrl)
    }

    @Test
    fun `gallery synthesizes a front entry from the small image when no large image exists either`() {
        val images = ProductImageSelector.galleryImages(product(imageUrl = small, largeImageUrl = null))

        assertEquals(1, images.size)
        assertEquals(small, images.single().displayUrl)
    }

    @Test
    fun `gallery stays empty when neither the structured gallery nor a fallback image is safe`() {
        assertTrue(ProductImageSelector.galleryImages(product()).isEmpty())
    }

    @Test
    fun `gallery does not synthesize a duplicate when the structured gallery already has a front image`() {
        val selected = ProductImage(ProductImageType.FRONT, "nl", large)

        val images = ProductImageSelector.galleryImages(
            product(imageUrl = small, largeImageUrl = large, images = listOf(selected)),
        )

        assertEquals(listOf(selected), images)
    }

    @Test
    fun `gallery does not synthesize a duplicate when the fallback URL matches a non-front structured entry`() {
        val nutrition = ProductImage(ProductImageType.NUTRITION, "en", large)

        val images = ProductImageSelector.galleryImages(
            product(imageUrl = small, largeImageUrl = large, images = listOf(nutrition)),
        )

        // The fallback would duplicate the same URL already present under a different role, so it
        // is not added again — but the existing NUTRITION entry is preserved.
        assertEquals(listOf(nutrition), images)
    }

    @Test
    fun `an unsafe fallback image is not synthesized into the gallery`() {
        val images = ProductImageSelector.galleryImages(
            product(imageUrl = "https://evil.example.com/small.jpg", largeImageUrl = null),
        )

        assertTrue(images.isEmpty())
    }

    @Test
    fun `whenever the hero has a photo the gallery can open it`() {
        val product = product(imageUrl = small, largeImageUrl = large)

        val hero = ProductImageSelector.heroImageUrl(product)
        val gallery = ProductImageSelector.galleryImages(product)

        assertEquals(hero, gallery.firstOrNull { it.type == ProductImageType.FRONT }?.displayUrl)
    }
}
