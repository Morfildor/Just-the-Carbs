package app.justthecarbs.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test

/**
 * A second tap on Close or Back while the first is already leaving is ignored (2026-09-25 review).
 *
 * Both taps reached `popBackStack`, so a quick double tap on a scanner's Close left the screen
 * behind it as well, and on Home's direct children could empty the back stack. The app wires every
 * tap-driven Close and Back through `dropUnlessResumed`, as below; `NavHostBackTapTest` (JVM) pins
 * that the NavHost does so.
 */
class DoubleTapBackTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aSecondTapWhileTheFirstIsLeavingIsIgnored() {
        rule.setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "a") {
                composable("a") { Text("Screen A") }
                composable("b") { Text("Screen B") }
                composable("c") {
                    Button(onClick = dropUnlessResumed { nav.popBackStack() }) { Text("Back") }
                }
            }
            LaunchedEffect(Unit) {
                nav.navigate("b")
                nav.navigate("c")
            }
        }
        rule.onNodeWithText("Back").assertIsDisplayed()

        // Both taps land before anything recomposes, as a quick double tap does.
        rule.onNodeWithText("Back").performTouchInput {
            click()
            click()
        }

        rule.onNodeWithText("Screen B").assertIsDisplayed()
    }
}
