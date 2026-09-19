package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.scan.MANUAL_BARCODE_FIELD_TAG
import app.justthecarbs.ui.scan.ManualBarcodeSheet
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Review captures of the manual-barcode sheet (1.0.8), for human inspection — see
 * [PortionRailVisualTest] for why these write PNGs rather than assert pixels.
 *
 * Run with `-e barcodeScreenshots true`.
 */
class ManualBarcodeVisualTest {

    @get:Rule
    val compose = createComposeRule()

    private val enabled: Boolean
        get() = InstrumentationRegistry.getArguments().getString("barcodeScreenshots") == "true"

    private fun capture(name: String, dark: Boolean, fontScale: Float, code: String?) {
        if (!enabled) return
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                JustTheCarbsTheme(themeChoice = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
                    ManualBarcodeSheet(onConfirm = {}, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
        if (code != null) {
            compose.onNodeWithTag(MANUAL_BARCODE_FIELD_TAG).performTextReplacement(code)
            compose.waitForIdle()
        }
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "manual-barcode",
        ).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun emptyLight() = capture("empty-light", dark = false, fontScale = 1f, code = null)

    @Test
    fun emptyDark() = capture("empty-dark", dark = true, fontScale = 1f, code = null)

    /** A valid code: Continue is enabled and no error shows. */
    @Test
    fun validLight() = capture("valid-light", dark = false, fontScale = 1f, code = "5449000000996")

    /** A broken check digit: the inline error, in the user's words. */
    @Test
    fun invalidLight() = capture("invalid-light", dark = false, fontScale = 1f, code = "5449000000997")

    @Test
    fun invalidDark() = capture("invalid-dark", dark = true, fontScale = 1f, code = "5449000000997")

    // NO large-font capture here, and that is a limitation of the harness rather than a gap left
    // unchecked. `ModalBottomSheet` renders in its own window and subcomposition, *outside* the
    // `CompositionLocalProvider` this test wraps its content in — so a `LocalDensity` override
    // never reaches it and a "2x" capture comes back byte-identical to the 1x one. Measured, not
    // assumed: the two PNGs matched exactly.
    //
    // Large-font behaviour for this sheet is covered instead by `ManualBarcodeSheetTest`, which
    // drives the real controls and asserts on the semantics tree rather than on pixels.
}
