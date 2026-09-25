package app.justthecarbs.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Space

/**
 * The camera permission, as one of five states — shared by [ScannerScreen] and
 * [LabelScannerScreen] (§6, startup-hardening pass).
 *
 * Both scanners previously carried their own copy of this logic, and both had the identical gap:
 * once a request had been answered, the screen showed only *Enter manually* — a temporary denial
 * (where Android still permits asking again) had no way back to the system dialog, and a permanent
 * denial (where Android has stopped showing it) had no way to the one place that still helps, the
 * app's own Settings page. Manual entry remained reachable throughout, but the camera itself was a
 * dead end from the first "Deny" onward.
 */
sealed interface CameraPermissionState {
    /** The camera may be used. */
    data object Granted : CameraPermissionState

    /** Nothing has been asked yet this screen visit. */
    data object NotRequested : CameraPermissionState

    /**
     * Denied, but the system will still show its own dialog again — Android's own signal
     * ([android.app.Activity.shouldShowRequestPermissionRationale]) that this is a "not this time"
     * answer rather than a "never ask me again" one.
     */
    data object DeniedCanAskAgain : CameraPermissionState

    /**
     * Denied by a request after which the system no longer shows its dialog (see
     * [dialogNoLongerOffered]): answered without one, or the user chose not to be asked again. The
     * only remaining path is the app's Settings page.
     *
     * Not simply "the rationale flag is false after a request": on Android 11+ dismissing the dialog
     * with Back leaves it false too, and treating that as permanent sent the user to Settings after
     * a single dismissal.
     */
    data object PermanentlyDenied : CameraPermissionState
}

/**
 * Derives the four-state model from what the platform reports right now.
 *
 * Pure and Android-framework-free (no `Activity` parameter) so it is plain-JVM-testable — this
 * codebase does not use Robolectric, and the state-selection logic is exactly the part worth
 * pinning without a device. [canAskAgain] is
 * [android.app.Activity.shouldShowRequestPermissionRationale]'s answer, read by the caller; this
 * function only combines it with what has happened on this screen visit.
 *
 * Not exposed as its own state case: "returning from Settings" is not a fifth value the render
 * layer needs to know about, it is simply the moment [Lifecycle.Event.ON_RESUME] triggers a recheck
 * that may move [CameraPermissionState] from [CameraPermissionState.PermanentlyDenied] to
 * [CameraPermissionState.Granted] — the two states already say everything the screen needs.
 */
internal fun currentPermissionState(
    granted: Boolean,
    requestedThisVisit: Boolean,
    canAskAgain: Boolean,
    systemDialogSuppressed: Boolean,
): CameraPermissionState = when {
    granted -> CameraPermissionState.Granted
    !requestedThisVisit -> CameraPermissionState.NotRequested
    canAskAgain -> CameraPermissionState.DeniedCanAskAgain
    systemDialogSuppressed -> CameraPermissionState.PermanentlyDenied
    // Denied with no rationale either side, but the dialog was shown: dismissed with Back, or a
    // denial the system has not yet decided to stop asking about. Asking again is still possible.
    else -> CameraPermissionState.DeniedCanAskAgain
}

/**
 * Whether a denied request is evidence that the system no longer shows its own dialog.
 *
 * Android offers no API that says so. `shouldShowRequestPermissionRationale` false after a denial
 * means either "never ask again" or, on Android 11+, a dialog dismissed with Back, and AndroidX
 * documents no way to tell them apart. The evidence used here, in order:
 *
 * - A rationale flag true before the request and false after it: the user was shown the dialog
 *   and chose not to be asked again. This is Android's own signal for that choice (2026-09-25
 *   review); treating it as "can ask again" left *Allow camera* as a button that did nothing.
 * - A rationale flag true after the request means the system still shows its dialog.
 * - Answered in under [SUPPRESSED_REQUEST_MS] with no rationale either side: the system returned
 *   without anything for a person to read and dismiss.
 * - A second such ambiguous answer in a row: the timing is a heuristic and a slow phone can return
 *   a suppressed request late, so *Allow camera* must not be offered a third time as a button that
 *   may silently do nothing. The Settings page works either way.
 */
internal fun dialogNoLongerOffered(
    rationaleBefore: Boolean,
    rationaleAfter: Boolean,
    elapsedMs: Long,
    previousRequestAlsoAmbiguous: Boolean,
): Boolean {
    if (rationaleBefore && !rationaleAfter) return true
    if (rationaleAfter) return false
    return elapsedMs < SUPPRESSED_REQUEST_MS || previousRequestAlsoAmbiguous
}

/**
 * Faster than a person can see the dialog animate in and dismiss it, slower than the system's own
 * no-dialog round trip (it starts and finishes its permission activity without drawing anything).
 */
internal const val SUPPRESSED_REQUEST_MS = 300L

/**
 * Holds the camera-permission state and the actions available from it.
 *
 * [request] re-launches the system dialog (only meaningful from [CameraPermissionState.NotRequested]
 * or [CameraPermissionState.DeniedCanAskAgain]); [openSettings] opens this app's own Settings page,
 * the only remaining path once Android has stopped offering its dialog.
 */
