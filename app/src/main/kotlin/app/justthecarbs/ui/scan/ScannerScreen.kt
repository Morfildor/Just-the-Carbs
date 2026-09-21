package app.justthecarbs.ui.scan

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.justthecarbs.R
import app.justthecarbs.domain.BarcodeAcceptance
import app.justthecarbs.domain.BarcodeStabilityTracker
import app.justthecarbs.domain.ImportedBarcodeSelection
import app.justthecarbs.ocr.ImportedPhotoIntake
import app.justthecarbs.ocr.OcrDiagnosticsLogger
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The scanner (§8, §9).
 *
 * Full-bleed preview, one subtle frame, minimal text. Everything else — torch, close, manual entry
 * — sits at the bottom within thumb reach (§40).
 */
@Composable
fun ScannerScreen(
    hapticsEnabled: Boolean,
    onBarcode: (String) -> Unit,
    onManualBarcode: (String) -> Unit,
    onClose: () -> Unit,
    onEnterManually: () -> Unit,
    /**
     * An image shared into the app from elsewhere, already copied into this app's cache (1.0.8).
     *
     * Non-null only on the share route, where the user has already been asked what the picture
     * contains and answered "barcode". It enters the **same** import this screen's own picker
     * feeds - the same staging, the same [ImportedBarcodeReader], the same
     * [app.justthecarbs.domain.BarcodeFrameReader] validation, the same choice sheet when several
     * codes are present, and the same `onBarcode` navigation. A share's only privilege is skipping
     * the picker.
     *
     * An [ImportedImageSource.Staged] holding a cache file this app already owns — never the
     * sender's `content://`. The sender's grant rides on the delivered Intent and may be dead by
     * the time the user has answered the chooser, so `MainActivity` takes the bytes while the grant
     * is certainly live. Typed as a staged file rather than a `file://` URI precisely so this
     * screen cannot stage it a second time; see [ImportedImageSource].
     */
    sharedImage: ImportedImageSource? = null,
) {
    // §6, startup-hardening pass: one shared five-state gate, used identically by this screen and
    // LabelScannerScreen. Previously each screen tracked only granted/not-granted plus whether a
    // request had been made, which made every denial a dead end — a "not this time" answer had no
    // way back to the system dialog, and a "never ask me again" answer had no way to Settings.
    val permission = rememberCameraPermissionController()

    // Barcode entry from the *permission* branch, where there is no camera session to pause and no
    // analyzer to stop — so it is held here rather than inside CameraPreview, which owns the
    // equivalent state for the live-preview branch and must also unbind the camera before leaving.
    var showBarcodeSheet by remember { mutableStateOf(false) }

    // Hoisted: both the photo-import host below and the branch further down need it, and a share
    // must reach whichever of the two actually composes.
    val cameraGranted = permission.state == CameraPermissionState.Granted

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraGranted) {
            CameraPreview(
                hapticsEnabled = hapticsEnabled,
                onBarcode = onBarcode,
                onManualBarcode = onManualBarcode,
                onClose = onClose,
                onEnterManually = onEnterManually,
                sharedImage = sharedImage,
            )
        } else {
            // Photo import on the permission-denied branch. Created INSIDE this branch, not
            // above it, and that placement is load-bearing.
            //
            // Hoisted out, this composable and CameraPreview's equivalent both exist across the
            // moment the permission state settles, and Compose moves the `rememberSaveable`
            // state between them as one leaves composition and the other enters. Measured on the
            // emulator: the import effect ran twice for a single delivery under the same key, the
            // second run found the consumed token its own first run had written, and the screen
            // sat on "Reading the photo you chose..." with no way out. Scoping it to the branch
            // that actually uses it means only ever one host exists.
            val photoOnlyImport = rememberBarcodePhotoImport(
                onBarcode = onBarcode,
                // Nothing to pause or resume: no camera was ever bound on this branch.
                onPauseCamera = {},
                onResumeCamera = {},
                // A share reaching this branch means the camera was declined - which costs the
                // camera and never the app. Reading a barcode out of a picture the user already
                // has needs no sensor at all.
                sharedImage = sharedImage,
            )

            CameraPermissionRationale(
                state = permission.state,
                onAllow = permission::request,
                onOpenSettings = permission::openSettings,
                onEnterManually = onEnterManually,
                // The dead end this closes: without a camera the only offered route was full
                // manual product entry, so the one thing that still works — reading the digits
                // printed under the bars — was unreachable precisely when it was needed most.
                onEnterBarcode = { showBarcodeSheet = true },
                // Reading a barcode out of a photograph needs no camera at all, so the one state
                // where the user has lost every other way to scan is precisely the state where it
                // must stay reachable. Declining the camera costs the camera, never the app.
                onChoosePhoto = photoOnlyImport::choosePhoto,
                onClose = onClose,
            )

            if (showBarcodeSheet) {
                ManualBarcodeSheet(
                    // Straight to the caller's ordinary barcode navigation: no camera was ever
                    // bound on this branch, so there is nothing to tear down first.
                    onConfirm = onManualBarcode,
                    onDismiss = { showBarcodeSheet = false },
                )
            }

            BarcodePhotoImportSurfaces(
                import = photoOnlyImport,
                // With no camera there is nothing to return to, so the dismissing action says what
                // it actually does rather than promising a scanner that will not appear.
                cameraAvailable = false,
                onEnterManually = { showBarcodeSheet = true },
            )
        }
    }
}

