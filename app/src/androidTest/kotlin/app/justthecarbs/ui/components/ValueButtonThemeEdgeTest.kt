package app.justthecarbs.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The value buttons (Usual, pack shortcuts) take their Dark edge from the app's theme, not the
 * phone's.
 *
 * In Dark the button's fill is 1.07:1 against the page, so the 1dp `outline` border is the only
 * thing that makes it read as a button. It used to be gated on `isSystemInDarkTheme()`, so a user
 * who chose Dark in Settings on a light phone got the fill alone and no edge -- seen on the
 * calculator's Usual and pack rows (2026-09-23).
 */
class ValueButtonThemeEdgeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anAppChosenDarkThemeDrawsTheEdgeEvenOnALightPhone() {
        // The precondition that makes this test discriminate: on a light phone the old
        // system-based check drew no edge. The emulator and CI both run with the day UI mode.
        val nightMode = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        assertFalse(
            "this test needs a light system theme to tell the two checks apart",
            nightMode == Configuration.UI_MODE_NIGHT_YES,
        )

        compose.setContent {
            JustTheCarbsTheme(themeChoice = ThemeChoice.DARK) {
                // Centred, so the button is clear of the status-bar band the theme paints over the
                // top of every window.
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    JtcValueButton(
                        text = "65 g",
                        onClick = {},
                        modifier = Modifier.width(120.dp).testTag(TAG),
                    )
                }
            }
        }

        // Copied to a software bitmap, since the capture may be hardware-backed.
        val pixels = compose.onNodeWithTag(TAG).captureToImage().asAndroidBitmap()
            .copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        val middle = pixels.height / 2
        fun luminanceAt(x: Int) = Color(pixels.getPixel(x, middle)).luminance()
        // The brightest of the first three columns: the 1dp stroke may be antialiased across two.
        val edge = (0..2).maxOf { x -> luminanceAt(x) }
        val fill = luminanceAt(pixels.width / 4)
        assertTrue("the dark page never reached the screen (fill luminance $fill)", fill < 0.5f)
        assertTrue(
            "the button's left edge (luminance $edge) must stand clear of its own fill ($fill)",
            edge > fill * 3f,
        )
    }

    private companion object {
        const val TAG = "value_button_under_test"
    }
}
