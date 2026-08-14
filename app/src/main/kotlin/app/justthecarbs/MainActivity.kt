package app.justthecarbs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        enableEdgeToEdge()

        val container = (application as JustTheCarbsApplication).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            JustTheCarbsTheme(themeChoice = settings.theme) {
                JustTheCarbsNavHost(container = container, settings = settings)
            }
        }
    }
}
