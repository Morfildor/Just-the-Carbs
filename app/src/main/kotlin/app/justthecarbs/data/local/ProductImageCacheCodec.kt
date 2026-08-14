package app.justthecarbs.data.local

import app.justthecarbs.domain.ProductImage
import app.justthecarbs.domain.ProductImageType
import app.justthecarbs.domain.ProductImageUrlValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stable, failure-tolerant representation of optional product-gallery metadata in Room. */
internal object ProductImageCacheCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(images: List<ProductImage>): String? {
        val safe = images.mapNotNull { image ->
            ProductImageUrlValidator.validate(image.displayUrl)?.let { url ->
                CachedProductImage(image.type.name, image.language, url)
            }
        }.distinctBy { it.url }
        return safe.takeIf { it.isNotEmpty() }?.let(json::encodeToString)
    }

    fun decode(encoded: String?): List<ProductImage> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<CachedProductImage>>(encoded) }
            .getOrDefault(emptyList())
            .mapNotNull { cached ->
                val type = runCatching { ProductImageType.valueOf(cached.type) }.getOrNull()
                    ?: return@mapNotNull null
                val safeUrl = ProductImageUrlValidator.validate(cached.url)
                    ?: return@mapNotNull null
                ProductImage(type, cached.language, safeUrl)
            }
            .distinctBy { it.displayUrl }
    }
}

@Serializable
private data class CachedProductImage(
    val type: String,
    val language: String? = null,
    val url: String,
)
