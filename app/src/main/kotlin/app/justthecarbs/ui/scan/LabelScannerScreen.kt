package app.justthecarbs.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.justthecarbs.R
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.ServingDescriptor
import app.justthecarbs.ocr.CarbCandidate
import app.justthecarbs.ocr.EvidenceResolver
import app.justthecarbs.ocr.LabelAnalyzer
import app.justthecarbs.ocr.LabelReading
import app.justthecarbs.ocr.LiveEvidenceBuffer
import app.justthecarbs.ocr.NormalizedRegion
import app.justthecarbs.ocr.PassAResult
import app.justthecarbs.ocr.ScanEvidenceRecorder
import app.justthecarbs.ocr.ScanRegionMapper
import app.justthecarbs.ocr.SelectedTableResolution
import app.justthecarbs.ocr.ServingCarbCandidate
import app.justthecarbs.ocr.TextResolutionGuidance
import app.justthecarbs.ocr.OcrDiagnosticsLogger
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.product.kindLabel
import app.justthecarbs.ui.theme.Space
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Where an accepted OCR portion has got to (correction pass §2).
 *
 * The states are distinct because the previous code had only one — a `savedPortionUnit` boolean set
 * synchronously, on the line after firing an asynchronous save. A Room insert that then failed left
 * the user reading "Saved" about a portion that does not exist. Success is now something only the
 * persistence layer can report.
 */
sealed interface PortionSaveState {
    /** Nothing accepted yet; the save action is offered. */
    data object Idle : PortionSaveState

    /** The write is in flight. The action is disabled so a second tap cannot duplicate it. */
    data object Saving : PortionSaveState

    /** Persisted. Only ever set from a completed write. */
    data object Saved : PortionSaveState

    /**
     * The write failed. Says so plainly and leaves the action available to retry — the one thing the
     * old boolean could not express, and the reason it silently reported success.
     */
    data object Failed : PortionSaveState

    /**
     * Held for a product that does not exist yet, to be saved once it is created (correction §2).
     *
     * The barcode was scanned but Open Food Facts did not know it, so there is no `products` row for
     * the portion's foreign key to reference. The accepted portion travels into manual entry instead
     * of being written now and failing.
     */
    data object PendingProductCreation : PortionSaveState
}

