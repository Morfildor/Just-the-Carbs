package app.justthecarbs.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space
import kotlin.math.roundToInt

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
private val SPOTLIGHT_STROKE = 1.dp

/**
 * Full-screen guided walkthrough over deterministic, non-interactive previews.
 *
 * Narration is measured first and always placed against the bottom edge. The preview receives only
 * the height above that measured region, so targets reframe while the reading zone never switches
 * sides. Skip is composed above the tap surface and wins its own hit test without coordinate math.
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
    val step = TUTORIAL_STEPS[boundedIndex]
    val last = stepIndex >= TUTORIAL_LAST_STEP
    val anchors = rememberTutorialAnchors()
    val discardedTransitionAnchors = rememberTutorialAnchors()
    var narrationBounds by remember { mutableStateOf<Rect?>(null) }

    BackHandler(enabled = !busy) { onExit() }

    // A departing crossfade scene reports to a separate sink, so it cannot restore stale geometry.
    LaunchedEffect(step.backdrop) {
        anchors.clear()
        discardedTransitionAnchors.clear()
    }

    val density = LocalDensity.current
    val paddingPx = with(density) { SPOTLIGHT_PADDING.toPx() }
    val narrationTop = narrationBounds?.top?.takeIf { it > 0f }
    val rawBounds = anchors.boundsOf(step.anchor)
    val target = if (rawBounds != null && narrationTop != null) {
        inflateWithin(
            rect = rawBounds,
            padding = paddingPx,
            width = narrationBounds!!.right,
            height = narrationTop,
        )
    } else {
        null
    }

    // One Animatable owns the rendered rectangle. No separate old rect/progress pair can expose a
    // stale combination for one frame.
    val spotlightAnimation = remember { Animatable(target ?: Rect.Zero, RectVectorConverter) }
    val previousTarget = remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val start = previousTarget.value
        previousTarget.value = target
        when {
            target == null -> Unit
            start == null || start == target -> spotlightAnimation.snapTo(target)
            else -> spotlightAnimation.animateTo(target, tween(Motion.STANDARD_MS))
        }
    }
    val spotlight = if (target == null) null else spotlightAnimation.value

    val scrimAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scrimAlpha.animateTo(1f, tween(Motion.STANDARD_MS)) }

    val tutorialLabel = stringResource(R.string.tutorial_pane_title)
    val accent by animateColorAsState(step.accent.color(), tween(Motion.STANDARD_MS), label = "tutorialAccent")
    val focusRadius by animateDpAsState(
        step.focusStyle.radius(SPOTLIGHT_PADDING), tween(Motion.STANDARD_MS), label = "tutorialRadius",
    )
    // A finite decoration-only acquisition. The aperture retains its single authoritative Rect.
    val focusSettle = remember { Animatable(1f) }
    LaunchedEffect(step.anchor, target != null) {
        if (target == null) return@LaunchedEffect
        focusSettle.snapTo(0f)
        focusSettle.animateTo(1f, tween(Motion.STANDARD_MS))
    }
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = BASE_SCRIM * scrimAlpha.value)

    SubcomposeLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(TUTORIAL_OVERLAY_TAG)
            .semantics(mergeDescendants = false) {
                isTraversalGroup = true
                paneTitle = tutorialLabel
            },
    ) { constraints ->
        val fullConstraints = Constraints.fixed(constraints.maxWidth, constraints.maxHeight)

        val narration = subcompose(TutorialLayer.NARRATION) {
            TutorialNarration(
                stepIndex = boundedIndex,
                accent = accent,
                last = last,
                busy = busy,
                completionError = completionError,
                onNext = onNext,
                onFinish = onExit,
                modifier = Modifier.onGloballyPositioned { narrationBounds = it.boundsInRoot() },
            )
        }.single().measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = 0))

        val previewHeight = tutorialPreviewHeight(constraints.maxHeight, narration.height)
        val preview = subcompose(TutorialLayer.PREVIEW) {
            Crossfade(
                targetState = step.backdrop,
                animationSpec = tween(Motion.QUICK_MS),
                modifier = Modifier.fillMaxSize(),
                label = "tutorialBackdrop",
            ) { backdrop ->
                TutorialBackdropContent(
                    backdrop = backdrop,
                    anchors = if (backdrop == step.backdrop) anchors else discardedTransitionAnchors,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }.single().measure(Constraints.fixed(constraints.maxWidth, previewHeight))

        val scrim = subcompose(TutorialLayer.SCRIM) {
            TutorialScrim(
                spotlight = spotlight,
                cornerRadius = focusRadius,
                scrimColor = scrimColor,
                modifier = Modifier.fillMaxSize(),
            )
        }.single().measure(fullConstraints)

        val aura = subcompose(TutorialLayer.AURA) {
            TutorialSpotlightDecoration(
                spotlight = spotlight,
                accent = accent,
                cornerRadius = focusRadius,
                strokeWidth = SPOTLIGHT_STROKE,
                acquisition = focusSettle.value,
                modifier = Modifier.fillMaxSize(),
            )
        }.single().measure(fullConstraints)

        val tapSurface = subcompose(TutorialLayer.TAP_SURFACE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TUTORIAL_TAP_SURFACE_TAG)
                    .pointerInput(last, busy) {
                        detectTapGestures {
                            if (!busy) {
                                if (last) onExit() else onNext()
                            }
                        }
                    }
                    .clearAndSetSemantics { },
            )
        }.single().measure(fullConstraints)

        // Pointer-free test geometry. The decorative Canvas itself remains absent from TalkBack.
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
            tapSurface.place(0, 0)
            spotlightHandle?.place(
                spotlight.left.roundToInt(),
                spotlight.top.roundToInt(),
            )
            narration.place(0, constraints.maxHeight - narration.height)
            skip.place(0, 0)
        }
    }
}

/**
 * Fixed reading zone with no card boundary, elevation, border, or pointer handler. A theme-derived
 * bottom gradient supplies separation while taps pass through to the full-screen advance surface.
 */
