package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/**
 * Review captures of the portion rail in its real screen, for human inspection (1.0.8).
 *
 * **Not pixel-golden assertions.** These write PNGs the way `TutorialVisualTest` does, because the
 * things worth checking here — does the rail read as subordinate to the amount and the result, does
 * it wrap or clip at a large font, does it hold up in both themes — are judgements a person makes
 * by looking, and an assertion that tried to encode them would either pin nothing or break on every
 * legitimate change.
 *
 * Run with `-e railScreenshots true` and pull the app's external files `portion-rail` directory.
 */
class PortionRailVisualTest {

    @get:Rule
    val compose = createComposeRule()

    private val enabled: Boolean
        get() = InstrumentationRegistry.getArguments().getString("railScreenshots") == "true"

    private fun product(packageAmount: String?) = Product(
        barcode = "5449000000996",
        name = "Wholegrain Sourdough Bread",
        brand = "The Bakery",
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.MANUAL,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        packageAmount = packageAmount?.let(::BigDecimal),
    )

    private fun capture(
        name: String,
        dark: Boolean,
        fontScale: Float,
        packageAmount: String?,
        portion: String,
    ) {
        if (!enabled) return
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                JustTheCarbsTheme(themeChoice = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
                    Box(Modifier.fillMaxSize()) {
                        ProductScreen(
                            state = ProductUiState(
                                loading = false,
                                product = product(packageAmount),
                                portionText = portion,
                                barcode = "5449000000996",
                            ),
                            settings = AppSettings(),
                            onPortionChanged = {},
                            onAdjust = {},
                            onSetPortion = {},
                            onToggleFavorite = {},
                            onBack = {},
                            onVerify = {},
                            onDismissVerify = {},
                            onConfirmVerification = { _, _, _ -> },
                            onResetOnline = {},
                            onScanLabel = {},
                            onEnterManually = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        write(name)
    }

    private fun write(name: String) {
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(null),
            "portion-rail",
        ).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // One @Test per capture: `setContent` may be called only once per ComposeTestRule, so a single
    // test looping over the matrix throws "has already set content" on its second capture.
    //
    // The matrix that matters: both themes at ordinary size, then the two font scales where this
    // app has previously found clipping, then the widest labels the package ladder ever renders.

    @Test
    fun light1x() = capture("light-1x", dark = false, fontScale = 1f, packageAmount = null, portion = "72")

    @Test
    fun dark1x() = capture("dark-1x", dark = true, fontScale = 1f, packageAmount = null, portion = "72")

    @Test
    fun light13x() = capture("light-1.3x", dark = false, fontScale = 1.3f, packageAmount = null, portion = "72")

    @Test
    fun light2x() = capture("light-2x", dark = false, fontScale = 2f, packageAmount = null, portion = "72")

    @Test
    fun dark2x() = capture("dark-2x", dark = true, fontScale = 2f, packageAmount = null, portion = "72")

    /** An 800 g pack puts the step at 50 — the widest labels the rail ever renders. */
    @Test
    fun lightBigStep2x() = capture("light-bigstep-2x", dark = false, fontScale = 2f, packageAmount = "800", portion = "250")

    @Test
    fun lightBigStep1x() = capture("light-bigstep-1x", dark = false, fontScale = 1f, packageAmount = "800", portion = "250")
}
