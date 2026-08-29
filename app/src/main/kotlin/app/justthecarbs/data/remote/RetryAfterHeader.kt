package app.justthecarbs.data.remote

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.ZonedDateTime

/**
 * Reads the `Retry-After` header a 429 may carry (RFC 9110 §10.2.3).
 *
 * Two wire forms are permitted and real servers send both: `delta-seconds` ("120") and an
 * HTTP-date ("Fri, 28 Aug 2026 12:02:00 GMT").
 *
 * **Every failure mode returns null**, never zero. Null means "the server did not usefully say",
 * which routes to the governor's conservative fallback backoff; zero would mean "retry now", which
 * is the one answer a refused client must never invent for itself. That includes a negative delta,
 * a past date, unparseable text, and a value too large to hold in milliseconds — the last of which
 * would otherwise overflow to a negative number and read as "retry immediately".
 */
object RetryAfterHeader {

    /** Milliseconds to wait, or null when the header is absent, unusable or already expired. */
    fun parseMs(header: String?, nowMs: Long): Long? {
        val value = header?.trim().orEmpty()
        if (value.isEmpty()) return null

        value.toLongOrNull()?.let { seconds ->
            if (seconds < 0) return null
            // Guard the multiply rather than the product: a wrapped result is indistinguishable
            // from a legitimate small one, so it has to be refused before it happens.
            if (seconds > Long.MAX_VALUE / 1_000) return null
            return seconds * 1_000
        }

        return try {
            val until = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
            (until.toInstant().toEpochMilli() - nowMs).takeIf { it > 0 }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
