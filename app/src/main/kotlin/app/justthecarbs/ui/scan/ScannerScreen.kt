package app.justthecarbs.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.justthecarbs.R
import app.justthecarbs.ui.components.RecoveryPanel
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
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    // §9: ask in context, at the moment the camera is actually needed, not on first launch.
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                hapticsEnabled = hapticsEnabled,
                onBarcode = onBarcode,
                onManualBarcode = onManualBarcode,
                onClose = onClose,
                onEnterManually = onEnterManually,
            )
        } else {
            PermissionRationale(
                showSettingsHint = permissionRequested,
                onAllow = { launcher.launch(Manifest.permission.CAMERA) },
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

    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        BarcodeAnalyzer { code ->
            // A short haptic confirms the read without the user having to look away from the
            // package (§41). Restrained: this is one of only two places the app vibrates.
            // Compose's abstraction is used rather than HapticFeedbackConstants.CONFIRM, which
            // needs API 30 and would be silently inlined as an unsupported constant on minSdk 26.
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onBarcode(code)
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
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

        ScanFrame(modifier = Modifier.align(Alignment.Center))

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
            Text(
                text = stringResource(R.string.scanner_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.m))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (torchAvailable) {
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

/** A subtle frame — a hint, not a target the barcode has to be squeezed into (§8). */
@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth(0.68f)
            .height(180.dp)
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
            .border(3.dp, accent, RoundedCornerShape(26.dp)),
    )
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

/**
 * §9's exact requirement: a concise explanation, then **Allow camera** and **Enter manually**.
 * Manual entry is present in every state, so declining the camera never costs the user the app.
 */
@Composable
private fun PermissionRationale(
    showSettingsHint: Boolean,
    onAllow: () -> Unit,
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
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.l))

        if (!showSettingsHint) {
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.permission_allow)) }
            Spacer(Modifier.height(Space.s))
        }

        Button(
            onClick = onEnterManually,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(Space.buttonRadius),
        ) { Text(stringResource(R.string.permission_manual)) }

        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget)) {
            Text(stringResource(R.string.action_close))
        }
    }
}
