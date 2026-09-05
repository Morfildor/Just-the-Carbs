package app.justthecarbs.ui.scan

import android.os.SystemClock
import android.util.Size
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
import androidx.compose.ui.layout.ContentScale
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
import app.justthecarbs.ocr.CaptureEvidenceCoordinator
import app.justthecarbs.ocr.CarbCandidate
import app.justthecarbs.ocr.CarbFailureDiagnosis
import app.justthecarbs.ocr.AutomaticScanAdvance
import app.justthecarbs.ocr.AutomaticVerification
import app.justthecarbs.ocr.CropChange
import app.justthecarbs.ocr.DisputedCandidates
import app.justthecarbs.ocr.EvidenceResolver
import app.justthecarbs.ocr.EvidenceSource
import app.justthecarbs.ocr.LabelAnalyzer
import app.justthecarbs.ocr.LabelReading
import app.justthecarbs.ocr.LiveEvidenceBuffer
import app.justthecarbs.ocr.NormalizedRegion
import app.justthecarbs.ocr.NutritionParseReport
import app.justthecarbs.ocr.PassAResult
import app.justthecarbs.ocr.PhysicalObservationId
import app.justthecarbs.ocr.RecognitionEvidence
import app.justthecarbs.ocr.ScaleAmbiguity
import app.justthecarbs.ocr.ScanEvidenceRecorder
import app.justthecarbs.ocr.ScanPresentationDecision
import app.justthecarbs.ocr.ScanRegionMapper
import app.justthecarbs.ocr.SelectedTableResolution
import app.justthecarbs.ocr.StatedBasis
import app.justthecarbs.ocr.ServingCarbCandidate
import app.justthecarbs.ocr.TextResolutionGuidance
import app.justthecarbs.ocr.OcrDiagnosticsLogger
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.product.kindLabel
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
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
    /**
     * *Edit* — manual entry with an empty amount, carrying the basis the label stated.
     *
     * The parameter is the whole point and is null only when no basis was ever established. A device
     * recording showed the cost of not having it: a coconut-milk label whose `per 100 ml` column the
     * classifier had read correctly was rejected by the user, who tapped *Edit* and landed on a
     * manual screen with **`100 g` selected**. Typing the correct figure there stores it against the
     * wrong denominator, and nothing downstream can detect that afterwards.
     *
     * The value is deliberately *not* carried: this action is reached when the app's number was
     * wrong or withheld, so pre-filling it would re-propose the figure the user has just declined.
     * [onCorrectValue] is the action that carries a value, and it is a different question.
     */
    onEditManually: (NutritionBasis?) -> Unit,
    onClose: () -> Unit,
    /**
     * *Correct* — manual entry opened with the detected figure already in the field (§3).
     *
     * Distinct from [onEditManually], which starts from an empty field. When OCR reads `40.3` and
     * the package says `48.3`, the user is fixing one digit, not transcribing a number; sending them
     * to a blank field makes them re-read the package the app just photographed. Defaults to
     * [onEditManually] so a caller that has nowhere to put a pre-filled value degrades to the
     * previous behaviour rather than losing the action.
     *
     * The basis is nullable and that is load-bearing (§5, startup-hardening pass): a candidate whose
     * basis was never established must carry `null` all the way to manual entry, never a pre-selected
     * `PER_100_G` standing in for "unknown". A pre-selected chip looks exactly like a value the app
     * actually placed, and the whole point of this path is that it did not.
     */
    onCorrectValue: (BigDecimal, NutritionBasis?) -> Unit = { _, basis -> onEditManually(basis) },
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
    // §6, startup-hardening pass: the same shared five-state gate ScannerScreen uses. Previously
    // this screen carried its own copy of the granted/not-granted-plus-requested tracking, with the
    // identical dead end once a request had been answered — no way back to the system dialog for a
    // "not this time" denial, and no way to Settings for a "never ask me again" one.
    val permission = rememberCameraPermissionController()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permission.state == CameraPermissionState.Granted) {
            LabelCamera(
                onUseValue = onUseValue,
                onEditManually = onEditManually,
                onCorrectValue = onCorrectValue,
                onClose = onClose,
                onSavePortionUnit = onSavePortionUnit,
                onCarryPendingPortionUnit = onCarryPendingPortionUnit,
            )
        } else {
            CameraPermissionRationale(
                state = permission.state,
                onAllow = permission::request,
                onOpenSettings = permission::openSettings,
                // No camera, so nothing has been read and no basis can have been established.
                onEnterManually = { onEditManually(null) },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun LabelCamera(
    onUseValue: (BigDecimal, NutritionBasis) -> Unit,
    onEditManually: (NutritionBasis?) -> Unit,
    onCorrectValue: (BigDecimal, NutritionBasis?) -> Unit,
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

    /**
     * The captured JPEG, shown while recognition runs.
     *
     * ## Why this exists separately from [pendingCrop]
     *
     * [pendingCrop] is only assigned once recognition has *finished*, so for the whole 323–1974 ms
     * the device evidence records ML Kit taking, the user was watching a **live camera preview of
     * whatever the phone is now pointed at** while the app reasoned about a photograph they had
     * already taken. Moving the phone during that window made the app look like it had lost the
     * label. This holds the file from `onImageSaved`, which is the earliest moment a photograph
     * exists at all.
     *
     * Loaded through Coil rather than `BitmapFactory`: it decodes off the main thread and
     * downsamples to the target size, so an 8 MP JPEG does not compete for the CPU with the ML Kit
     * pass the user is actually waiting on. Decoding it synchronously here would make the scan
     * slower to make the wait look better, which is the wrong trade.
     */
    var capturedPreview by remember { mutableStateOf<File?>(null) }
    var cropSelection by remember { mutableStateOf<NormalizedRegion?>(null) }
    /** True while a confirmed crop is being re-parsed; disables the primary action. */
    var readingTable by remember { mutableStateOf(false) }
    /**
     * The automatic post-capture attempt ran and did not resolve confidently (1.0.3 P3).
     *
     * Purely presentational: it changes what the crop screen *says*, never what it does. Without it
     * the fallback is indistinguishable from a fresh capture, so a user who has just waited through
     * an automatic attempt is asked to crop with no indication that anything was tried — which reads
     * as the app having done nothing, or as having restarted.
     */
    var autoAttempted by remember { mutableStateOf(false) }
    /**
     * The region the last recognition actually ran over, or null when none has run (1.0.3 P2).
     *
     * Keyed on the region rather than on which button was pressed, because the question the skip
     * answers is *"would recognising this tell us anything new?"* — a property of the input, not of
     * the caller. Recognition is deterministic over identical input, so re-running it on a region
     * already recognised for this capture is guaranteed to produce the outcome that already
     * declined.
     *
     * Cleared by [resumeLive] along with the rest of the capture's derived state, so a new capture
     * can never be mistaken for an unchanged crop of the previous one.
     */
    var lastRecognisedRegion by remember { mutableStateOf<NormalizedRegion?>(null) }
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
     * The candidates a distinct recognition run contradicted, held so they survive the hand-off from
     * the conflict screen into the assisted path.
     *
     * Without this the dispute died with the resolver's outcome: the conflict screen built
     * `AssistState(document = ...)` from the winning document alone, and [RecoveryCandidates]
     * rebuilt its list with no idea that anything had been refused. Measured on `131511`, whose
     * first recovery offer was the very `89 g / 100 ml` the resolver had just declined.
     */
    var disputedCandidates by remember { mutableStateOf(DisputedCandidates.NONE) }
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
     * The two capture identities, kept apart.
     *
     * This replaces a single `captureSession` `AtomicLong` that answered two different questions with
     * one number, and therefore had to bump on a schedule that was right for one of them and wrong
     * for the other:
     *
     * - **Work generation** — "is this in-flight async callback still current?" Bumps on every new
     *   capture attempt, so a still-recognition result from an abandoned attempt cannot land.
     *   Recognition of an 8 MP still takes seconds, during which the user can tap Retake; without
     *   this the earlier capture's result would arrive afterwards and replace the newer one.
     * - **Aim epoch** — "which pre-shutter live-camera stream does this frame belong to?" Must NOT
     *   bump on a shutter press, because live frames recorded while framing the shot are necessarily
     *   recorded *before* the tap and belong to the same aim as the still that tap produces.
     *
     * The old counter bumped at the top of [captureLabel], so every pre-shutter frame carried the
     * pre-tap value while the later evidence read filtered on the post-tap one — excluding all of
     * them. See [CaptureEvidenceCoordinator]'s KDoc.
     */
    val coordinator = remember { CaptureEvidenceCoordinator() }
    /**
     * What the live camera had agreed on at the instant the shutter fired, frozen there.
     *
     * Read by [readSelectedTable] instead of re-querying [liveEvidence]. A fresh query cannot work:
     * the still pipeline is measured at 477–2458 ms on real hardware and [LiveEvidenceBuffer]'s
     * consensus window is 1500 ms, so by the time there is a still reading to corroborate, valid
     * pre-shutter evidence has usually expired — and the manual *Read table* path can be minutes
     * later still, while the user drags a crop rectangle.
     *
     * Using this snapshot from a late crop confirmation is correct rather than stale: it says what
     * the camera saw as *this photograph* was taken, which is exactly the evidence that should
     * corroborate a still recognised from that same shutter press, however long the user then takes.
     */
    var frozenLiveSnapshot by remember {
        mutableStateOf<CaptureEvidenceCoordinator.LiveEvidenceSnapshot?>(null)
    }
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
                    //
                    // Stamped with the AIM EPOCH, not a per-capture counter. The epoch bumps only on
                    // dispose and retake — a genuinely new aim — so a frame recorded while the user
                    // was framing this shot still belongs to it after the shutter fires. Stamping
                    // with a counter that bumps at the shutter is what excluded every pre-shutter
                    // frame from the evidence read that followed.
                    //
                    // elapsedRealtime, never currentTimeMillis: LiveEvidenceBuffer's window and
                    // freezeAtShutter's age arithmetic both require one monotonic clock, and a
                    // wall-clock adjustment must not be able to make an old frame look fresh.
                    liveEvidence.record(result, SystemClock.elapsedRealtime(), coordinator.aimEpoch)
                }
            },
            onFraming = { estimate -> mainExecutor.execute { framing = estimate } },
        )
    }

    // Loads ML Kit's native detector while the user is still framing, so the first capture of a
    // session does not pay for it. Measured at 2789 ms on the first capture of the third phone
    // session against 447-1838 ms on the eight that followed. Fire-and-forget: it runs off the main
    // thread inside ML Kit, never blocks the shutter, and a failure simply restores the previous
    // behaviour of the first real recognition paying the cost.
    LaunchedEffect(analyzer) { analyzer.warmUp() }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            // BOTH counters, and the pairing is deliberate rather than tidy.
            //
            // Leaving the screen ends the aim, so no frame recorded under it may corroborate a
            // capture taken after the user returns. It also ends any in-flight recognition, which is
            // what the single `captureSession.incrementAndGet()` here used to do on its own — so
            // bumping only the aim epoch would silently drop that guarantee and let a result arrive
            // after the screen is gone. Splitting one counter into two means every site that
            // previously bumped it has to say which of the two questions it was answering; dispose
            // answers both.
            coordinator.beginNewAim()
            coordinator.beginNewWork()
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
        // Both, first, and for two separate reasons.
        //
        // Bumping the WORK generation is what makes this a cancellation and not merely a reset: any
        // recognition still in flight will now find its generation stale and discard its own result.
        // Bumping the AIM epoch is the new half — a retake is the definition of a genuinely new aim,
        // so live frames recorded while framing the abandoned shot must not be able to corroborate
        // the next one. (`liveEvidence.clear()` below drops them anyway; the epoch bump is what makes
        // that structural rather than dependent on the clear happening.)
        coordinator.beginNewAim()
        coordinator.beginNewWork()
        pendingCrop?.recycle()
        pendingCrop = null
        // Belongs to the capture being abandoned or replaced. Left set, a Retake would drop back to
        // the live camera with the previous photograph still painted over it, and a new capture
        // would briefly show the old one.
        capturedPreview = null
        cropSelection = null
        readingTable = false
        // Belongs to the capture being abandoned. Left set, the next capture's crop screen would
        // open saying an automatic attempt had failed before one had been made.
        autoAttempted = false
        // Likewise: a region recognised for the abandoned capture says nothing about the new one,
        // and leaving it set would let a new capture's first Read table be skipped as an
        // "unchanged" crop of a photograph that no longer exists (1.0.3 P2).
        lastRecognisedRegion = null
        // The frozen live evidence belongs to the shutter press being abandoned. Left set, the next
        // capture's resolution would be corroborated by what the camera saw before a *different*
        // photograph — the exact cross-capture contamination the aim epoch exists to prevent, arriving
        // through a field instead of through the buffer.
        frozenLiveSnapshot = null
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
        // A dispute belongs to the capture that produced it. Carrying it into the next one would
        // suppress a value on a new photograph because a previous photograph's runs disagreed.
        disputedCandidates = DisputedCandidates.NONE
        liveEvidence.clear()
        analyzer.resume()
    }

    /**
     * The proposal to draw on the frozen photograph for [outcome]'s confident reading.
     *
     * A [EvidenceResolver.Outcome.NeedsVerification] is already one and is passed through unchanged,
     * so its `winningEvidence` — and therefore the coordinate space its geometry is measured in —
     * survives. A [EvidenceResolver.Outcome.Resolved] is rewrapped, and **the winning evidence is
     * carried across**: without it the screen would fall back to assuming full-frame coordinates,
     * which is the defect this pass exists to close for a Strategy B reading.
     */
    fun proposalFor(
        outcome: EvidenceResolver.Outcome,
        confident: LabelReading.Confident,
    ): EvidenceResolver.Outcome.NeedsVerification? = when (outcome) {
        is EvidenceResolver.Outcome.NeedsVerification -> outcome
        is EvidenceResolver.Outcome.Resolved -> EvidenceResolver.Outcome.NeedsVerification(
            reading = confident,
            report = outcome.report,
            source = outcome.agreeingSources.firstOrNull() ?: EvidenceSource.FULL_FRAME_PASS_A,
            winningEvidence = outcome.winningEvidence,
        )
        // No other outcome carries a confident reading, so `confident` is non-null only for the two
        // above and none of these is reachable with one. Enumerated rather than defaulted so adding
        // an outcome that *can* carry a reading is a compile error here, instead of silently
        // falling through to full-frame geometry for a crop-local candidate.
        is EvidenceResolver.Outcome.Unresolved -> null
        is EvidenceResolver.Outcome.Conflicted -> null
        EvidenceResolver.Outcome.Nothing -> null
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
     *
     * @param automatic true when this is the post-capture attempt against the app's own rectangle
     *   rather than a rectangle the user confirmed. It only ever *narrows* what may happen next:
     *   an automatic attempt that does not resolve confidently hands over to the crop screen
     *   instead of presenting its outcome, so nothing is auto-accepted that a confirmed crop would
     *   not also have auto-accepted.
     */
    fun readSelectedTable(region: NormalizedRegion, automatic: Boolean = false) {
        val captured = pendingCrop ?: return

        // The confirmed rectangle is the one already recognised for this capture (1.0.3 P2).
        //
        // Recognition is deterministic over identical input — same retained elements for Strategy
        // A, same pixels of the same bitmap for Strategy B — so running it again cannot produce a
        // different outcome. It would cost the user a second wait, measured at ~400 ms for the ML
        // Kit pass alone, to arrive at the refusal they have already seen.
        //
        // So this hands straight to the assisted path, which is exactly where a repeat of that
        // outcome would have led. It is a *shortcut through a known result*, never a relaxation:
        // no rule is skipped, because the rules already ran on this very region and declined. A
        // crop the user genuinely moved fails `isMaterial` and is recognised normally.
        if (!automatic && !CropChange.isMaterial(lastRecognisedRegion, region)) {
            OcrDiagnosticsLogger.timing("selected-table skipped (crop unchanged since last pass)")
            assisting = AssistState(
                document = captured.document,
                // Its own flag, not `ineffectiveSelection`: that one says the box kept nearly the
                // whole photo, which is a claim about size. This box may be perfectly tight — it
                // simply has not moved since the pass that already failed.
                cropUnchanged = true,
            )
            return
        }

        // Read on Main, before the coroutine below switches off it, so the guard downstream asks
        // about the attempt that started this resolution rather than whichever attempt happens to be
        // current by the time the IO-dispatcher work finishes.
        //
        // Both call sites reach here — the automatic post-capture pass and the user's *Read table*
        // tap after confirming a crop — and both are answering for the capture that is currently
        // frozen on screen, which is the capture the current work generation belongs to.
        val workGeneration = coordinator.workGeneration
        // Taken at the shutter, not now. See the field's KDoc and the comment at the call below.
        val snapshotAtShutter = frozenLiveSnapshot
        // The temp file created for this shutter press is already a stable, unique per-attempt
        // identifier (`captureLabel` names it `justthecarbs-label-<unique>.jpg`). `capturedPreview`
        // is set to that same file at `onImageSaved` and cleared exactly when `pendingCrop` is, so it
        // is non-null for the whole lifetime `readSelectedTable` can be called in — from the
        // automatic post-capture pass through however long the user spends dragging a crop
        // rectangle before *Read table*. Both call sites therefore agree on one still id per capture.
        val stillObservationId = PhysicalObservationId.forStill(
            capturedPreview?.name ?: "unknown-capture",
        )
        readingTable = true
        lastRecognisedRegion = region

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
                    stillObservationId = stillObservationId,
                    // The FROZEN snapshot taken at shutter time — never a fresh query of the live
                    // buffer, which is what this line used to do.
                    //
                    // A fresh query here is structurally unable to succeed on the common path. The
                    // still pipeline is measured at 477–2458 ms on real hardware and
                    // LiveEvidenceBuffer's consensus window is 1500 ms, so by the moment there is a
                    // still reading to corroborate, the frames that would have corroborated it have
                    // usually aged out — and on the *Read table* path the user may have been
                    // dragging a crop rectangle for far longer than that. Reading the mutable buffer
                    // from this background coroutine also raced the analyzer's frame callback
                    // writing to it.
                    //
                    // Constructed exactly as LiveEvidenceBuffer.asEvidence constructs it (same
                    // source, same report shape, no document — live frames are transient and the
                    // buffer deliberately retains no geometry), just from a value captured at the
                    // shutter instead of from a query issued seconds later.
                    //
                    // Stamped as a LIVE snapshot, distinct from the still it may corroborate: these
                    // are genuinely different sensor frames captured before the shutter fired, not a
                    // re-processing of the same pixels the still evidence carries.
                    liveEvidence = snapshotAtShutter?.candidate?.let { candidate ->
                        RecognitionEvidence(
                            source = EvidenceSource.LIVE_STABLE_FRAME,
                            report = NutritionParseReport(
                                LabelReading.Confident(candidate),
                                emptyList(),
                            ),
                            document = null,
                            physicalObservation = PhysicalObservationId.forLiveSnapshot(
                                coordinator.aimEpoch,
                                capturedPreview?.name ?: "unknown-capture",
                            ),
                        )
                    },
                )
            }

            // The same stale-result guard the capture path uses: a Retake during a second recognition
            // pass must not have its answer arrive afterwards and replace the new capture's.
            if (!coordinator.isCurrentWork(workGeneration)) return@launch

            OcrDiagnosticsLogger.selectedTable(
                outcome = result.filtered.outcome.name,
                elementsBefore = result.filtered.elementsBefore,
                elementsAfter = result.filtered.elementsAfter,
                elapsedMs = result.elapsedMs,
                reading = result.filtered.report.reading,
            )

            readingTable = false

            // The fast path's only decision, and it is a *veto*, not an acceptance (1.0.3 P3).
            //
            // An automatic attempt that did not resolve confidently stops here and hands the frozen
            // photograph to the crop screen, exactly as if the capture had gone straight there. It
            // does not present its own outcome, so an ambiguity, an uncorroborated value or a
            // conflict never reaches the user *without* them having had the chance to tighten the
            // rectangle first — which is the one lever they have over all three.
            //
            // Nothing below this line was reordered or relaxed: a confirmed crop still reaches the
            // same four branches with the same rules, and `mayAdvance` reads an outcome the
            // resolver already decided rather than computing a confidence of its own.
            // Independent verification, asked once and used for both decisions below.
            //
            // This is what separates "the parser found a placeable value" from "the value was
            // checked against something the same recognition could not have got wrong". See
            // [AutomaticVerification]; the failure it exists for is `085542-213`, where a misread
            // `72,0 g` -> `12,0.g` satisfied every structural rule and advanced automatically.
            val automaticVerification = AutomaticVerification.verify(result.evidence)
            OcrDiagnosticsLogger.timing(
                "automatic-verification: ${automaticVerification.route}" +
                    (automaticVerification.rejectionReason?.let { " ($it)" } ?: ""),
            )

            // **The document every parser question is asked of: the winning pass's own.**
            //
            // This used to be `captured.document` — Pass A's whole-frame recognition — whichever
            // pass had actually produced the reading. When Strategy B wins, its candidate geometry
            // is in *crop-local* coordinates, so it matches no row of Pass A's document and
            // [ScaleAmbiguity] degrades silently to "no row to pair against": the scale question was
            // being asked about a candidate that document has never seen.
            //
            // The winning pass's document is the one its candidate was measured in, so row lookup,
            // sibling pairing and column association all resolve. It is **not** translated first:
            // the parser reasons about a document in its own space, and moving the boxes without
            // moving the document they are compared against would break every stage. Translation
            // happens once, at the presentation boundary below.
            //
            // Falls back to the capture's own document when no pass carried one, which is the
            // previous behaviour for every outcome that has no reading to attribute.
            val evaluationDocument = result.outcome.winningEvidence?.document ?: captured.document

            // Whether the evidence establishes this reading's absolute decimal scale.
            //
            // Asked here, once, so the gate below and the evidence bundle read the same verdict.
            // See [ScaleAmbiguity]: a uniform decimal collapse preserves every column ratio, so
            // cross-column agreement cannot see it and this is a separate question.
            val scaleVerdict = AutomaticScanAdvance.scaleVerdict(result.outcome, evaluationDocument)
            (scaleVerdict as? ScaleAmbiguity.Verdict.Ambiguous)?.let {
                OcrDiagnosticsLogger.timing(
                    "scale-ambiguous: '${it.candidateText}' paired with '${it.pairedText}' — ${it.reason}",
                )
            }

            // Which candidates a genuinely distinct recognition run contradicted, carried into
            // recovery so a refused value cannot be re-offered there. See [DisputedCandidates].
            val disputed = DisputedCandidates.of(result.evidence)
            disputedCandidates = disputed

            // **One decision, and it is the one that runs.**
            //
            // This block used to compute `decision` and then *independently recompute the same
            // policy*: a separate `declined` from `mayPresentAutomatically`, and a `when` whose
            // branches each asked `AutomaticScanAdvance.presentation(...)` over again. The pure
            // decision was consulted for nothing but a diagnostics string — so the rule a JVM test
            // could reach and the rule the user actually met were two different pieces of code that
            // merely happened to agree, which is the same shape as the eighth session's P0 (an
            // anonymous `else` in this file) and the ninth's (a local `val` in this file).
            //
            // They are now one piece of code. [ScanPresentationDecision] is the authority; the
            // `when` below acts on its `Action` and computes no policy of its own. The outcome is
            // still read, but only to pick *which* non-proposal screen an action lands on and to
            // carry the report — never to re-decide whether to show it.
            val decision = ScanPresentationDecision.decide(
                outcome = result.outcome,
                verification = automaticVerification,
                document = evaluationDocument,
                automatic = automatic,
            )

            // What the app actually did, for the evidence bundle. It *is* the executed action, taken
            // from the decision that executed it rather than reconstructed beside it.
            val uiAction = decision.name

            // The photograph is released on exactly the transitions the decision calls terminal.
            // The eighth session's P0 was `releaseCapture` running first and unconditionally, so a
            // confirmation card inherited a recycled bitmap and fell back to drawing itself over the
            // live preview — a question about a package the user had already moved away.
            val releasesCapture = ScanPresentationDecision.releasesCapture(decision)

            when (decision) {
                ScanPresentationDecision.Action.CROP_FALLBACK -> {
                    // Nothing worth presenting. The rectangle is the user's lever over an ambiguity,
                    // a conflict or a failed read, so the crop screen takes over — and says it has
                    // already tried.
                    autoAttempted = true
                    OcrDiagnosticsLogger.timing(
                        "fast-path declined (${result.outcome::class.simpleName})",
                    )
                }

                ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY -> {
                    // The row and the basis are established; only the digits failed. Asking for a
                    // crop here invites the user to fix a rectangle that is already correct — see
                    // [ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY] for the capture that
                    // measured it. The photograph is kept, because the digits are read off it.
                    OcrDiagnosticsLogger.timing("row and basis established; asking for the digits")
                    assisting = AssistState(
                        document = captured.document,
                        disputed = disputed,
                        startOnFocusedEntry = true,
                    )
                }

                ScanPresentationDecision.Action.AUTO_ADVANCE -> {
                    val confident = AutomaticScanAdvance.confidentReading(result.outcome)
                    val basis = confident?.candidate?.basis
                    if (confident != null && basis != null) {
                        // Verified by something outside this recognition run, and its decimal scale
                        // is established. Straight to the calculator, which shows the same figure
                        // with its basis and provenance and offers *Change*.
                        OcrDiagnosticsLogger.timing(
                            "fast-path advanced (Confident ${basis.name}, " +
                                "verified ${automaticVerification.route})",
                        )
                        (result.outcome as? EvidenceResolver.Outcome.Resolved)?.let {
                            servingCandidate = it.report.servingCandidate
                        }
                        // Terminal: the scan is over.
                        releaseCapture(captured)
                        onUseValue(confident.candidate.value, basis)
                    } else {
                        // Unreachable: the decision cannot return AUTO_ADVANCE without a confident
                        // reading carrying a basis. Handled rather than asserted so a future change
                        // to the decision degrades to the crop screen instead of doing nothing.
                        autoAttempted = true
                        OcrDiagnosticsLogger.timing("fast-path advanced with no basis — declined")
                    }
                }

                ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE -> {
                    // Confident and confirmable, and nothing outside this one recognition run agreed
                    // with it. Asked on the frozen photograph, with the row highlighted and enlarged,
                    // because the printed table is the only place the answer can be checked.
                    val confident = AutomaticScanAdvance.confidentReading(result.outcome)
                    if (confident != null) {
                        OcrDiagnosticsLogger.timing(
                            "unverified reading held on the capture (${automaticVerification.route})",
                        )
                        (result.outcome as? EvidenceResolver.Outcome.Resolved)?.let {
                            servingCandidate = it.report.servingCandidate
                        }
                        verification = proposalFor(result.outcome, confident)
                            ?: EvidenceResolver.Outcome.NeedsVerification(
                                reading = confident,
                                report = result.filtered.report,
                                source = EvidenceSource.FULL_FRAME_PASS_A,
                            )
                    }
                }

                ScanPresentationDecision.Action.CONFIRM -> {
                    // Reached through a confirmed crop, where the user has already been asked a
                    // question. The outcome selects the screen; none of these is a proposal the
                    // decision had to authorise.
                    when (val outcome = result.outcome) {
                        is EvidenceResolver.Outcome.Conflicted -> conflicted = outcome
                        is EvidenceResolver.Outcome.Unresolved -> {
                            reading = outcome.reading
                            servingCandidate = outcome.report.servingCandidate
                        }
                        is EvidenceResolver.Outcome.Resolved -> {
                            reading = outcome.reading
                            servingCandidate = outcome.report.servingCandidate
                        }
                        is EvidenceResolver.Outcome.NeedsVerification -> verification = outcome
                        EvidenceResolver.Outcome.Nothing -> assisting = AssistState(
                            document = captured.document,
                            ineffectiveSelection = result.selectionWasIneffective,
                            disputed = disputed,
                        )
                    }
                }

                ScanPresentationDecision.Action.RECOVERY -> {
                    // Either the digits were withheld — the evidence could not establish their
                    // decimal scale — or nothing usable was read at all. Both keep the photograph:
                    // the user is being asked to read the number off it, with the basis the label
                    // stated preserved. Nothing is divided, shifted or repaired.
                    val withheld = AutomaticScanAdvance.confidentReading(result.outcome) != null
                    if (withheld) {
                        OcrDiagnosticsLogger.timing("confirmation withheld (scale not established)")
                    }
                    assisting = AssistState(
                        document = captured.document,
                        ineffectiveSelection = !withheld && result.selectionWasIneffective,
                        disputed = disputed,
                        scaleAmbiguous = withheld,
                    )
                }
            }

            // Belt-and-braces on the invariant, and the one statement of it in this function: a
            // screen that still has a question for the user keeps the photograph that question is
            // about. `AUTO_ADVANCE` releases above, as part of its own terminal transition; this
            // asserts nothing else did.
            check(!releasesCapture || pendingCrop == null) {
                "a terminal action must have released the capture"
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
                // The verdict that actually gated the crop skip, and the second-pass facts that used
                // to be a hardcoded "no". Without these the bundle cannot answer why a capture did
                // or did not advance — which is exactly the question a device session asks.
                resolverVerdict = result.outcome::class.simpleName ?: "unknown",
                strategyBStatus = result.strategyB.name,
                // How the digits were checked, and what the app then did. `Resolved` + `Confident`
                // was true of the capture that reached the user with `12` where the package prints
                // `72`; only these two lines distinguish that from a correct automatic scan.
                verification = buildString {
                    append(automaticVerification.route.name)
                    if (automaticVerification.supportingRows > 0) {
                        append(" (support=${automaticVerification.supportingRows}")
                        automaticVerification.medianRatio?.let { append(", median=%.3f".format(it)) }
                        automaticVerification.candidateRatio?.let { append(", candidate=%.3f".format(it)) }
                        append(")")
                    }
                    automaticVerification.rejectionReason?.let { append(" — $it") }
                },
                // The branch that ran, and — when they differ — the pure decision that says what
                // should have run. [ScanPresentationDecision] is the JVM-testable statement of this
                // rule; the branches below it are the imperative UI that acts on it. A bundle
                // printing two different names here is a divergence between them, which is the
                // failure this recording exists to make visible rather than argued about.
                uiAction = if (decision.name == uiAction) {
                    uiAction
                } else {
                    "$uiAction (decision=${decision.name})"
                },
                // Each pass with what it actually contributed, and which evidence family it belongs
                // to. The bare source list said `FULL_FRAME_PASS_A, FILTERED_PASS_A,
                // SELECTED_REGION_OCR` even when the third produced no reading at all and the first
                // two are two parses of one recognition — three names reading as three opinions,
                // beside a verdict, on a scan that went on to be wrong.
                passesRan = result.evidence.map { evidence ->
                    val contribution = when (val reading = evidence.reading) {
                        is LabelReading.Confident ->
                            "Confident ${reading.candidate.value.toPlainString()}" +
                                "/${reading.candidate.basis?.name ?: "no-basis"}"
                        is LabelReading.Ambiguous ->
                            "Ambiguous(${reading.candidates.size}) — contributes no support"
                        LabelReading.NotFound -> "NotFound — contributes no support"
                    }
                    "${evidence.source.name} [run=${evidence.source.recognitionRun.name}] $contribution"
                },
                disputed = disputed,
                scaleVerdict = scaleVerdict,
                failureReason = CarbFailureDiagnosis.classify(
                    report = result.filtered.report,
                    outcome = result.outcome,
                    scaleVerdict = scaleVerdict,
                    action = decision,
                ),
                // Strategy B's own document, when it ran. The bundle used to record only its verdict,
                // so a session where Pass A and Strategy B disagreed could not be replayed — which is
                // precisely the ninth session's shape, and why its diagnosis needed a reconstruction.
                strategyBDocument = result.evidence
                    .firstOrNull { it.source == EvidenceSource.SELECTED_REGION_OCR }
                    ?.document,
                // The space that document is measured in. Without it the dump's boxes cannot be
                // compared against `diagnostics.txt`'s full-frame ones, and a reader would conclude
                // the two passes disagree about where the row is when they agree exactly.
                strategyBCrop = result.evidence
                    .firstOrNull { it.source == EvidenceSource.SELECTED_REGION_OCR }
                    ?.crop,
                // Which basis the correction path was handed, and whether an amount went with it.
                // A bundle previously could not say why a manual screen opened on `100 g`.
                correctionHandoff = when {
                    uiAction == "AUTO_ADVANCE" -> "none (advanced without correction)"
                    else -> {
                        val basis = StatedBasis.of(captured.document)
                        "basis=${basis?.name ?: "none established"}, amount=" +
                            when {
                                scaleVerdict is ScaleAmbiguity.Verdict.Ambiguous ->
                                    "blank (withheld: scale ambiguous)"
                                // The eighth session's case, and it must not be reported as though
                                // an amount were handed over: an unverified reading whose scale
                                // nothing established is withheld too, and the user types the
                                // digits. Reporting "per the offered candidate" here is what made
                                // `213005-691` read as though the app had handed over a figure.
                                uiAction == "RECOVERY" &&
                                    scaleVerdict is ScaleAmbiguity.Verdict.Unsupported ->
                                    "blank (withheld: scale not established by the evidence)"
                                !disputed.isEmpty -> "blank (withheld: cross-run dispute)"
                                else -> "per the offered candidate"
                            }
                    }
                },
            )
        }
    }

    /**
     * @param workGeneration the value [CaptureEvidenceCoordinator.beginNewWork] returned for *this*
     *   attempt, threaded down from [captureLabel] rather than re-read here. Re-reading it would
     *   defeat the guard entirely: it would always compare equal to itself, so a result from an
     *   abandoned attempt would pass the very check meant to discard it.
     */
    fun takePictureNow(capture: ImageCapture, file: File, workGeneration: Long) {
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
                    mainExecutor.execute {
                        captureState = CaptureState.PROCESSING
                        // The photograph now exists on disk. Show it instead of the live preview
                        // for the rest of the wait — see `capturedPreview`.
                        capturedPreview = file
                    }
                    // Recognition starts immediately and runs while the user is looking at the frozen
                    // photo and adjusting the rectangle, so the "Read table" tap costs only a
                    // re-parse of elements already in memory rather than a second ML Kit pass.
                    // `PassAResult.sessionId` is an opaque echo — whatever is handed in here comes
                    // back untouched, and its documented purpose is exactly this guard. So the work
                    // generation is what belongs in it; it is NOT a third identity of its own.
                    analyzer.analyzeStillRetaining(context, file, workGeneration) { result ->
                        pendingCapture.compareAndSet(file, null)
                        mainExecutor.execute {
                            // The stale-result guard. A Retake bumps the work generation, so a result
                            // from the abandoned capture is released rather than shown. `sessionId`
                            // is this attempt's own generation, echoed back by the analyzer.
                            if (!coordinator.isCurrentWork(result.sessionId)) {
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
                                val proposed = ScanRegionMapper.expand(
                                    scanRegion.get() ?: DEFAULT_CROP,
                                )
                                cropSelection = proposed
                                pendingCrop = result
                                autoAttempted = false

                                // Read that rectangle immediately instead of asking the user to
                                // approve it first (1.0.3 P3).
                                //
                                // This is the same call the *Read table* button makes, with the same
                                // region the crop screen would have opened on — so it is the user's
                                // own tap, made for them, and it costs nothing extra: Strategy A is
                                // a re-parse of elements already in memory, and Strategy B is
                                // skipped entirely when A is already corroborated.
                                //
                                // The gate on the far side is what makes it safe: only a confidently
                                // resolved reading proceeds, and everything else lands on the crop
                                // screen exactly as before.
                                readSelectedTable(proposed, automatic = true)
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    OcrDiagnosticsLogger.failure("Label capture failed", exception)
                    pendingCapture.compareAndSet(file, null)
                    file.delete()
                    mainExecutor.execute {
                        if (!coordinator.isCurrentWork(workGeneration)) return@execute
                        captureState = CaptureState.IDLE
                        // The file was just deleted, so any reference to it must go with it.
                        capturedPreview = null
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
     *
     * @param workGeneration this attempt's own work generation, carried through to
     *   [takePictureNow] so every cancellation check downstream asks about the attempt that started
     *   here rather than re-reading whatever is current when the callback eventually runs.
     */
    fun focusThenCapture(capture: ImageCapture, file: File, workGeneration: Long) {
        val cameraControl = camera?.cameraControl
        val region = scanRegion.get()
        if (cameraControl == null || region == null) {
            takePictureNow(capture, file, workGeneration)
            return
        }

        val fired = java.util.concurrent.atomic.AtomicBoolean(false)
        fun fireOnce() {
            // `disposed` is checked here, not only at the call sites, because this is the one point
            // both paths into the shutter go through — the focus listener and the timeout below.
            //
            // What is established by reading the code: the timeout is a `postDelayed` on the main
            // looper that nothing cancels, so closing the scanner within FOCUS_TIMEOUT_MS of tapping
            // capture leaves it queued, and it runs after `onDispose` has set `disposed`, unbound the
            // provider, closed the analyzer and called `executor.shutdown()`. Without this guard it
            // would then call `takePicture` against an unbound camera, handing its callback to that
            // shut-down executor.
            //
            // What is NOT established: the exact failure that produces. It could surface as a
            // rejected execution, a CameraX error callback, or be swallowed internally — this has
            // not been reproduced on a device, so no specific exception is claimed here. The guard
            // is kept as defensive hardening on a state that is provably reachable, not as a fix
            // for a demonstrated crash. The session guard cannot cover this case: it is read inside
            // the capture callback, which on this path never runs.
            if (disposed.get()) return
            if (fired.compareAndSet(false, true)) takePictureNow(capture, file, workGeneration)
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

        // Delivered on `mainExecutor` rather than `executor`, so the guard inside `fireOnce` is
        // reachable on this path too: `executor` is shut down by `onDispose`, and a listener handed
        // to a shut-down ExecutorService is not guaranteed to run at all — which would skip the
        // disposal check rather than perform it. The main executor stays valid for the life of the
        // process. `fireOnce` is only a compare-and-set plus a volatile read, so this costs the main
        // thread nothing; the capture work itself is still handed to `executor` by `takePictureNow`.
        focusResult.addListener({ fireOnce() }, mainExecutor)
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
        // FREEZE FIRST. This is the fix, and the ordering is the whole of it.
        //
        // Nothing above this line has touched the camera or any capture state — the two branches
        // that precede it abort the attempt outright — so this is the first thing a committed
        // shutter press does. It must stay first: `analyzer.pause()` below stops new frames landing,
        // and every millisecond between the tap and this call is a millisecond of the 1500 ms
        // consensus window spent, on a still pipeline measured at 477–2458 ms.
        //
        // The freeze reads the CURRENT aim epoch, which has deliberately not moved — the frames it
        // is about to capture were recorded while the user was framing *this* shot. Under the old
        // single counter this line's predecessor incremented before any read, so those frames were
        // stamped with one value and read back under another, and none of them ever qualified.
        frozenLiveSnapshot = coordinator.freezeAtShutter(liveEvidence, SystemClock.elapsedRealtime())
        // Only now: a new attempt invalidates anything still in flight from the previous one. The
        // aim epoch is NOT bumped here — a shutter press does not start a new aim, it ends one.
        val workGeneration = coordinator.beginNewWork()
        pendingCrop?.recycle()
        pendingCrop = null
        // Belongs to the capture being abandoned or replaced. Left set, a Retake would drop back to
        // the live camera with the previous photograph still painted over it, and a new capture
        // would briefly show the old one.
        capturedPreview = null
        cropSelection = null
        readingTable = false
        // Belongs to the capture being replaced. A *Capture label* tap from the crop screen — the
        // "retake without leaving" path — would otherwise open the next crop screen still saying an
        // automatic attempt had failed, before the new one had run.
        autoAttempted = false
        // Likewise, and for the same reason `resumeLive` clears it: a region recognised for the
        // previous capture says nothing about this one. This path is reached from *Capture label* on
        // the ambiguous, not-found and searching cards — all of which render after `releaseCapture`
        // has dropped `pendingCrop`, so they bypass `resumeLive` entirely. Left set, and with both
        // captures proposing the same `ScanRegionMapper.expand(scanRegion)` rectangle, the new
        // photograph's first *Read table* would compare equal to the old one's and be skipped as
        // "unchanged" — telling the user a brand-new capture would read the same as before (P2).
        lastRecognisedRegion = null
        analyzer.pause()
        reading = null
        captureState = CaptureState.CAPTURING
        pendingCapture.set(file)
        focusThenCapture(capture, file, workGeneration)
    }

    if (cameraFailed) {
        RecoveryPanel(
            title = stringResource(R.string.scanner_unavailable),
            body = null,
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
        ) {
            Button(
                // The camera never started, so nothing was read and no basis exists to carry.
                onClick = { onEditManually(null) },
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
                    assisting = AssistState(
                        document = frozen.document,
                        disputed = disputedCandidates,
                    )
                },
                onRetake = ::resumeLive,
            )

            conflict != null -> ConflictScreen(
                bitmap = frozenBitmap,
                conflict = conflict,
                onAssist = {
                    conflicted = null
                    // The dispute travels with the hand-off. Rebuilding the recovery list from the
                    // document alone is what re-offered the refused value on `131511`.
                    assisting = AssistState(
                        document = frozen.document,
                        disputed = disputedCandidates,
                    )
                },
                onRetake = ::resumeLive,
            )

            else -> CropConfirmationScreen(
                bitmap = frozenBitmap,
                initialSelection = cropSelection ?: DEFAULT_CROP,
                reading = readingTable,
                // True only once the automatic attempt has run and declined, which changes the
                // screen's wording from "crop this" to "I tried, and I need a hand" (1.0.3 P4).
                // While the automatic attempt is still running, `readingTable` is true and the
                // screen shows its own reading state, so the crop UI never flashes up as an
                // instruction the user is meant to act on and then answers itself.
                afterAutomaticAttempt = autoAttempted,
                onReadTable = { region -> readSelectedTable(region) },
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

        // The photograph, over the live preview, for as long as the app is reading it.
        //
        // Recognition takes 323–1974 ms on the measured device, and for all of it the user was
        // previously watching a live camera feed of wherever the phone had drifted to — while the
        // app worked on a picture already taken. Moving the phone during that window made the scan
        // look lost. Showing the capture makes the wait legible: *this* is what is being read.
        //
        // Deliberately NOT a staged progress list. `analyzeStillRetaining` takes a single
        // `onComplete` callback, so between the shutter and the result there is exactly one
        // observable transition and no honest way to report thirds of it. Inventing stages on a
        // timer would claim knowledge the app does not have, on the screen whose output someone
        // doses insulin from. The existing single line already says what is happening.
        val processingPhoto = capturedPreview
        if (processingPhoto != null && captureState == CaptureState.PROCESSING) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(processingPhoto)
                    // Coil decodes off the main thread and downsamples to the target size, so the
                    // 8 MP JPEG does not compete for CPU with the ML Kit pass being waited on.
                    .crossfade(Motion.QUICK_MS)
                    .build(),
                contentDescription = stringResource(R.string.ocr_captured_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

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
                // Nothing has been read yet, so there is no candidate basis — but the label may
                // still have stated one, and that fact is as true here as anywhere.
                null -> SearchingCard(captureState, liveReadiness, framing, ::captureLabel) {
                    onEditManually(StatedBasis.of(pendingCrop?.document))
                }
                is LabelReading.Confident -> ProposalCard(
                    candidate = current.candidate,
                    onUse = onUseValue,
                    onCorrect = onCorrectValue,
                    onCapture = ::captureLabel,
                    // The candidate's own basis, which is the one the user is looking at. Editing a
                    // proposal must not silently move the figure to a different denominator.
                    onEdit = { onEditManually(current.candidate.basis) },
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
                    // The candidates disagree about the value; they may still agree about the
                    // basis, and when they do that is an established fact worth carrying.
                    onEdit = {
                        onEditManually(
                            current.candidates.mapNotNull { it.basis }.distinct().singleOrNull()
                                ?: StatedBasis.of(pendingCrop?.document),
                        )
                    },
                    onRetry = ::resumeLive,
                )
                LabelReading.NotFound -> NotFoundCard(
                    ::captureLabel,
                    { onEditManually(StatedBasis.of(pendingCrop?.document)) },
                    ::resumeLive,
                )
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
                            // Checked before size for the same reason the estimator computes it
                            // first: on a sideways frame the size measure reports a rotated word's
                            // width as its height, so "move closer" would be advice derived from a
                            // number that means nothing. Measured on `20260903-212804-751`, where
                            // every row reconstructed across the printed columns instead of along
                            // the printed rows and the capture died silently as `NotFound`.
                            framing?.readiness == TextResolutionGuidance.Readiness.SIDEWAYS ->
                                R.string.ocr_turn_upright
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
    onCorrect: (BigDecimal, NutritionBasis?) -> Unit,
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
    onCorrect: (BigDecimal, NutritionBasis?) -> Unit,
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
    onCorrect: (BigDecimal, NutritionBasis?) -> Unit,
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
            // Null, genuinely — not a pre-selected PER_100_G standing in for "unknown". Manual entry
            // shows neither basis chip selected and disables Save until the user picks one with the
            // package in hand, which is the whole point of this path: nothing here is a reading.
            onClick = { onCorrect(candidate.value, null) },
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
