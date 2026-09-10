package app.justthecarbs.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.SpaceGrotesk
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Stable handles for instrumented tests. */
const val TUTORIAL_OVERLAY_TAG = "tutorial_overlay"
const val TUTORIAL_TITLE_TAG = "tutorial_title"
const val TUTORIAL_BODY_TAG = "tutorial_body"
const val TUTORIAL_SKIP_TAG = "tutorial_skip"
const val TUTORIAL_TAP_SURFACE_TAG = "tutorial_tap_surface"
const val TUTORIAL_NARRATION_TAG = "tutorial_narration"
const val TUTORIAL_PROGRESS_TAG = "tutorial_progress"
const val TUTORIAL_SPOTLIGHT_TAG = "tutorial_spotlight"

/** The visual tap-anywhere line; the tag is inside the semantics reset so it survives. */
const val TUTORIAL_TAP_AFFORDANCE_TAG = "tutorial_tap_affordance"

// Clear exactly the measured feature; the outer feather supplies breathing room without a cream rim.
private val SPOTLIGHT_PADDING = 0.dp

/**
 * Full-screen guided walkthrough over deterministic, non-interactive previews.
 *
 * Narration is measured first and centered in the visual teaching band. The deterministic preview
 * still fills the screen, reserving only the teaching statement's measured footprint so controls
 * above and below remain recognizable. Skip is composed above the tap surface and wins its own hit
 * test without coordinate math.
 */
