package app.justthecarbs.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/** Stable handles for instrumented tests. */
const val TUTORIAL_OVERLAY_TAG = "tutorial_overlay"
const val TUTORIAL_TITLE_TAG = "tutorial_title"
const val TUTORIAL_BODY_TAG = "tutorial_body"
const val TUTORIAL_PRIMARY_TAG = "tutorial_primary"
const val TUTORIAL_SKIP_TAG = "tutorial_skip"
const val TUTORIAL_BACK_TAG = "tutorial_back"
const val TUTORIAL_PROGRESS_TAG = "tutorial_progress"

/** How much larger than its control the spotlight is drawn. */
private val SPOTLIGHT_PADDING = 10.dp

/**
 * Generous on purpose. A tight radius makes the hole read as a rectangle cut out of the dim; at this
 * size it reads as light falling on the control. Paired with the feathered edge and graded dim in
 * [TutorialScrim] — all three exist to stop the overlay looking like a box on top of the app.
 */
private val SPOTLIGHT_RADIUS = 28.dp
private val SPOTLIGHT_STROKE = 2.dp

/**
 * Roughly how tall the callout card is, used only to decide which side of the spotlight it goes on.
 *
 * An estimate rather than a measurement, deliberately generous: the decision is made before the card
 * is laid out, and over-estimating causes a centred fallback (always readable) while
 * under-estimating would place the card into a gap it does not fit in and let the screen edge clip
 * it. Scales with font size, so a large-text user gets a correspondingly larger reservation.
 */
private val CALLOUT_HEIGHT_ESTIMATE = 220.dp

/**
 * The first-launch tutorial: a coach-mark walkthrough over a deterministic preview of the app.
 *
 * Replaces the previous three-slide carousel. The carousel described the app in the abstract; this
 * points at the controls the user is about to use, using their real labels, over a rendering of the
 * screens they appear on.
 *
 * Nothing behind the scrim is real. [TutorialBackdropContent] draws constants — see its own
 * documentation for why that is a structural guarantee rather than a convention.
 *
 * @param onExit leaves the tutorial. What that means is the caller's business and differs by mode:
 *   Home on first run, Settings on replay. This screen never decides its own destination.
 */