/**
 * The photo-import half of the barcode scanner: picker, staging, recognition and what to show.
 *
 * ## The one downstream path
 *
 * A barcode read from a photograph reaches [onBarcode] — the **same** callback the live analyzer
 * fires on acceptance, which the nav host routes to the same `Routes.product(barcode)` as a camera
 * scan and as a typed code. Nothing here looks a product up, and there is no second lookup to
 * drift: the photograph's only privilege is producing a validated barcode, by the same
 * [app.justthecarbs.domain.BarcodeFrameReader] boundary the camera uses.
 *
 * ## Why the camera is paused for the whole import
 *
 * [onPauseCamera] runs the moment a photograph is accepted and [onResumeCamera] only when the
 * import is finished with. Navigation is one-shot and latched, so a live detection landing while a
 * photo result is being presented would be two answers competing for it — and the user is by then
 * looking at a sheet about a picture, not at the preview. Pausing also stops the analyzer doing
 * work nobody is waiting for while ML Kit reads the still.
 *
 * ## Staleness
 *
 * Every step re-asks [ImportGeneration.isCurrent] at the point a result would become visible, so a
 * slow first photograph cannot navigate after a second was chosen, and nothing lands after the user
 * leaves. Cancellation is passed down to the copy as well, but it is the optimisation — the
 * generation check is the guarantee.
 */