@Composable
fun OnboardingScreen(
    stepIndex: Int,
    mode: TutorialMode,
    onNext: () -> Unit,
    onExit: () -> Unit,
    completionError: String? = null,
    busy: Boolean = false,
) {
    val boundedIndex = stepIndex.coerceIn(0, TUTORIAL_LAST_STEP)
    var presentedIndex by remember { mutableStateOf(boundedIndex) }
    val contentVisibility = remember { Animatable(1f) }
    val focusVisibility = remember { Animatable(0f) }
    val focusSettle = remember { Animatable(1f) }
    val spotlightAnimation = remember { Animatable(Rect.Zero, RectVectorConverter) }
    var spotlightReady by remember { mutableStateOf(false) }
    val presentation = tutorialPresentation(presentedIndex)
    val step = presentation.step
    val last = presentation.isLast
    val interactionLast = boundedIndex >= TUTORIAL_LAST_STEP
    val anchors = rememberTutorialAnchors()
    var titleBounds by remember { mutableStateOf<Pair<Int, Rect>?>(null) }
    var bodyBounds by remember { mutableStateOf<Pair<Int, Rect>?>(null) }
    var progressBounds by remember { mutableStateOf<Pair<Int, Rect>?>(null) }
    var tapAffordanceBounds by remember { mutableStateOf<Pair<Int, Rect>?>(null) }
    var viewportWidth by remember { mutableStateOf(0) }
    var viewportHeight by remember { mutableStateOf(0) }

    BackHandler(enabled = !busy) { onExit() }

    // A new atomic backdrop must measure its own target before focus is acquired.
    LaunchedEffect(step.backdrop) {
        anchors.clear()
    }

    val density = LocalDensity.current
    val paddingPx = with(density) { SPOTLIGHT_PADDING.toPx() }
    fun measuredTarget(anchor: TutorialAnchor): Rect? = anchors.boundsOf(anchor)?.let { bounds ->
        if (viewportWidth > 0 && viewportHeight > 0) {
            inflateWithin(
                rect = bounds,
                padding = paddingPx,
                width = viewportWidth.toFloat(),
                height = viewportHeight.toFloat(),
            )
        } else {
            null
        }
    }
    val target = measuredTarget(step.anchor)
    val requestedStep = TUTORIAL_STEPS[boundedIndex]
    val requestedTarget = if (requestedStep.backdrop == step.backdrop) {
        measuredTarget(requestedStep.anchor)
    } else {
        null
    }

    // Copy, chapter, rail and affordance fade as one immutable presentation. For incompatible
    // targets, the old focus releases before that presentation switches.
    LaunchedEffect(boundedIndex, requestedTarget) {
        if (boundedIndex == presentedIndex) {
            contentVisibility.animateTo(1f, tween(TUTORIAL_COPY_ENTER_MS, easing = LinearEasing))
            return@LaunchedEffect
        }
        val transition = if (spotlightReady) {
            tutorialSpotlightTransition(spotlightAnimation.value, requestedTarget)
        } else {
            TutorialSpotlightTransition.RELEASE_ACQUIRE
        }
        coroutineScope {
            launch {
                contentVisibility.animateTo(0f, tween(TUTORIAL_COPY_EXIT_MS, easing = LinearEasing))
            }
            if (transition == TutorialSpotlightTransition.RELEASE_ACQUIRE) {
                launch {
                    focusVisibility.animateTo(0f, tween(TUTORIAL_FOCUS_RELEASE_MS, easing = LinearEasing))
                }
            }
        }
        presentedIndex = boundedIndex
        contentVisibility.animateTo(1f, tween(TUTORIAL_COPY_ENTER_MS, easing = LinearEasing))
    }

    // One Animatable owns the rendered rectangle. No separate old rect/progress pair can expose a
    // stale combination for one frame. Visibility releases before incompatible geometry snaps.
    LaunchedEffect(presentation.index, target) {
        if (target == null) return@LaunchedEffect
        if (spotlightReady && spotlightAnimation.value == target) {
            focusVisibility.snapTo(1f)
            return@LaunchedEffect
        }
        val transition = if (spotlightReady) {
            tutorialSpotlightTransition(spotlightAnimation.value, target)
        } else {
            TutorialSpotlightTransition.RELEASE_ACQUIRE
        }
        if (!spotlightReady || focusVisibility.value <= 0.05f) {
            spotlightAnimation.snapTo(target)
            spotlightReady = true
            focusSettle.snapTo(0f)
            coroutineScope {
                launch { focusVisibility.animateTo(1f, tween(TUTORIAL_FOCUS_ACQUIRE_MS, easing = LinearEasing)) }
                launch { focusSettle.animateTo(1f, tween(Motion.STANDARD_MS, easing = LinearEasing)) }
            }
        } else if (transition == TutorialSpotlightTransition.DIRECT) {
            focusSettle.snapTo(0f)
            coroutineScope {
                launch { spotlightAnimation.animateTo(target, tween(Motion.STANDARD_MS)) }
                launch { focusSettle.animateTo(1f, tween(Motion.STANDARD_MS, easing = LinearEasing)) }
            }
        } else {
            focusVisibility.animateTo(0f, tween(TUTORIAL_FOCUS_RELEASE_MS, easing = LinearEasing))
            spotlightAnimation.snapTo(target)
            focusSettle.snapTo(0f)
            coroutineScope {
                launch { focusVisibility.animateTo(1f, tween(TUTORIAL_FOCUS_ACQUIRE_MS, easing = LinearEasing)) }
                launch { focusSettle.animateTo(1f, tween(Motion.STANDARD_MS, easing = LinearEasing)) }
            }
        }
    }
    val spotlight = if (spotlightReady) spotlightAnimation.value else null

    val scrimAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scrimAlpha.animateTo(1f, tween(Motion.STANDARD_MS)) }

    val tutorialLabel = stringResource(R.string.tutorial_pane_title)
    val accent = step.accent.color()
    val accentInk = step.accent.ink()
    val lightIntensity = tutorialLightIntensity()
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val scrimTone = tutorialScrimTone()
    val field = step.accent.lightField()
    val focusTreatment = step.focusEmphasis.treatment()
    val bloomSpread = field.bloomSpread * focusTreatment.bloomSpreadMultiplier
    val bloomAlpha = field.bloomAlpha * focusTreatment.bloomAlphaMultiplier
    val edgeAlpha = focusTreatment.edgeAlpha
    val focusRadius = step.focusStyle.radius(SPOTLIGHT_PADDING)
    val scrimMultiplier = step.focusStyle.scrimMultiplier()
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(
        alpha = scrimTone.baseAlpha * scrimMultiplier * scrimAlpha.value,
    )
    val edgeColor = tutorialFocusEdgeColor(step.accent, accent)
    val pointerCasingColor = tutorialPointerCasingColor()

    SubcomposeLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(TUTORIAL_OVERLAY_TAG)
            .onGloballyPositioned {
                viewportWidth = it.size.width
                viewportHeight = it.size.height
            }
            .semantics(mergeDescendants = false) {
                isTraversalGroup = true
                paneTitle = tutorialLabel
            },
    ) { constraints ->
        val fullConstraints = Constraints.fixed(constraints.maxWidth, constraints.maxHeight)

        // Presentation identity is part of the slot key: SubcomposeLayout must never retain old
        // copy/progress while updating the final-step affordance from a newer presentation.
        val narration = subcompose(TutorialLayer.NARRATION to presentation.index) {
            TutorialNarration(
                stepIndex = presentedIndex,
                accent = accent,
                accentInk = accentInk,
                acquisition = focusSettle.value,
                contentVisibility = contentVisibility.value,
                last = last,
                busy = busy,
                completionError = completionError,
                onNext = onNext,
                onFinish = onExit,
                onTitleBounds = { titleBounds = presentedIndex to it },
                onBodyBounds = { bodyBounds = presentedIndex to it },
                onProgressBounds = { progressBounds = presentedIndex to it },
                onTapAffordanceBounds = { tapAffordanceBounds = presentedIndex to it },
            )
        }.single().measure(Constraints(
            minWidth = minOf(constraints.maxWidth, with(density) { 320.dp.roundToPx() }),
            maxWidth = minOf(constraints.maxWidth, with(density) { 320.dp.roundToPx() }),
            maxHeight = constraints.maxHeight - navigationBottom,
        ))

        val stageTop = tutorialStageTop(constraints.maxHeight - navigationBottom, narration.height)
        val stageLeft = (constraints.maxWidth - narration.width) / 2
        // Background context is part of the atomic presentation. A crossfade here would briefly
        // teach with two screens at once even if copy, progress, and affordance already agree.
        val preview = subcompose(TutorialLayer.PREVIEW to presentation.index) {
            TutorialBackdropContent(
                backdrop = step.backdrop,
                teachingTop = with(density) { stageTop.toDp() },
                teachingHeight = with(density) { narration.height.toDp() },
                anchors = anchors,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }.single().measure(fullConstraints)

        val scrim = subcompose(TutorialLayer.SCRIM) {
            TutorialScrim(
                spotlight = spotlight,
                cornerRadius = focusRadius,
                scrimColor = scrimColor,
                edgeAlpha = scrimTone.edgeAlpha * scrimMultiplier * scrimAlpha.value,
                localAlpha = scrimTone.localAlpha * scrimMultiplier *
                    (1f - field.contextRelief) * scrimAlpha.value,
                localRelief = field.contextRelief * focusVisibility.value,
                apertureVisibility = focusVisibility.value,
                clearAperture = step.focusStyle != TutorialFocusStyle.ORIENTATION,
                modifier = Modifier.fillMaxSize(),
            )
        }.single().measure(fullConstraints)

        val aura = subcompose(TutorialLayer.AURA) {
            TutorialSpotlightDecoration(
                spotlight = spotlight,
                accent = accent,
                edgeColor = edgeColor,
                cornerRadius = focusRadius,
                acquisition = focusSettle.value,
                intensity = lightIntensity,
                spreadScale = bloomSpread,
                bloomAlpha = bloomAlpha,
                edgeAlpha = edgeAlpha,
                edgeWidth = focusTreatment.edgeWidth,
                arrivalBoost = focusTreatment.arrivalBoost,
                visibility = focusVisibility.value,
                drawEdge = step.focusStyle != TutorialFocusStyle.ORIENTATION,
                clearCenter = step.focusStyle != TutorialFocusStyle.ORIENTATION,
                modifier = Modifier.fillMaxSize(),
            )
        }.single().measure(fullConstraints)

        val pointerGeometry = if (step.pointer == TutorialPointer.FEATURE) {
            val pointerTarget = spotlight
            val currentTitle = titleBounds?.takeIf { it.first == presentedIndex }?.second
            val currentBody = bodyBounds?.takeIf { it.first == presentedIndex }?.second
            val pointerTeaching = if (currentTitle != null && currentBody != null) {
                Rect(
                    left = minOf(currentTitle.left, currentBody.left),
                    top = minOf(currentTitle.top, currentBody.top),
                    right = maxOf(currentTitle.right, currentBody.right),
                    bottom = maxOf(currentTitle.bottom, currentBody.bottom),
                )
            } else {
                null
            }
            if (pointerTarget != null && pointerTeaching != null) {
                with(density) {
                    val measuredObstacles = listOfNotNull(
                        progressBounds?.takeIf { it.first == presentedIndex }?.second,
                        tapAffordanceBounds?.takeIf { it.first == presentedIndex }?.second,
                    ) + anchors.boundsExcept(
                        anchor = step.anchor,
                        ignored = setOf(TutorialAnchor.FIND_ACTIONS),
                    )
                    tutorialPointerGeometry(
                        teaching = pointerTeaching,
                        target = pointerTarget,
                        viewport = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
                        exclusions = measuredObstacles + listOf(
                            Rect(
                                left = constraints.maxWidth - 120.dp.toPx(),
                                top = 0f,
                                right = constraints.maxWidth.toFloat(),
                                bottom = 96.dp.toPx(),
                            ),
                        ),
                        startGap = 6.dp.toPx(),
                        endGap = (if (step.accent == TutorialAccent.PORTION) 3.dp else 8.dp).toPx(),
                        edgeInset = (if (step.accent == TutorialAccent.PORTION) 18.dp else 14.dp).toPx(),
                        viewportInset = (if (step.accent == TutorialAccent.PORTION) 12.dp else 10.dp).toPx(),
                        minLength = 24.dp.toPx(),
                        maxLength = 440.dp.toPx(),
                    )
                }
            } else {
                null
            }
        } else {
            null
        }
        val pointer = subcompose(TutorialLayer.POINTER) {
            TutorialPointerDecoration(
                geometry = pointerGeometry,
                accent = accent,
                casingColor = pointerCasingColor,
                acquisition = focusSettle.value,
                visibility = focusVisibility.value,
                modifier = Modifier.fillMaxSize(),
            )
        }.single().measure(fullConstraints)

        val tapSurface = subcompose(TutorialLayer.TAP_SURFACE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TUTORIAL_TAP_SURFACE_TAG)
                    .pointerInput(interactionLast, busy) {
                        detectTapGestures {
                            if (!busy) {
                                if (interactionLast) onExit() else onNext()
                            }
                        }
                    }
                    .clearAndSetSemantics { },
            )
        }.single().measure(fullConstraints)

        // Semantics-only test geometry. The decorative Canvas itself remains absent from TalkBack.
        val spotlightHandle = spotlight?.let { rect ->
            subcompose(TutorialLayer.SPOTLIGHT_HANDLE) {
                Box(Modifier.clearAndSetSemantics { testTag = TUTORIAL_SPOTLIGHT_TAG })
            }.single().measure(
                Constraints.fixed(
                    rect.width.roundToInt().coerceAtLeast(1),
                    rect.height.roundToInt().coerceAtLeast(1),
                ),
            )
        }

        val skip = subcompose(TutorialLayer.SKIP) {
            Box(Modifier.fillMaxSize()) {
                TextButton(
                    onClick = onExit,
                    enabled = !busy,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = Space.screenEdge)
                        .heightIn(min = Space.minTouchTarget)
                        .testTag(TUTORIAL_SKIP_TAG),
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_skip),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }.single().measure(fullConstraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            preview.place(0, 0)
            scrim.place(0, 0)
            aura.place(0, 0)
            pointer.place(0, 0)
            tapSurface.place(0, 0)
            spotlightHandle?.place(
                spotlight.left.roundToInt(),
                spotlight.top.roundToInt(),
            )
            narration.place(stageLeft, stageTop)
            skip.place(0, 0)
        }
    }
}

