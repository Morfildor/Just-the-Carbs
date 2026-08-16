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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.justthecarbs.R
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.ServingDescriptor
import app.justthecarbs.ocr.CarbCandidate
import app.justthecarbs.ocr.LabelAnalyzer
import app.justthecarbs.ocr.LabelReading
import app.justthecarbs.ocr.ServingCarbCandidate
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
    onClose: () -> Unit,
    onSavePortionUnit: (suspend (PortionUnitKind, PortionConversion) -> Boolean)? = null,
    onCarryPendingPortionUnit: ((PortionUnitKind, PortionConversion) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val saveScope = rememberCoroutineScope()

    var reading by remember { mutableStateOf<LabelReading?>(null) }
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
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var torchAvailable by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }

    val pendingCapture = remember { AtomicReference<File?>(null) }
    val cameraProvider = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val disposed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        LabelAnalyzer { result -> mainExecutor.execute { reading = result } }
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            cameraProvider.getAndSet(null)?.unbindAll()
            analyzer.close()
            pendingCapture.getAndSet(null)?.delete()
            executor.shutdown()
        }
    }

    LaunchedEffect(reading) {
        if (reading != null) analyzer.pause()
    }

    fun resumeLive() {
        reading = null
        servingCandidate = null
        portionSaveState = PortionSaveState.Idle
        captureState = CaptureState.IDLE
        analyzer.resume()
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
        analyzer.pause()
        reading = null
        captureState = CaptureState.CAPTURING
        pendingCapture.set(file)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    mainExecutor.execute { captureState = CaptureState.PROCESSING }
                    analyzer.analyzeStill(context, file) { report ->
                        pendingCapture.compareAndSet(file, null)
                        mainExecutor.execute {
                            captureState = CaptureState.IDLE
                            reading = report.reading
                            servingCandidate = report.servingCandidate
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    OcrDiagnosticsLogger.failure("Label capture failed", exception)
                    pendingCapture.compareAndSet(file, null)
                    file.delete()
                    mainExecutor.execute {
                        captureState = CaptureState.IDLE
                        reading = LabelReading.NotFound
                    }
                }
            },
        )
    }

    if (cameraFailed) {
        RecoveryPanel(
            title = stringResource(R.string.scanner_unavailable),
            body = null,
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
        ) {
            Button(
                onClick = onEditManually,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.permission_manual)) }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_close))
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener(listener@{
                    if (disposed.get()) return@listener
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
                        val stillSelector = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1920, 1440),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build()
                        val stillCapture = ImageCapture.Builder()
                            .setResolutionSelector(stillSelector)
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                            stillCapture,
                        )
                        imageCapture = stillCapture
                        torchAvailable = camera?.cameraInfo?.hasFlashUnit() == true
                    } catch (error: Exception) {
                        OcrDiagnosticsLogger.failure("Could not bind label camera", error)
                        cameraFailed = true
                    }
                }, mainExecutor)
                previewView
            },
        )

        ScanRegionOverlay(modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(Space.l))

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
                null -> SearchingCard(captureState, ::captureLabel, onEditManually)
                is LabelReading.Confident -> ProposalCard(
                    candidate = current.candidate,
                    onUse = onUseValue,
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
 * Restrained corner-bracket frame showing roughly where the nutrition table should sit. Purely a
 * visual guide — OCR still processes the full frame, since cropping to this region has no
 * demonstrated recognition benefit and would only add risk.
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

@Composable
private fun SearchingCard(captureState: CaptureState, onCapture: () -> Unit, onEdit: () -> Unit) {
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
                        CaptureState.IDLE -> R.string.ocr_looking
                        CaptureState.CAPTURING -> R.string.ocr_capturing
                        CaptureState.PROCESSING -> R.string.ocr_processing
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
    /** The typed descriptor and its per-serving carbohydrate figure, when the label named one. */
    savablePortion: Pair<ServingDescriptor, BigDecimal>? = null,
    onSavePortionUnit: (PortionUnitKind, PortionConversion) -> Unit = { _, _ -> },
    saveState: PortionSaveState = PortionSaveState.Idle,
) {
    ScannerCard {
        CandidateChoice(candidate, onUse)
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
        candidates.forEach { candidate -> CandidateChoice(candidate, onUse) }
        SecondaryScannerActions(onCapture, onEdit, onRetry)
    }
}

@Composable
private fun CandidateChoice(candidate: CarbCandidate, onUse: (BigDecimal, NutritionBasis) -> Unit) {
    val display = candidate.value.stripTrailingZeros().toPlainString()
    val basis = candidate.basis
    if (basis != null) {
        Text(
            text = stringResource(
                R.string.ocr_detected,
                candidate.label,
                "$display g",
                basis.unitLabel,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onUse(candidate.value, basis) },
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
        ) { Text(stringResource(R.string.ocr_use, display)) }
    } else {
        // The row is trustworthy but the printed per-100 basis was never spatially established
        // — a genuine unknown the app cannot guess, unlike which row is the carbohydrate total.
        Text(
            text = stringResource(R.string.ocr_detected_basis_unknown, candidate.label, "$display g"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            OutlinedButton(
                onClick = { onUse(candidate.value, NutritionBasis.PER_100_G) },
                modifier = Modifier.weight(1f).height(Space.minTouchTarget),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.ocr_use_per_100_g)) }
            OutlinedButton(
                onClick = { onUse(candidate.value, NutritionBasis.PER_100_ML) },
                modifier = Modifier.weight(1f).height(Space.minTouchTarget),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.ocr_use_per_100_ml)) }
        }
    }
}

@Composable
private fun NotFoundCard(onCapture: () -> Unit, onEdit: () -> Unit, onRetry: () -> Unit) {
    ScannerCard {
        Text(stringResource(R.string.ocr_not_found_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.ocr_not_found_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CaptureButton(onCapture)
        OutlinedButton(
            onClick = onEdit,
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
        ) { Text(stringResource(R.string.ocr_enter_manually)) }
        TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget)) {
            Text(stringResource(R.string.ocr_scan_again))
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
        modifier = Modifier.fillMaxWidth().height(56.dp),
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.permission_allow)) }
            Spacer(Modifier.height(Space.s))
        }
        Button(
            onClick = onEditManually,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(Space.buttonRadius),
        ) { Text(stringResource(R.string.permission_manual)) }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget)) {
            Text(stringResource(R.string.action_close))
        }
    }
}

private enum class CaptureState { IDLE, CAPTURING, PROCESSING }