@Composable
private fun rememberBarcodePhotoImport(
    onBarcode: (String) -> Unit,
    onPauseCamera: () -> Unit,
    onResumeCamera: () -> Unit,
    /**
     * An image shared into the app, imported as this composable appears instead of via the picker.
     *
     * It is injected into the same `picked` state the launcher writes, so everything below - the
     * intake rules, staging, recognition, the choice sheet, the navigation - is reached by one
     * path whichever way the photograph arrived.
     */
    sharedImage: ImportedImageSource? = null,
): BarcodePhotoImport {
    val context = LocalContext.current
    var state by remember { mutableStateOf<BarcodePhotoImportState>(BarcodePhotoImportState.Idle) }
    val generation = remember { ImportGeneration() }

    // `rememberSaveable`, deliberately: this is the guard against a launcher re-delivering its last
    // result to a recreated composition (rotation, process death, returning from Settings). A plain
    // `remember` is discarded by exactly the recreation the replay accompanies, so the result would
    // arrive looking new and silently re-import a photograph the user already acted on.
    var consumedPhotoToken by rememberSaveable { mutableStateOf<String?>(null) }

    val disposed = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            // Whatever is in flight must not land on a screen that is gone.
            generation.invalidate()
        }
    }

    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnPause by rememberUpdatedState(onPauseCamera)
    val currentOnResume by rememberUpdatedState(onResumeCamera)

    /**
     * The launcher's delivery, as a value that changes on **every** delivery.
     *
     * Keyed by a monotonic id rather than by the URI alone, because choosing the *same* photograph
     * twice is an ordinary thing to do — after a "no barcode found", the obvious next act is to try
     * the same picture again, or to pick it deliberately after a cancel. Keying the effect on the
     * URI would leave the state unchanged on that second delivery and the import would never run.
     *
     * This is not the replay guard: a launcher re-delivering its last result to a recreated
     * composition is caught by `consumedPhotoToken`, which survives recreation and is compared by
     * value.
     */
    var picked by remember { mutableStateOf<Pair<Long, ImportedImageSource?>?>(null) }
    var deliveries by remember { mutableLongStateOf(0L) }

    /**
     * The share this screen has already delivered onto [picked], so it delivers it once only.
     *
     * `rememberSaveable`, and that is the whole point: it must survive the recreation a rotation
     * causes, which is exactly when a re-delivery would otherwise happen.
     *
     * **Measured on the emulator, not reasoned about.** The first version keyed a `LaunchedEffect`
     * on the URI and wrote `picked` from inside it. That effect ran *twice* for one share - the
     * import's own first state write recomposes this function, the effect is relaunched, and the
     * second run then found the token its own first run had just written. The visible result was
     * a screen stuck on "Reading the photo you chose..." forever, because the second delivery was
     * refused as ALREADY_CONSUMED after the first had already put the screen into Reading.
     *
     * Comparing by value here (rather than a bare boolean) keeps a genuinely new share working:
     * each one stages to its own cache file, so a second share presents a different path.
     */
    var deliveredShare by rememberSaveable { mutableStateOf<String?>(null) }

    // A shared image enters exactly where a picked one does: as a delivery on `picked`.
    //
    // In a `SideEffect` rather than written straight into the composable body: this mutates state
    // the same composition reads, and doing that inline is the "backwards write" Compose warns
    // about. A `SideEffect` runs after a successful composition, so the write happens once the
    // frame it belongs to is settled, and the guard above keeps it to one delivery per share.
    SideEffect {
        val shared = sharedImage ?: return@SideEffect
        if (shared.token == deliveredShare) return@SideEffect
        deliveredShare = shared.token
        deliveries += 1
        picked = deliveries to shared
    }

    // The system photo picker: no storage or media permission, and the URI it returns carries only
    // a temporary read grant, which staging consumes immediately and never persists.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        // Every delivery is handed on, cancellation included: deciding what a null URI means is
        // ImportedPhotoIntake's rule, and answering it here as well would put one rule in two
        // places. A picked URI is somebody else's bytes, so it enters as `Picked` and will be
        // staged; a share arrives already staged and must not be.
        deliveries += 1
        picked = deliveries to uri?.let(ImportedImageSource::Picked)
    }

    // Keyed on the delivery NUMBER, not on the `picked` pair.
    //
    // `Pair` is a data class, so two pairs holding the same values are `equals` - but Compose
    // restarts a keyed effect whenever the key is not equal to the previous one, and a fresh
    // `Long` boxed into a new `Pair` on each recomposition was enough to make this effect restart
    // mid-import. Measured: the import began, wrote `consumedPhotoToken`, that write recomposed
    // the screen, the effect relaunched and its second run found the token its own first run had
    // just written - refused as ALREADY_CONSUMED, leaving the screen on "Reading the photo you
    // chose..." forever with no way out.
    //
    // The delivery number is monotonic and changes exactly once per delivery, which is precisely
    // when this effect should run again.
    LaunchedEffect(picked?.first) {
        val delivery = picked ?: return@LaunchedEffect
        val source = delivery.second
        // Whether this delivery came from a share rather than the picker; see the token note below.
        val isShare = source != null && source.token == deliveredShare

        // Nothing is cleared here. `picked` is this effect's own key, so assigning it inside the
        // effect re-keys it and Compose cancels this coroutine before any of the work below runs —
        // which is what happened the first time this was written, and it presents as a picker that
        // returns to an unchanged screen with nothing in the log at all.

        // The three intake rules — cancelled, already consumed, newest wins — reused unchanged from
        // the nutrition-label path, where they are already pinned by their own JVM tests.
        // `lastConsumedToken` is null for a share, and that is not a loophole - it is what keeps
        // the two replay questions apart.
        //
        // The token guard exists for ONE hazard: `rememberLauncherForActivityResult` handing its
        // last result to a recreated composition, which would silently re-import a photograph the
        // user already dealt with. A share is not delivered by the launcher and cannot be replayed
        // that way; its own once-per-share guard is `deliveredShare` above, plus the Activity
        // consuming the share the moment the chooser is answered.
        //
        // Passing the token here as well was measured to break the feature outright: a keyed
        // `LaunchedEffect` restarts when its composable leaves and re-enters composition (a
        // navigation transition is enough), and the restarted run - the only one still alive -
        // then found the token its own cancelled predecessor had written and refused itself. The
        // screen sat on "Reading the photo you chose..." with no way out.
        val decision = ImportedPhotoIntake.decide(
            hasSelection = source != null,
            resultToken = source?.token,
            lastConsumedToken = if (isShare) null else consumedPhotoToken,
            beginWork = generation::begin,
        )
        val work = when (decision) {
            is ImportedPhotoIntake.Decision.Import -> decision.workGeneration
            is ImportedPhotoIntake.Decision.Ignore -> {
                OcrDiagnosticsLogger.timing("barcode photo import ignored (${decision.reason})")
                return@LaunchedEffect
            }
        }
        // Non-null by construction: `decide` reports CANCELLED for a null selection, above.
        val resolvedSource = requireNotNull(source)
        consumedPhotoToken = resolvedSource.token

        currentOnPause()
        state = BarcodePhotoImportState.Reading

        // Staged only when there is something to stage. A shared image was copied into this app's
        // cache at arrival, so it is returned as it is rather than copied a second time — see
        // [ImportedImageSource]. Either way this screen owns exactly one file afterwards.
        val staged = withContext(Dispatchers.IO) {
            ImportedImageResolver.resolve(
                context = { context },
                source = resolvedSource,
                cancelled = { disposed.get() || !generation.isCurrent(work) },
                prefix = ImportedPhotoStaging.BARCODE_PREFIX,
            )
        }

        if (!generation.isCurrent(work)) {
            // A newer photograph (or a departure) superseded this one mid-copy. The file is this
            // import's alone — whether staged here or handed over by the share — so deleting it is
            // cleanup rather than a race with the newer import, and it is what stops an abandoned
            // share being left in `cacheDir`.
            (staged as? ImportedImageResolver.Result.Ready)?.file?.delete()
            return@LaunchedEffect
        }

        when (staged) {
            is ImportedImageResolver.Result.Failed -> {
                // Every staging failure and an unreadable photograph are one statement to the user:
                // no usable barcode came out of that picture. Splitting "too large" from "no
                // barcode" would name a cause they act on identically.
                OcrDiagnosticsLogger.timing("barcode photo staging failed (${staged.reason})")
                state = BarcodePhotoImportState.NoBarcode
            }
            is ImportedImageResolver.Result.Ready -> {
                val outcome = try {
                    ImportedBarcodeReader.read(context, staged.file)
                } finally {
                    // Read once, then gone — the single disposal this path promises. Unlike a label
                    // capture, nothing downstream takes ownership of this file (there is no crop
                    // screen and no evidence record), and that is equally true of a shared image:
                    // it is the same file the Activity staged, so deleting it here is what keeps a
                    // successful share from leaving a `justthecarbs-shared-*` behind.
                    staged.file.delete()
                }

                // Re-asked after recognition, not only after staging: ML Kit is the slow half, so
                // this is the check a second photograph is most likely to overtake.
                if (!generation.isCurrent(work)) return@LaunchedEffect

                when (outcome) {
                    ImportedBarcodeSelection.Outcome.None ->
                        state = BarcodePhotoImportState.NoBarcode

                    is ImportedBarcodeSelection.Outcome.Single -> {
                        // Straight through, with no confirmation step: choosing the photograph is
                        // the deliberate act the live path's geometry and hold gates exist to
                        // infer, so re-asking would add a tap that answers nothing.
                        state = BarcodePhotoImportState.Idle
                        // Nothing may follow this import, and the camera must not resume behind
                        // the destination now replacing this screen.
                        generation.invalidate()
                        currentOnBarcode(outcome.value)
                    }

                    is ImportedBarcodeSelection.Outcome.Choice ->
                        state = BarcodePhotoImportState.Choosing(outcome.values)
                }
            }
        }
    }

    return remember(generation) {
        BarcodePhotoImport(
            stateProvider = { state },
            chooseAction = {
                // A fresh pick must be able to re-deliver the same photograph the user chose
                // before, so the consumed token is cleared as the picker opens: the replay guard
                // exists to stop the *launcher* re-delivering by itself, never to stop a user
                // deliberately choosing the same picture twice.
                consumedPhotoToken = null
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            selectAction = { barcode ->
                state = BarcodePhotoImportState.Idle
                generation.invalidate()
                currentOnBarcode(barcode)
            },
            dismissAction = {
                // Back to the live camera with nothing changed — the same outcome cancelling the
                // picker has, and the reason dismissing a choice is a real answer rather than a
                // way of being asked again.
                generation.invalidate()
                state = BarcodePhotoImportState.Idle
                currentOnResume()
            },
        )
    }
}

