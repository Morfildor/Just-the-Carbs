package app.justthecarbs.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.ui.home.HOME_BODY_TAG
import app.justthecarbs.ui.home.HOME_MANUAL_TAG
import app.justthecarbs.ui.home.HOME_PROTEIN_TAG
import app.justthecarbs.ui.home.HOME_SCAN_BARCODE_TAG
import app.justthecarbs.ui.home.HomeScreen
import app.justthecarbs.ui.home.RecentEntry
import app.justthecarbs.ui.settings.SETTINGS_PROTEIN_TAG
import app.justthecarbs.ui.settings.SettingsScreen
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * The two controls for the one protein setting (design spec 2026-09-24, sections 2 and 6): Home's
 * `Show protein` chip and the Settings row. Both are switches named `Show protein`; the platform
 * speaks the state, so the tests assert node properties, never a spoken sentence.
 */
class ProteinControlsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val proteinProduct = Product(
        barcode = "8000500310427",
        name = "Nutella",
        carbsPer100 = BigDecimal("57.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        lastPortion = BigDecimal("65"),
        proteinPer100 = BigDecimal("6.3"),
        proteinOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
    )

    /** Home with the setting held here, so a chip tap round-trips exactly as the NavHost's does. */
    private fun showHome(
        initiallyOn: Boolean = false,
        recents: List<RecentEntry> = emptyList(),
        fontScale: Float? = null,
        writes: MutableList<Boolean> = mutableListOf(),
    ) {
        compose.setContent {
            var settings by remember { mutableStateOf(AppSettings(proteinEnabled = initiallyOn)) }
            val content = @androidx.compose.runtime.Composable {
                JustTheCarbsTheme {
                    HomeScreen(
                        recents = recents,
                        settings = settings,
                        onScan = {},
                        onManualEntry = {},
                        onOpenProduct = {},
                        onToggleFavorite = {},
                        onOpenSettings = {},
                        onProteinChanged = {
                            writes += it
                            settings = settings.copy(proteinEnabled = it)
                        },
                    )
                }
            }
            if (fontScale != null) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    content = content,
                )
            } else {
                content()
            }
        }
    }

    private fun chip() = compose.onNodeWithTag(HOME_PROTEIN_TAG)

    private fun scrollToChip() {
        compose.onNodeWithTag(HOME_BODY_TAG).performScrollToNode(androidx.compose.ui.test.hasTestTag(HOME_PROTEIN_TAG))
    }

    @Test
    fun theChipIsOneSwitchNamedShowProteinAndOffByDefault() {
        showHome()
        scrollToChip()

        chip()
            .assertIsDisplayed()
            .assert(isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(hasText(string(R.string.protein_toggle)))
            .assertIsOff()
    }

    @Test
    fun tappingTheChipWritesTheSettingAndReflectsIt() {
        val writes = mutableListOf<Boolean>()
        showHome(writes = writes)
        scrollToChip()

        chip().performClick()
        compose.waitForIdle()
        chip().assertIsOn()

        chip().performClick()
        compose.waitForIdle()
        chip().assertIsOff()

        assertEquals(listOf(true, false), writes)
    }

    @Test
    fun theBarcodeTileSaysWhatItWillReadWhileProteinIsOn() {
        showHome(initiallyOn = true)

        val title = string(R.string.home_scan_button)
        val subtitle = string(R.string.home_action_barcode_subtitle_protein)
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG)
            .assert(hasContentDescription(string(R.string.home_action_description).format(title, subtitle)))
    }

    @Test
    fun theBarcodeTileKeepsItsCopyWhileProteinIsOff() {
        showHome(initiallyOn = false)

        compose.onAllNodesWithText(string(R.string.home_action_barcode_subtitle_protein)).assertCountEquals(0)
    }

    @Test
    fun theChipMeetsTheTouchTargetWhenOff() {
        showHome(initiallyOn = false)
        assertChipIsAtLeastATouchTarget()
    }

    @Test
    fun theChipMeetsTheTouchTargetWhenOn() {
        showHome(initiallyOn = true)
        assertChipIsAtLeastATouchTarget()
    }

    @Test
    fun theChipMeetsTheTouchTargetWhenOnAtLargeText() {
        showHome(initiallyOn = true, fontScale = 1.8f)
        assertChipIsAtLeastATouchTarget()
        // The footer wraps rather than squeezing either control: Enter manually keeps its target too.
        compose.onNodeWithTag(HOME_BODY_TAG).performScrollToNode(androidx.compose.ui.test.hasTestTag(HOME_MANUAL_TAG))
        val manual = compose.onNodeWithTag(HOME_MANUAL_TAG).fetchSemanticsNode()
        val minPx = 48 * manual.layoutInfo.density.density
        assertTrue("Enter manually is ${manual.size}", manual.size.height >= minPx - 1f)
    }

    private fun assertChipIsAtLeastATouchTarget() {
        scrollToChip()
        val node = chip().fetchSemanticsNode()
        val minPx = 48 * node.layoutInfo.density.density
        assertTrue(
            "chip is ${node.size.width}x${node.size.height}px, below a 48dp target",
            node.size.height >= minPx - 1f && node.size.width >= minPx - 1f,
        )
    }

    /** Home stays carbs-only: no protein figure appears on a recent card, even with protein on. */
    @Test
    fun homeShowsNoProteinFigureEvenWithProteinOn() {
        showHome(initiallyOn = true, recents = listOf(RecentEntry(proteinProduct, null)))

        compose.onAllNodesWithText(string(R.string.product_protein_label)).assertCountEquals(0)
        compose.onAllNodesWithText("grams of protein", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("4.1 g").assertCountEquals(0)
    }

    // ---- Settings ------------------------------------------------------------------------------

    private fun showSettings(initiallyOn: Boolean, writes: MutableList<Boolean>) {
        compose.setContent {
            var settings by remember { mutableStateOf(AppSettings(proteinEnabled = initiallyOn)) }
            JustTheCarbsTheme {
                SettingsScreen(
                    settings = settings,
                    onThemeChanged = {},
                    onResultStyleChanged = {},
                    onHapticsChanged = {},
                    onProteinChanged = {
                        writes += it
                        settings = settings.copy(proteinEnabled = it)
                    },
                    onClearRecents = {},
                    onClearProducts = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun theSettingsRowIsOneSwitchThatReflectsTheSetting() {
        val writes = mutableListOf<Boolean>()
        showSettings(initiallyOn = true, writes = writes)

        val row = compose.onNodeWithTag(SETTINGS_PROTEIN_TAG)
        row.performScrollTo()
        row.assert(isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(hasText(string(R.string.protein_toggle)))
            .assert(hasText(string(R.string.settings_protein_body)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))

        row.performClick()
        compose.waitForIdle()
        row.assertIsOff()
        assertEquals(listOf(false), writes)
    }
}
