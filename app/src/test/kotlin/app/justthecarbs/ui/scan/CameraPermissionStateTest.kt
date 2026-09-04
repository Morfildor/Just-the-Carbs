package app.justthecarbs.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * §6, startup-hardening pass.
 *
 * [currentPermissionState] is the one piece of the shared camera-permission gate that is pure
 * Android-framework-free logic, so it is the piece pinned here without a device. Both
 * [ScannerScreen] and [LabelScannerScreen] call the same [rememberCameraPermissionController], which
 * in turn calls this exact function — there is no second copy of the state-selection logic for the
 * two scanners to disagree about, which is the defect this pass closes (previously each screen
 * carried its own hand-rolled granted/requested tracking).
 */
class CameraPermissionStateTest {

    @Test
    fun `nothing requested yet is NotRequested`() {
        val state = currentPermissionState(granted = false, requestedThisVisit = false, canAskAgain = false)

        assertEquals(CameraPermissionState.NotRequested, state)
    }

    @Test
    fun `granted is Granted regardless of what came before`() {
        // A permission that is granted is granted — whether or not a request happened this visit,
        // and whether or not the platform would still show its own rationale dialog, neither matters
        // once the answer is yes.
        assertEquals(
            CameraPermissionState.Granted,
            currentPermissionState(granted = true, requestedThisVisit = false, canAskAgain = false),
        )
        assertEquals(
            CameraPermissionState.Granted,
            currentPermissionState(granted = true, requestedThisVisit = true, canAskAgain = true),
        )
    }

    @Test
    fun `a temporary denial where the system still offers its own dialog is DeniedCanAskAgain`() {
        // Android's own shouldShowRequestPermissionRationale() answering true is precisely a "not
        // this time" denial rather than a "never ask me again" one.
        val state = currentPermissionState(granted = false, requestedThisVisit = true, canAskAgain = true)

        assertEquals(CameraPermissionState.DeniedCanAskAgain, state)
    }

    @Test
    fun `a permanent denial where the system dialog is gone is PermanentlyDenied`() {
        val state = currentPermissionState(granted = false, requestedThisVisit = true, canAskAgain = false)

        assertEquals(CameraPermissionState.PermanentlyDenied, state)
    }

    @Test
    fun `granting after returning from Settings moves PermanentlyDenied to Granted`() {
        // Simulates the ON_RESUME recheck: a screen previously PermanentlyDenied, then the platform
        // reports granted=true on the next state read after the user flips it on in Settings and
        // returns. The recheck is what feeds a fresh `granted` value into this function — this test
        // pins that the function itself responds correctly to that transition, independent of how
        // the caller obtains the new value.
        val before = currentPermissionState(granted = false, requestedThisVisit = true, canAskAgain = false)
        assertEquals(CameraPermissionState.PermanentlyDenied, before)

        val after = currentPermissionState(granted = true, requestedThisVisit = true, canAskAgain = false)

        assertEquals(CameraPermissionState.Granted, after)
    }

    @Test
    fun `a denial never strands the scanner because Granted is the only terminal state`() {
        // Every non-Granted state is reachable from every other non-Granted state via a fresh
        // permission read (there is no state this function can return that has no path back to
        // Granted) — the property that matters is that PermanentlyDenied, the worst case, still
        // yields Granted the moment `granted` becomes true, checked above. This test additionally
        // pins that neither denial state is ever confused with NotRequested, which would incorrectly
        // suppress the "was this asked already" distinction and could re-show `permission_body`
        // instead of `permission_settings_body`.
        val temporary = currentPermissionState(granted = false, requestedThisVisit = true, canAskAgain = true)
        val permanent = currentPermissionState(granted = false, requestedThisVisit = true, canAskAgain = false)
        val notYetAsked = currentPermissionState(granted = false, requestedThisVisit = false, canAskAgain = false)

        assertEquals(CameraPermissionState.DeniedCanAskAgain, temporary)
        assertEquals(CameraPermissionState.PermanentlyDenied, permanent)
        assertEquals(CameraPermissionState.NotRequested, notYetAsked)
    }
}
