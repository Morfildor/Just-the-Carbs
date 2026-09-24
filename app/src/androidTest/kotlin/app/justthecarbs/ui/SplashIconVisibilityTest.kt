package app.justthecarbs.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The launch splash must actually show the app's mark (2026-09-24 review).
 *
 * The splash rendered a white mark on the cream splash background, effectively invisible:
 * `ic_splash_mark` drew its "coral plate" from `ic_launcher_background`, a colour the launcher
 * redesign (68c85a3) had turned cream, and hard-coded a white mark. Nothing failed, because
 * nothing looked at the pixels.
 *
 * This resolves the icon the way the system does -- the platform `windowSplashScreenAnimatedIcon`
 * attribute of the starting theme, which the splash library maps from its own attribute on
 * API 31+ -- renders it over the splash background, and requires a meaningful share of the icon
 * to stand out from it.
 *
 * The floor is 2:1, deliberately not WCAG's 3:1 for non-text graphics: logos are exempt from that
 * rule, and the brand's own launcher mark (coral on cream) measures 2.85:1. Whether to deepen the
 * brand colour is a design decision, not this test's. What it catches is the defect: the white
 * mark measured about 1.07:1.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class SplashIconVisibilityTest {

    @Test
    fun theSplashIconIsVisibleAgainstTheSplashBackground() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A real Theme, because the splash library's v31 style points the platform attributes at
        // its own via `?attr/`, which only resolves inside a theme that carries the style.
        val theme = context.resources.newTheme().apply {
            applyStyle(R.style.Theme_JustTheCarbs_Starting, true)
        }
        val attrs = theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowSplashScreenBackground, android.R.attr.windowSplashScreenAnimatedIcon),
        )
        val background = attrs.getColor(0, Color.MAGENTA)
        val icon = attrs.getDrawable(1)
        attrs.recycle()
        requireNotNull(icon) { "the starting theme names no splash icon" }

        val size = 288
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(background)
            icon.setBounds(0, 0, size, size)
            icon.draw(this)
        }

        val pixels = IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }
        val visible = pixels.count { contrast(it, background) >= MIN_CONTRAST }
        // The mark covers roughly a tenth of the canvas; 3% leaves room for a smaller mark while
        // still failing an icon that is invisible, which scores zero.
        assertTrue(
            "only $visible of ${pixels.size} splash pixels reach $MIN_CONTRAST:1 against the background " +
                "(max ${pixels.maxOf { contrast(it, background) }})",
            visible >= pixels.size * 0.03,
        )
    }

    private companion object {
        const val MIN_CONTRAST = 2.0
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
