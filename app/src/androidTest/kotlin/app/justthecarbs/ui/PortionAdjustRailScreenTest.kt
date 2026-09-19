package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.justthecarbs.domain.PortionAdjustment
import app.justthecarbs.ui.components.PORTION_RAIL_DOUBLE_TAG
import app.justthecarbs.ui.components.PORTION_RAIL_HALVE_TAG
import app.justthecarbs.ui.components.PORTION_RAIL_MINUS_TAG
import app.justthecarbs.ui.components.PORTION_RAIL_PLUS_TAG
import app.justthecarbs.ui.components.PORTION_RAIL_TAG
import app.justthecarbs.ui.components.PortionAdjustRail
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * The quick-adjust rail as a rendered control (1.0.8).
 *
 * The arithmetic is covered in the JVM suite; what can only be checked here is that the four
 * segments are reachable, correctly sized, correctly labelled for TalkBack, and that the rail stays
 * a rail at a large font scale rather than growing without bound — the `fillMaxHeight`/
 * `IntrinsicSize.Min` hazard this app has met before, which no assertion about behaviour would
 * catch because nothing in the suite looks at how tall a control is.
 */
class PortionAdjustRailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showRail(
        step: BigDecimal = BigDecimal(10),
        fontScale: Float = 1f,
        width: Int = 400,
        onAdjust: (PortionAdjustment.Operation) -> Unit = {},
    ) {
        compose.setContent {
            JustTheCarbsTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    Box(Modifier.width(width.dp)) {
                        PortionAdjustRail(
                            step = step,
                            onAdjust = onAdjust,
                            hapticsEnabled = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun allFourAcceleratorsAreShownAndTappable() {
        val applied = mutableListOf<PortionAdjustment.Operation>()
        showRail(onAdjust = { applied += it })

        listOf(
            PORTION_RAIL_HALVE_TAG,
            PORTION_RAIL_DOUBLE_TAG,
            PORTION_RAIL_MINUS_TAG,
            PORTION_RAIL_PLUS_TAG,
        ).forEach { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
        }

        assertEquals(4, applied.size)
        assertEquals(PortionAdjustment.Operation.Halve, applied[0])
        assertEquals(PortionAdjustment.Operation.Double, applied[1])
        assertEquals(PortionAdjustment.Operation.Step(BigDecimal(-10)), applied[2])
        assertEquals(PortionAdjustment.Operation.Step(BigDecimal(10)), applied[3])
    }

    @Test
    fun everySegmentMeetsTheTouchTargetFloor() {
        // 48dp each way. These are tapped one-handed, often repeatedly and often in a shop, which
        // is exactly where an undersized target costs the most.
        showRail()
        listOf(
            PORTION_RAIL_HALVE_TAG,
            PORTION_RAIL_DOUBLE_TAG,
            PORTION_RAIL_MINUS_TAG,
            PORTION_RAIL_PLUS_TAG,
        ).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun theStepSegmentsAreSpokenAsAmountsNotGlyphs() {
        // TalkBack reads "−" as a detached mathematical operator and "½" as "one half" attached to
        // nothing, so each segment names what it does to the amount instead.
        showRail(step = BigDecimal(25))
        compose.onNodeWithContentDescription("Minus 25").assertIsDisplayed()
        compose.onNodeWithContentDescription("Plus 25").assertIsDisplayed()
        compose.onNodeWithContentDescription("Halve the amount").assertIsDisplayed()
        compose.onNodeWithContentDescription("Double the amount").assertIsDisplayed()
    }

    @Test
    fun everySegmentIsAnnouncedAsAButton() {
        // These are Boxes with a `clickable`, not Material buttons, so the role has to be stated.
        // Without it TalkBack reads the label and offers the action but never says what kind of
        // control it is — "Plus 10, double tap to activate", with no indication it is a button.
        showRail()
        listOf(
            PORTION_RAIL_HALVE_TAG,
            PORTION_RAIL_DOUBLE_TAG,
            PORTION_RAIL_MINUS_TAG,
            PORTION_RAIL_PLUS_TAG,
        ).forEach { tag ->
            compose.onNodeWithTag(tag).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            )
        }
    }

    @Test
    fun theRailStaysARailAtALargeFontScale() {
        // The `fillMaxHeight` hazard, asserted rather than argued: the dividers fill the row's
        // height, and without `IntrinsicSize.Min` on the Row they would resolve against an
        // unbounded constraint and drag the rail down the screen. A generous ceiling — this is
        // checking "did it stop being a rail", not pinning a pixel height.
        showRail(fontScale = 2f)
        compose.onNodeWithTag(PORTION_RAIL_TAG).assertIsDisplayed()
        val height = compose.onNodeWithTag(PORTION_RAIL_TAG)
            .fetchSemanticsNode().size.height
        val maxPx = with(compose.density) { 160.dp.toPx() }
        assert(height <= maxPx) { "the rail grew to ${height}px at 2x font scale" }
    }

    @Test
    fun theLabelsSurviveTheLargestStepOnANarrowScreen() {
        // 320dp is the historical Android minimum width, and ±50 is the largest step the package
        // ladder produces. A segment that truncated "−50" to "−5" would be a control that lies
        // about what it does — the failure the previous row was shaped around.
        showRail(step = BigDecimal(50), fontScale = 2f, width = 320)
        compose.onNodeWithContentDescription("Minus 50").assertIsDisplayed()
        compose.onNodeWithContentDescription("Plus 50").assertIsDisplayed()
    }

    @Test
    fun aCountRailStepsByOne() {
        val applied = mutableListOf<PortionAdjustment.Operation>()
        showRail(step = BigDecimal.ONE, onAdjust = { applied += it })

        compose.onNodeWithTag(PORTION_RAIL_PLUS_TAG).performClick()
        compose.onNodeWithTag(PORTION_RAIL_MINUS_TAG).performClick()

        assertEquals(PortionAdjustment.Operation.Step(BigDecimal.ONE), applied[0])
        assertEquals(PortionAdjustment.Operation.Step(BigDecimal.ONE.negate()), applied[1])
        // Spoken without a decimal point: "Plus 1", never "Plus 1.0".
        compose.onNodeWithContentDescription("Plus 1").assertIsDisplayed()
    }
}
