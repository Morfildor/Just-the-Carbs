package app.justthecarbs.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.SpaceGrotesk
import app.justthecarbs.ui.theme.extendedColors

private data class Slide(
    val eyebrowRes: Int,
    val titleRes: Int,
    val bodyRes: Int,
)

private val SLIDES = listOf(
    Slide(R.string.onboarding_step_1, R.string.onboarding_title_1, R.string.onboarding_body_1),
    Slide(R.string.onboarding_step_2, R.string.onboarding_title_2, R.string.onboarding_body_2),
    Slide(R.string.onboarding_step_3, R.string.onboarding_title_3, R.string.onboarding_body_3),
)

/** Stable handles for instrumented tests. */
const val CAROUSEL_ROOT_TAG = "welcome_carousel"
const val CAROUSEL_TITLE_TAG = "welcome_carousel_title"
const val CAROUSEL_PRIMARY_TAG = "welcome_carousel_primary"
const val CAROUSEL_SKIP_TAG = "welcome_carousel_skip"

/**
 * First-launch, 3-slide welcome carousel (design doc "Onboarding"). Slide 1: blue bg, slide 2: cream
 * bg, slide 3: red bg — colors read from the theme's own tokens rather than hardcoded, so dark mode
 * gets a sensible extrapolation automatically.
 *
 * This is the *first* of the app's two introductions and the only one the app opens by itself. It
 * states the rhythm — scan, portion, carbs — in the abstract, because on a first launch there is no
 * data on screen and no control worth pointing at yet. Finishing it lands on Home, where
 * [OnboardingScreen]'s coach-mark tutorial is then *offered* rather than imposed. The two are
 * deliberately separate screens with separate flags; see `AppSettings.hasSeenOnboarding`.
 */
