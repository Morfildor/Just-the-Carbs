package app.justthecarbs.domain

import java.net.URI

/**
 * Guards against a malicious or corrupt remote product record making the app contact an arbitrary
 * third-party host (countable-portions brief §15). Only HTTPS URLs on an approved Open Food Facts
 * image host are accepted; anything else is treated the same as no image at all.
 */
object ProductImageUrlValidator {

    private val APPROVED_HOSTS = setOf(
        "images.openfoodfacts.org",
        "static.openfoodfacts.org",
    )

    fun validate(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host !in APPROVED_HOSTS) return null

        return trimmed
    }
}
