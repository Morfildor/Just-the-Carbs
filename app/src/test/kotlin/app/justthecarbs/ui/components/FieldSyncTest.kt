package app.justthecarbs.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldSyncTest {
    private fun field(text: String) = TextFieldValue(text, TextRange(text.length))

    @Test
    fun `an outside change while focused is selected so the next digit replaces it`() {
        val sync = FieldSync()
        val result = sync.reconcile(field("65"), "400", focused = true)

        assertEquals("400", result.text)
        assertEquals(TextRange(0, 3), result.selection)
    }

    @Test
    fun `an outside change while not focused leaves the caret at the end`() {
        val result = FieldSync().reconcile(field("65"), "400", focused = false)

        assertEquals("400", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun `the echo of an earlier keystroke arriving late changes nothing`() {
        val sync = FieldSync()
        sync.typed("6")
        sync.typed("65")
        val typing = field("65")

        val result = sync.reconcile(typing, "6", focused = true)

        assertEquals(typing, result)
    }

    @Test
    fun `once caught up, a value equal to an earlier keystroke is an outside change`() {
        val sync = FieldSync()
        sync.typed("400")
        sync.typed("4000")
        sync.reconcile(field("4000"), "4000", focused = true)

        // Full pack, 400, tapped after the field had caught up.
        val result = sync.reconcile(field("4000"), "400", focused = true)

        assertEquals("400", result.text)
        assertEquals(TextRange(0, 3), result.selection)
    }

    @Test
    fun `the caller's own echo leaves the field and its caret alone`() {
        val sync = FieldSync()
        sync.typed("65")
        val typing = TextFieldValue("65", TextRange(1))

        assertEquals(typing, sync.reconcile(typing, "65", focused = true))
    }
}
