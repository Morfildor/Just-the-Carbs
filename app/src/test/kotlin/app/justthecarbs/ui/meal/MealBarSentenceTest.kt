package app.justthecarbs.ui.meal

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The meal bar's sentence with its total set apart (2026-09-25 review). */
class MealBarSentenceTest {
    private val bold = SpanStyle(fontWeight = FontWeight.Bold)

    @Test
    fun `the figure replaces the marker and carries the style`() {
        val sentence = sentenceWithFigure("Meal · 3 items ·  g carbs", "", "45.2", bold)

        assertEquals("Meal · 3 items · 45.2 g carbs", sentence.text)
        val styled = sentence.spanStyles.map { sentence.text.substring(it.start, it.end) to it.item }
        assertEquals(listOf("45.2" to bold), styled)
    }

    @Test
    fun `a template without the marker is shown as it is rather than failing`() {
        val sentence = sentenceWithFigure("Meal · 3 items", "", "45.2", bold)

        assertEquals("Meal · 3 items", sentence.text)
        assertTrue(sentence.spanStyles.isEmpty())
    }
}
