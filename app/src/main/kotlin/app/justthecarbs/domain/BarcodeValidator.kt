package app.justthecarbs.domain

/**
 * The barcode symbologies the scanner is configured to detect (§8). A domain-level mirror of ML
 * Kit's `Barcode.getFormat()` constants — kept separate so `domain/` stays free of an ML Kit import.
 */
enum class BarcodeFormat {
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
}

/**
 * GTIN validation (§8, §36 "malformed barcode", "unsupported barcode").
 *
 * Every supported symbology — EAN-13, EAN-8, UPC-A, UPC-E — carries a mod-10 check digit. Verifying
 * it locally costs nothing and stops a misread scan from becoming a pointless network request
 * against a 15/min budget, or worse, from matching some unrelated product.
 *
 * UPC-E is not just "an 8-digit code": it is a zero-suppressed *compression* of a 12-digit UPC-A,
 * and its raw 8 digits do not carry a valid EAN-8 check digit relationship — they must be expanded
 * before the check digit means anything. [validate] is format-aware for exactly this reason; the
 * length-only [isValid]/[normalize] pair remains for manual entry, where there is no format signal
 * because the user is transcribing printed digits, not letting the scanner report a symbology.
 */
object BarcodeValidator {

    private val SUPPORTED_LENGTHS = setOf(8, 12, 13, 14)

    fun isValid(raw: String): Boolean {
        val digits = raw.trim()
        if (digits.length !in SUPPORTED_LENGTHS) return false
        if (!digits.all { it.isDigit() }) return false
        return checkDigit(digits) == digits.last().digitToInt()
    }

    /**
     * Normalises to the form used as the database key.
     *
     * A 12-digit UPC-A is the same article as the 13-digit GTIN with a leading zero. Storing both
     * forms would let one physical product occupy two rows with two different verified values.
     */
    fun normalize(raw: String): String? {
        val digits = raw.trim()
        if (!isValid(digits)) return null
        return when (digits.length) {
            12 -> "0$digits"
            14 -> digits.removePrefix("0").takeIf { it.length == 13 } ?: digits
            else -> digits
        }
    }

    /**
     * Format-aware validation and normalisation for a scanned barcode.
     *
     * The scanner knows which symbology ML Kit detected, so it can route UPC-E through expansion
     * instead of falling through to plain length-based EAN-8 rules — an 8-digit UPC-E raw value is
     * not an EAN-8 and validating it as one accepts/rejects the wrong codes. Returns the normalised
     * 13-digit form used as the database key, or null if malformed for the given format.
     */
    fun validate(raw: String, format: BarcodeFormat): String? {
        val digits = raw.trim()
        if (!digits.all { it.isDigit() }) return null

        return when (format) {
            BarcodeFormat.EAN_13 -> digits.takeIf { it.length == 13 }?.let(::normalize)
            BarcodeFormat.EAN_8 -> digits.takeIf { it.length == 8 }?.let(::normalize)
            BarcodeFormat.UPC_A -> digits.takeIf { it.length == 12 }?.let(::normalize)
            BarcodeFormat.UPC_E -> expandUpcE(digits)
        }
    }

    /**
     * Expands an 8-digit UPC-E code (number system + 6 data digits + check digit) to its equivalent
     * 13-digit GTIN, per the GS1/UPC zero-suppression table. Returns null for anything malformed:
     * wrong length, a number system other than 0/1 (the only two standard UPC-E allows), or a check
     * digit that does not match the expanded UPC-A body.
     */
    private fun expandUpcE(digits: String): String? {
        if (digits.length != 8) return null
        val numberSystem = digits[0]
        if (numberSystem != '0' && numberSystem != '1') return null

        val d = (1..6).map { digits[it].digitToInt() }
        val suppliedCheck = digits[7].digitToInt()

        // The 11-digit UPC-A body (number system + manufacturer + product, before the check digit).
        val body = when (d[5]) {
            0, 1, 2 -> "$numberSystem${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            3 -> "$numberSystem${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            4 -> "$numberSystem${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "$numberSystem${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }

        if (checkDigitOfBody(body) != suppliedCheck) return null

        return normalize(body + suppliedCheck)
    }

    /**
     * Mod-10: from the right, excluding the check digit itself, alternate weights of 3 and 1.
     * Weighting is anchored to the right-hand end so it holds for every GTIN length.
     */
    private fun checkDigit(digits: String): Int = checkDigitOfBody(digits.dropLast(1))

    private fun checkDigitOfBody(body: String): Int {
        val sum = body.reversed()
            .mapIndexed { index, char -> char.digitToInt() * if (index % 2 == 0) 3 else 1 }
            .sum()
        return (10 - sum % 10) % 10
    }
}
