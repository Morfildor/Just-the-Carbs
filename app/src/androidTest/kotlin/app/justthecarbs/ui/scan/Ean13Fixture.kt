package app.justthecarbs.ui.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Draws a real, standards-conformant EAN-13 barcode.
 *
 * ## Why generated rather than committed as a PNG
 *
 * A committed image is opaque: nothing in the repository states which digits it encodes, so a test
 * asserting `"4006381333931"` against it rests on a claim nobody can check without a scanner. A
 * fixture whose digits are an *argument* states its own contract — the test names the number, this
 * function encodes exactly that number, and the assertion is that the app reads back what was
 * drawn. It also makes the multi-barcode cases possible at all: two distinct codes in one image
 * cannot be photographed into existence on a build machine.
 *
 * It is fully deterministic. The encoding below is the published EAN-13 symbology, not an
 * approximation: the same digits always produce the same pixels, so a passing test cannot become a
 * failing one because of a re-render.
 *
 * ## The encoding
 *
 * An EAN-13 is 95 modules: a 3-module guard, six 7-module digits, a 5-module centre guard, six more
 * digits, and a 3-module guard. The first digit is not drawn as bars at all — it is encoded by
 * *which* parity table each of the next six digits uses, which is what [FIRST_DIGIT_PARITY] holds.
 * Getting that wrong produces a barcode a scanner reads as a different number, so it is the one
 * part of this file worth checking against the standard rather than trusting.
 */
internal object Ean13Fixture {

    /** L-code: digits 0-9, odd parity. R-code is its complement; G-code is R reversed. */
    private val L_CODE = arrayOf(
        "0001101", "0011001", "0010011", "0111101", "0100011",
        "0110001", "0101111", "0111011", "0110111", "0001011",
    )

    /**
     * Which of the first six digits use G-code rather than L-code, selected by the leading digit.
     * `true` means G. The leading digit itself is never drawn.
     */
    private val FIRST_DIGIT_PARITY = arrayOf(
        booleanArrayOf(false, false, false, false, false, false), // 0
        booleanArrayOf(false, false, true, false, true, true), // 1
        booleanArrayOf(false, false, true, true, false, true), // 2
        booleanArrayOf(false, false, true, true, true, false), // 3
        booleanArrayOf(false, true, false, false, true, true), // 4
        booleanArrayOf(false, true, true, false, false, true), // 5
        booleanArrayOf(false, true, true, true, false, false), // 6
        booleanArrayOf(false, true, false, true, false, true), // 7
        booleanArrayOf(false, true, false, true, true, false), // 8
        booleanArrayOf(false, true, true, false, true, false), // 9
    )

    private fun gCode(digit: Int): String = L_CODE[digit].reversed().map {
        if (it == '0') '1' else '0'
    }.joinToString("")

    private fun rCode(digit: Int): String = L_CODE[digit].map {
        if (it == '0') '1' else '0'
    }.joinToString("")

    /** The mod-10 check digit of a 12-digit body, so a fixture cannot encode an invalid code. */
    fun checkDigit(body: String): Int {
        require(body.length == 12) { "an EAN-13 body is 12 digits, got ${body.length}" }
        val sum = body.mapIndexed { index, ch ->
            ch.digitToInt() * if (index % 2 == 0) 1 else 3
        }.sum()
        return (10 - sum % 10) % 10
    }

    /** A full 13-digit code from its 12-digit body. */
    fun complete(body: String): String = body + checkDigit(body)

    /** The 95-module pattern for [barcode], as '1' = bar, '0' = space. */
    fun modules(barcode: String): String {
        require(barcode.length == 13) { "EAN-13 is 13 digits, got ${barcode.length}" }
        require(barcode.all { it.isDigit() }) { "EAN-13 is numeric" }
        require(checkDigit(barcode.take(12)) == barcode.last().digitToInt()) {
            "$barcode has an invalid check digit — a fixture must never encode an unreadable code"
        }

        val digits = barcode.map { it.digitToInt() }
        val parity = FIRST_DIGIT_PARITY[digits[0]]

        return buildString {
            append("101") // left guard
            for (i in 1..6) {
                append(if (parity[i - 1]) gCode(digits[i]) else L_CODE[digits[i]])
            }
            append("01010") // centre guard
            for (i in 7..12) append(rCode(digits[i]))
            append("101") // right guard
        }
    }

    /**
     * Renders [barcodes] into one bitmap, stacked vertically with generous white margins.
     *
     * The margins are not decoration: EAN-13 specifies a quiet zone either side, and a scanner is
     * entitled to refuse a symbol that lacks one. [moduleWidth] is generous for the same reason —
     * this fixture exists to prove the pipeline reads a barcode, so it must not also be a test of
     * how small a barcode ML Kit can resolve.
     */
    fun bitmap(
        barcodes: List<String>,
        moduleWidth: Int = 4,
        barHeight: Int = 220,
        margin: Int = 60,
    ): Bitmap {
        val patterns = barcodes.map(::modules)
        val width = patterns.first().length * moduleWidth + margin * 2
        val height = barcodes.size * (barHeight + margin) + margin

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            // No anti-aliasing: a bar edge must land on a pixel boundary, so the decoder sees the
            // module widths that were drawn rather than a grey ramp between them.
            isAntiAlias = false
        }

        patterns.forEachIndexed { index, pattern ->
            val top = (margin + index * (barHeight + margin)).toFloat()
            pattern.forEachIndexed { module, bit ->
                if (bit == '1') {
                    val left = (margin + module * moduleWidth).toFloat()
                    canvas.drawRect(
                        left,
                        top,
                        left + moduleWidth,
                        top + barHeight,
                        paint,
                    )
                }
            }
        }

        return bitmap
    }
}
