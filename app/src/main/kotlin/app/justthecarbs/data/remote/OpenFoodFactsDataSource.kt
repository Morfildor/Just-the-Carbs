package app.justthecarbs.data.remote

import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionValueValidator
import app.justthecarbs.domain.PackageQuantityParser
import app.justthecarbs.domain.PortionUnitCandidate
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductDataSource
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductImage
import app.justthecarbs.domain.ProductImageType
import app.justthecarbs.domain.ProductImageUrlValidator
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.ServingSizeParser
import app.justthecarbs.domain.VerificationStatus
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

/**
 * Open Food Facts as a [ProductDataSource] (§11, §12).
 *
 * Nothing above this class knows OFF exists. Adding GS1 or a retailer feed later means writing
 * another implementation of the same interface.
 *
 * Every remote value passes through [NutritionValueValidator] before it can become a [Product]:
 * this is the boundary where untrusted data stops being trusted (§13).
 */
class OpenFoodFactsDataSource(
    private val api: OpenFoodFactsApi,
    private val preferredLanguage: () -> String = { Locale.getDefault().toLanguageTag() },
) : ProductDataSource, ProductSearchSource {

    /**
     * Free-text search (spec §9).
     *
     * Hits are mapped leniently on purpose: a record missing its carbohydrate value still appears,
     * because the user may well recognise the package and can verify it from the label afterwards.
     * That is the opposite of [fetch]'s rule, and deliberately so — [fetch] returns something the
     * app is about to calculate with, while a hit is only something the user is being asked to
     * recognise. Nothing here can become a stored product without an explicit tap and a normal
     * barcode lookup.
     *
     * A hit with no barcode or no name is dropped: neither can be selected usefully, and a blank
     * row in a disambiguation list is worse than a shorter list.
     */
    override suspend fun search(terms: String): ProductSearchResult {
        val query = terms.trim()
        if (query.isEmpty()) return ProductSearchResult.NoMatches

        return try {
            val response = api.search(query)
            when {
                response.code() == HTTP_TOO_MANY_REQUESTS ->
                    ProductSearchResult.Failed(LookupError.RATE_LIMITED)
                // Observed live on 2026-08-14: this endpoint intermittently answers 503 with an
                // HTML "temporarily unavailable" page while the product-read endpoint is fine.
                // Reported as a server problem the user can retry, never as "no matches" — telling
                // someone their product does not exist because a search host was busy would send
                // them off to type in a label they did not need to.
                !response.isSuccessful -> ProductSearchResult.Failed(LookupError.SERVER)
                else -> toSearchResult(response.body())
            }
        } catch (_: UnknownHostException) {
            ProductSearchResult.Failed(LookupError.OFFLINE)
        } catch (_: SocketTimeoutException) {
            ProductSearchResult.Failed(LookupError.TIMEOUT)
        } catch (_: SerializationException) {
            ProductSearchResult.Failed(LookupError.MALFORMED)
        } catch (_: IOException) {
            ProductSearchResult.Failed(LookupError.OFFLINE)
        }
    }

    private fun toSearchResult(body: OffSearchResponse?): ProductSearchResult {
        val hits = body?.products.orEmpty().mapNotNull { it.toHit() }
        return if (hits.isEmpty()) ProductSearchResult.NoMatches else ProductSearchResult.Found(hits)
    }

    private fun OffProduct.toHit(): ProductSearchHit? {
        val barcode = code?.takeIf { it.isNotBlank() } ?: return null
        val displayName = listOfNotNull(productNameNl, productName)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return null

        val parsedQuantity = PackageQuantityParser.parse(quantity)
        val basis = parsedQuantity?.basis ?: PackageQuantityParser.inferBasis(quantity)

        return ProductSearchHit(
            barcode = barcode,
            name = displayName,
            brand = brands?.takeIf { it.isNotBlank() }?.substringBefore(',')?.trim(),
            packageQuantity = quantity?.takeIf { it.isNotBlank() }?.trim(),
            // Still validated: an out-of-range figure is shown as "no value" rather than as a
            // number, so a card can never display something the calculator would refuse (§13).
            carbsPer100 = NutritionValueValidator.validateCarbsPer100(
                raw = nutriments?.carbohydrates100g,
                basis = basis,
            ),
            basis = basis,
            imageUrl = imageFrontUrl?.takeIf { it.isNotBlank() }
                ?: imageFrontSmallUrl?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun fetch(barcode: String): ProductFetchResult = try {
        val response = api.getProduct(barcode)
        when {
            // OFF allows 15 reads/min/IP. Surfacing this distinctly lets the UI say "too many
            // lookups just now" instead of a generic failure the user cannot act on (§36).
            response.code() == HTTP_TOO_MANY_REQUESTS -> ProductFetchResult.Failed(LookupError.RATE_LIMITED)
            response.code() == HTTP_NOT_FOUND -> ProductFetchResult.NotFound
            !response.isSuccessful -> ProductFetchResult.Failed(LookupError.SERVER)
            else -> toResult(response.body(), barcode)
        }
    } catch (_: UnknownHostException) {
        ProductFetchResult.Failed(LookupError.OFFLINE)
    } catch (_: SocketTimeoutException) {
        ProductFetchResult.Failed(LookupError.TIMEOUT)
    } catch (_: SerializationException) {
        ProductFetchResult.Failed(LookupError.MALFORMED)
    } catch (_: IOException) {
        // Any other transport-level problem. Deliberately last, since the cases above are all
        // IOExceptions too and each deserves its own message.
        ProductFetchResult.Failed(LookupError.OFFLINE)
    }

    private fun toResult(body: OffProductResponse?, barcode: String): ProductFetchResult {
        val remote = body?.product ?: return ProductFetchResult.NotFound

        // A record with no name cannot be shown in Recents or recognised on a second scan. Treating
        // it as absent routes the user to manual entry, which is a working outcome rather than a
        // nameless row (§26). It is never a dead end.
        val name = listOfNotNull(remote.productNameNl, remote.productName)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return ProductFetchResult.NotFound

        val quantity = PackageQuantityParser.parse(remote.quantity)
        val basis = quantity?.basis ?: PackageQuantityParser.inferBasis(remote.quantity)

        val carbs = NutritionValueValidator.validateCarbsPer100(
            raw = remote.nutriments?.carbohydrates100g,
            basis = basis,
        ) ?: return ProductFetchResult.Unusable(barcode)

        // A parsed serving size is only trustworthy if its basis matches the product's own — a
        // countable unit measured in ml has no meaning for a product whose carbs are per 100 g, and
        // the two never converting into each other (§17) rules out silently coercing one to the
        // other here too.
        val servingSize = ServingSizeParser.parse(remote.servingSize)
            ?.takeIf { it.basis == basis }
            ?.let {
                PortionUnitCandidate(
                    kind = it.kind,
                    amountPerUnit = it.amountPerUnit,
                    basis = it.basis,
                    rawServingText = remote.servingSize.orEmpty(),
                )
            }

        return ProductFetchResult.Found(
            product = Product(
                barcode = barcode,
                name = name,
                carbsPer100 = carbs,
                basis = basis,
                dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
                verificationStatus = VerificationStatus.UNVERIFIED,
                brand = remote.brands?.takeIf { it.isNotBlank() }?.substringBefore(',')?.trim(),
                packageAmount = quantity?.amount,
                imageUrl = remote.imageFrontSmallUrl?.takeIf { it.isNotBlank() },
                largeImageUrl = remote.imageFrontUrl?.takeIf { it.isNotBlank() },
                images = remote.selectedProductImages(),
            ),
            portionUnitCandidate = servingSize,
        )
    }

    /**
     * Chooses one display image per role. The ordering is deliberately explicit and stable:
     * device/app language, product language, English, then lexicographic fallback. Unsafe URLs are
     * skipped rather than blocking a later safe language, and a repeated URL is shown only once.
     */
    private fun OffProduct.selectedProductImages(): List<ProductImage> {
        val selected = selectedImages ?: return emptyList()
        val priorities = buildList {
            addLanguagePreference(preferredLanguage())
            addLanguagePreference(lang)
            addLanguagePreference("en")
        }.distinct()

        return listOf(
            ProductImageType.FRONT to selected.front,
            ProductImageType.NUTRITION to selected.nutrition,
            ProductImageType.INGREDIENTS to selected.ingredients,
            ProductImageType.PACKAGING to selected.packaging,
        ).mapNotNull { (type, image) -> image?.selectDisplay(type, priorities) }
            .distinctBy { it.displayUrl }
    }

    private fun MutableList<String>.addLanguagePreference(language: String?) {
        val normalized = normalizeLanguage(language) ?: return
        add(normalized)
        normalized.substringBefore('-').takeIf { it != normalized }?.let(::add)
    }

    private fun OffSelectedImage.selectDisplay(
        type: ProductImageType,
        priorities: List<String>,
    ): ProductImage? {
        val entries = display.entries.sortedBy { it.key.lowercase(Locale.ROOT) }
        val ordered = priorities.flatMap { preferred ->
            entries.filter { normalizeLanguage(it.key) == preferred }
        } + entries

        return ordered.distinctBy { it.key.lowercase(Locale.ROOT) }.firstNotNullOfOrNull { entry ->
            ProductImageUrlValidator.validate(entry.value)?.let { safeUrl ->
                ProductImage(type = type, language = entry.key, displayUrl = safeUrl)
            }
        }
    }

    private fun normalizeLanguage(language: String?): String? = language
        ?.trim()
        ?.replace('_', '-')
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
