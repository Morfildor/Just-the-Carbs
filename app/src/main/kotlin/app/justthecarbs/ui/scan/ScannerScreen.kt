package app.justthecarbs.ui.scan

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
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
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space
import java.util.concurrent.Executors

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
) {
    // §6, startup-hardening pass: one shared five-state gate, used identically by this screen and
    // LabelScannerScreen. Previously each screen tracked only granted/not-granted plus whether a
    // request had been made, which made every denial a dead end — a "not this time" answer had no
    // way back to the system dialog, and a "never ask me again" answer had no way to Settings.
    val permission = rememberCameraPermissionController()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permission.state == CameraPermissionState.Granted) {
            CameraPreview(
                hapticsEnabled = hapticsEnabled,
                onBarcode = onBarcode,
                onManualBarcode = onManualBarcode,
                onClose = onClose,
                onEnterManually = onEnterManually,
            )
        } else {
            CameraPermissionRationale(
                state = permission.state,
                onAllow = permission::request,
                onOpenSettings = permission::openSettings,
                onEnterManually = onEnterManually,
                onClose = onClose,
            )
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

    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        BarcodeAnalyzer { acceptance ->
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
                    acquired = true
                    currentOnBarcode(acceptance.value)
                }
                BarcodeAcceptance.Stabilizing -> holdSteady = true
                BarcodeAcceptance.Searching -> holdSteady = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            executor.shutdown()
        }
    }

    if (cameraFailed) {
        RecoveryPanel(
            title = stringResource(R.string.scanner_unavailable),
            body = null,
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
        ) {
            Button(
                onClick = onEnterManually,
                modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.permission_manual)) }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_close))
            }
        }
        return
    }

    if (showBarcodeDialog) {
        ManualBarcodeDialog(
            onConfirm = { code -> showBarcodeDialog = false; onManualBarcode(code) },
            onDismiss = { showBarcodeDialog = false },
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

                        provider.unbindAll()
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
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(Space.s),
            horizontalArrangement = Arrangement.Start,
        ) {
            ScrimIconButton(
                onClick = onClose,
                icon = Icons.Filled.Close,
                description = stringResource(R.string.scanner_close),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Space.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (acquired) {
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
                    onClick = { showBarcodeDialog = true },
                    // A lookup is already under way and this screen is about to be replaced;
                    // opening the manual dialog on top of it would start a second, competing one.
                    enabled = !acquired,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                ) {
                    Text(
                        text = stringResource(R.string.scanner_enter_manually),
                        color = Color.White,
                    )
                }
            }
        }
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
        targetValue = if (acquired) 0.30f else 0.08f,
        animationSpec = tween(Motion.QUICK_MS),
        label = "scanFrameFill",
    )
    Box(
        modifier = modifier
            .fillMaxWidth(0.68f)
            .height(180.dp)
            .background(accent.copy(alpha = fillAlpha), RoundedCornerShape(26.dp))
            .border(3.dp, accent, RoundedCornerShape(26.dp)),
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