/**
 * Central reading zone with no card boundary, elevation, border, or pointer handler. The scrim's
 * soft quiet pocket supplies contrast while taps pass through to the advance surface.
 */
@Composable
private fun TutorialNarration(
    stepIndex: Int,
    accent: Color,
    accentInk: Color,
    acquisition: Float,
    contentVisibility: Float,
    last: Boolean,
    busy: Boolean,
    completionError: String?,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onTitleBounds: (Rect) -> Unit,
    onBodyBounds: (Rect) -> Unit,
    onProgressBounds: (Rect) -> Unit,
    onTapAffordanceBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val advanceLabel = stringResource(if (last) R.string.tutorial_finish else R.string.tutorial_next)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s, vertical = Space.xs)
            .testTag(TUTORIAL_NARRATION_TAG)
            .semantics {
                if (!busy) {
                    onClick(label = advanceLabel) {
                        if (last) onFinish() else onNext()
                        true
                    }
                }
            },
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val progressLabel = stringResource(R.string.tutorial_progress, stepIndex + 1, TUTORIAL_STEPS.size)
        val chapter = stringResource(TUTORIAL_STEPS[stepIndex].chapterRes)
        val counter = stringResource(R.string.tutorial_chapter_counter, stepIndex + 1, TUTORIAL_STEPS.size, chapter)
        Box(Modifier.width(148.dp).graphicsLayer { alpha = contentVisibility }) {
            TutorialProgressSegments(stepIndex, TUTORIAL_STEPS.size, accent, acquisition)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = counter,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
            ),
            color = accentInk,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .graphicsLayer { alpha = contentVisibility }
                .clearAndSetSemantics {
                    testTag = TUTORIAL_PROGRESS_TAG
                    contentDescription = progressLabel
                }
                .onGloballyPositioned { onProgressBounds(it.boundsInRoot()) },
        )

        Spacer(Modifier.height(Space.s))
        StableTutorialCopy(
            stepIndex = stepIndex,
            onTitleBounds = onTitleBounds,
            onBodyBounds = onBodyBounds,
            modifier = Modifier.graphicsLayer { alpha = contentVisibility },
        )

        if (completionError != null) {
            Text(
                text = stringResource(R.string.tutorial_completion_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(
                if (last) R.string.tutorial_tap_to_finish else R.string.tutorial_tap_to_continue,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { testTag = TUTORIAL_TAP_AFFORDANCE_TAG }
                .onGloballyPositioned { onTapAffordanceBounds(it.boundsInRoot()) },
        )
    }
}

/** Measure every copy pair once per layout; the shared fade-through places only one message. */
@Composable
private fun StableTutorialCopy(
    stepIndex: Int,
    onTitleBounds: (Rect) -> Unit,
    onBodyBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val copyConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val height = TUTORIAL_STEPS.indices.maxOf { index ->
            subcompose(index) { TutorialCopy(index, measuring = true) }
                .single().measure(copyConstraints).height
        }
        val active = subcompose("active-$stepIndex") {
            TutorialCopy(
                index = stepIndex,
                onTitleBounds = onTitleBounds,
                onBodyBounds = onBodyBounds,
            )
        }.single().measure(copyConstraints.copy(maxHeight = height))
        layout(constraints.maxWidth, height) { active.place(0, (height - active.height) / 2) }
    }
}

