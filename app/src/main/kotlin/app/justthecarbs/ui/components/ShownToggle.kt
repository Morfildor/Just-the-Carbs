package app.justthecarbs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * A persisted switch's shown value: the user's latest tap at once, then whatever the store
 * reports (2026-09-25 review).
 *
 * Reading the stored value alone, a second tap before the write came back saw the old value and
 * wrote the same thing again, so two quick taps left the setting on while the user meant off.
 * Whenever [stored] changes, the shown value follows it.
 */
@Composable
fun rememberShownToggle(stored: Boolean): MutableState<Boolean> = remember(stored) { mutableStateOf(stored) }
