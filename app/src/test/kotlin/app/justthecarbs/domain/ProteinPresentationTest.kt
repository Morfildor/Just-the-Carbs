package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * One case per data row of the design spec's state table (2026-09-24, section 5). The two layout
 * gates (keyboard, starved window) are the screen's and are covered by instrumented tests.
 */
class ProteinPresentationTest {

    private val figure = BigDecimal("4.095")

    private fun off(
        protein: String? = "6.3",
        verified: Boolean = false,
        origin: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
    ) = Product(
        barcode = "8000500310427",
        name = "Nutella",
        carbsPer100 = BigDecimal("57.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = origin,
        verificationStatus = if (verified) VerificationStatus.USER_VERIFIED else VerificationStatus.UNVERIFIED,
        proteinPer100 = protein?.let(::BigDecimal),
        proteinOrigin = protein?.let { ProductDataOrigin.OPEN_FOOD_FACTS },
    )

    private fun present(
        product: Product?,
        enabled: Boolean = true,
        exactProtein: BigDecimal? = figure,
        hasAnswer: Boolean = true,
        directCarbPortion: Boolean = false,
    ) = ProteinPresentation.of(enabled, product, exactProtein, hasAnswer, directCarbPortion)

    @Test
    fun `off by default shows nothing, whatever the data`() {
        assertEquals(ProteinPresentation.Hidden, present(off(), enabled = false))
        assertEquals(ProteinPresentation.Hidden, present(off(protein = null), enabled = false, exactProtein = null))
    }

    @Test
    fun `no product yet shows nothing`() {
        assertEquals(ProteinPresentation.Hidden, present(null))
    }

    @Test
    fun `no answer yet shows nothing, so protein appears with the answer and not before it`() {
        assertEquals(ProteinPresentation.Hidden, present(off(), hasAnswer = false, exactProtein = null))
        assertEquals(
            ProteinPresentation.Hidden,
            present(off(protein = null), hasAnswer = false, exactProtein = null),
        )
    }

    @Test
    fun `an online product with a value shows the figure`() {
        assertEquals(ProteinPresentation.Value(figure), present(off()))
    }

    @Test
    fun `a verified online product with a value shows the figure`() {
        assertEquals(ProteinPresentation.Value(figure), present(off(verified = true)))
    }

    @Test
    fun `an online record without protein says so, never 0 g`() {
        assertEquals(ProteinPresentation.NoOnlineValue, present(off(protein = null), exactProtein = null))
    }

    @Test
    fun `a verified online record without protein says so`() {
        assertEquals(
            ProteinPresentation.NoOnlineValue,
            present(off(protein = null, verified = true), exactProtein = null),
        )
    }

    @Test
    fun `a product the user typed in or read from a label shows nothing`() {
        for (origin in listOf(ProductDataOrigin.MANUAL, ProductDataOrigin.OCR)) {
            val product = off(protein = null, verified = true, origin = origin)
            assertEquals(origin.name, ProteinPresentation.Hidden, present(product, exactProtein = null))
        }
    }

    @Test
    fun `a direct-carb portion shows nothing, because no weight means no protein figure`() {
        assertEquals(ProteinPresentation.Hidden, present(off(), exactProtein = null, directCarbPortion = true))
        assertEquals(
            ProteinPresentation.Hidden,
            present(off(protein = null), exactProtein = null, directCarbPortion = true),
        )
    }

    @Test
    fun `a product with a value but no protein result shows nothing rather than a false no-value`() {
        assertEquals(ProteinPresentation.Hidden, present(off(), exactProtein = null))
    }

    @Test
    fun `a zero figure is a value`() {
        assertEquals(ProteinPresentation.Value(BigDecimal.ZERO), present(off(protein = "0"), exactProtein = BigDecimal.ZERO))
    }
}
