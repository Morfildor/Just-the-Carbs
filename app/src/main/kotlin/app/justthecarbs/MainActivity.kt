package app.justthecarbs

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.ui.JustTheCarbsNavHost
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import app.justthecarbs.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash screen, with no artificial hold: it disappears the moment the first
        // frame is ready (§6). Nothing is preloaded behind it.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as JustTheCarbsApplication).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            // The same rule MaterialTheme uses, so the bars can never follow the device while the
            // app follows the user's choice. `enableEdgeToEdge()` above resolves its own light/dark
            // from the device configuration and runs once in onCreate, so on its own it was wrong
            // in both directions: it ignored an explicit Light/Dark selection, and — because the
            // manifest declares `configChanges="uiMode"`, so this Activity is never recreated — it
            // also never re-ran when anything changed. Applying it here instead re-runs on every
            // recomposition that changes the resolved theme, which is what makes switching
            // immediate without an Activity restart or a visible flash.
            val dark = resolveDarkTheme(settings.theme, isSystemInDarkTheme())
            val view = LocalView.current

            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                // "Light bars" means light *background*, hence dark icons. Light app theme
                // therefore wants `true`, dark theme `false` — the inversion that made status-bar
                // icons white-on-cream and effectively invisible in Light mode.
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark

                // API 29+ paints a translucent scrim behind the navigation bar when the app draws
                // edge-to-edge. With three-button navigation over this app's flat backgrounds that
                // scrim reads as a grey band that matches neither theme. Turning it off is safe
                // here only because the icons keep their contrast from the line above, against a
                // background that is a solid, known theme colour rather than arbitrary content.
                // Gesture navigation is unaffected — its handle already follows the appearance flag.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
            }

            JustTheCarbsTheme(themeChoice = settings.theme) {
                JustTheCarbsNavHost(container = container, settings = settings)
            }
        }
    }
}
