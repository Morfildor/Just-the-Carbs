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
import app.justthecarbs.ocr.CarbCandidate
import app.justthecarbs.ocr.LabelAnalyzer
import app.justthecarbs.ocr.LabelReading
import app.justthecarbs.ocr.OcrDiagnosticsLogger
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.theme.Space
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Nutrition-table OCR camera. It proposes values; it never commits one without a tap. */
@Composable
fun LabelScannerScreen(
    onUseValue: (BigDecimal, NutritionBasis) -> Unit,
    onEditManually: () -> Unit,
    onClose: () -> Unit,
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var reading by remember { mutableStateOf<LabelReading?>(null) }
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
                    analyzer.analyzeStill(context, file) { result ->
                        pendingCapture.compareAndSet(file, null)
                        mainExecutor.execute {
                            captureState = CaptureState.IDLE
                            reading = result
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
) {
    ScannerCard {
        CandidateChoice(candidate, onUse)
        SecondaryScannerActions(onCapture, onEdit, onRetry)
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
