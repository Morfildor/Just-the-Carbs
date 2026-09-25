package app.justthecarbs.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectOnFocusTest {
    private fun field(text: String, caret: Int = text.length) = TextFieldValue(text, TextRange(caret))

    @Test
    fun `gaining focus selects the whole value`() {
        val selected = SelectOnFocus().focusGained(field("65"))

        assertEquals(TextRange(0, 2), selected.selection)
    }

    /** The tap that focused the field then places its caret; the selection must survive it. */
    @Test
    fun `the caret of the tap that gave focus does not undo the selection`() {
        val select = SelectOnFocus()
        val selected = select.focusGained(field("65"))

        val result = select.edited(selected, field("65", caret = 0))

        assertEquals(selected, result)
    }

    @Test
    fun `a second tap places the caret where the user put it`() {
        val select = SelectOnFocus()
        val selected = select.focusGained(field("65"))
        select.edited(selected, field("65", caret = 0))

        val result = select.edited(selected, field("65", caret = 1))

        assertEquals(TextRange(1), result.selection)
    }

    @Test
    fun `typing straight after focus replaces the value`() {
        val select = SelectOnFocus()
        val selected = select.focusGained(field("65"))

        assertEquals(field("8"), select.edited(selected, field("8")))
    }

    @Test
    fun `an empty field has nothing to protect`() {
        val select = SelectOnFocus()
        val selected = select.focusGained(field(""))

        assertEquals(TextRange(0), select.edited(selected, field("", caret = 0)).selection)
    }

    @Test
    fun `losing focus ends the protection`() {
        val select = SelectOnFocus()
        val selected = select.focusGained(field("65"))
        select.focusLost()

        assertEquals(TextRange(1), select.edited(selected, field("65", caret = 1)).selection)
    }
}