@Composable
fun OnboardingScreen(
    stepIndex: Int,
    mode: TutorialMode,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExit: () -> Unit,
    completionError: String? = null,
    busy: Boolean = false,
) {
    val step = TUTORIAL_STEPS[stepIndex.coerceIn(0, TUTORIAL_LAST_STEP)]
    val last = stepIndex >= TUTORIAL_LAST_STEP
    val anchors = rememberTutorialAnchors()

    // System Back is an explicit exit, exactly like Skip, on both the first step and every later
    // one. Stepping backwards is what the Back *button* in the card is for; conflating the two would
    // mean the only way to leave from step 4 is to walk forward through 5 and 6.
    BackHandler(enabled = !busy) { onExit() }

    // A stale rectangle from the previous preview must not be pointed at while the new backdrop is
    // still being laid out — that is the one situation where an anchor exists but describes the
    // wrong screen. Cleared on backdrop change, so the overlay falls back to a centred callout for
    // the frame or two before the new controls report their bounds.
    LaunchedEffect(step.backdrop) { anchors.clear() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val paddingPx = with(density) { SPOTLIGHT_PADDING.toPx() }
        val calloutEstimatePx = with(density) { CALLOUT_HEIGHT_ESTIMATE.toPx() }

        val rawBounds = if (step.anchor == TutorialAnchor.NONE) null else anchors.boundsOf(step.anchor)
        val target: Rect? = rawBounds?.let { inflateWithin(it, paddingPx, widthPx, heightPx) }

        // The spotlight travels between steps instead of jumping.
        //
        // Only between two *known* targets, and that restriction is the whole of the safety
        // argument: animating out of or into null would slide the hole from the screen's origin, or
        // leave it briefly over a control the current step is not talking about. So an appearing or
        // disappearing target snaps, and only a move from one real rectangle to another is animated.
        //
        // Interpolated per edge rather than as a centre plus a size, so a target that changes shape
        // as well as position (a wide card to a small icon) stays a rectangle throughout.
        val previous = remember { mutableStateOf<Rect?>(null) }
        val progress = remember { Animatable(1f) }
        val from = previous.value

        LaunchedEffect(target) {
            val start = previous.value
            if (target != null && start != null && start != target) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(Motion.STANDARD_MS))
            } else {
                progress.snapTo(1f)
            }
            previous.value = target
        }

        val spotlight: Rect? = when {
            target == null -> null
            from == null || progress.value >= 1f -> target
            else -> lerpRect(from, target, progress.value)
        }

        val side = if (spotlight == null) {
            CalloutSide.CENTERED
        } else {
            calloutSideFor(
                spotlightTop = spotlight.top,
                spotlightBottom = spotlight.bottom,
                screenHeight = heightPx,
                requiredHeight = calloutEstimatePx,
            )
        }

        // The preview, then the scrim over it, then the ring and connector, then the controls. The
        // whole backdrop is marked decorative: while the tutorial is up, its controls must not be
        // separately reachable by TalkBack, or the user could tab to a "Scan barcode" card that does
        // nothing.
        TutorialBackdropContent(
            backdrop = step.backdrop,
            anchors = anchors,
            // `clearAndSetSemantics {}` with an empty block removes the whole subtree from the
            // semantics tree, which is what stops TalkBack reaching a preview "Scan barcode" card
            // that would do nothing if activated. The same idiom this codebase already uses for
            // decorative imagery and progress indicators.
            modifier = Modifier.clearAndSetSemantics { },
        )

        // Fades in once, on arrival. This previously animated from 1f to 1f, so it produced no
        // motion at all and the dim appeared instantly at full strength — the app looked as though
        // it had been switched off between one frame and the next.
        val scrimAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            scrimAlpha.animateTo(1f, tween(Motion.STANDARD_MS))
        }

        // A light base wash. The far corners get the rest of their weight from the radial gradient
        // inside TutorialScrim, so this number is not the strength of the dim the user sees at the
        // edges -- it is the strength near the control, which is what has to stay readable.
        TutorialScrim(
            spotlight = spotlight,
            cornerRadius = SPOTLIGHT_RADIUS,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = BASE_SCRIM * scrimAlpha.value),
            modifier = Modifier.fillMaxSize(),
        )

        val accent = MaterialTheme.colorScheme.primary

        // Where the connector leaves the card: the edge of the callout facing the target.
        val arrowStart: Offset? = when {
            spotlight == null -> null
            side == CalloutSide.CENTERED -> null
            side == CalloutSide.ABOVE -> Offset(spotlight.center.x, spotlight.top - calloutEstimatePx * 0.12f)
            else -> Offset(spotlight.center.x, spotlight.bottom + calloutEstimatePx * 0.12f)
        }

        // A slow breath on the halo only. Slow and shallow deliberately: this runs for as long as
        // the step is on screen, and anything faster becomes the loudest thing in the app.
        val breathing = rememberInfiniteTransition(label = "tutorialHalo")
        val pulse by breathing.animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(PULSE_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "tutorialHaloPulse",
        )

        TutorialSpotlightDecoration(
            spotlight = spotlight,
            arrowStart = arrowStart,
            accent = accent,
            cornerRadius = SPOTLIGHT_RADIUS,
            strokeWidth = SPOTLIGHT_STROKE,
            pulse = pulse,
            modifier = Modifier.fillMaxSize(),
        )

        val tutorialLabel = stringResource(R.string.tutorial_pane_title)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .testTag(TUTORIAL_OVERLAY_TAG)
                // Announced as the active modal so a screen-reader user is told the tutorial has
                // taken over rather than silently finding the app unresponsive.
                .semantics(mergeDescendants = false) {
                    isTraversalGroup = true
                    paneTitle = tutorialLabel
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screenEdge),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onExit,
                    enabled = !busy,
                    modifier = Modifier
                        .heightIn(min = Space.minTouchTarget)
                        .testTag(TUTORIAL_SKIP_TAG),
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_skip),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // The card sits in the half of the screen the spotlight is not in. Weighted spacers
            // rather than absolute offsets, so the arrangement survives any screen size without
            // arithmetic that could put the card off-screen.
            if (side == CalloutSide.BELOW || side == CalloutSide.CENTERED) {
                Spacer(Modifier.weight(1f))
            }

            // Cross-fades the words while the card itself stays put. Fading the whole card would
            // make the one fixed thing on screen flicker on every step; only its contents change.
            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = {
                    (fadeIn(tween(Motion.STANDARD_MS)) +
                        slideInVertically(tween(Motion.STANDARD_MS)) { it / 8 })
                        .togetherWith(fadeOut(tween(Motion.QUICK_MS)))
                },
                label = "tutorialCallout",
            ) { index ->
                val animated = TUTORIAL_STEPS[index.coerceIn(0, TUTORIAL_LAST_STEP)]
                CalloutCard(
                    title = stringResource(animated.titleRes),
                    body = stringResource(animated.bodyRes),
                    stepNumber = index + 1,
                    stepCount = TUTORIAL_STEPS.size,
                    last = index >= TUTORIAL_LAST_STEP,
                    canGoBack = index > 0,
                    busy = busy,
                    completionError = completionError,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onFinish = onExit,
                    modifier = Modifier.padding(horizontal = Space.screenEdge),
                )
            }

            if (side == CalloutSide.ABOVE || side == CalloutSide.CENTERED) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The instructional card: title, one sentence, progress, and the actions.
 *
 * The title and body are one polite live region, so a step change is announced as a single sentence
 * rather than as two separate interruptions — and politely, so it waits for whatever the user is
 * already hearing rather than cutting across it.
 */
