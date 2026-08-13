package app.carbscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.carbscan.domain.AppSettings
import app.carbscan.ui.CarbScanNavHost
import app.carbscan.ui.theme.CarbScanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash screen, with no artificial hold: it disappears the moment the first
        // frame is ready (§6). Nothing is preloaded behind it.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as CarbScanApplication).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            CarbScanTheme(themeChoice = settings.theme) {
                CarbScanNavHost(container = container, settings = settings)
            }
        }
    }
}
