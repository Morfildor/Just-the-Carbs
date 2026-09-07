package app.justthecarbs.data.remote

import app.justthecarbs.domain.BarcodeValidator
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.NutritionValueValidator
import app.justthecarbs.domain.PackageBasisResolver
import app.justthecarbs.domain.PortionConversion
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
import app.justthecarbs.domain.UnusableReason
import app.justthecarbs.domain.VerificationStatus
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.math.RoundingMode
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
                // The server's own stated wait is carried through rather than discarded: the
                // governor can then honour exactly what was asked for instead of guessing, and a
                // 429 with no usable header falls back to its conservative default.
                response.code() == HTTP_TOO_MANY_REQUESTS -> ProductSearchResult.Failed(
                    LookupError.RATE_LIMITED,
                    retryAfterMs = RetryAfterHeader.parseMs(
                        response.headers()[HEADER_RETRY_AFTER],
                        System.currentTimeMillis(),
                    ),
                )
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
        // Validated and normalised (P1 §10), not merely non-blank. `code` is untrusted remote text —
        // a search hit's barcode has never passed through the scanner or manual entry's own
        // BarcodeValidator check, unlike every other barcode this app puts into a `product/{barcode}`
        // route. A malformed value (wrong length, non-digit characters, or one containing `/`, `?`,
        // `#`, `%` or whitespace) reaching that route unvalidated could corrupt navigation — extra
        // path segments, a broken route match, or characters a URI parser treats as delimiters. This
        // is the boundary (see the class KDoc) where that stops being possible: `normalize` returns
        // null for anything that is not a real, check-digit-valid GTIN, and a hit that fails is
        // silently absent from the result list rather than reaching the screen with a barcode this
        // app cannot safely act on.
        val barcode = BarcodeValidator.normalize(code.orEmpty()) ?: return null
        val displayName = listOfNotNull(productNameNl, productName)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return null

        // A hit whose basis was never established shows no number at all. The card still carries the
        // name, brand, package text and photo — everything the user needs to recognise their
        // package — and selecting it runs a normal barcode lookup, which routes the basis question
        // to the user through the same safe path. Printing a figure here would mean printing it
        // under an assumed unit, which is the whole defect this pass removes.
        val resolution = PackageBasisResolver.resolve(productQuantityUnit, quantity)
        val basis = (resolution as? PackageBasisResolver.Resolution.Resolved)?.basis

        return ProductSearchHit(
            barcode = barcode,
            name = displayName,
            brand = brands?.takeIf { it.isNotBlank() }?.substringBefore(',')?.trim(),
            packageQuantity = quantity?.takeIf { it.isNotBlank() }?.trim(),
            // Still validated: an out-of-range figure is shown as "no value" rather than as a
            // number, so a card can never display something the calculator would refuse (§13).
            carbsPer100 = basis?.let {
                NutritionValueValidator.validateCarbsPer100(
                    raw = nutriments?.carbohydrates100g,
                    basis = it,
                )
            },
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

        // "Is there a number at all?" is answered before "what is it measured per?", and the order
        // is deliberate: asking someone whether a value is per 100 g or per 100 ml, when the record
        // holds no value, sends them looking for a distinction that changes nothing. The permissive
        // ceiling is used here on purpose — this pass rejects only what no basis could rescue
        // (missing, negative, non-finite, or beyond even the millilitre bound), and the real
        // basis-specific ceiling is applied below once the basis is known.
        val rawCarbs = remote.nutriments?.carbohydrates100g
        if (NutritionValueValidator.validateCarbsPer100(rawCarbs, NutritionBasis.PER_100_ML) == null) {
            return ProductFetchResult.Unusable(barcode, UnusableReason.NO_CARB_VALUE)
        }

        // Grams or millilitres, established or admitted absent (§17, release pass §3). The old code
        // read `PackageQuantityParser.inferBasis`, which answered PER_100_G for any quantity it could
        // not parse — so a drink whose `quantity` was "1,5 liter" produced a product whose portion
        // field asked for grams, indistinguishable from one where the app had actually read a weight.
        val resolution = PackageBasisResolver.resolve(remote.productQuantityUnit, remote.quantity)
        val basis = when (resolution) {
            is PackageBasisResolver.Resolution.Resolved -> resolution.basis
            PackageBasisResolver.Resolution.Unresolved ->
                return ProductFetchResult.Unusable(barcode, UnusableReason.UNKNOWN_BASIS)
        }
        val quantity = resolution.quantity

        val carbs = NutritionValueValidator.validateCarbsPer100(rawCarbs, basis)
            ?: return ProductFetchResult.Unusable(barcode, UnusableReason.NO_CARB_VALUE)

        val servingSize = portionUnitCandidate(remote, basis)

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
     * Turns `serving_size` plus `carbohydrates_serving` into a countable unit, or into nothing
     * (spec §9, §16).
     *
     * Precedence, in order:
     *
     * - **A/C** a printed weight wins whenever one is present, even if per-serving carbs are also
     *   available. A weight is the stronger relationship: it survives a recipe reformulation, and it
     *   feeds the app's single existing calculation path.
     * - **B** no weight, but per-serving carbs → a direct-carb unit. This is the case that used to
     *   send the user to fetch a kitchen scale.
     * - **D** neither → no candidate. The app may still know the product is sold in slices, but it
     *   does not invent a relationship it was not given; the UI asks the user once instead.
     *
     * A parsed serving size is only trustworthy if its basis matches the product's own — a countable
     * unit measured in ml has no meaning for a product whose carbs are per 100 g, and the two never
     * converting into each other (§17) rules out silently coercing one to the other here too. That
     * check applies to the weight path only: a direct-carb figure carries no basis to disagree.
     */
    private fun portionUnitCandidate(remote: OffProduct, basis: NutritionBasis): PortionUnitCandidate? {
        val descriptor = ServingSizeParser.parseDescriptor(remote.servingSize) ?: return null

        descriptor.amountPerUnit?.let { perUnit ->
            if (perUnit.basis != basis) return null
            return PortionUnitCandidate(
                kind = descriptor.kind,
                conversion = PortionConversion.WeightBased(perUnit.amount, perUnit.basis),
                rawServingText = remote.servingSize.orEmpty(),
            )
        }

        val carbsPerServing = NutritionValueValidator.validateCarbsPerServing(
            remote.nutriments?.carbohydratesServing,
        ) ?: return null

        val carbsPerUnit = carbsPerServing
            .divide(descriptor.count, CARBS_PER_UNIT_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()

        return PortionUnitCandidate(
            kind = descriptor.kind,
            conversion = PortionConversion.DirectCarbs(carbsPerUnit),
            rawServingText = remote.servingSize.orEmpty(),
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

        /** RFC 9110 §10.2.3 — how long the server wants the client to wait. */
        const val HEADER_RETRY_AFTER = "Retry-After"

        /** Matches the scale [app.justthecarbs.domain.ServingDescriptor] uses for its own division. */
        const val CARBS_PER_UNIT_SCALE = 4
    }
}
