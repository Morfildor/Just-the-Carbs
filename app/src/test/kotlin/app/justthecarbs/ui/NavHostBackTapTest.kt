package app.justthecarbs.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every tap-driven Close and Back in the NavHost ignores a second tap (2026-09-25 review).
 *
 * A plain `{ navController.popBackStack() }` pops once per tap, so a quick double tap on a
 * scanner's Close also left the screen behind it. `dropUnlessResumed` drops the second, because the
 * first has already moved the entry off RESUMED; `DoubleTapBackTest` shows that on a device. This
 * reads the source so a new route cannot quietly reintroduce the plain form.
 */
class NavHostBackTapTest {
    @Test
    fun `no Close or Back handler pops the back stack once per tap`() {
        val source = java.io.File("src/main/kotlin/app/justthecarbs/ui/JustTheCarbsNavHost.kt")
            .readText()
            .replace(Regex("""\s+"""), " ")
        val plain = Regex("""on(Close|Back) = \{ navController\.popBackStack\(\) \}""").findAll(source).count()
        val guarded = Regex("""on(Close|Back) = dropUnlessResumed \{ navController\.popBackStack\(\) \}""")
            .findAll(source).count()

        assertTrue("found $plain plain Close/Back handlers", plain == 0)
        assertTrue("found no guarded Close/Back handlers; did the wiring move?", guarded > 0)
    }
}
