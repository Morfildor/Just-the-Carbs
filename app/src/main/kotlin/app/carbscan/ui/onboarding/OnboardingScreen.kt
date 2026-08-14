package app.carbscan.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carbscan.R
import app.carbscan.ui.theme.Space
import app.carbscan.ui.theme.SpaceGrotesk
import app.carbscan.ui.theme.extendedColors

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

/**
 * First-launch, 3-slide carousel (design doc "Onboarding"). Slide 1: blue bg, slide 2: cream bg,
 * slide 3: red bg — colors read from the theme's own tokens rather than hardcoded, so dark mode
 * gets a sensible extrapolation automatically.
 */
@Composable
fun OnboardingScreen(
    slideIndex: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onGetStarted: () -> Unit,
) {
    val slide = SLIDES[slideIndex]
    val last = slideIndex == SLIDES.lastIndex

    val background by animateColorAsState(
        targetValue = when (slideIndex) {
            0 -> MaterialTheme.colorScheme.primary
            1 -> MaterialTheme.colorScheme.background
            else -> MaterialTheme.extendedColors.result
        },
        animationSpec = tween(280),
        label = "onboardingBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (slideIndex == 1) MaterialTheme.colorScheme.onBackground else Color.White,
        animationSpec = tween(280),
        label = "onboardingForeground",
    )
    val markColor = if (slideIndex == 1) MaterialTheme.colorScheme.tertiary else Color.White

    Box(modifier = Modifier.fillMaxSize().background(background)) {
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

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Space.screenEdge),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!last) {
                    TextButton(onClick = onSkip) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            color = foreground.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                OnboardingMark(color = markColor, size = 64.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(slide.eyebrowRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(slide.titleRes),
                    fontFamily = SpaceGrotesk,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp,
                    lineHeight = 46.sp,
                    color = foreground,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(slide.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = foreground.copy(alpha = 0.85f),
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Dots(count = SLIDES.size, active = slideIndex, color = foreground)

                val ctaContainer = when {
                    last -> Color.White
                    slideIndex == 1 -> MaterialTheme.colorScheme.primary
                    else -> Color.White
                }
                val ctaContent = when {
                    last -> MaterialTheme.extendedColors.result
                    slideIndex == 1 -> Color.White
                    else -> background
                }

                Button(
                    onClick = if (last) onGetStarted else onNext,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ctaContainer,
                        contentColor = ctaContent,
                    ),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                ) {
                    Text(
                        text = stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun Dots(count: Int, active: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    .clip(RoundedCornerShape(4.dp))
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
    val centerDot = MaterialTheme.colorScheme.background
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width / 64f
        val tileTopLeft = Offset(4f * s, 4f * s)
        val tileSize = Size(56f * s, 56f * s)
        val tileCorner = CornerRadius(18f * s, 18f * s)

        // The tile itself, at low opacity.
        drawRoundRect(
            color = color.copy(alpha = 0.16f),
            topLeft = tileTopLeft,
            size = tileSize,
            cornerRadius = tileCorner,
        )

        // The "sliced open" band across the top third, clipped to the tile's rounded corners so
        // it reads as part of the same shape rather than a separate rectangle overlapping it.
        val tileRoundRect = RoundRect(
            rect = Rect(offset = tileTopLeft, size = tileSize),
            cornerRadius = tileCorner,
        )
        clipPath(Path().apply { addRoundRect(tileRoundRect) }) {
            drawRect(color = color, topLeft = tileTopLeft, size = Size(56f * s, 22f * s))
        }

        // The core circle with a light center dot.
        drawCircle(color = color, radius = 13f * s, center = Offset(32f * s, 38f * s))
        drawCircle(color = centerDot, radius = 6f * s, center = Offset(32f * s, 38f * s))
    }
}