/** Nutrition-table OCR camera. It proposes values; it never commits one without a tap. */
@Composable
fun LabelScannerScreen(
    onUseValue: (BigDecimal, NutritionBasis) -> Unit,
    onEditManually: () -> Unit,
    onClose: () -> Unit,
    /**
     * *Correct* — manual entry opened with the detected figure already in the field (§3).
     *
     * Distinct from [onEditManually], which starts from an empty field. When OCR reads `40.3` and
     * the package says `48.3`, the user is fixing one digit, not transcribing a number; sending them
     * to a blank field makes them re-read the package the app just photographed. Defaults to
     * [onEditManually] so a caller that has nowhere to put a pre-filled value degrades to the
     * previous behaviour rather than losing the action.
     */
    onCorrectValue: (BigDecimal, NutritionBasis) -> Unit = { _, _ -> onEditManually() },
    /**
     * Persists the accepted portion, returning true only once it is genuinely on disk.
     *
     * Suspending, and its result is what drives [PortionSaveState] — the UI cannot report success
     * before persistence completes. Null when there is no product to attach a countable unit to at
     * all (spec §17).
     */
    onSavePortionUnit: (suspend (PortionUnitKind, PortionConversion) -> Boolean)? = null,
    /**
     * Carries an accepted portion into product creation when no product row exists yet (§2).
     *
     * Null when the product already exists, which is what selects the direct-save path above.
     */
    onCarryPendingPortionUnit: ((PortionUnitKind, PortionConversion) -> Unit)? = null,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            LabelCamera(
                onUseValue = onUseValue,
                onEditManually = onEditManually,
                onCorrectValue = onCorrectValue,
                onClose = onClose,
                onSavePortionUnit = onSavePortionUnit,
                onCarryPendingPortionUnit = onCarryPendingPortionUnit,
            )
        } else {
            LabelPermissionRationale(
                showAllow = !permissionRequested,
                onAllow = { launcher.launch(Manifest.permission.CAMERA) },
                onEditManually = onEditManually,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun LabelCamera(
    onUseValue: (BigDecimal, NutritionBasis) -> Unit,
    onEditManually: () -> Unit,
    onCorrectValue: (BigDecimal, NutritionBasis) -> Unit,
    onClose: () -> Unit,
    onSavePortionUnit: (suspend (PortionUnitKind, PortionConversion) -> Boolean)? = null,
    onCarryPendingPortionUnit: ((PortionUnitKind, PortionConversion) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val saveScope = rememberCoroutineScope()

    /**
     * The authoritative reading. Set ONLY from a still capture — never from a live frame.
     */
    var reading by remember { mutableStateOf<LabelReading?>(null) }

    /**
     * The most recent live-frame interpretation, used purely to tell the user whether the table
     * currently looks readable. It can never become the result.
     */
    var liveReadiness by remember { mutableStateOf<LabelReading?>(null) }
    /**
     * Whether the nutrition print is physically large enough in the frame to be worth capturing.
     *
     * Advisory, and on a separate channel from [liveReadiness] so camera advice and a value-bearing
     * reading cannot be confused for one another. It never gates the shutter — the user may capture
     * whenever they like, and a wrong "move closer" must not stand between them and their answer.
     */
    var framing by remember { mutableStateOf<TextResolutionGuidance.Estimate?>(null) }
    /**
     * A per-serving figure from the **still** capture only (spec §17).
     *
     * Live frames never set this: a countable portion is persisted only from a deliberate capture
     * the user then explicitly accepts, so a passing camera frame cannot save anything.
     */
    var servingCandidate by remember { mutableStateOf<ServingCarbCandidate?>(null) }
    /**
     * How far the accepted portion has got (correction pass §2).
     *
     * Never set to [PortionSaveState.Saved] except from a completed write.
     */
    var portionSaveState by remember { mutableStateOf<PortionSaveState>(PortionSaveState.Idle) }
    var captureState by remember { mutableStateOf(CaptureState.IDLE) }

    /**
     * The frozen capture awaiting the user's crop confirmation, or null while the camera is live.
     *
     * Holding the whole [app.justthecarbs.ocr.PassAResult] rather than just the bitmap is what makes
     * "Read table" cost no second recognition: the retained document is re-filtered and re-parsed in
     * memory.
     */
    var pendingCrop by remember { mutableStateOf<PassAResult?>(null) }
    var cropSelection by remember { mutableStateOf<NormalizedRegion?>(null) }
    /** True while a confirmed crop is being re-parsed; disables the primary action. */
    var readingTable by remember { mutableStateOf(false) }
    /**
     * A value one pass found that nothing corroborated (§8).
     *
     * Shown on the frozen photograph as "check this against the label", never as a settled answer.
     * Re-recognition is measured to recover correct values *and* to invent wrong ones, so an
     * uncorroborated reading may be proposed but must not decide.
     */
    var verification by remember { mutableStateOf<EvidenceResolver.Outcome.NeedsVerification?>(null) }
    /** Two passes disagreed. The app must not choose; it says so and offers the assisted path. */
    var conflicted by remember { mutableStateOf<EvidenceResolver.Outcome.Conflicted?>(null) }
    /**
     * The assisted fallback (§17-§19): the frozen table stays up and the user points at the answer.
     *
     * Non-null means automatic recognition is finished and did not produce a usable result. This is
     * what makes "Couldn't confidently find carbohydrates" stop being a dead end.
     */
    var assisting by remember { mutableStateOf<AssistState?>(null) }
    /**
     * Retains stable pre-shutter live interpretations (§5, §9).
     *
     * The physical recording shows live reaching a usable reading immediately before a capture whose
     * still path then fails. That evidence used to be discarded; it is now available to corroborate
     * the still reading or to be offered for verification. It can never resolve a scan alone.
     */
    val liveEvidence = remember { LiveEvidenceBuffer() }
    /**
     * Identifies the current capture attempt.
     *
     * Recognition of an 8 MP still takes seconds, during which the user can tap Retake. Without this
     * the earlier capture's result would arrive afterwards and replace the newer one — a race that is
     * invisible in testing because it needs a human to be impatient at the wrong moment. Every
     * asynchronous result is checked against the session it belongs to and dropped if it is stale.
     */
    val captureSession = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var torchAvailable by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }

    /**
     * The scan region the user is framing the table in, as fractions of the preview (§10).
     *
     * Measured from the overlay's own laid-out bounds rather than recomputed from the constants that
     * position it — the two would silently disagree the moment either changed, and nothing on screen
     * would show it. Null until the first layout pass, which makes the first capture read the whole
     * frame rather than a guessed rectangle.
     */
    val scanRegion = remember { AtomicReference<NormalizedRegion?>(null) }
    val pendingCapture = remember { AtomicReference<File?>(null) }
    val cameraProvider = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val disposed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    /**
     * CAPTURE-FIRST: a live frame reports READINESS, never the answer (§ capture-first design).
     *
     * Previously this wrote straight into `reading`, and `LaunchedEffect(reading)` then paused the
     * analyzer — so the first 1280x720 frame that produced any interpretation latched the UI into a
     * result card. Because the primary capture button lives only inside `SearchingCard`, which is
     * rendered only while `reading == null`, the user frequently never reached the 8 MP capture path
     * at all: the app answered from an analysis frame of a table they were still aiming.
     *
     * The live reading now only tells the user whether the table looks readable. The authoritative
     * value comes exclusively from [captureLabel] -> `analyzeStill`, which is the high-resolution,
     * focused, uncropped path.
     */
    val analyzer = remember {
        LabelAnalyzer(
            onReading = { result ->
                mainExecutor.execute {
                    liveReadiness = result
                    // Retained, not acted on. Nothing downstream reads this until AFTER a deliberate
                    // capture, and even then only through EvidenceResolver, which never lets a live
                    // frame resolve a scan by itself.
                    liveEvidence.record(result, System.currentTimeMillis())
                }
            },
            onFraming = { estimate -> mainExecutor.execute { framing = estimate } },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            // Invalidates in-flight recognition so a result cannot arrive after the screen is gone.
            captureSession.incrementAndGet()
            cameraProvider.getAndSet(null)?.unbindAll()
            analyzer.close()
            pendingCapture.getAndSet(null)?.delete()
            pendingCrop?.recycle()
            executor.shutdown()
        }
    }

    // Live analysis is paused once a CAPTURED result is on screen — there is nothing for framing
    // guidance to do while the user is reading an answer, and the frames would cost battery. It is
    // deliberately NOT paused merely because a live frame produced an interpretation: that was the
    // latch which stopped users ever reaching the capture path.
    LaunchedEffect(reading) {
        if (reading != null) analyzer.pause() else analyzer.resume()
    }

    fun resumeLive() {
        // Bumping the session first is what makes this a cancellation and not merely a reset: any
        // recognition still in flight will now find its id stale and discard its own result.
        captureSession.incrementAndGet()
        pendingCrop?.recycle()
        pendingCrop = null
        cropSelection = null
        readingTable = false
        reading = null
        liveReadiness = null
        framing = null
        servingCandidate = null
        portionSaveState = PortionSaveState.Idle
        captureState = CaptureState.IDLE
        // Every derived state from the abandoned capture goes with it (§25 session/race). Leaving any
        // of these set would let a previous package's proposal appear over a new capture.
        verification = null
        conflicted = null
        assisting = null
        liveEvidence.clear()
        analyzer.resume()
    }

    /** Releases the frozen capture and its bitmap, returning the screen to the result cards. */
    fun releaseCapture(captured: PassAResult) {
        captured.recycle()
        pendingCrop = null
        cropSelection = null
    }

    /**
     * Applies the confirmed rectangle, gathering every available recognition as evidence (§2, §4, §8).
     *
     * Two strategies now run against the same rectangle rather than one:
     *
     * - **Strategy A** filters Pass A's elements and re-parses. Cheap, and structurally incapable of
     *   inventing a character Pass A did not read.
     * - **Strategy B** recognises the selected region afresh from the upright source bitmap at native
     *   resolution. This is what can recover a *recognition* failure, which no amount of filtering
     *   can — measured on the real corpus to rescue `NotFound` -> `2.3` on one fixture and to correct
     *   a known-wrong `2.09` -> the printed `2` on another.
     *
     * Neither overwrites the other. [EvidenceResolver] corroborates them, proposes an uncorroborated
     * value for explicit verification, or refuses outright when they disagree — because on the same
     * corpus re-recognition *also* produced values that were confidently wrong.
     *
     * Runs off the main thread: Strategy B is a real ML Kit pass and would jank the frozen photo.
     */
    fun readSelectedTable(region: NormalizedRegion) {
        val captured = pendingCrop ?: return
        val session = captureSession.get()
        readingTable = true

        saveScope.launch {
            // Dispatchers.IO, not Default. Strategy B waits on a `CountDownLatch` for ML Kit to call
            // back, and Default is a CPU-count-sized pool meant for work that never blocks — parking
            // one of its threads for up to the recognition timeout starves every other Default
            // consumer on the device for that whole period. IO exists for exactly this shape of wait.
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                SelectedTableResolution.resolve(
                    passA = captured,
                    region = region,
                    bitmap = captured.bitmap,
                    liveEvidence = liveEvidence.asEvidence(System.currentTimeMillis()),
                )
            }

            // The same stale-result guard the capture path uses: a Retake during a second recognition
            // pass must not have its answer arrive afterwards and replace the new capture's.
            if (session != captureSession.get()) return@launch

            OcrDiagnosticsLogger.selectedTable(
                outcome = result.filtered.outcome.name,
                elementsBefore = result.filtered.elementsBefore,
                elementsAfter = result.filtered.elementsAfter,
                elapsedMs = result.elapsedMs,
                reading = result.filtered.report.reading,
            )

            readingTable = false
            when (val outcome = result.outcome) {
                is EvidenceResolver.Outcome.Resolved -> {
                    releaseCapture(captured)
                    reading = outcome.reading
                    servingCandidate = outcome.report.servingCandidate
                }
                is EvidenceResolver.Outcome.NeedsVerification -> {
                    // Held on the frozen photo on purpose: the user is looking at the printed table,
                    // which is the only place this can actually be checked.
                    verification = outcome
                }
                is EvidenceResolver.Outcome.Conflicted -> {
                    conflicted = outcome
                }
                EvidenceResolver.Outcome.Nothing -> {
                    // The scan is not over. The frozen capture stays on screen and the user is
                    // offered the assisted path, which is what removes the dead end (§17-§19).
                    assisting = AssistState(
                        document = captured.document,
                        ineffectiveSelection = result.selectionWasIneffective,
                    )
                }
            }

            // Written only after the outcome is on screen. It used to run between the resolution and
            // the `when` above, which put a debug-only file write — every retained and rejected
            // element, with geometry — directly on the critical path of the "Read table" tap, the
            // second half of the very scan these bundles exist to time. Nothing here can affect the
            // reading; `releaseCapture` recycles only the bitmap, and this reads the folder handle
            // and the document, both of which outlive it.
            ScanEvidenceRecorder.recordSelection(
                folder = captured.evidence,
                region = region,
                document = captured.document,
                outcome = result.filtered.outcome.name,
                elementsBefore = result.filtered.elementsBefore,
                elementsAfter = result.filtered.elementsAfter,
                report = result.filtered.report,
            )
        }
    }

    fun takePictureNow(capture: ImageCapture, file: File) {
        val session = captureSession.get()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        // Image acquisition — shutter press to JPEG on disk — is the one stage of the scan that
        // happens entirely outside `analyzeStill`, so `ScanTrace` cannot see it and the device
        // evidence bundles had a hole exactly where sensor readout, JPEG encode and file write live.
        // Monotonic, for the same reason ScanTrace is: a wall clock can jump mid-capture.
        val shutterNanos = android.os.SystemClock.elapsedRealtimeNanos()
        capture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    OcrDiagnosticsLogger.timing(
                        "acquisition " +
                            "${(android.os.SystemClock.elapsedRealtimeNanos() - shutterNanos) / 1_000_000}ms " +
                            "(shutter to file)",
                    )
                    mainExecutor.execute { captureState = CaptureState.PROCESSING }
                    // Recognition starts immediately and runs while the user is looking at the frozen
                    // photo and adjusting the rectangle, so the "Read table" tap costs only a
                    // re-parse of elements already in memory rather than a second ML Kit pass.
                    analyzer.analyzeStillRetaining(context, file, session) { result ->
                        pendingCapture.compareAndSet(file, null)
                        mainExecutor.execute {
                            // The stale-result guard. A Retake bumps the session, so a result from
                            // the abandoned capture is released rather than shown.
                            if (result.sessionId != captureSession.get()) {
                                result.recycle()
                                return@execute
                            }
                            captureState = CaptureState.IDLE
                            if (result.bitmap == null) {
                                // Nothing to show a crop of; report the whole-frame outcome, which
                                // is the pre-crop behaviour and always safe.
                                reading = result.report.reading
                                servingCandidate = result.report.servingCandidate
                            } else {
                                // The scan guide the user was aiming with, widened by the same
                                // safety margin the old crop used.
                                //
                                // An automatic table-detecting proposal was built and MEASURED
                                // AGAINST THE REAL CORPUS, and it is not in this code because it
                                // failed: it damaged two of four canaries, in two different ways,
                                // and four successive fixes each moved the failure rather than
                                // removing it. On kinder it cut the value columns off; on yoghurt it
                                // proposed 77% of the frame, contained the winning candidate
                                // entirely, and STILL lost the reading because the removed elements
                                // included the basis header. No geometric measure available here
                                // separates that from a good proposal.
                                //
                                // This is the same problem three earlier localisation attempts were
                                // measured and rejected for. The scan guide is a better starting
                                // point precisely because it is not a guess about the table — it is
                                // where the user was already pointing.
                                cropSelection = ScanRegionMapper.expand(
                                    scanRegion.get() ?: DEFAULT_CROP,
                                )
                                pendingCrop = result
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    OcrDiagnosticsLogger.failure("Label capture failed", exception)
                    pendingCapture.compareAndSet(file, null)
                    file.delete()
                    mainExecutor.execute {
                        if (session != captureSession.get()) return@execute
                        captureState = CaptureState.IDLE
                        reading = LabelReading.NotFound
                    }
                }
            },
        )
    }

    /**
     * Focus on the scan region, then capture — with a bounded wait so the shutter never hangs.
     *
     * Before this, capture fired on whatever autofocus state happened to exist when the button was
     * tapped: there was no `FocusMeteringAction` anywhere in the app. For print a few millimetres
     * high that is a large source of variance, and it is invisible in fixtures because every fixture
     * is a photograph a human already focused.
     *
     * AF, AE and AWB are all requested on the framed region: a nutrition table is usually a bright
     * white panel, and metering exposure on it rather than the whole scene is what stops a glossy
     * package blowing out the print.
     *
     * The timeout is the important part of the contract. `startFocusAndMetering` may never complete
     * on a low-contrast surface, so the capture is fired either when focus reports back or when
     * [FOCUS_TIMEOUT_MS] elapses — whichever is first, exactly once, guarded by an
     * `AtomicBoolean`. A missed focus costs a slightly softer photograph; a hung shutter costs the
     * feature.
     */
    fun focusThenCapture(capture: ImageCapture, file: File) {
        val cameraControl = camera?.cameraControl
        val region = scanRegion.get()
        if (cameraControl == null || region == null) {
            takePictureNow(capture, file)
            return
        }

        val fired = java.util.concurrent.atomic.AtomicBoolean(false)
        fun fireOnce() {
            if (fired.compareAndSet(false, true)) takePictureNow(capture, file)
        }

        val focusResult = runCatching {
            // SurfaceOrientedMeteringPointFactory works in a normalized 0..1 space, which is exactly
            // what NormalizedRegion already is — so the metering point needs no device-specific
            // arithmetic, for the same reason ScanRegionMapper needs none: the ViewPort binding makes
            // the preview's fractions and the capture's fractions the same fractions.
            val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f)
            val centre = factory.createPoint(
                ((region.left + region.right) / 2.0).toFloat(),
                ((region.top + region.bottom) / 2.0).toFloat(),
                // The metering circle, as a fraction of the smaller frame dimension: the framed
                // table, not a pinpoint, so a single dark glyph cannot drive the whole exposure.
                (minOf(region.width, region.height)).toFloat(),
            )
            cameraControl.startFocusAndMetering(
                androidx.camera.core.FocusMeteringAction.Builder(
                    centre,
                    androidx.camera.core.FocusMeteringAction.FLAG_AF or
                        androidx.camera.core.FocusMeteringAction.FLAG_AE or
                        androidx.camera.core.FocusMeteringAction.FLAG_AWB,
                )
                    // CameraX's own auto-cancel would restart continuous AF mid-capture; the bounded
                    // timeout below is this code's cancellation policy instead.
                    .disableAutoCancel()
                    .build(),
            )
        }.getOrElse {
            OcrDiagnosticsLogger.failure("Could not start focus metering", it)
            fireOnce()
            return
        }

        focusResult.addListener({ fireOnce() }, executor)
        // The bound. Fires the capture even if focus never reports back.
        mainExecutor.execute {
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ fireOnce() }, FOCUS_TIMEOUT_MS)
        }
    }

    fun captureLabel() {
        val capture = imageCapture
        if (capture == null) {
            reading = LabelReading.NotFound
            return
        }

        val file = runCatching { File.createTempFile("justthecarbs-label-", ".jpg", context.cacheDir) }
            .getOrElse {
                OcrDiagnosticsLogger.failure("Could not create temporary label image", it)
                reading = LabelReading.NotFound
                return
            }
        // A new attempt invalidates anything still in flight from the previous one.
        captureSession.incrementAndGet()
        pendingCrop?.recycle()
        pendingCrop = null
        cropSelection = null
        readingTable = false
        analyzer.pause()
        reading = null
        captureState = CaptureState.CAPTURING
        pendingCapture.set(file)
        focusThenCapture(capture, file)
    }

    if (cameraFailed) {
        RecoveryPanel(
            title = stringResource(R.string.scanner_unavailable),
            body = null,
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
        ) {
            Button(
                onClick = onEditManually,
                modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.permission_manual)) }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_close))
            }
        }
        return
    }

    // The frozen capture takes over the whole screen. Returning early rather than overlaying keeps
    // the camera preview from continuing to render underneath, which would be both confusing and a
    // waste of power while the user is looking at a still photograph.
    //
    // The ordering below is the product decision that removes the dead end: assisted mode and the
    // verification prompt both keep the FROZEN PHOTO on screen, because both ask the user to compare
    // a number against the printed table. Dropping back to the camera would take away the one thing
    // they need to answer.
    val frozen = pendingCrop
    val frozenBitmap = frozen?.bitmap
    if (frozen != null && frozenBitmap != null) {
        val assist = assisting
        val proposal = verification
        val conflict = conflicted

        when {
            assist != null -> AssistedReadingScreen(
                bitmap = frozenBitmap,
                state = assist,
                onUseValue = { value, basis ->
                    releaseCapture(frozen)
                    assisting = null
                    onUseValue(value, basis)
                },
                onRetake = ::resumeLive,
                onClose = onClose,
            )

            proposal != null -> VerificationScreen(
                bitmap = frozenBitmap,
                proposal = proposal,
                onConfirm = { value, basis ->
                    releaseCapture(frozen)
                    verification = null
                    onUseValue(value, basis)
                },
                // Rejecting is not a failure: the user is still on the photo and still in the task,
                // so it hands straight to the assisted path rather than dropping them out.
                onReject = {
                    verification = null
                    assisting = AssistState(document = frozen.document)
                },
                onRetake = ::resumeLive,
            )

            conflict != null -> ConflictScreen(
                bitmap = frozenBitmap,
                conflict = conflict,
                onAssist = {
                    conflicted = null
                    assisting = AssistState(document = frozen.document)
                },
                onRetake = ::resumeLive,
            )

            else -> CropConfirmationScreen(
                bitmap = frozenBitmap,
                initialSelection = cropSelection ?: DEFAULT_CROP,
                reading = readingTable,
                onReadTable = ::readSelectedTable,
                onRetake = ::resumeLive,
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                fun bind() {
                    try {
                        val provider = providerFuture.get()
                        cameraProvider.set(provider)
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }
                        val analysisSelector = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build()
                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(analysisSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(executor, analyzer) }
                        // 8 MP rather than the previous 2.7 MP (§12). The figure that has to be read
                        // is a few millimetres of print: on a 1920-wide capture of a package held at
                        // arm's length, "53,5" is a couple of dozen pixels across, which is where
                        // recognition starts guessing. Doubling the linear resolution is the single
                        // cheapest thing available for small text. Not the sensor maximum — a 50 MP
                        // capture costs seconds of latency and a bitmap that will not fit in memory
                        // alongside its crop, for detail well past what ML Kit uses.
                        val stillSelector = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(3264, 2448),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build()
                        val stillCapture = ImageCapture.Builder()
                            .setResolutionSelector(stillSelector)
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        provider.unbindAll()
                        // A ViewPort matched to the preview is what makes the scan region mean
                        // anything (§10). Without it the capture stream (4:3) and the preview
                        // (whatever the screen is) cover different fields of view, so a rectangle
                        // measured on screen is not the same rectangle in the JPEG — and the crop
                        // would be wrong by an amount that varies per device, in a direction nobody
                        // could see. Binding all three use cases through one viewport makes "the
                        // fraction the user framed" identical in all of them, which is why
                        // ScanRegionMapper needs no aspect-ratio arithmetic at all.
                        val useCases = UseCaseGroup.Builder()
                            .addUseCase(preview)
                            .addUseCase(analysis)
                            .addUseCase(stillCapture)
                            .apply { previewView.viewPort?.let { setViewPort(it) } }
                            .build()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            useCases,
                        )
                        imageCapture = stillCapture
                        torchAvailable = camera?.cameraInfo?.hasFlashUnit() == true

                        // What the device ACTUALLY negotiated (§23). The builder above states a
                        // preference; CameraX picks from what the camera supports, and until now
                        // nothing recorded the outcome — which is how a 900x1600 fixture replay was
                        // once mistaken for a physical camera result. `resolutionInfo` is only
                        // populated after binding, so this must stay here rather than at build time.
                        stillCapture.resolutionInfo?.let { info ->
                            val selected = "${info.resolution.width}x${info.resolution.height}"
                            val crop = info.cropRect.toShortString()
                            OcrDiagnosticsLogger.captureConfiguration(
                                requested = "3264x2448",
                                selected = selected,
                                cropRect = crop,
                                rotationDegrees = info.rotationDegrees,
                            )
                            // Also into the exportable evidence bundle, so a physical-device run can
                            // answer §23/§24 from the bundle alone rather than needing a live logcat.
                            ScanEvidenceRecorder.recordCaptureConfiguration(
                                requested = "3264x2448",
                                selected = selected,
                                cropRect = crop,
                                rotation = info.rotationDegrees,
                            )
                        }
                    } catch (error: Exception) {
                        OcrDiagnosticsLogger.failure("Could not bind label camera", error)
                        cameraFailed = true
                    }
                }

                providerFuture.addListener(listener@{
                    if (disposed.get()) return@listener
                    // PreviewView.viewPort is null until the view has been measured, and binding
                    // without it silently gives back the un-aligned fields of view this whole
                    // arrangement exists to avoid — the crop would then be wrong on every device
                    // with nothing on screen to show it. doOnLayout runs immediately when the view
                    // is already laid out, so this costs nothing in the common case.
                    previewView.doOnLayout layout@{
                        if (disposed.get()) return@layout
                        bind()
                    }
                }, mainExecutor)
                previewView
            },
        )

        ScanRegionOverlay(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(Space.l)
                // The region OCR reads is taken from where this actually landed, so the rectangle
                // the user aims at and the rectangle the parser gets cannot drift apart. The parent
                // Box and the PreviewView fill the same space, so the overlay's bounds in that Box
                // are directly fractions of the preview.
                .onGloballyPositioned { coordinates ->
                    val parent = coordinates.parentLayoutCoordinates?.size ?: return@onGloballyPositioned
                    if (parent.width <= 0 || parent.height <= 0) return@onGloballyPositioned
                    val origin = coordinates.positionInParent()
                    val left = origin.x / parent.width
                    val top = origin.y / parent.height
                    val right = (origin.x + coordinates.size.width) / parent.width
                    val bottom = (origin.y + coordinates.size.height) / parent.height
                    if (right <= left || bottom <= top) return@onGloballyPositioned
                    scanRegion.set(
                        NormalizedRegion(
                            left = left.toDouble().coerceIn(0.0, 1.0),
                            top = top.toDouble().coerceIn(0.0, 1.0),
                            right = right.toDouble().coerceIn(0.0, 1.0),
                            bottom = bottom.toDouble().coerceIn(0.0, 1.0),
                        ),
                    )
                },
        )

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(Space.s),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LabelScrimIconButton(
                onClick = onClose,
                icon = Icons.Filled.Close,
                description = stringResource(R.string.scanner_close),
            )
            if (torchAvailable) {
                LabelScrimIconButton(
                    onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                    icon = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    description = stringResource(
                        if (torchOn) R.string.scanner_torch_off else R.string.scanner_torch_on,
                    ),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Space.m),
        ) {
            when (val current = reading) {
                null -> SearchingCard(captureState, liveReadiness, framing, ::captureLabel, onEditManually)
                is LabelReading.Confident -> ProposalCard(
                    candidate = current.candidate,
                    onUse = onUseValue,
                    onCorrect = onCorrectValue,
                    onCapture = ::captureLabel,
                    onEdit = onEditManually,
                    onRetry = ::resumeLive,
                    // Offered whenever the label named a countable unit and there is somewhere for
                    // it to go — an existing product to save against, or product creation to carry
                    // it into. The count comes off the typed descriptor; the header text is never
                    // re-parsed at this boundary (spec §17).
                    savablePortion = servingCandidate
                        ?.takeIf {
                            (onSavePortionUnit != null || onCarryPendingPortionUnit != null) &&
                                it.descriptor != null
                        }
                        ?.let { it.descriptor!! to it.carbsPerServing },
                    onSavePortionUnit = { kind, conversion ->
                        when {
                            // No product row yet: carry the portion into creation rather than
                            // writing it against a foreign key that has nothing to point at.
                            onSavePortionUnit == null -> {
                                portionSaveState = PortionSaveState.PendingProductCreation
                                onCarryPendingPortionUnit?.invoke(kind, conversion)
                            }
                            else -> {
                                portionSaveState = PortionSaveState.Saving
                                saveScope.launch {
                                    // Success is whatever persistence reports, never the mere fact
                                    // that a save was started.
                                    val persisted = runCatching {
                                        onSavePortionUnit(kind, conversion)
                                    }.getOrElse { error ->
                                        OcrDiagnosticsLogger.failure("Could not save portion unit", error)
                                        false
                                    }
                                    portionSaveState = if (persisted) {
                                        PortionSaveState.Saved
                                    } else {
                                        PortionSaveState.Failed
                                    }
                                }
                            }
                        }
                    },
                    saveState = portionSaveState,
                )
                // Insufficient evidence to pick one interpretation: showing the top-ranked
                // candidate as if it were confident would fabricate certainty the parser doesn't
                // have. Surface up to 3 distinct candidates and let the user choose explicitly.
                is LabelReading.Ambiguous -> AmbiguousCard(
                    candidates = current.candidates.take(3),
                    onUse = onUseValue,
                    onCorrect = onCorrectValue,
                    onCapture = ::captureLabel,
                    onEdit = onEditManually,
                    onRetry = ::resumeLive,
                )
                LabelReading.NotFound -> NotFoundCard(::captureLabel, onEditManually, ::resumeLive)
            }
        }
    }
}

