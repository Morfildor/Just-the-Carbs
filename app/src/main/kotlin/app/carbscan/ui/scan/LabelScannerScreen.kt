package app.carbscan.ui.scan

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.carbscan.R
import app.carbscan.domain.NutritionBasis
import app.carbscan.ocr.CarbCandidate
import app.carbscan.ocr.LabelAnalyzer
import app.carbscan.ocr.LabelReading
import app.carbscan.ui.theme.Space
import java.math.BigDecimal
import java.util.concurrent.Executors

/**
 * Nutrition-label OCR (§29).
 *
 * The camera never commits a value. Whatever is read is presented as a proposal — *Detected —
 * Koolhydraten 47,3 g / 100 g* — with **Use 47,3** and **Edit**, and nothing is stored until the
 * user picks one. When several rows are plausible the app shows them all and asks; it does not
 * choose. This is design decision 3.3 in practice: OCR is never auto-accepted.
 */
@Composable
fun LabelScannerScreen(
    onUseValue: (BigDecimal, NutritionBasis) -> Unit,
    onEditManually: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasPermission = it }

    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    var reading by remember { mutableStateOf<LabelReading?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        LabelAnalyzer { result -> reading = result }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            executor.shutdown()
        }
    }

    // Freeze the proposal while the user decides, so the card cannot change mid-tap.
    LaunchedEffect(reading) {
        if (reading != null) analyzer.pause()
    }

    if (!hasPermission) {
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
            Spacer(Modifier.height(Space.l))
            Button(
                onClick = onEditManually,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) { Text(stringResource(R.string.permission_manual)) }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    runCatching {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(executor, analyzer) }

                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(Space.s),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(Space.minTouchTarget)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.scanner_close),
                    tint = Color.White,
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
                null -> Text(
                    text = stringResource(R.string.ocr_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                is LabelReading.Single -> ProposalCard(
                    candidates = listOf(current.candidate),
                    onUse = onUseValue,
                    onEdit = onEditManually,
                    onRetry = { reading = null; analyzer.resume() },
                )

                is LabelReading.Ambiguous -> ProposalCard(
                    candidates = current.candidates,
                    onUse = onUseValue,
                    onEdit = onEditManually,
                    onRetry = { reading = null; analyzer.resume() },
                )

                LabelReading.NotFound -> ProposalCard(
                    candidates = emptyList(),
                    onUse = onUseValue,
                    onEdit = onEditManually,
                    onRetry = { reading = null; analyzer.resume() },
                )
            }
        }
    }
}

@Composable
private fun ProposalCard(
    candidates: List<CarbCandidate>,
    onUse: (BigDecimal, NutritionBasis) -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Space.cardRadius))
            .padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        when {
            candidates.isEmpty() -> {
                Text(
                    text = stringResource(R.string.ocr_ambiguous_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.ocr_ambiguous_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            candidates.size > 1 -> Text(
                // Several plausible rows: the user decides which is the total (§29).
                text = stringResource(R.string.ocr_candidates_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        candidates.forEach { candidate ->
            val display = candidate.value.stripTrailingZeros().toPlainString()
            Column {
                Text(
                    text = stringResource(
                        R.string.ocr_detected,
                        candidate.label,
                        "$display g",
                        candidate.basis.unitLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.xs))
                Button(
                    onClick = { onUse(candidate.value, candidate.basis) },
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
                ) { Text(stringResource(R.string.ocr_use, display)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            OutlinedButton(
                onClick = onEdit,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.weight(1f).height(Space.minTouchTarget),
            ) { Text(stringResource(R.string.ocr_edit)) }

            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.weight(1f).height(Space.minTouchTarget),
            ) { Text(stringResource(R.string.error_retry)) }
        }
    }
}
