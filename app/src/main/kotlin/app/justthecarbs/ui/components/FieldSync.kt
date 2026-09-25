package app.justthecarbs.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Reconciles a text field that owns its selection with the text its caller holds.
 *
 * The caller's text reaches the screen through a flow, so it can arrive a frame behind the field.
 * Text equal to one of the field's own keystrokes still on its way back is that echo and changes
 * nothing. Any other text came from outside, typically a shortcut tapped while typing (buttons
 * consume the tap, so the field keeps focus); it is selected while the field is focused, so the
 * next digit replaces it rather than appending (65, Full pack, 5 read 4005; 2026-09-25 review).
 */
internal class FieldSync {
    private val inFlight = ArrayList<String>()

    /** Record text the field itself produced and handed to the caller. */
    fun typed(text: String) {
        inFlight += text
        if (inFlight.size > MAX_IN_FLIGHT) inFlight.removeAt(0)
    }

    /** The field's value once the caller's [value] is taken into account. */
    fun reconcile(field: TextFieldValue, value: String, focused: Boolean): TextFieldValue {
        if (field.text == value) {
            inFlight.clear()
            return field
        }
        if (value in inFlight) return field
        inFlight.clear()
        return field.copy(
            text = value,
            selection = if (focused) TextRange(0, value.length) else TextRange(value.length),
        )
    }

    private companion object {
        const val MAX_IN_FLIGHT = 16
    }
}
