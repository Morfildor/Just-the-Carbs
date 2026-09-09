package app.justthecarbs.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space

/** Stable handles for instrumented tests. */
const val TUTORIAL_OVERLAY_TAG = "tutorial_overlay"
const val TUTORIAL_TITLE_TAG = "tutorial_title"
const val TUTORIAL_BODY_TAG = "tutorial_body"
const val TUTORIAL_SKIP_TAG = "tutorial_skip"
const val TUTORIAL_TAP_SURFACE_TAG = "tutorial_tap_surface"

/** The callout card itself — the node carrying the accessible Next/Finish `onClick` action. */
const val TUTORIAL_CALLOUT_TAG = "tutorial_callout"

/**
 * The purely-visual "Tap anywhere to continue/finish" line. Its tag lives inside
 * `clearAndSetSemantics {}` rather than a separate `Modifier.testTag()` call, because a tag applied
 * outside that block would be wiped along with everything else it clears — see the call site.
 */
const val TUTORIAL_TAP_AFFORDANCE_TAG = "tutorial_tap_affordance"

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
 * The first-launch tutorial: a full-screen walkthrough over a deterministic preview of the app.
 *
 * Tapping almost anywhere on screen advances to the next step — the highlighted control is visual
 * context, never a precision target the user must aim for. Skip is the one deliberate exception: it
 * sits above the tap-catching surface in composition order, so Compose's own pointer-input dispatch
 * gives it first refusal on any tap that lands on it, structurally, before that tap could ever reach
 * the "advance" surface underneath.
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
    onExit: () -> Unit,
    completionError: String? = null,
    busy: Boolean = false,
) {
    val step = TUTORIAL_STEPS[stepIndex.coerceIn(0, TUTORIAL_LAST_STEP)]
    val last = stepIndex >= TUTORIAL_LAST_STEP
    val anchors = rememberTutorialAnchors()

    // System Back is an explicit exit, exactly like Skip.
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
        val safeAreaBottomPx = with(density) {
            WindowInsets.systemBars.asPaddingValues(density).calculateBottomPadding().toPx()
        }

        val rawBounds = if (step.anchor == TutorialAnchor.NONE) null else anchors.boundsOf(step.anchor)
        val target: Rect? = rawBounds?.let { inflateWithin(it, paddingPx, widthPx, heightPx) }

        // The spotlight travels between steps instead of jumping.
        //
        // Only between two *known* targets, and that restriction is the whole of the safety
        // argument: animating out of or into null would slide the hole from the screen's origin, or
        // leave it briefly over a control the current step is not talking about. So an appearing or
        // disappearing target snaps, and only a move from one real rectangle to another is animated.
        //
        // A single Animatable<Rect> holds the RENDERED rectangle directly, rather than splitting the
        // state into a separately-read "previous" rect plus a 0..1 progress float. That split used to
        // let a recomposition land between the two updates: on the frame `target` first changed, the
        // read of `previous.value` and the read of `progress.value` could still both reflect the OLD
        // step (progress left at 1f from the last completed animation) while `target` already named
        // the NEW one -- so the `progress.value >= 1f` branch fired early and rendered the new target
        // outright, one frame before the LaunchedEffect below had even run to snap progress back to
        // 0 and start the real animation from the old rect. The next recomposition then jumped BACK
        // to the old rect and animated forward from there -- a visible flash-then-rewind. Reading the
        // Animatable's own value can't produce that: there is only one piece of state, so there is no
        // stale combination to observe mid-update.
        val spotlightAnimation = remember { Animatable(target ?: Rect.Zero, RectVectorConverter) }
        val previousTarget = remember { mutableStateOf(target) }

        LaunchedEffect(target) {
            val start = previousTarget.value
            previousTarget.value = target
            when {
                target == null -> Unit // keep whatever is currently drawn; the caller reads null below
                start == null || start == target -> spotlightAnimation.snapTo(target)
                else -> spotlightAnimation.animateTo(target, tween(Motion.STANDARD_MS))
            }
        }

        val spotlight: Rect? = if (target == null) null else spotlightAnimation.value

        // The preview, then the scrim over it, then the ring, then the controls. The whole backdrop
        // is marked decorative: while the tutorial is up, its controls must not be separately
        // reachable by TalkBack, or the user could tab to a "Scan barcode" card that does nothing.
        TutorialBackdropContent(
            backdrop = step.backdrop,
            anchors = anchors,
            modifier = Modifier.clearAndSetSemantics { },
        )

        // Fades in once, on arrival.
        val scrimAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            scrimAlpha.animateTo(1f, tween(Motion.STANDARD_MS))
        }

        TutorialScrim(
            spotlight = spotlight,
            cornerRadius = SPOTLIGHT_RADIUS,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = BASE_SCRIM * scrimAlpha.value),
            modifier = Modifier.fillMaxSize(),
        )

        val accent = MaterialTheme.colorScheme.primary

        TutorialSpotlightDecoration(
            spotlight = spotlight,
            accent = accent,
            cornerRadius = SPOTLIGHT_RADIUS,
            strokeWidth = SPOTLIGHT_STROKE,
            modifier = Modifier.fillMaxSize(),
        )

        // The dedicated tap-anywhere surface. It sits ABOVE the backdrop/scrim/spotlight and BELOW
        // the callout card and Skip in composition order — that ordering is the entire correctness
        // argument. Because Skip and the card render afterward (i.e. on top), Compose gives their
        // own pointer-input regions first refusal on a tap that lands on them; only a tap that
        // reaches neither falls through to this box and calls onNext/onFinish. There is no manual
        // "did this tap hit Skip's bounds" check anywhere in this file.
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
                // Decorative: the tap surface has no meaning of its own to announce. TalkBack
                // reaches "advance" through Skip and the callout card's own onClick action instead
                // — see CalloutCard.
                .clearAndSetSemantics { },
        )

        val tutorialLabel = stringResource(R.string.tutorial_pane_title)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .testTag(TUTORIAL_OVERLAY_TAG)
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

            // AnimatedContent cross-fades the words while the card itself stays put.
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
                val cardLast = index >= TUTORIAL_LAST_STEP
                val title = stringResource(animated.titleRes)
                val body = stringResource(animated.bodyRes)
                val progressLabel = stringResource(R.string.tutorial_progress, index + 1, TUTORIAL_STEPS.size)

                // Measures the real CalloutCard once (with real strings, real font scale) to decide
                // bottom vs. top, then places it — a single extra measure pass around content that
                // already exists, not a second placement engine. See CalloutPlacement.kt.
                SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
                    val cardConstraints = constraints.copy(minHeight = 0)
                    val measured = subcompose("card") {
                        CalloutCard(
                            title = title,
                            body = body,
                            progressLabel = progressLabel,
                            last = cardLast,
                            busy = busy,
                            completionError = completionError,
                            onNext = onNext,
                            onFinish = onExit,
                            modifier = Modifier.padding(horizontal = Space.screenEdge),
                        )
                    }.first().measure(cardConstraints)

                    val side = if (spotlight == null) {
                        CalloutSide.CENTERED
                    } else {
                        calloutSideFor(
                            spotlightTop = spotlight.top,
                            spotlightBottom = spotlight.bottom,
                            screenHeight = heightPx,
                            cardHeight = measured.height.toFloat(),
                            safeAreaBottomInset = safeAreaBottomPx,
                        )
                    }

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val y = when (side) {
                            CalloutSide.BELOW -> constraints.maxHeight - measured.height
                            CalloutSide.ABOVE -> 0
                            CalloutSide.CENTERED -> (constraints.maxHeight - measured.height) / 2
                            // The emergency case: the card fits neither clear zone. Clamp into
                            // [0, maxHeight - height] so it stays fully on screen (never partly
                            // above y=0 or spilling past the bottom edge) rather than picking a side
                            // that is, by construction, already known to overlap the spotlight.
                            CalloutSide.CLAMPED ->
                                (constraints.maxHeight - measured.height).coerceAtLeast(0)
                        }
                        measured.place(0, y)
                    }
                }
            }
        }
    }
}