@Composable
private fun TutorialNarration(
    stepIndex: Int,
    accent: Color,
    last: Boolean,
    busy: Boolean,
    completionError: String?,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val advanceLabel = stringResource(if (last) R.string.tutorial_finish else R.string.tutorial_next)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NARRATION_MIN_HEIGHT)
            .background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = 0f),
                    NARRATION_BLEND_MIDPOINT to background.copy(alpha = NARRATION_MID_ALPHA),
                    1f to background,
                ),
            )
            .drawBehind {
                drawRect(Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to accent.copy(alpha = 0.075f),
                    0.65f to accent.copy(alpha = 0.045f),
                    1f to Color.Transparent,
                ))
            }
            .navigationBarsPadding()
            .padding(start = Space.screenEdge, end = Space.screenEdge, top = Space.xxl, bottom = Space.l)
            .testTag(TUTORIAL_NARRATION_TAG)
            .semantics {
                if (!busy) {
                    onClick(label = advanceLabel) {
                        if (last) onFinish() else onNext()
                        true
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        val progressLabel = stringResource(R.string.tutorial_progress, stepIndex + 1, TUTORIAL_STEPS.size)
        TutorialProgressSegments(stepIndex = stepIndex, count = TUTORIAL_STEPS.size, accent = accent)
        Text(
            text = stringResource(
                R.string.tutorial_chapter_counter, stepIndex + 1, TUTORIAL_STEPS.size,
                stringResource(TUTORIAL_STEPS[stepIndex].chapterRes),
            ),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {
                testTag = TUTORIAL_PROGRESS_TAG
                contentDescription = progressLabel
            },
        )

        StableTutorialCopy(stepIndex)

        if (completionError != null) {
            Text(
                text = stringResource(R.string.tutorial_completion_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Text(
            text = stringResource(
                if (last) R.string.tutorial_tap_to_finish else R.string.tutorial_tap_to_continue,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { testTag = TUTORIAL_TAP_AFFORDANCE_TAG },
        )
    }
}

/** Measure every copy pair once per layout; only place the active animated copy. */
@Composable
private fun StableTutorialCopy(stepIndex: Int) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val copyConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val height = TUTORIAL_STEPS.indices.maxOf { index ->
            subcompose(index) { TutorialCopy(index, measuring = true) }
                .single().measure(copyConstraints).height
        }
        val active = subcompose("active") {
            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = {
                    fadeIn(tween(Motion.STANDARD_MS)).togetherWith(fadeOut(tween(Motion.QUICK_MS)))
                },
                contentAlignment = Alignment.TopStart,
                label = "tutorialNarrationCopy",
            ) { TutorialCopy(it) }
        }.single().measure(copyConstraints.copy(minHeight = height, maxHeight = height))
        layout(constraints.maxWidth, height) { active.place(0, 0) }
    }
}

@Composable
private fun TutorialCopy(index: Int, measuring: Boolean = false) {
    val step = TUTORIAL_STEPS[index]
    val title = stringResource(step.titleRes)
    val body = stringResource(step.bodyRes)
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.s),
        modifier = if (measuring) Modifier.clearAndSetSemantics { } else Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "$title $body"
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = if (measuring) Modifier else Modifier.testTag(TUTORIAL_TITLE_TAG),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp,
            modifier = if (measuring) Modifier else Modifier.testTag(TUTORIAL_BODY_TAG),
        )
    }
}

@Composable
private fun TutorialProgressSegments(stepIndex: Int, count: Int, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Space.xs).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        repeat(count) { index ->
            val color by animateColorAsState(
                when {
                    index == stepIndex -> accent
                    index < stepIndex -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                }, tween(Motion.STANDARD_MS), label = "tutorialRail",
            )
            Box(Modifier.weight(1f).height(2.dp).background(color))
        }
    }
}

/** The preview always owns exactly the viewport above the measured narration region. */
internal fun tutorialPreviewHeight(viewportHeight: Int, narrationHeight: Int): Int =
    (viewportHeight - narrationHeight).coerceAtLeast(0)

private enum class TutorialLayer {
    PREVIEW,
    SCRIM,
    AURA,
    TAP_SURFACE,
    SPOTLIGHT_HANDLE,
    NARRATION,
    SKIP,
}

private val NARRATION_MIN_HEIGHT = Space.xxl * 4
private const val NARRATION_BLEND_MIDPOINT = 0.36f
private const val NARRATION_MID_ALPHA = 0.88f
private const val BASE_SCRIM = 0.42f

private val RectVectorConverter = TwoWayConverter<Rect, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
    convertFromVector = { Rect(it.v1, it.v2, it.v3, it.v4) },
)
