package app.justthecarbs.domain

/**
 * Chooses which product image URL to display, and at which quality (development-pass brief §5).
 *
 * The calculator's hero image exists to answer one question: *is this the package in my hand?* A
 * 200 px thumbnail cannot answer it — many packages in a range differ only in a colour band or a
 * flavour word. So the hero prefers Open Food Facts' larger front image and falls back only when
 * that is missing.
 *
 * Verified against the live API on 2026-08-14 (barcode 3017620422003):
 *
 * | Field | Actual size |
 * |---|---|
 * | `image_front_url` | **400 px** (`front_en.879.400.jpg`) |
 * | `image_front_small_url` | 200 px |
 * | `image_front_thumb_url` | 100 px |
 *
 * 400 px is the right size for a 120–170 dp hero (≈360–510 px at 3×) and is a display variant OFF
 * generates itself — not the multi-megabyte original. Requesting the original would be slower, use
 * more of the user's data, and decode to a far larger bitmap for no visible gain (§33).
 *
 * **Every candidate still passes through [ProductImageUrlValidator].** Preferring a bigger image
 * must not become a way to reach a host the allowlist would otherwise reject, so validation happens
 * here per-candidate rather than once on whichever URL was picked (§5, §24).
 */
object ProductImageSelector {

    /**
     * Safe, de-duplicated gallery images with at most one entry for each semantic role.
     *
     * Legacy/cached products predating structured `selected_images` (or search-result products,
     * which never carry gallery metadata — see [OpenFoodFactsApi.SEARCH_FIELDS]) can still have a
     * safe [Product.largeImageUrl]/[Product.imageUrl]. If [heroImageUrl] can show that photo, the
     * gallery must be able to open it too: a synthesized [ProductImageType.FRONT] entry is added
     * when the structured gallery has none, so "can I see a hero photo" and "can I tap it open"
     * never disagree.
     */
    fun galleryImages(product: Product): List<ProductImage> {
        val structured = product.images
            .mapNotNull { image ->
                ProductImageUrlValidator.validate(image.displayUrl)?.let { image.copy(displayUrl = it) }
            }
            .distinctBy { it.type }
            .distinctBy { it.displayUrl }

        if (structured.any { it.type == ProductImageType.FRONT }) return structured

        val fallbackUrl = ProductImageUrlValidator.validate(product.largeImageUrl)
            ?: ProductImageUrlValidator.validate(product.imageUrl)
            ?: return structured
        if (structured.any { it.displayUrl == fallbackUrl }) return structured

        return structured + ProductImage(type = ProductImageType.FRONT, language = null, displayUrl = fallbackUrl)
    }

    /**
     * The best safe image for a large, identification-grade display.
     *
     * Returns `null` when neither candidate is present or safe — callers fall back to the monogram
     * tile, exactly as they already do for a product with no image at all.
     */
    fun heroImageUrl(product: Product): String? =
        galleryImages(product)
            .firstOrNull { it.type == ProductImageType.FRONT }
            ?.displayUrl
            ?: ProductImageUrlValidator.validate(product.largeImageUrl)
            ?: ProductImageUrlValidator.validate(product.imageUrl)

    /**
     * The best safe image for a compact thumbnail (Recents, search results).
     *
     * Prefers the *smaller* image deliberately: a list of 52 dp tiles gains nothing from 400 px
     * bitmaps, and decoding them costs memory on a screen that may show many at once (§33). Falls
     * back to the larger one so a product that only has a big image still shows something.
     */
    fun thumbnailUrl(product: Product): String? =
        ProductImageUrlValidator.validate(product.imageUrl)
            ?: ProductImageUrlValidator.validate(product.largeImageUrl)
}
