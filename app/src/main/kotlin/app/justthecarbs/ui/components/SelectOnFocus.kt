package app.justthecarbs.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Selects a pre-filled value when its field gains focus, so the next digit replaces it.
 *
 * A tap both focuses the field and places a caret, and the caret arrives after the focus: selecting
 * in the focus callback alone was undone by the same tap, so tapping the digits of a remembered 65
 * and typing 80 read 8065 (2026-09-25 review). The first caret-only change after focus is that
 * tap's, and is ignored; a second tap places the caret as usual.
 */
internal class SelectOnFocus {
    private var holding = false

    fun focusGained(field: TextFieldValue): TextFieldValue {
        holding = field.text.isNotEmpty()
        return field.copy(selection = TextRange(0, field.text.length))
    }

    fun focusLost() {
        holding = false
    }

    /** The value to keep when the field reports [next] while showing [current]. */
    fun edited(current: TextFieldValue, next: TextFieldValue): TextFieldValue {
        val tapCaret = holding && next.text == current.text
        holding = false
        return if (tapCaret) current else next
    }
}
