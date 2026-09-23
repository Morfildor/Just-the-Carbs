package app.justthecarbs.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import app.justthecarbs.ui.theme.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * [WrappingRow]'s one promise beyond wrapping: asked how tall it will be at a width, it answers the
 * height it then measures to.
 *
 * The calculator reserves its product header from that answer before the portion controls take
 * their height (CalculatorFrame and ProductIdentityRow), so an answer short of the measurement is
 * a header drawn over the meal bar above it. `FlowRow` answers as if its items shared one line;
 * swapping it in below fails this test at the narrow widths and large font scales.
 *
 * The content is the calculator's own facts line: the per-100 figure, the real provenance badge,
 * and a Verify action shaped as the screen shapes it.
 *
 * **Instrumented: needs a device or emulator.**
 */
class WrappingRowTest {

    @get:Rule
    val compose = createComposeRule()

    private data class Case(val width: Dp, val fontScale: Float)

    private val cases = listOf(120.dp, 180.dp, 240.dp, 320.dp, 400.dp).flatMap { width ->
        listOf(1.0f, 1.3f, 1.8f, 2.0f).map { Case(width, it) }
    }

    @Test
    fun itsIntrinsicHeightIsTheHeightItMeasuresTo() {
        var case by mutableStateOf(cases.first())
        val estimated = mutableMapOf<Case, Int>()
        val measured = mutableMapOf<Case, Int>()

        compose.setContent {
            val current = case
            // The device's own density with a larger font scale, never an invented window.
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, current.fontScale)) {
                JustTheCarbsTheme {
                    Layout(content = { Facts() }) { measurables, _ ->
                        val width = current.width.roundToPx()
                        val row = measurables.single()
                        estimated[current] = row.minIntrinsicHeight(width)
                        val placeable = row.measure(Constraints(maxWidth = width))
                        measured[current] = placeable.height
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                }
            }
        }
        cases.forEach {
            case = it
            compose.waitForIdle()
        }

        // Not vacuous: the narrow cases really wrap onto more lines than the wide ones.
        val largeText = cases.filter { it.fontScale == 2.0f }
        assertTrue(
            "precondition: the facts wrap at the narrow width: $measured",
            measured.getValue(largeText.first()) > measured.getValue(largeText.last()),
        )
        cases.forEach {
            assertEquals("estimated vs measured height at $it", measured.getValue(it), estimated.getValue(it))
        }
    }

    @Composable
    private fun Facts() {
        WrappingRow(horizontalSpacing = Space.s, verticalSpacing = Space.xs) {
            Text(
                text = "57.5 g carbs / 100 g",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
            SourceBadge(product, showHint = false)
            TextButton(
                onClick = {},
                contentPadding = PaddingValues(horizontal = Space.s, vertical = Space.xs),
                modifier = Modifier.heightIn(min = Space.minTouchTarget),
            ) {
                Text(text = "Verify", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    private val product = Product(
        barcode = "8712100849060",
        name = "Nutella",
        carbsPer100 = BigDecimal("57.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )
}
