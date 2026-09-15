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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.justthecarbs.ui.JustTheCarbsNavHost
import app.justthecarbs.ui.StartupDestination
import app.justthecarbs.ui.StartupRequest
import app.justthecarbs.ui.StartupState
import app.justthecarbs.ui.asStartupState
import app.justthecarbs.ui.theme.JustTheCarbsTheme

class MainActivity : ComponentActivity() {

    /**
     * The launcher shortcut this launch came from, if any, as Compose state so the nav host sees it.
     *
     * Held here rather than read from `intent` inside the composition because the action must be
     * consumed exactly once: a configuration change recreates the activity and re-runs `setContent`,
     * and an Intent whose action is still set would reopen a scanner the user had already closed.
     * [consumeShortcutAction] is what makes the read destructive.
     */
    private var startupRequest by mutableStateOf(StartupRequest.NONE)

    /** Increments per shortcut delivery, so repeating the same shortcut is still a new request. */
    private var shortcutDeliveries = 0L

    /**
     * The latest settings snapshot, mirrored out of the composition.
     *
     * The splash screen's keep-on-screen condition runs outside composition and cannot collect a
     * Flow, and [onNewIntent] needs the real `hasSeenOnboarding` rather than an assumption about it,
     * so both read this one field.
     */
    private var startupState: StartupState = StartupState.Loading

    /**
     * Reads the shortcut action off [intent] and clears it, so it cannot be acted on twice.
     *
     * Clearing the action on the *Activity's* intent (rather than on a copy) is deliberate: that is
     * the object Android hands back on recreation, so blanking it here is what stops a rotation from
     * being read as a second shortcut launch.
     */
    private fun consumeShortcutAction(hasSeenOnboarding: Boolean): StartupRequest {
        val action = intent?.action
        val resolved = StartupDestination.from(action, hasSeenOnboarding)
        if (action == StartupDestination.ACTION_SCAN_BARCODE ||
            action == StartupDestination.ACTION_SCAN_LABEL
        ) {
            intent?.action = null
        }
        if (resolved == StartupDestination.DEFAULT) return StartupRequest.NONE
        return StartupRequest(resolved, ++shortcutDeliveries)
    }

    /**
     * A shortcut tapped while the app is already running.
     *
     * `launchMode` is the default, but the launcher reuses an existing task for these intents, so
     * without this a second shortcut tap would deliver a new Intent that nothing ever read and the
     * app would simply come to the foreground on whatever screen it was last on — which reads as the
     * shortcut not working.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The real flag, never an assumption that a running app is past its gate: a shortcut can
        // arrive while the welcome carousel is still on screen, and that must not jump the gate.
        // Settings not yet loaded reads as "not seen", which resolves to DEFAULT and changes nothing.
        val seen = (startupState as? StartupState.Ready)?.settings?.hasSeenOnboarding == true
        startupRequest = consumeShortcutAction(hasSeenOnboarding = seen)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Held on screen (see setKeepOnScreenCondition below) until the first real DataStore value
        // arrives — a returning user's start destination must never be decided from a synthetic
        // default (§StartupState).
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { startupState is StartupState.Loading }
        // Explicitly dark on both bars. Left to resolve itself, `enableEdgeToEdge()` picks a
        // light or dark scrim from the *device* configuration, which on a light-themed phone
        // produced a pale scrim under bands this app now paints black.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )

        val container = (application as JustTheCarbsApplication).container

        // One launch, counted once — and only while the tutorial reminder still depends on the
        // number (the repository re-checks that inside its own transaction, so this cannot make the
        // counter climb forever).
        //
        // Deliberately in `onCreate` rather than in the composition: `setContent`'s block runs again
        // on every recomposition and the Activity is recreated on rotation, so counting there would
        // inflate the number and retire the reminder several launches early. `savedInstanceState ==
        // null` is what distinguishes a genuine launch from a configuration change.
        if (savedInstanceState == null) {
            lifecycleScope.launch { container.settingsRepository.recordLaunch() }
        }

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
                    // Resolved here rather than in `onCreate` because it needs the real
                    // `hasSeenOnboarding`, and that is not known until settings have loaded — which
                    // is exactly what `Ready` means. Consumed once: `consumeShortcutAction` clears
                    // the Intent's action, so this cannot fire again on a configuration change.
                    LaunchedEffect(Unit) {
                        startupRequest = consumeShortcutAction(current.settings.hasSeenOnboarding)
                    }

                    JustTheCarbsNavHost(
                        container = container,
                        settings = current.settings,
                        startupRequest = startupRequest,
                    )
                }
            }
        }
    }
}
