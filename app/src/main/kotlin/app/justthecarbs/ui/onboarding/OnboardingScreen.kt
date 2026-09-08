package app.justthecarbs.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
private val SPOTLIGHT_PADDING = 8.dp
private val SPOTLIGHT_RADIUS = 20.dp
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
        val spotlight: Rect? = rawBounds?.let { inflateWithin(it, paddingPx, widthPx, heightPx) }

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

        // Fades between steps rather than sliding: the target moves, and animating the hole across
        // the screen would draw the eye along a path rather than to a control.
        val scrimAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(Motion.STANDARD_MS),
            label = "tutorialScrim",
        )
        TutorialScrim(
            spotlight = spotlight,
            cornerRadius = SPOTLIGHT_RADIUS,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f * scrimAlpha),
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

        TutorialSpotlightDecoration(
            spotlight = spotlight,
            arrowStart = arrowStart,
            accent = accent,
            cornerRadius = SPOTLIGHT_RADIUS,
            strokeWidth = SPOTLIGHT_STROKE,
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

            CalloutCard(
                title = stringResource(step.titleRes),
                body = stringResource(step.bodyRes),
                stepNumber = stepIndex + 1,
                stepCount = TUTORIAL_STEPS.size,
                last = last,
                canGoBack = stepIndex > 0,
                busy = busy,
                completionError = completionError,
                onNext = onNext,
                onPrevious = onPrevious,
                onFinish = onExit,
                modifier = Modifier.padding(horizontal = Space.screenEdge),
            )

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.cardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Space.xs),
            modifier = Modifier.semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "$title. $body"
            },
        ) {
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
                modifier = Modifier.testTag(TUTORIAL_BODY_TAG),
            )
        }

        // Dots plus a spoken "Step 2 of 6": the dots alone communicate progress by position only,
        // which is nothing to a screen reader.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .semantics(mergeDescendants = true) { contentDescription = progressLabel }
                .testTag(TUTORIAL_PROGRESS_TAG),
        ) {
            repeat(stepCount) { index ->
                val active = index == stepNumber - 1
                Box(
                    modifier = Modifier
                        .padding(end = Space.xs)
                        .height(6.dp)
                        .width(if (active) 20.dp else 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
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