@Composable
fun WelcomeCarouselScreen(
    slideIndex: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onGetStarted: () -> Unit,
    onSlideChanged: (Int) -> Unit = {},
    completionError: String? = null,
    busy: Boolean = false,
) {
    val last = slideIndex == SLIDES.lastIndex

    // `slideIndex` stays the single source of truth; the pager is an input device for it, not a
    // second copy of the state. The two effects below are deliberately asymmetric:
    //
    //  - settled page -> state: reads `settledPage`, not `currentPage`, so a half-finished drag
    //    that springs back does not count as having changed slide.
    //  - state -> pager: animates when Next or Skip moved the index, which is what makes the button
    //    and the gesture produce the same motion instead of the button teleporting.
    val pagerState = rememberPagerState(initialPage = slideIndex) { SLIDES.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != slideIndex) onSlideChanged(page)
        }
    }
    LaunchedEffect(slideIndex) {
        if (pagerState.currentPage != slideIndex) pagerState.animateScrollToPage(slideIndex)
    }

    // Switch each tested semantic pair as one unit. Independently tweening foreground and
    // background creates intermediate frames where neither endpoint's contrast guarantee holds.
    // Pager/content motion remains owned by HorizontalPager below.
    val background = when (slideIndex) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.extendedColors.result
    }
    val foreground = when (slideIndex) {
        0 -> MaterialTheme.colorScheme.onPrimary
        1 -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.extendedColors.onResult
    }
    val markColor = if (slideIndex == 1) MaterialTheme.colorScheme.tertiary else foreground

    Box(modifier = Modifier.fillMaxSize().background(background).testTag(CAROUSEL_ROOT_TAG)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(220.dp)
                .background(foreground.copy(alpha = 0.14f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(180.dp)
                .background(foreground.copy(alpha = 0.14f), CircleShape),
        )

        // Inset padding on the CONTENT, not on the Box above it, so the coloured background and its
        // decorative circles still bleed to the screen edges while Skip and the CTA stay clear of
        // the system bars.
        //
        // This screen was the one place in the app with no inset handling at all, which left Skip
        // sitting underneath the status bar. It was survivable while the bars were transparent —
        // the button was drawn over its own blue background and merely sat high. Against the opaque
        // black bars this app now paints, the same layout clips it outright, so the pre-existing
        // defect became a visible one and is fixed here rather than left for the screen that
        // exposed it.
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Space.screenEdge),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!last) {
                    // heightIn, not the TextButton's own default: Material's text button is 40dp
                    // tall, below the 48dp floor (§39). The carousel shipped that way before it was
                    // removed and it came back with the same defect; caught here by an instrumented
                    // assertion rather than by eye, which is the only way a 8dp shortfall gets
                    // noticed.
                    TextButton(
                        onClick = onSkip,
                        enabled = !busy,
                        modifier = Modifier
                            .heightIn(min = Space.minTouchTarget)
                            .testTag(CAROUSEL_SKIP_TAG),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            color = foreground.copy(alpha = WELCOME_SUPPORTING_ALPHA),
                        )
                    }
                }
            }

            // Only the slide content pages. The background colour, Skip and the CTA are shared
            // chrome that switches semantic colour pairs with `slideIndex` instead of sliding, so
            // a swipe moves the words while the screen itself stays put.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                // No looping: the last slide is an endpoint with its own action, and wrapping back
                // to slide 1 from it would hide that the sequence had finished.
                pageSpacing = 0.dp,
            ) { page ->
                val pageSlide = SLIDES[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.xl),
                    verticalArrangement = Arrangement.Center,
                ) {
                    OnboardingMark(color = markColor, size = 64.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(pageSlide.eyebrowRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = foreground.copy(alpha = WELCOME_SUPPORTING_ALPHA),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(pageSlide.titleRes),
                        fontFamily = SpaceGrotesk,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                        lineHeight = 46.sp,
                        color = foreground,
                        modifier = Modifier.testTag(CAROUSEL_TITLE_TAG),
                    )
                    Spacer(Modifier.height(Space.m))
                    Text(
                        text = stringResource(pageSlide.bodyRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = foreground.copy(alpha = WELCOME_SUPPORTING_ALPHA),
                        modifier = Modifier.widthIn(max = 280.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = Space.xl, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Dots(count = SLIDES.size, active = slideIndex, color = foreground)

                val ctaContainer = when {
                    last -> MaterialTheme.colorScheme.surfaceContainerLowest
                    slideIndex == 1 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerLowest
                }
                val ctaContent = when {
                    last -> MaterialTheme.extendedColors.result
                    slideIndex == 1 -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.primary
                }

                Button(
                    onClick = if (last) onGetStarted else onNext,
                    enabled = !busy,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ctaContainer,
                        contentColor = ctaContent,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight).testTag(CAROUSEL_PRIMARY_TAG),
                ) {
                    Text(
                        text = stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                // A failed save is said out loud, the same way `manual_error_save` and
                // `quick_save_failed` are elsewhere in this app: a static, friendly message rather
                // than the raw exception text, positioned directly beneath the button that failed
                // so the explanation is where the user is already looking. Only reachable on the
                // last slide, where `onGetStarted` is the button's action -- `completionError` stays
                // null on every earlier slide because `onNext` never touches the repository.
                if (last && completionError != null) {
                    Text(
                        text = stringResource(R.string.onboarding_completion_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = foreground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

/** Lowest alpha used for readable carousel copy; every slide pair stays at or above 4.5:1. */
private const val WELCOME_SUPPORTING_ALPHA = 0.96f

@Composable
private fun Dots(count: Int, active: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        repeat(count) { index ->
            val width by animateDpAsState(
                targetValue = if (index == active) 22.dp else 7.dp,
                animationSpec = tween(220),
                label = "dotWidth",
            )
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(RoundedCornerShape(Space.xs))
                    .background(color.copy(alpha = if (index == active) 1f else 0.35f)),
            )
        }
    }
}

/**
 * The app's icon mark (design doc "Assets"): a rounded square "sliced open" at the top, revealing
 * a smaller solid circle with a light center dot. New to this redesign — a placeholder pending
 * sign-off, not a final logo (per the design handoff), so it is drawn inline here rather than
 * wired up as the launcher icon.
 *
 * Approximated with rects/circles only (no bezier curves), since the mark is explicitly a
 * placeholder and a pixel-exact reproduction of the mockup's SVG path is not worth the
 * geometry-debugging cost here — the silhouette (square tile, a band cut across its top revealing
 * the circle beneath, circle with a light center dot) is what needs to read, not the exact curve.
 */
@Composable
private fun OnboardingMark(color: Color, size: Dp) {
    // The real brand mark, not a drawing of one.
    //
    // This was a hand-built Canvas approximation — a rounded tile with a band and a dot — that
    // resembled nothing the user had seen. The app it introduces is identified everywhere else by
    // the split container with three drips: on the launcher icon they tapped, on the splash screen
    // they just watched, and in the Play listing. Onboarding is the first screen after that splash,
    // and it was the one place showing a different mark.
    //
    // `ic_launcher_foreground` is the right source here (unlike the splash, which needed its own
    // plate) because its paths are a single flat colour and onboarding tints them to sit on a
    // coloured slide — which is exactly what a monochrome/themed icon is for.
    Icon(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size),
    )
}