@Composable
private fun CalloutCard(
    title: String,
    body: String,
    stepNumber: Int,
    stepCount: Int,
    last: Boolean,
    canGoBack: Boolean,
    busy: Boolean,
    completionError: String?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressLabel = stringResource(R.string.tutorial_progress, stepNumber, stepCount)
    val shape = RoundedCornerShape(CALLOUT_RADIUS)
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Lifted off the dim rather than sitting flat on it. Without a shadow the card is a
            // pale rectangle on a dark wash and its edges are the loudest thing about it; with one
            // it reads as a surface in front of the app, which is what it is.
            .shadow(CALLOUT_ELEVATION, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            // Height comes from the words. Without this the Row expands to the whole screen: the
            // spine asks to fill the height, and inside a parent that offers unbounded height a
            // `fillMaxHeight` child takes all of it and drags the card with it. Measured on the
            // device -- the card filled the screen top to bottom and the tutorial stopped being a
            // callout at all.
            .height(IntrinsicSize.Min),
    ) {
        // The accent spine, matching JtcTopBar and RecentCard elsewhere in the app. It also does the
        // work the old full-width border did -- giving the card an edge -- without drawing a box
        // around the words.
        Box(
            modifier = Modifier
                .width(SPINE_WIDTH)
                .fillMaxHeight()
                .background(accent),
        )

        Column(
            modifier = Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$title. $body"
                },
            ) {
                // The step count in words, above the title. The dots below say the same thing by
                // position; this says it in a form you can read at a glance, and gives the title
                // something to sit under so it does not start hard against the card's top edge.
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(TUTORIAL_TITLE_TAG),
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.testTag(TUTORIAL_BODY_TAG),
                )
            }

            // The dots communicate progress by position only, which is nothing to a screen reader.
            // The eyebrow above now carries the same thing in words, so this row is marked
            // decorative rather than announcing "Step 2 of 6" a second time.
            //
            // `invisibleToUser` rather than `clearAndSetSemantics {}`: the latter removes the whole
            // subtree from the semantics tree, and takes the test tag with it -- which is exactly
            // what it did, and what `progressIsRenderedForEveryStep` caught. The node has to stay
            // findable; it just must not be spoken.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .semantics { hideFromAccessibility() }
                    .testTag(TUTORIAL_PROGRESS_TAG),
            ) {
                repeat(stepCount) { index ->
                    val active = index == stepNumber - 1
                    // Widths animate so the indicator slides between steps instead of one dot
                    // blinking off and another on.
                    val dotWidth by animateDpAsState(
                        targetValue = if (active) 22.dp else 6.dp,
                        animationSpec = tween(Motion.STANDARD_MS),
                        label = "tutorialDot",
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = Space.xs)
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (active) accent else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }

            if (completionError != null) {
                Text(
                    text = stringResource(R.string.tutorial_completion_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (canGoBack) {
                    TextButton(
                        onClick = onPrevious,
                        enabled = !busy,
                        modifier = Modifier
                            .heightIn(min = Space.minTouchTarget)
                            .testTag(TUTORIAL_BACK_TAG),
                    ) {
                        Text(stringResource(R.string.tutorial_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = if (last) onFinish else onNext,
                    enabled = !busy,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier
                        .heightIn(min = Space.minTouchTarget)
                        .testTag(TUTORIAL_PRIMARY_TAG),
                ) {
                    Text(
                        text = stringResource(
                            if (last) R.string.tutorial_finish else R.string.tutorial_next,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/**
 * The card's own radius, larger than [Space.cardRadius].
 *
 * The callout is the one surface floating over a dimmed app rather than sitting in a list with
 * other cards, so it can afford a softer corner than the app's ordinary card idiom — and needs one,
 * since a tight radius here is most of what made the overlay read as boxy.
 */
private val CALLOUT_RADIUS = 26.dp
private val CALLOUT_ELEVATION = 12.dp
private val SPINE_WIDTH = 4.dp

/** How long one half of the halo's breath takes. Slow enough not to nag; see the call site. */
private const val PULSE_MS = 1400

/**
 * The dim immediately around the target.
 *
 * Well below the 0.78 this screen used to apply everywhere: at that strength the app underneath was
 * effectively gone, which is the opposite of what a tutorial pointing at real controls wants. The
 * far edges still reach a comparable weight through [TutorialScrim]'s radial gradient.
 */
private const val BASE_SCRIM = 0.42f

/**
 * Interpolate between two rectangles, edge by edge.
 *
 * Per-edge rather than centre-plus-size so a target that changes shape as well as position stays a
 * rectangle for every frame in between, instead of scaling through an intermediate aspect ratio.
 */
private fun lerpRect(from: Rect, to: Rect, fraction: Float): Rect = Rect(
    left = from.left + (to.left - from.left) * fraction,
    top = from.top + (to.top - from.top) * fraction,
    right = from.right + (to.right - from.right) * fraction,
    bottom = from.bottom + (to.bottom - from.bottom) * fraction,
)