/**
 * Corner-bracket frame showing where the nutrition table should sit.
 *
 * No longer purely decorative: a still capture is cropped to this rectangle plus a safety margin
 * before recognition (§10). It previously said cropping "has no demonstrated recognition benefit" —
 * that was written against rendered fixtures, where the frame contains a table and nothing else. On
 * a real package the rest of the frame is the ingredient list, marketing copy, a barcode and a
 * best-before date, and every one of those adds rows and stray numbers for the table reconstruction
 * to survive. The user has already said which part matters by putting it in here.
 */
@Composable
private fun ScanRegionOverlay(modifier: Modifier = Modifier) {
    val strokeColor = Color.White.copy(alpha = 0.85f)
    val shadowColor = Color.Black.copy(alpha = 0.35f)
    Canvas(modifier = modifier.aspectRatio(0.8f)) {
        val bracket = size.minDimension * 0.14f
        val corners = listOf(
            Pair(0f, 0f) to Pair(1, 1),
            Pair(size.width, 0f) to Pair(-1, 1),
            Pair(0f, size.height) to Pair(1, -1),
            Pair(size.width, size.height) to Pair(-1, -1),
        )
        corners.forEach { (origin, direction) ->
            val (ox, oy) = origin
            val (dx, dy) = direction
            listOf(shadowColor to 6f, strokeColor to 3f).forEach { (color, width) ->
                drawLine(
                    color = color,
                    start = Offset(ox, oy),
                    end = Offset(ox + bracket * dx, oy),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(ox, oy),
                    end = Offset(ox, oy + bracket * dy),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * The pre-capture state: framing guidance and the one obvious capture control.
 *
 * [liveReadiness] is the live analyzer's most recent interpretation and is used ONLY to say whether
 * the table currently looks readable. It deliberately never shows a carbohydrate value: a number
 * that appeared here would be a 1280x720 reading of a table the user is still aiming, and the whole
 * point of the capture-first change is that such a frame cannot answer. Nor is "Ready" a promise
 * that recognition will succeed — it reports what the live frames can see, which is why the wording
 * describes the table rather than the outcome.
 */
@Composable
private fun SearchingCard(
    captureState: CaptureState,
    liveReadiness: LabelReading?,
    framing: TextResolutionGuidance.Estimate?,
    onCapture: () -> Unit,
    onEdit: () -> Unit,
) {
    ScannerCard {
        Text(stringResource(R.string.ocr_align_title), style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (captureState != CaptureState.IDLE) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(
                text = stringResource(
                    when (captureState) {
                        CaptureState.CAPTURING -> R.string.ocr_capturing
                        CaptureState.PROCESSING -> R.string.ocr_processing
                        CaptureState.IDLE -> when {
                            // A live frame could interpret the table, so the framing is good enough
                            // to be worth capturing. It is not a claim about the final value.
                            liveReadiness is LabelReading.Confident ||
                                liveReadiness is LabelReading.Ambiguous ->
                                R.string.ocr_ready_to_capture
                            // Checked only when no live frame managed a reading: text that IS being
                            // read is large enough by demonstration, whatever the measurement says.
                            framing?.readiness == TextResolutionGuidance.Readiness.TOO_SMALL ->
                                R.string.ocr_move_closer
                            else -> R.string.ocr_looking
                        }
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CaptureButton(onCapture, enabled = captureState == CaptureState.IDLE)
        TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget)) {
            Text(stringResource(R.string.ocr_enter_manually))
        }
    }
}

@Composable
private fun ProposalCard(
    candidate: CarbCandidate,
    onUse: (BigDecimal, NutritionBasis) -> Unit,
    onCapture: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    /** Opens manual entry pre-filled with the detected figure (§3 "Correct"). */
    onCorrect: (BigDecimal, NutritionBasis) -> Unit,
    /** The typed descriptor and its per-serving carbohydrate figure, when the label named one. */
    savablePortion: Pair<ServingDescriptor, BigDecimal>? = null,
    onSavePortionUnit: (PortionUnitKind, PortionConversion) -> Unit = { _, _ -> },
    saveState: PortionSaveState = PortionSaveState.Idle,
) {
    ScannerCard {
        CandidateChoice(candidate, onUse, onCorrect = onCorrect)
        savablePortion?.let { (descriptor, carbsPerServing) ->
            SavePortionUnitAction(
                descriptor = descriptor,
                carbsPerServing = carbsPerServing,
                saveState = saveState,
                onSave = onSavePortionUnit,
            )
        }
        SecondaryScannerActions(onCapture, onEdit, onRetry)
    }
}

/**
 * "Save as a slice portion" (spec §17).
 *
 * The explicit acceptance step: nothing is persisted until this is tapped, and it only appears after
 * a still capture, never from a live frame. A printed weight is preferred when the same descriptor
 * carried one — it feeds the app's existing weight-based path — and the carbs-per-unit figure is the
 * fallback that makes a weightless label usable at all.
 */
@Composable
private fun SavePortionUnitAction(
    descriptor: ServingDescriptor,
    carbsPerServing: BigDecimal,
    saveState: PortionSaveState,
    onSave: (PortionUnitKind, PortionConversion) -> Unit,
) {
    // Each terminal state says what actually happened. "Saved" is reachable only from a completed
    // write; a failure says so and leaves the action available rather than claiming success.
    when (saveState) {
        PortionSaveState.Saved -> {
            Text(
                text = stringResource(R.string.label_portion_unit_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        PortionSaveState.PendingProductCreation -> {
            // Not saved, and deliberately not described as saved: the portion is waiting for the
            // product the user is about to create.
            Text(
                text = stringResource(R.string.label_portion_unit_pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        PortionSaveState.Saving -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.label_portion_unit_saving),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        PortionSaveState.Failed ->
            Text(
                text = stringResource(R.string.label_portion_unit_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        PortionSaveState.Idle -> Unit
    }

    TextButton(
        onClick = {
            val conversion = descriptor.amountPerUnit?.let { perUnit ->
                PortionConversion.WeightBased(perUnit.amount, perUnit.basis)
            } ?: PortionConversion.DirectCarbs(
                // The count is read straight off the typed descriptor rather than re-parsed from
                // the header text, which is the point of typing it at parse time.
                carbsPerServing.divide(descriptor.count, 4, RoundingMode.HALF_UP).stripTrailingZeros(),
            )
            onSave(descriptor.kind, conversion)
        },
    ) {
        // The kind's own word rather than `kind.name.lowercase()`, which happened to read correctly
        // for single-word constants and would have printed "custom" for CUSTOM (§24).
        Text(stringResource(R.string.label_save_as_portion_unit, descriptor.kind.kindLabel()))
    }
}

/**
 * Insufficient evidence to pick one interpretation. Shows up to 3 distinct candidates (the caller
 * already truncated the list) so the user chooses explicitly, rather than the app silently
 * promoting the highest-scored one to a confident answer.
 */
@Composable
private fun AmbiguousCard(
    candidates: List<CarbCandidate>,
    onUse: (BigDecimal, NutritionBasis) -> Unit,
    onCorrect: (BigDecimal, NutritionBasis) -> Unit,
    onCapture: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
) {
    ScannerCard {
        Text(stringResource(R.string.ocr_ambiguous_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.ocr_ambiguous_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        candidates.forEach { candidate ->
            CandidateChoice(candidate, onUse, onCorrect, showCorrectPair = false)
        }
        SecondaryScannerActions(onCapture, onEdit, onRetry)
    }
}

/**
 * The detected figure, rendered as the thing the user is actually being asked to check.
 *
 * Previously this was one line of `bodyMedium` prose ("Detected — Carbohydrate 40.3 g / 100 g") with
 * the value buried mid-sentence at the same size as the explanation around it. The user's whole job
 * on this card is to compare a number against the package in their hand, and the number was the
 * hardest thing on the card to read.
 *
 * It is deliberately **not** [NumberType.result]: this is a proposal awaiting confirmation, not an
 * answer. Borrowing the calculator's 64sp result treatment would make an unconfirmed OCR reading
 * look exactly like a computed carbohydrate total, which is the one confusion this app cannot
 * afford. `headlineMedium` in the ordinary on-surface colour is large enough to check at arm's
 * length and visibly not the result hue.
 */
@Composable
private fun DetectedValue(display: String, basisLabel: String?, nutrientLabel: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = nutrientLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            // "40.3 g / 100 g" — the brief's requested shape. The basis is part of the same string
            // so the figure can never be read without the basis it depends on.
            text = if (basisLabel != null) {
                stringResource(R.string.ocr_detected_value, display, basisLabel)
            } else {
                stringResource(R.string.ocr_detected_value_no_basis, display)
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CandidateChoice(
    candidate: CarbCandidate,
    onUse: (BigDecimal, NutritionBasis) -> Unit,
    /**
     * Opens manual entry pre-filled with this value (§3 "Correct").
     *
     * Always supplied, because the no-basis branch below has no other safe destination — it is the
     * only route out of a value whose basis the parser could not establish. [showCorrectPair]
     * controls whether it *also* appears next to Confirm on a candidate whose basis is known.
     */
    onCorrect: (BigDecimal, NutritionBasis) -> Unit,
    /**
     * Whether a basis-known candidate shows Correct beside Confirm.
     *
     * False in the ambiguous list, where the card already offers a choice between readings and a
     * per-candidate Correct button would triple the controls on a card whose problem is that it has
     * too many numbers on it already. The card's shared *Edit* action covers correction there.
     */
    showCorrectPair: Boolean = true,
) {
    val display = candidate.value.stripTrailingZeros().toPlainString()
    val basis = candidate.basis
    if (basis != null) {
        DetectedValue(display, basis.unitLabel, candidate.label)
        if (showCorrectPair) {
            // Confirm and Correct as a pair, both full touch targets, Confirm carrying the weight.
            // Correct is a real destination rather than a hint to go back and retype: it pre-fills
            // the detected figure, so a one-digit OCR slip costs one keystroke, not a re-scan.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                OutlinedButton(
                    onClick = { onCorrect(candidate.value, basis) },
                    modifier = Modifier.weight(1f).height(Space.primaryButtonHeight),
                    shape = RoundedCornerShape(Space.buttonRadius),
                ) { Text(stringResource(R.string.ocr_correct)) }
                Button(
                    onClick = { onUse(candidate.value, basis) },
                    modifier = Modifier.weight(1f).height(Space.primaryButtonHeight),
                    shape = RoundedCornerShape(Space.buttonRadius),
                ) { Text(stringResource(R.string.ocr_confirm)) }
            }
        } else {
            Button(
                onClick = { onUse(candidate.value, basis) },
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
            ) { Text(stringResource(R.string.ocr_use, display)) }
        }
    } else {
        // The row is trustworthy but the printed basis was never established.
        //
        // This used to offer two buttons — "Use as /100 g" and "Use as /100 ml" — each of which
        // committed the value immediately on the strength of a guess. That is the one shape of
        // question this app must never ask (§1B): the number is bare grams, and bare grams on a
        // nutrition table can equally be per 100 g, per 100 ml, or **per serving**. A device scan
        // (`20260825-123828-500`) produced precisely that: a per-portion `22 g` surviving as the only
        // placeable figure on a label printing `86 g` per 100 g. Asking "per 100 g or per 100 ml?"
        // about it offers two answers and both are wrong, and whichever the user picks is then
        // indistinguishable from a value the parser actually placed.
        //
        // So the value is shown — the user's photograph did read something and hiding it helps
        // nobody — and the only way forward is manual correction, where the number is pre-filled and
        // the basis is an explicit, visible, changeable chip rather than a one-tap commitment.
        DetectedValue(display, basisLabel = null, nutrientLabel = candidate.label)
        Text(
            text = stringResource(R.string.ocr_basis_unknown_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            // PER_100_G pre-selects the chip manual entry opens on; it is not a reading. The user
            // lands on a screen that names the unit and lets them change it, with the package in
            // hand — the opposite of committing a basis from a scanner card.
            onClick = { onCorrect(candidate.value, NutritionBasis.PER_100_G) },
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
        ) { Text(stringResource(R.string.ocr_basis_unknown_correct)) }
    }
}

/**
 * The recovery for a failed read (§19).
 *
 * *Try again* is primary and returns to a clean live scanner — not straight to another capture. A
 * failed read usually means the framing or the light was wrong, and firing the shutter again from
 * the same position mostly reproduces the same failure; going back to the live view is what lets the
 * user re-aim. Capture stays one tap away for when they already have.
 *
 * Nothing here navigates away, so the user never has to go back to Home to retry the task they are
 * in the middle of (§20).
 */
@Composable
private fun NotFoundCard(onCapture: () -> Unit, onEdit: () -> Unit, onRetry: () -> Unit) {
    ScannerCard {
        Text(stringResource(R.string.ocr_not_found_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.ocr_not_found_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
        ) { Text(stringResource(R.string.ocr_try_again)) }
        OutlinedButton(
            onClick = onEdit,
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
        ) { Text(stringResource(R.string.ocr_enter_manually)) }
        TextButton(onClick = onCapture, modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget)) {
            Text(stringResource(R.string.ocr_capture_label))
        }
    }
}

@Composable
private fun SecondaryScannerActions(onCapture: () -> Unit, onEdit: () -> Unit, onRetry: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        OutlinedButton(
            onClick = onCapture,
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.weight(1f).height(Space.minTouchTarget),
        ) { Text(stringResource(R.string.ocr_capture_label)) }
        OutlinedButton(
            onClick = onEdit,
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.weight(1f).height(Space.minTouchTarget),
        ) { Text(stringResource(R.string.ocr_edit)) }
    }
    TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget)) {
        Text(stringResource(R.string.ocr_scan_again))
    }
}

@Composable
private fun CaptureButton(onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
    ) { Text(stringResource(R.string.ocr_capture_label)) }
}

@Composable
private fun ScannerCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Space.cardRadius))
            .padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
        content = content,
    )
}

@Composable
private fun LabelScrimIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(Space.minTouchTarget)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .semantics { contentDescription = description },
    ) { Icon(icon, contentDescription = null, tint = Color.White) }
}

@Composable
private fun LabelPermissionRationale(
    showAllow: Boolean,
    onAllow: () -> Unit,
    onEditManually: () -> Unit,
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
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.l))
        if (showAllow) {
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.permission_allow)) }
            Spacer(Modifier.height(Space.s))
        }
        Button(
            onClick = onEditManually,
            modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
            shape = RoundedCornerShape(Space.buttonRadius),
        ) { Text(stringResource(R.string.permission_manual)) }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget)) {
            Text(stringResource(R.string.action_close))
        }
    }
}

private enum class CaptureState { IDLE, CAPTURING, PROCESSING }

/**
 * How long the shutter waits for focus before firing anyway.
 *
 * Long enough for an ordinary AF sweep on a close subject, short enough that the scanner never feels
 * stuck. `startFocusAndMetering` can legitimately never complete on a low-contrast surface, so this
 * is a bound on the whole interaction rather than a hint.
 */
private const val FOCUS_TIMEOUT_MS = 1200L

/**
 * The crop rectangle used when nothing better is available.
 *
 * Reached only when the scan overlay has not been measured *and* Pass A found no table structure —
 * so it is a starting position for a user who is about to move it, not a guess the app acts on. A
 * generous central rectangle rather than the whole frame, because a selection covering everything
 * would filter nothing and reproduce the whole-frame behaviour this feature exists to improve on.
 */
private val DEFAULT_CROP = NormalizedRegion(left = 0.08, top = 0.20, right = 0.92, bottom = 0.80)