@Composable
private fun TutorialCopy(
    index: Int,
    measuring: Boolean = false,
    onTitleBounds: ((Rect) -> Unit)? = null,
    onBodyBounds: ((Rect) -> Unit)? = null,
) {
    val step = TUTORIAL_STEPS[index]
    val title = stringResource(step.titleRes)
    val body = stringResource(step.bodyRes)
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (measuring) Modifier.clearAndSetSemantics { } else Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "$title $body"
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = SpaceGrotesk,
                fontSize = if (largeFont) 24.sp else 30.sp,
                lineHeight = if (largeFont) 28.sp else 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = if (measuring) {
                Modifier
            } else {
                Modifier
                    .testTag(TUTORIAL_TITLE_TAG)
                    .onGloballyPositioned { onTitleBounds?.invoke(it.boundsInRoot()) }
            },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = SpaceGrotesk,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f),
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = if (measuring) {
                Modifier
            } else {
                Modifier
                    .testTag(TUTORIAL_BODY_TAG)
                    .onGloballyPositioned { onBodyBounds?.invoke(it.boundsInRoot()) }
            },
        )
    }
}

@Composable
private fun TutorialProgressSegments(
    stepIndex: Int, count: Int, accent: Color, acquisition: Float,
) {
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)
    Row(
        modifier = Modifier.fillMaxWidth().height(4.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        repeat(count) { index ->
            val own = TUTORIAL_STEPS[index].accent.color()
            val segmentColor = tutorialRailColor(index, stepIndex, own, accent, track)
            // Fixed segment bounds: acquisition fills once, without moving the rail or adding input.
            Box(Modifier.weight(1f).height(4.dp).drawBehind {
                val start = androidx.compose.ui.geometry.Offset(2.dp.toPx(), size.height / 2f)
                val end = androidx.compose.ui.geometry.Offset(size.width - 2.dp.toPx(), start.y)
                drawLine(track, start, end, 2.dp.toPx(), StrokeCap.Round)
                if (index <= stepIndex) {
                    val active = index == stepIndex
                    val fill = if (active) 0.25f + 0.75f * acquisition else 1f
                    drawLine(
                        segmentColor, start,
                        end.copy(x = start.x + (end.x - start.x) * fill),
                        (if (active) 3.dp else 2.dp).toPx(),
                        StrokeCap.Round,
                    )
                }
            })
        }
    }
}

/** Measured content stays around the lower visual center, relaxing only to fit the viewport. */
internal fun tutorialStageTop(viewportHeight: Int, narrationHeight: Int): Int =
    (viewportHeight * 0.60f - narrationHeight / 2f).roundToInt()
        .coerceIn(0, (viewportHeight - narrationHeight).coerceAtLeast(0))

private enum class TutorialLayer {
    PREVIEW,
    SCRIM,
    AURA,
    POINTER,
    TAP_SURFACE,
    SPOTLIGHT_HANDLE,
    NARRATION,
    SKIP,
}

private const val TUTORIAL_COPY_EXIT_MS = 60
private const val TUTORIAL_COPY_ENTER_MS = 120
private const val TUTORIAL_FOCUS_RELEASE_MS = 70
private const val TUTORIAL_FOCUS_ACQUIRE_MS = 160

private val RectVectorConverter = TwoWayConverter<Rect, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
    convertFromVector = { Rect(it.v1, it.v2, it.v3, it.v4) },
)
