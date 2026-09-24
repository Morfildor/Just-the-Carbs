package app.justthecarbs.ui.scan

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starts CameraX's one-time initialisation ahead of the first scan, once per process.
 *
 * Both scanners call [ProcessCameraProvider.getInstance] from their `AndroidView` factory, so the
 * first scan of a process paid for CameraX's setup (camera enumeration, characteristics, the
 * validator) while the preview sat black. Starting it earlier turns their own `getInstance` calls into
 * reads of the already-completing future; they are left in place and remain the only binding.
 *
 * What this does and does not do, since it runs without the user having asked for the camera:
 * - It **never opens a camera** and binds nothing, so no frame is captured and Android shows no
 *   camera privacy indicator. Only `bindToLifecycle`, in the scanners, opens one.
 * - It needs **no camera permission** — enumeration does not — so it is not gated on one. A user who
 *   has not granted it yet is the one whose first scan follows the permission dialog, where the
 *   wait is otherwise most visible.
 * - A failure (no camera, a vendor error) is ignored here: CameraX discards a failed initialisation,
 *   so the scanner's own call retries and reports the failure exactly as it always did.
 * - It touches no network and no app data.
 *
 * Called after Home's first frame rather than at process start, so it never competes with the
 * launch it is meant to be invisible to.
 */
internal object CameraWarmUp {

    private val started = AtomicBoolean(false)

    fun startOnce(context: Context) {
        if (!started.compareAndSet(false, true)) return
        // The future is deliberately not observed: the scanners read it when they need it.
        ProcessCameraProvider.getInstance(context.applicationContext)
    }
}
