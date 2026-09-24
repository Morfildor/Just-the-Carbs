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

    /** Open Food Facts' image archive on Amazon S3. Search thumbnails only; see [validateArchive]. */
    const val ARCHIVE_HOST = "openfoodfacts-images.s3.eu-west-3.amazonaws.com"

    /** `/data/<code padded to 13 digits, split 3/3/3/rest>/<image number>.400.jpg`, nothing else. */
    private val ARCHIVE_PATH = Regex("""^/data/\d{3}/\d{3}/\d{3}/\d{4,5}/\d{1,9}\.400\.jpg$""")

    fun validate(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host !in APPROVED_HOSTS) return null

        return trimmed
    }

    /**
     * A search thumbnail's address in Open Food Facts' S3 archive: exactly the archive host and a
     * 400 px original under `/data/`, with nothing added (no port, user, query or fragment). Kept
     * apart from [validate] on purpose, so a product record can never point the calculator or
     * Recents at this host.
     */
    fun validateArchive(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != ARCHIVE_HOST) return null
        if (uri.port != -1 || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (!ARCHIVE_PATH.matches(uri.rawPath.orEmpty())) return null

        return trimmed
    }
}
