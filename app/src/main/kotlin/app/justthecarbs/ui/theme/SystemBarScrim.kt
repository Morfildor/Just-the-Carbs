package app.justthecarbs.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Opaque black bands behind the status and navigation bars.
 *
 * ## Why this is drawn rather than configured
 *
 * The owner's requirement is ordinary, non-immersive system bars: solid black, with no app content
 * showing through. The instinctive implementation — `android:statusBarColor` and
 * `android:navigationBarColor` in `themes.xml`, plus dropping `enableEdgeToEdge()` — **does not
 * work at this app's target.**
 *
 * `targetSdk` is 36. From Android 15 (API 35) the platform enforces edge-to-edge and treats both of
 * those attributes as deprecated no-ops. A fix resting on them would be correct on an older
 * emulator image and silently wrong on a current device, which is precisely the class of failure
 * this repo keeps recording ("a green suite and a broken screen"). So the window stays
 * edge-to-edge — which is not optional — and the app paints the bands itself.
 *
 * ## Why black rather than a theme colour
 *
 * Black in both themes, deliberately. The requirement is that the bars look like the system's, and
 * a cream status bar is exactly what made the app look full-screen. Because the ground is now a
 * known constant rather than arbitrary app content, [MainActivity] can pin the bar icons to light
 * unconditionally instead of inverting them with the theme.
 *
 * ## Ordering
 *
 * Drawn *over* the content, as the last child of the theme's root Box. Under it, a screen that
 * paints its own background — every screen here does — would cover the bands.
 */
@Composable
fun SystemBarScrim() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Color.Black),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(Color.Black),
        )
    }
}
