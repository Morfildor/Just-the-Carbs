package app.justthecarbs.ocr

import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: nutrition terminology
// Invariant: every named sugar that appears as a child row on real packaging is an exclusion term,
// so a row naming one can never be mistaken for the total-carbohydrate row.
class NutritionTerminologyTest {

    @Test
    fun `named sugars are exclusion terms`() {
        val named = listOf("dextrose", "glucose", "fructose", "sucrose", "lactose", "maltose")
        named.forEach { term ->
            assertTrue(
                "$term must be an exclusion term",
                NutritionTerminology.exclusionTerms.any {
                    NutritionTerminology.containsTerm(NutritionTerminology.normalize(term), it)
                },
            )
        }
    }

    @Test
    fun `added sugars phrasings are exclusion terms`() {
        listOf("added sugars", "added sugar", "of which sugars").forEach { phrase ->
            assertTrue(
                "$phrase must be an exclusion term",
                NutritionTerminology.exclusionTerms.any {
                    NutritionTerminology.containsTerm(NutritionTerminology.normalize(phrase), it)
                },
            )
        }
    }

    @Test
    fun `carbohydrate itself is never an exclusion term`() {
        assertTrue(
            NutritionTerminology.exclusionTerms.none {
                NutritionTerminology.normalize(it) == "carbohydrate"
            },
        )
    }
}