/**
 * The instructional card: title, one sentence, step count, and a restrained tap-anywhere affordance.
 *
 * The title and body are one polite live region, so a step change is announced as a single sentence
 * rather than as two separate interruptions — and politely, so it waits for whatever the user is
 * already hearing rather than cutting across it.
 *
 * There is no visible button. Tap-anywhere is the sighted-user progression model, so a primary
 * button here would be a second, competing way to do the same thing and would draw the eye away
 * from the words it exists to support. The card carries no `clickable` of its own and no pointer
 * input at all: a tap anywhere on it — including the affordance line — falls through to the
 * tap-anywhere surface beneath it, exactly like a tap on open scrim.
 *
 * TalkBack still gets a real Next/Finish action. [Modifier.semantics] `onClick` attaches an
 * accessibility action without installing a pointer-input gesture handler, so it adds nothing for a
 * sighted user to accidentally hit and steals no touch from the tap-anywhere surface — but explore-
 * by-touch exposes it as the card's double-tap action, labelled from the same `tutorial_next` /
 * `tutorial_finish` strings the old button used.
 */
@Composable
private fun CalloutCard(
    title: String,
    body: String,
    progressLabel: String,
    last: Boolean,
    busy: Boolean,
    completionError: String?,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CALLOUT_RADIUS)
    val accent = MaterialTheme.colorScheme.primary
    val advanceLabel = stringResource(if (last) R.string.tutorial_finish else R.string.tutorial_next)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TUTORIAL_CALLOUT_TAG)
            .shadow(CALLOUT_ELEVATION, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .height(IntrinsicSize.Min)
            .semantics {
                if (!busy) {
                    onClick(label = advanceLabel) {
                        if (last) onFinish() else onNext()
                        true
                    }
                }
            },
    ) {
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

            Text(
                text = stringResource(
                    if (last) R.string.tutorial_tap_to_finish else R.string.tutorial_tap_to_continue,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    // Purely visual restatement of the card's own onClick action above -- giving it
                    // semantics of its own would announce the affordance twice. The tag has to go
                    // INSIDE the clearAndSetSemantics block, not chained before it -- a testTag set
                    // outside is wiped along with everything else and the node becomes unfindable by
                    // tag, exactly as SearchScreen's SEARCH_REFRESH_PROGRESS_TAG already documents.
                    .clearAndSetSemantics { testTag = TUTORIAL_TAP_AFFORDANCE_TAG },
            )
        }
    }
}

/**
 * The card's own radius, larger than [Space.cardRadius].
 *
 * The callout is the one surface floating over a dimmed app rather than sitting in a list with
 * other cards, so it can afford a softer corner than the app's ordinary card idiom.
 */
private val CALLOUT_RADIUS = 26.dp
private val CALLOUT_ELEVATION = 12.dp
private val SPINE_WIDTH = 4.dp

/**
 * The dim immediately around the target.
 *
 * Well below the 0.78 this screen used to apply everywhere: at that strength the app underneath was
 * effectively gone, which is the opposite of what a tutorial pointing at real controls wants. The
 * far edges still reach a comparable weight through [TutorialScrim]'s radial gradient.
 */
private const val BASE_SCRIM = 0.42f

/**
 * Lets [Animatable] hold a [Rect] directly, animating all four edges together.
 *
 * Per-edge rather than centre-plus-size, so a target that changes shape as well as position (a wide
 * card to a small icon) stays a rectangle for every frame in between, instead of scaling through an
 * intermediate aspect ratio.
 */
private val RectVectorConverter = TwoWayConverter<Rect, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
    convertFromVector = { Rect(it.v1, it.v2, it.v3, it.v4) },
)
