package app.carbscan.data.remote

import app.carbscan.domain.LookupError
import app.carbscan.domain.NutritionValueValidator
import app.carbscan.domain.PackageQuantityParser
import app.carbscan.domain.PortionUnitCandidate
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.ProductDataSource
import app.carbscan.domain.ProductFetchResult
import app.carbscan.domain.ServingSizeParser
import app.carbscan.domain.VerificationStatus
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Open Food Facts as a [ProductDataSource] (§11, §12).
 *
 * Nothing above this class knows OFF exists. Adding GS1 or a retailer feed later means writing
 * another implementation of the same interface.
 *
 * Every remote value passes through [NutritionValueValidator] before it can become a [Product]:
 * this is the boundary where untrusted data stops being trusted (§13).
 */
class OpenFoodFactsDataSource(private val api: OpenFoodFactsApi) : ProductDataSource {

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
            ),
            portionUnitCandidate = servingSize,
        )
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
