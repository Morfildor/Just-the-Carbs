package app.justthecarbs.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** English speaks "1 gram" only for a bare 1; any shown decimal is plural ("1.0 grams"). */
class SpokenGramsTest {
    @Test
    fun `a bare one is singular`() = assertEquals(1, spokenGramsQuantity("1"))

    @Test
    fun `a one with a shown decimal is plural`() {
        assertEquals(2, spokenGramsQuantity("1.0"))
        assertEquals(2, spokenGramsQuantity("1,0"))
    }

    @Test
    fun `everything else is plural`() {
        for (figure in listOf("0", "0.5", "2", "11", "31.3", "100")) {
            assertEquals(figure, 2, spokenGramsQuantity(figure))
        }
    }
}
