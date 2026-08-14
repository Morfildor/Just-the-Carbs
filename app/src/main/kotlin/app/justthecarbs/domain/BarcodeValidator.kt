package app.justthecarbs.domain

/**
 * GTIN validation (§8, §36 "malformed barcode", "unsupported barcode").
 *
 * Every supported symbology — EAN-13, EAN-8, UPC-A, UPC-E — carries a mod-10 check digit. Verifying
 * it locally costs nothing and stops a misread scan from becoming a pointless network request
 * against a 15/min budget, or worse, from matching some unrelated product.
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
     * Mod-10: from the right, excluding the check digit itself, alternate weights of 3 and 1.
     * Weighting is anchored to the right-hand end so it holds for every GTIN length.
     */
    private fun checkDigit(digits: String): Int {
        val body = digits.dropLast(1)
        val sum = body.reversed()
            .mapIndexed { index, char -> char.digitToInt() * if (index % 2 == 0) 3 else 1 }
            .sum()
        return (10 - sum % 10) % 10
    }
}
