package app.justthecarbs.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun state(
        granted: Boolean = false,
        requestedThisVisit: Boolean = true,
        canAskAgain: Boolean = false,
        systemDialogSuppressed: Boolean = false,
    ) = currentPermissionState(granted, requestedThisVisit, canAskAgain, systemDialogSuppressed)

    @Test
    fun `nothing requested yet is NotRequested`() {
        assertEquals(CameraPermissionState.NotRequested, state(requestedThisVisit = false))
    }

    @Test
    fun `granted is Granted regardless of what came before`() {
        // A permission that is granted is granted — whether or not a request happened this visit,
        // and whether or not the platform would still show its own rationale dialog, neither matters
        // once the answer is yes.
        assertEquals(CameraPermissionState.Granted, state(granted = true, requestedThisVisit = false))
        assertEquals(
            CameraPermissionState.Granted,
            state(granted = true, canAskAgain = true, systemDialogSuppressed = true),
        )
    }

    @Test
    fun `a temporary denial where the system still offers its own dialog is DeniedCanAskAgain`() {
        // Android's own shouldShowRequestPermissionRationale() answering true is precisely a "not
        // this time" denial rather than a "never ask me again" one.
        assertEquals(CameraPermissionState.DeniedCanAskAgain, state(canAskAgain = true))
    }

    @Test
    fun `a denial the system answered without showing its dialog is PermanentlyDenied`() {
        assertEquals(CameraPermissionState.PermanentlyDenied, state(systemDialogSuppressed = true))
    }

    @Test
    fun `a dialog dismissed with Back is not a permanent denial`() {
        // Android 11+: dismissing the system dialog with Back leaves the rationale flag false, which
        // looks exactly like "never ask again". Without evidence that the system stopped showing its
        // dialog, the screen must still offer Allow camera rather than send the user to Settings.
        assertEquals(CameraPermissionState.DeniedCanAskAgain, state(canAskAgain = false))
    }

    @Test
    fun `granting after returning from Settings moves PermanentlyDenied to Granted`() {
        // Simulates the ON_RESUME recheck: a screen previously PermanentlyDenied, then the platform
        // reports granted=true on the next state read after the user flips it on in Settings and
        // returns. The recheck is what feeds a fresh `granted` value into this function — this test
        // pins that the function itself responds correctly to that transition, independent of how
        // the caller obtains the new value.
        assertEquals(CameraPermissionState.PermanentlyDenied, state(systemDialogSuppressed = true))
        assertEquals(CameraPermissionState.Granted, state(granted = true, systemDialogSuppressed = true))
    }

    @Test
    fun `a denial is never confused with NotRequested`() {
        // Neither denial state may be confused with NotRequested, which would suppress the "was this
        // asked already" distinction and could re-show `permission_body` instead of
        // `permission_settings_body`.
        assertEquals(CameraPermissionState.DeniedCanAskAgain, state(canAskAgain = true))
        assertEquals(CameraPermissionState.PermanentlyDenied, state(systemDialogSuppressed = true))
        assertEquals(CameraPermissionState.NotRequested, state(requestedThisVisit = false))
    }

    // --- Was the system dialog shown at all? ---

    @Test
    fun `a request that returned almost at once with no rationale either side had no dialog`() {
        assertTrue(
            deniedWithoutADialog(
                rationaleBefore = false,
                rationaleAfter = false,
                elapsedMs = 40,
                previousRequestAlsoAmbiguous = false,
            ),
        )
    }

    @Test
    fun `a request answered at human speed was a dialog the user dismissed`() {
        assertFalse(
            deniedWithoutADialog(
                rationaleBefore = false,
                rationaleAfter = false,
                elapsedMs = 1_800,
                previousRequestAlsoAmbiguous = false,
            ),
        )
    }

    @Test
    fun `a rationale flag on either side means the system still shows its dialog`() {
        assertFalse(
            deniedWithoutADialog(
                rationaleBefore = true,
                rationaleAfter = false,
                elapsedMs = 40,
                previousRequestAlsoAmbiguous = true,
            ),
        )
        assertFalse(
            deniedWithoutADialog(
                rationaleBefore = false,
                rationaleAfter = true,
                elapsedMs = 40,
                previousRequestAlsoAmbiguous = true,
            ),
        )
    }

    @Test
    fun `a second ambiguous denial in a row stops offering a request that may do nothing`() {
        // The timing is a heuristic; on a slow phone a suppressed request may return late. Allow
        // camera must never become a button that silently does nothing twice, so the second
        // ambiguous answer moves on to the Settings page, which works either way.
        assertTrue(
            deniedWithoutADialog(
                rationaleBefore = false,
                rationaleAfter = false,
                elapsedMs = 1_800,
                previousRequestAlsoAmbiguous = true,
            ),
        )
    }
}
