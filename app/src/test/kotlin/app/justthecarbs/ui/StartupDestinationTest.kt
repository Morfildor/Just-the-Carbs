package app.justthecarbs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The launcher-shortcut rules.
 *
 * These exist because the interesting cases are the ones that are painful to reach on a device: the
 * gate cases need a fresh install between every run, and the unknown-action cases cannot be produced
 * from the launcher at all. Keeping the decision in a pure function is what makes them checkable.
 */
class StartupDestinationTest {

    @Test
    fun `the barcode shortcut opens the barcode scanner`() {
        assertEquals(
            StartupDestination.SCAN_BARCODE,
            StartupDestination.from(
                StartupDestination.ACTION_SCAN_BARCODE,
                hasSeenOnboarding = true,
            ),
        )
    }

    @Test
    fun `the label shortcut opens the label scanner`() {
        assertEquals(
            StartupDestination.SCAN_LABEL,
            StartupDestination.from(
                StartupDestination.ACTION_SCAN_LABEL,
                hasSeenOnboarding = true,
            ),
        )
    }

    @Test
    fun `an ordinary launch has no action and opens the default destination`() {
        assertEquals(
            StartupDestination.DEFAULT,
            StartupDestination.from(null, hasSeenOnboarding = true),
        )
    }

    @Test
    fun `an unrecognised action opens the default destination rather than failing`() {
        // Something other than the launcher can start this activity, and an action this app does not
        // publish must degrade to an ordinary launch rather than to an error or an empty screen.
        assertEquals(
            StartupDestination.DEFAULT,
            StartupDestination.from("android.intent.action.VIEW", hasSeenOnboarding = true),
        )
    }

    @Test
    fun `no shortcut skips the welcome carousel`() {
        // The gate, and the reason this function exists. The carousel is the only place
        // `hasSeenOnboarding` is written, so a shortcut that jumped it would both put a camera in
        // front of someone who has not been told what the app does and leave the carousel to appear
        // later, on an unrelated launch.
        assertEquals(
            StartupDestination.DEFAULT,
            StartupDestination.from(
                StartupDestination.ACTION_SCAN_BARCODE,
                hasSeenOnboarding = false,
            ),
        )
        assertEquals(
            StartupDestination.DEFAULT,
            StartupDestination.from(
                StartupDestination.ACTION_SCAN_LABEL,
                hasSeenOnboarding = false,
            ),
        )
    }

    @Test
    fun `the published actions are the ones the manifest shortcuts declare`() {
        // These strings are a contract with res/xml/shortcuts.xml: the resource names the action and
        // this class matches on it, and nothing in the compiler connects the two. Renaming one
        // without the other produces shortcuts that silently open Home, which looks like a shortcut
        // that does nothing rather than like a broken build.
        assertEquals("app.justthecarbs.action.SCAN_BARCODE", StartupDestination.ACTION_SCAN_BARCODE)
        assertEquals("app.justthecarbs.action.SCAN_LABEL", StartupDestination.ACTION_SCAN_LABEL)
    }

    @Test
    fun `repeating the same shortcut is a distinct request`() {
        // The device-measured defect this exists to stop: a `LaunchedEffect` keyed on the
        // destination alone does not re-run when the value is unchanged, so tapping *Barcode* a
        // second time did nothing at all and the app sat on whatever screen it was already showing.
        // Two deliveries of the same destination must not be equal.
        val first = StartupRequest(StartupDestination.SCAN_BARCODE, id = 1)
        val second = StartupRequest(StartupDestination.SCAN_BARCODE, id = 2)

        assertNotEquals(first, second)
        assertEquals(first.destination, second.destination)
    }

    @Test
    fun `an ordinary launch carries no request`() {
        // NONE is the resting value, and it must stay distinguishable from a real delivery so that
        // an ordinary launch never navigates anywhere.
        assertEquals(StartupDestination.DEFAULT, StartupRequest.NONE.destination)
        assertEquals(0L, StartupRequest.NONE.id)
    }
}
