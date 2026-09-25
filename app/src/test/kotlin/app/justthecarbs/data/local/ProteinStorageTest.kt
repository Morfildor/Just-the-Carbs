package app.justthecarbs.data.local

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/**
 * Protein is stored exactly like the carbohydrate figure (decimal TEXT) with its source beside it,
 * and never enters the meal (design spec 2026-09-24, sections 9 and 11).
 */
class ProteinStorageTest {

    private fun product(protein: String?) = Product(
        barcode = "8000500310427",
        name = "Nutella",
        carbsPer100 = BigDecimal("57.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        proteinPer100 = protein?.let(::BigDecimal),
        proteinOrigin = protein?.let { ProductDataOrigin.OPEN_FOOD_FACTS },
    )

    @Test
    fun `protein round-trips through the entity exactly`() {
        val entity = product("6.30").toEntity()

        assertEquals("6.30", entity.proteinPer100)
        assertEquals("OPEN_FOOD_FACTS", entity.proteinOrigin)
        val back = entity.toDomain()
        assertEquals(BigDecimal("6.30"), back.proteinPer100)
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, back.proteinOrigin)
    }

    @Test
    fun `a row without protein reads back without protein`() {
        val back = product(null).toEntity().toDomain()

        assertNull(back.proteinPer100)
        assertNull(back.proteinOrigin)
    }

    /** A damaged pair costs the protein, never the product. */
    @Test
    fun `an unreadable stored pair leaves the product without protein`() {
        val base = product("6.3").toEntity()

        assertNull(base.copy(proteinOrigin = null).toDomain().proteinPer100)
        assertNull(base.copy(proteinOrigin = "SOMEWHERE").toDomain().proteinPer100)
        assertNull(base.copy(proteinPer100 = "six").toDomain().proteinOrigin)
        assertEquals(0, BigDecimal("57.5").compareTo(base.copy(proteinPer100 = "six").toDomain().carbsPer100))
    }

    private val schema9 = File("schemas/app.justthecarbs.data.local.JustTheCarbsDatabase/9.json").readText()

    @Test
    fun `version 9 stores protein as nullable text on products`() {
        val products = schema9.substringAfter("\"tableName\": \"products\"").substringBefore("\"tableName\":")

        assertTrue(products.contains("`proteinPer100` TEXT,"))
        assertTrue(products.contains("`proteinOrigin` TEXT,"))
    }

    /** The meal is a carb scratchpad: no protein column, so no protein total can exist. */
    @Test
    fun `version 9 gives the meal no protein column`() {
        val meal = schema9.substringAfter("\"tableName\": \"current_meal_items\"").substringBefore("\"tableName\":")

        assertTrue("the meal table must be in the schema", meal.contains("createSql"))
        assertFalse(meal.contains("protein", ignoreCase = true))
    }
}