class CameraPermissionController internal constructor(
    stateProvider: () -> CameraPermissionState,
    private val requestAction: () -> Unit,
    private val openSettingsAction: () -> Unit,
) {
    private val stateProvider = stateProvider

    val state: CameraPermissionState get() = stateProvider()

    fun request() = requestAction()

    fun openSettings() = openSettingsAction()
}

/**
 * Remembers a [CameraPermissionController] for the CAMERA permission.
 *
 * Requests once automatically on first composition (§9's "ask in context" rule, unchanged) and
 * rechecks on [Lifecycle.Event.ON_RESUME] — the moment the user returns from the app's own Settings
 * page, which is the one path [CameraPermissionState.PermanentlyDenied] leaves open and the one
 * transition neither `ActivityResultContracts.RequestPermission()`'s callback nor a plain
 * recomposition would otherwise observe, since nothing about the *composable* changes when the user
 * leaves and returns to it — only the platform's own permission state does.
 */
@Composable
fun rememberCameraPermissionController(): CameraPermissionController {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var requestedThisVisit by remember { mutableStateOf(false) }
    var systemDialogSuppressed by remember { mutableStateOf(false) }
    // What the last request looked like as it went out, for [dialogNoLongerOffered]. Plain fields,
    // not state: nothing renders from them. Lost with the composition (a rotation mid-dialog), which
    // reads as a slow, ambiguous answer — the safe side, since it keeps *Allow camera* on offer.
    val request = remember { PendingCameraRequest() }

    fun canAskAgain(): Boolean =
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        requestedThisVisit = true
        if (!result) {
            val rationaleAfter = canAskAgain()
            systemDialogSuppressed = dialogNoLongerOffered(
                rationaleBefore = request.rationaleBefore,
                rationaleAfter = rationaleAfter,
                elapsedMs = SystemClock.elapsedRealtime() - request.startedAtMs,
                previousRequestAlsoAmbiguous = request.lastWasAmbiguous,
            )
            request.lastWasAmbiguous = !request.rationaleBefore && !rationaleAfter
        }
    }

    fun launchRequest() {
        request.rationaleBefore = canAskAgain()
        request.startedAtMs = SystemClock.elapsedRealtime()
        launcher.launch(Manifest.permission.CAMERA)
    }

    // Ask once, automatically, the moment the screen that needs the camera is actually shown —
    // unchanged from the pre-existing behaviour in both scanners.
    LaunchedEffect(Unit) {
        if (!granted) launchRequest()
    }

    // Rechecking on ON_RESUME — not on every recomposition — is what makes "granted it in Settings,
    // then pressed back" work: `ContextCompat.checkSelfPermission` only changes because the OS
    // changed it while this screen was backgrounded, and ON_RESUME is exactly the signal that a
    // backgrounding-and-return just happened.
    val currentGranted = rememberUpdatedState(granted)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (nowGranted != currentGranted.value) granted = nowGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(activity) {
        CameraPermissionController(
            stateProvider = {
                currentPermissionState(granted, requestedThisVisit, canAskAgain(), systemDialogSuppressed)
            },
            requestAction = { launchRequest() },
            openSettingsAction = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    },
                )
            },
        )
    }
}

/** The last camera request as it went out; see [rememberCameraPermissionController]. */
private class PendingCameraRequest {
    var rationaleBefore = false
    var startedAtMs = 0L
    var lastWasAmbiguous = false
}

/**
 * The camera-denied screen, shared by both scanners (§6, startup-hardening pass).
 *
 * *Enter manually* is present in every branch — declining or losing the camera permission must
 * never cost the user the app, only the camera. What differs by [state] is only the top action:
 * [CameraPermissionState.NotRequested] and [CameraPermissionState.DeniedCanAskAgain] can still ask
 * Android for the permission directly, so they show *Allow camera*; once Android has stopped
 * offering its own dialog ([CameraPermissionState.PermanentlyDenied]) that button would do nothing,
 * so this shows *Open Settings* and different body text explaining why.
 */
@Composable
fun CameraPermissionRationale(
    state: CameraPermissionState,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnterManually: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(Space.screenEdge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = stringResource(
                if (state == CameraPermissionState.PermanentlyDenied) {
                    R.string.permission_settings_body
                } else {
                    R.string.permission_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.l))

        when (state) {
            CameraPermissionState.PermanentlyDenied -> {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                    shape = RoundedCornerShape(Space.buttonRadius),
                ) { Text(stringResource(R.string.permission_open_settings)) }
                Spacer(Modifier.height(Space.s))
            }
            CameraPermissionState.NotRequested, CameraPermissionState.DeniedCanAskAgain -> {
                Button(
                    onClick = onAllow,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                    shape = RoundedCornerShape(Space.buttonRadius),
                ) { Text(stringResource(R.string.permission_allow)) }
                Spacer(Modifier.height(Space.s))
            }
            CameraPermissionState.Granted -> Unit
        }

        // Outlined, not filled: exactly one filled action per state, the camera path above. Two
        // filled buttons stacked read as two equal answers to one question.
        OutlinedButton(
            onClick = onEnterManually,
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
            shape = RoundedCornerShape(Space.buttonRadius),
        ) { Text(stringResource(R.string.permission_manual)) }

        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouchTarget)) {
            Text(stringResource(R.string.action_close))
        }
    }
}