/**
 * The actions and state of an in-progress photo import, handed to whichever branch is rendering.
 *
 * A class rather than a bundle of lambdas so the call sites read as one thing with a lifetime, and
 * so `state` is read through a provider — reading it as a captured value would freeze it at the
 * composition that built this object.
 */
internal class BarcodePhotoImport(
    private val stateProvider: () -> BarcodePhotoImportState,
    private val chooseAction: () -> Unit,
    private val selectAction: (String) -> Unit,
    private val dismissAction: () -> Unit,
) {
    val state: BarcodePhotoImportState get() = stateProvider()

    fun choosePhoto() = chooseAction()

    fun select(barcode: String) = selectAction(barcode)

    fun dismiss() = dismissAction()
}

/**
 * The two surfaces an import can put on screen: the choice sheet and the no-barcode recovery.
 *
 * Shared by both permission branches so the photograph behaves identically whether or not a camera
 * is available — the only difference is what the dismissing action can offer, which is what
 * [cameraAvailable] decides.
 */
@Composable
private fun BarcodePhotoImportSurfaces(
    import: BarcodePhotoImport,
    cameraAvailable: Boolean,
    onEnterManually: () -> Unit,
) {
    when (val current = import.state) {
        BarcodePhotoImportState.Idle, BarcodePhotoImportState.Reading -> Unit

        is BarcodePhotoImportState.Choosing -> ImportedBarcodeSheet(
            barcodes = current.barcodes,
            onSelect = import::select,
            onDismiss = import::dismiss,
        )

        // An OPAQUE surface, and that is not cosmetic. RecoveryPanel draws no background of its
        // own and colours its text `onSurface`, which is correct over a screen background and
        // illegible over a live camera preview — the first build of this put the title, both
        // buttons and the scan frame on top of each other, readable in neither direction. The
        // camera's own `cameraFailed` branch gets away with a bare panel only because it returns
        // before the preview is composed at all; this state renders *over* a running preview.
        //
        // It also stops the preview reading as still-live while the app is showing a result about
        // a photograph, which is the same reason the analyzer is paused underneath it.
        BarcodePhotoImportState.NoBarcode -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Consumes every tap that misses the panel's own controls. Without it the camera
                // chrome underneath stays reachable — measured on the device, the torch really was
                // tappable through this surface — so a tap aimed at the recovery could toggle a
                // flashlight for a camera the user is not currently looking at. `null` indication
                // and interaction source because this is a barrier, not a control: it must not
                // ripple, and it must not be announced to TalkBack as something to activate.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            RecoveryPanel(
                title = stringResource(R.string.scanner_photo_no_barcode),
                body = null,
                modifier = Modifier.fillMaxSize().padding(top = 120.dp),
            ) {
                Button(
                    onClick = import::choosePhoto,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                    shape = RoundedCornerShape(Space.buttonRadius),
                ) { Text(stringResource(R.string.scanner_photo_choose_another)) }

                // Typing the digits printed under the bars is the route that still works when a
                // photograph does not, and it reaches the real product rather than asking the user
                // to transcribe a nutrition panel. Preserved from the live-preview chrome.
                OutlinedButton(
                    onClick = { import.dismiss(); onEnterManually() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                    shape = RoundedCornerShape(Space.buttonRadius),
                ) { Text(stringResource(R.string.scanner_enter_manually)) }

                TextButton(
                    onClick = import::dismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouchTarget),
                ) {
                    Text(
                        stringResource(
                            if (cameraAvailable) R.string.ocr_scan_again else R.string.action_close,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    hapticsEnabled: Boolean,
    onBarcode: (String) -> Unit,
    onManualBarcode: (String) -> Unit,
    onClose: () -> Unit,
    onEnterManually: () -> Unit,
    /** A shared image to import as the screen appears. See [ScannerScreen]'s parameter. */
    sharedImage: ImportedImageSource? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    var showBarcodeDialog by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    // Which of the two guidance lines to show (§4). Only ever these two: the scanner has one reason
    // to be waiting that the user can act on — aim it, then hold it.
    var holdSteady by remember { mutableStateOf(false) }

    /**
     * Set the instant a barcode is accepted, and never cleared.
     *
     * The screen used to call `onBarcode` and then keep rendering an unchanged live camera with
     * "Point the barcode inside the frame" still on it, for as long as the product lookup took. The
     * scan had succeeded and nothing on screen said so, which reads as the app having missed it —
     * so the user keeps holding the phone at the shelf, or re-aims, or taps.
     *
     * One-way on purpose: acceptance is already latched by an `AtomicBoolean` in the analyzer, so
     * there is no path back to scanning from here. Navigation away is what ends this state.
     */
    var acquired by remember { mutableStateOf(false) }

    // The analyzer below is `remember`ed once, unkeyed, and lives for the composable's whole
    // lifetime — recreating it on every recomposition would tear down and rebuild the ML Kit
    // client mid-scan. Its closure would otherwise capture `hapticsEnabled`, `haptics` and
    // `onBarcode` from whichever composition happened to be current when the analyzer was first
    // created, so a settings change after that point (or any other reason these values might
    // change) would silently keep firing against the stale ones. `rememberUpdatedState` gives the
    // closure a stable reference that always reads the latest value without ever recreating the
    // analyzer itself.
    val currentHapticsEnabled by rememberUpdatedState(hapticsEnabled)
    val currentHaptics by rememberUpdatedState(haptics)
    val currentOnBarcode by rememberUpdatedState(onBarcode)

    val ended = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var providerOwned by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var useCases by remember { mutableStateOf<List<androidx.camera.core.UseCase>>(emptyList()) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        BarcodeAnalyzer { acceptance ->
            if (ended.get()) return@BarcodeAnalyzer
            when (acceptance) {
                is BarcodeAcceptance.Accepted -> {
                    // A short haptic confirms the read without the user having to look away from
                    // the package (§41). Restrained: this is one of only two places the app
                    // vibrates — the label scanner's shutter capture is the other. Compose's
                    // abstraction is used rather than HapticFeedbackConstants.CONFIRM, which needs
                    // API 30 and would be silently inlined as an unsupported constant on minSdk 26.
                    if (currentHapticsEnabled) {
                        currentHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    ended.set(true)
                    acquired = true
                    currentOnBarcode(acceptance.value)
                }
                BarcodeAcceptance.Stabilizing -> holdSteady = true
                BarcodeAcceptance.Searching -> holdSteady = false
            }
        }
    }

    // Photo import, sharing this screen's one-shot barcode callback. The analyzer is paused for
    // the whole import and resumed only if the user comes back empty-handed, so a live detection
    // and a photo result can never both reach `onBarcode`.
    val photoImport = rememberBarcodePhotoImport(
        onBarcode = { code ->
            // Through the same teardown a live acceptance performs: the session is ended and the
            // ML Kit client closed before the destination replaces this screen, so no frame
            // analysed in the meantime can fire a second navigation.
            if (!ended.getAndSet(true)) {
                analyzer.close()
                acquired = true
                currentOnBarcode(code)
            }
        },
        onPauseCamera = { analyzer.setPaused(true); holdSteady = false },
        // `setPaused(false)` also resets the stability tracker, so returning to the camera starts
        // counting frames afresh rather than resuming a count begun before the photograph.
        onResumeCamera = { analyzer.setPaused(false) },
        sharedImage = sharedImage,
    )

    val leave: (() -> Unit) -> Unit = { action ->
        ended.set(true)
        analyzer.close()
        action()
    }
    // Deliberately not migrated to PredictiveBackHandler in the 2026-09-14 interaction pass —
    // camera/executor disposal ordering here needs its own dedicated audit; see
    // docs/superpowers/specs/2026-09-14-interaction-polish-design.md.
    // Back closes whatever the import put on screen before it closes the scanner: pressing back
    // on a "which barcode?" sheet means "not these", not "leave the app's scanner".
    BackHandler(enabled = !showBarcodeDialog) {
        if (photoImport.state == BarcodePhotoImportState.Idle) {
            leave(onClose)
        } else {
            photoImport.dismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ended.set(true)
            analyzer.close()
            providerOwned?.unbind(*useCases.toTypedArray())
            executor.shutdown()
        }
    }

    if (cameraFailed) {
        RecoveryPanel(
            title = stringResource(R.string.scanner_unavailable),
            body = null,
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
        ) {
            // Same ordering as the permission rationale, for the same reason: a camera that failed
            // to bind (in use by another app, absent, a vendor fault) leaves the printed digits
            // perfectly readable, and typing them reaches the real product rather than asking the
            // user to transcribe a nutrition panel.
            // No `analyzer.setPaused(true)` here, unlike the live-preview button: the camera
            // failed to bind, so nothing is analysing frames to pause.
            Button(
                onClick = { showBarcodeDialog = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.scanner_enter_manually)) }
            OutlinedButton(
                onClick = { leave(onEnterManually) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.permission_manual)) }
            TextButton(onClick = { leave(onClose) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_close))
            }
        }

        // Rendered inside the failure branch too, which returns early — without this the sheet
        // opened from the button above would never be composed.
        if (showBarcodeDialog) {
            ManualBarcodeSheet(
                // Still through `leave`, which closes the analyzer and marks the session ended —
                // the camera never bound, but the analyzer object exists and owns an ML Kit client.
                onConfirm = { code -> leave { onManualBarcode(code) } },
                onDismiss = { showBarcodeDialog = false },
            )
        }
        return
    }

    if (showBarcodeDialog) {
        ManualBarcodeSheet(
            onConfirm = { code -> leave { onManualBarcode(code) } },
            onDismiss = { showBarcodeDialog = false; analyzer.setPaused(false) },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)

                providerFuture.addListener({
                    if (ended.get()) return@addListener
                    try {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            // Dropping stale frames keeps detection on what the camera sees now,
                            // which is what makes the scanner feel instant (§62).
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(executor, analyzer) }

                        providerOwned = provider
                        useCases = listOf(preview, analysis)
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        torchAvailable = camera?.cameraInfo?.hasFlashUnit() == true
                    } catch (_: Exception) {
                        // No camera, camera in use by another app, or a vendor failure. Manual
                        // entry is always still available (§9, §36).
                        cameraFailed = true
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
        )

        ScanFrame(acquired = acquired, modifier = Modifier.align(Alignment.Center))


        // Top row: close only. Nothing essential lives up here (§40).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = Space.s, vertical = Space.s),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScrimIconButton(
                onClick = { leave(onClose) },
                icon = Icons.Filled.Close,
                description = stringResource(R.string.scanner_close),
            )
            Spacer(Modifier.width(Space.s))
            Text(
                text = stringResource(R.string.home_scan_button),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.m, vertical = Space.s)
                .background(Color.Black.copy(alpha = 0.66f), RoundedCornerShape(Space.cardRadius))
                .padding(Space.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val reading = photoImport.state == BarcodePhotoImportState.Reading
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (acquired || reading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                }
                Text(
                    text = stringResource(
                        when {
                            // Names the work actually in progress rather than a bare spinner, so
                            // the wait is attributable to something (§2).
                            acquired -> R.string.scanner_finding_product
                            // Said here as well as on the button, because this line is the screen's
                            // polite live region: it is what announces to a TalkBack user that the
                            // photograph is being read, and what tells a sighted user why the
                            // preview has stopped responding to what the camera sees.
                            reading -> R.string.scanner_photo_reading
                            holdSteady -> R.string.scanner_hint_steady
                            else -> R.string.scanner_hint_aim
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    // Announced on change so a TalkBack user hears the scan land, rather than the
                    // screen going silent until the next destination arrives.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(Space.m))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // The second way in, in the same disc treatment the torch and the label scanner's
                // own gallery action use — one 48dp scrim button among others, so the two scanners
                // read as a matched pair and the camera stays the dominant action. It sits before
                // the torch because it is a way of scanning rather than a way of adjusting the
                // camera, and it is present whether or not the torch is.
                ChoosePhotoButton(
                    reading = photoImport.state == BarcodePhotoImportState.Reading,
                    onChoose = photoImport::choosePhoto,
                    description = stringResource(
                        if (photoImport.state == BarcodePhotoImportState.Reading) {
                            R.string.scanner_photo_reading
                        } else {
                            R.string.scanner_choose_photo
                        },
                    ),
                )
                Spacer(Modifier.width(Space.m))

                if (torchAvailable && !acquired) {
                    ScrimIconButton(
                        onClick = {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        },
                        icon = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        description = stringResource(
                            if (torchOn) R.string.scanner_torch_off else R.string.scanner_torch_on,
                        ),
                    )
                    Spacer(Modifier.width(Space.m))
                }

                // §8's "optional manual barcode entry" — genuinely a barcode field now. It
                // previously jumped straight to manual product entry, so the control did not do
                // what its label said.
                TextButton(
                    onClick = { analyzer.setPaused(true); holdSteady = false; showBarcodeDialog = true },
                    // A lookup is already under way and this screen is about to be replaced;
                    // opening the manual dialog on top of it would start a second, competing one.
                    // Same reasoning while a photograph is being read: that import may navigate.
                    enabled = !acquired && !reading,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier
                        .heightIn(min = Space.minTouchTarget)
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.32f),
                            RoundedCornerShape(Space.buttonRadius),
                        )
                        .background(
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(Space.buttonRadius),
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.scanner_enter_manually),
                        color = Color.White.copy(alpha = if (acquired || reading) 0.38f else 1f),
                    )
                }
            }
        }

        // LAST child of this Box, deliberately. Compose paints and hit-tests siblings in
        // declaration order, so rendered earlier these surfaces sat *under* the camera chrome:
        // measured on the device, the torch was still tappable straight through the no-barcode
        // recovery, which meant a tap aimed at the panel could toggle a flashlight for a camera
        // the user had stopped looking at. Declared last, the recovery's own opaque surface is
        // what receives the tap.
        BarcodePhotoImportSurfaces(
            import = photoImport,
            cameraAvailable = true,
            onEnterManually = { showBarcodeDialog = true },
        )
    }
}

/**
 * The scan frame (§8), now a functional target rather than decoration.
 *
 * [BarcodeStabilityTracker] refuses a barcode whose centre sits outside the middle of the frame, so
 * this rectangle finally means what it always looked like it meant. It stays a *hint*: acceptance
 * needs the barcode's centre inside a generous central region, never the whole box squeezed in here,
 * which is why the drawn frame is narrower than the region that actually gates the scan.
 */
@Composable
private fun ScanFrame(acquired: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    // The frame is the one element already holding the user's gaze, so it is where the "got it"
    // belongs — no new overlay, no toast, nothing that covers the preview. The fill deepens and a
    // tick appears; the change is confirmation, not decoration, so it is a single short crossfade
    // rather than anything that delays the result behind an animation.
    val fillAlpha by animateFloatAsState(
        targetValue = if (acquired) 0.24f else 0.04f,
        animationSpec = tween(Motion.QUICK_MS),
        label = "scanFrameFill",
    )
    // A brief outward pulse on acceptance only — never on the resting/searching state, and never
    // repeating. Existing infra (the fill/check-icon above) already says "got it"; this adds a
    // small sense of the frame actually reacting to the moment of acceptance rather than merely
    // switching state.
    val scale by animateFloatAsState(
        targetValue = if (acquired) 1.03f else 1f,
        animationSpec = tween(Motion.STANDARD_MS),
        label = "scanFrameScale",
    )
    Box(
        modifier = modifier
            .fillMaxWidth(0.68f)
            .height(176.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(accent.copy(alpha = fillAlpha), RoundedCornerShape(Space.cardRadius))
            .border(2.dp, accent, RoundedCornerShape(Space.cardRadius)),
        contentAlignment = Alignment.Center,
    ) {
        // Never colour alone (§39): the hint line below states the same fact in words, and the tick
        // carries no content description because it would duplicate that line for TalkBack.
        if (acquired) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

@Composable
private fun ScrimIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(Space.minTouchTarget)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
            .semantics { contentDescription = description },
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
    }
}
