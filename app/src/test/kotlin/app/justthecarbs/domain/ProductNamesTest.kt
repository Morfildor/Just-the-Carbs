package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductNamesTest {

    private val names = mapOf("nl" to "Chocoladehagelslag", "tr" to "Çikolata granülü")

    @Test
    fun `an English app keeps preferring the Dutch name`() {
        assertEquals(listOf("nl"), ProductNames.preferenceFor("en-NL"))
        assertEquals("Chocoladehagelslag", ProductNames.choose("Chocolate sprinkles", names, "en-NL"))
    }

    @Test
    fun `a Dutch app prefers the Dutch name`() {
        assertEquals(listOf("nl"), ProductNames.preferenceFor("nl-BE"))
    }

    @Test
    fun `a device set to Turkish prefers the Turkish name`() {
        assertEquals(listOf("tr"), ProductNames.preferenceFor("tr-TR"))
        assertEquals("Çikolata granülü", ProductNames.choose("Chocolate sprinkles", names, "tr-TR"))
    }

    /**
     * Products sold in Turkey are usually entered with Turkish as their main language, so the main
     * name is Turkish too. A Dutch translation is not a better fallback for a Turkish reader.
     */
    @Test
    fun `a device set to Turkish falls back to the main name, not the Dutch one`() {
        assertEquals(
            "Chocolate sprinkles",
            ProductNames.choose("Chocolate sprinkles", mapOf("nl" to "Chocoladehagelslag"), "tr"),
        )
    }

    @Test
    fun `a language tag is read by its language alone`() {
        assertEquals(listOf("tr"), ProductNames.preferenceFor("TR"))
        assertEquals(listOf("tr"), ProductNames.preferenceFor("tr_TR"))
        assertEquals(listOf("nl"), ProductNames.preferenceFor(""))
    }

    @Test
    fun `a blank localized name is skipped`() {
        assertEquals("Main", ProductNames.choose("Main", mapOf("tr" to "  "), "tr"))
    }

    @Test
    fun `the chosen name is trimmed`() {
        assertEquals("Pınar Süt", ProductNames.choose(null, mapOf("tr" to " Pınar Süt "), "tr"))
    }

    @Test
    fun `no usable name is null`() {
        assertNull(ProductNames.choose(" ", mapOf("nl" to null, "tr" to ""), "tr"))
    }
}
