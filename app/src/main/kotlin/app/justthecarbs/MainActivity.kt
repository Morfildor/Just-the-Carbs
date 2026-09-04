package app.justthecarbs

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.justthecarbs.ui.JustTheCarbsNavHost
import app.justthecarbs.ui.StartupState
import app.justthecarbs.ui.asStartupState
import app.justthecarbs.ui.theme.JustTheCarbsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Held on screen (see setKeepOnScreenCondition below) until the first real DataStore value
        // arrives — a returning user's start destination must never be decided from a synthetic
        // default (§StartupState).
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var startupState: StartupState = StartupState.Loading
        splashScreen.setKeepOnScreenCondition { startupState is StartupState.Loading }
        // Explicitly dark on both bars. Left to resolve itself, `enableEdgeToEdge()` picks a
        // light or dark scrim from the *device* configuration, which on a light-themed phone
        // produced a pale scrim under bands this app now paints black.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )

        val container = (application as JustTheCarbsApplication).container

        setContent {
            // `asStartupState()` applies a `Flow.map`, which lint's FlowOperatorInvokedInComposition
            // rule correctly refuses to see called directly in the composable body — that would
            // build a new mapped Flow on every recomposition instead of once. `remember` makes it
            // one Flow for the composition's lifetime, matching `container` (stable for the life of
            // the Activity) rather than nothing, so it is not silently rebuilt on an unrelated key.
            val startupFlow = remember(container) { container.settingsRepository.settings.asStartupState() }
            val state by startupFlow.collectAsStateWithLifecycle(initialValue = StartupState.Loading)
            // Mirrored into the plain var the splash screen's poll-based condition reads, since
            // `setKeepOnScreenCondition`'s lambda runs outside composition and cannot itself
            // collect a Flow or read Compose state.
            startupState = state

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

            when (val current = state) {
                // The splash screen is still on top for the whole duration of Loading (the poll
                // above), so what renders here is never actually seen — a neutral background is
                // still correct rather than Onboarding, which is the one thing a default-shaped
                // value must never imply.
                StartupState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black))
                is StartupState.Ready -> JustTheCarbsTheme(themeChoice = current.settings.theme) {
                    JustTheCarbsNavHost(container = container, settings = current.settings)
                }
            }
        }
    }
}
