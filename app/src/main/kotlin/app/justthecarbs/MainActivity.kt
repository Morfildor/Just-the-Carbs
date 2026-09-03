package app.justthecarbs

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.ui.JustTheCarbsNavHost
import app.justthecarbs.ui.theme.JustTheCarbsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash screen, with no artificial hold: it disappears the moment the first
        // frame is ready (§6). Nothing is preloaded behind it.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Explicitly dark on both bars. Left to resolve itself, `enableEdgeToEdge()` picks a
        // light or dark scrim from the *device* configuration, which on a light-themed phone
        // produced a pale scrim under bands this app now paints black.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )

        val container = (application as JustTheCarbsApplication).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            val view = LocalView.current

            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                // Light icons, unconditionally.
                //
                // This used to track the app theme (`!dark`), which was correct while the bars took
                // the app's own background — a cream status bar needs dark icons. The bars are now
                // painted opaque black by `SystemBarScrim` in both themes, so the ground behind
                // these icons is a known constant and inverting them with the theme would make them
                // black-on-black in Light mode.
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false

                // Re-enabled. The previous comment argued for `false` because the framework's
                // translucent scrim "reads as a grey band that matches neither theme" over this
                // app's flat backgrounds. That reasoning does not survive the change above: the
                // band is now deliberately black, so the framework enforcing contrast against it
                // agrees with the design instead of fighting it.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = true
                }
            }

            JustTheCarbsTheme(themeChoice = settings.theme) {
                JustTheCarbsNavHost(container = container, settings = settings)
            }
        }
    }
}
