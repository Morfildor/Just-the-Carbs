package app.justthecarbs.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/** The feature's existing colour echoes through focus, rail and narration. */
@Composable
internal fun TutorialAccent.color(): Color = when (this) {
    TutorialAccent.PRIMARY -> MaterialTheme.colorScheme.primary
    TutorialAccent.BARCODE -> MaterialTheme.colorScheme.primary
    TutorialAccent.SEARCH -> {
        val scheme = MaterialTheme.colorScheme
        val lighterTone = if (scheme.background.luminance() < 0.5f) scheme.onBackground else scheme.background
        lerp(scheme.primary, lighterTone, 0.12f)
    }
    TutorialAccent.LABEL -> MaterialTheme.extendedColors.accents.green
    TutorialAccent.PORTION -> MaterialTheme.colorScheme.tertiary
    TutorialAccent.RESULT -> MaterialTheme.extendedColors.result
}

/** Decoration only: none of these values affect the measured aperture or preview. */
internal data class TutorialLightField(
    val bloomSpread: Float = 1f,
    val bloomAlpha: Float = 0.15f,
    val contextRelief: Float = 0f,
)

internal fun TutorialAccent.lightField(): TutorialLightField = when (this) {
    TutorialAccent.PRIMARY -> TutorialLightField(1.38f, 0.14f)
    TutorialAccent.BARCODE -> TutorialLightField(1f, 0.17f)
    TutorialAccent.SEARCH -> TutorialLightField(1.04f, 0.13f)
    TutorialAccent.LABEL -> TutorialLightField(1.04f, 0.18f)
    TutorialAccent.PORTION -> TutorialLightField(1.10f, 0.19f, contextRelief = 0.12f)
    TutorialAccent.RESULT -> TutorialLightField(1f, 0.12f)
}

internal data class TutorialFocusTreatment(
    val bloomAlphaMultiplier: Float,
    val bloomSpreadMultiplier: Float,
    val edgeAlpha: Float,
    val edgeWidth: Dp,
    val arrivalBoost: Float,
)

internal fun TutorialFocusEmphasis.treatment(): TutorialFocusTreatment = when (this) {
    TutorialFocusEmphasis.STANDARD -> TutorialFocusTreatment(
        bloomAlphaMultiplier = 1.08f,
        bloomSpreadMultiplier = 1f,
        edgeAlpha = 0.72f,
        edgeWidth = 1.15.dp,
        arrivalBoost = 0.12f,
    )
    TutorialFocusEmphasis.STRONG -> TutorialFocusTreatment(
        bloomAlphaMultiplier = 1.34f,
        bloomSpreadMultiplier = 1.03f,
        edgeAlpha = 0.94f,
        edgeWidth = 1.35.dp,
        arrivalBoost = 0.20f,
    )
}

/** A contrast-aware tonal definition, never a raw white outline. */
@Composable
internal fun tutorialFocusEdgeColor(kind: TutorialAccent, accent: Color): Color {
    val scheme = MaterialTheme.colorScheme
    if (kind == TutorialAccent.SEARCH) return scheme.primary
    return if (scheme.background.luminance() < 0.5f) {
        lerp(accent, scheme.onBackground, 0.30f)
    } else {
        lerp(accent, scheme.surfaceContainerLowest, 0.44f)
    }
}

/** Ground-colored casing separates the semantic pointer from variable preview content. */
@Composable
internal fun tutorialPointerCasingColor(): Color = MaterialTheme.colorScheme.background

/** One finite acquisition: arrive above steady emphasis, then settle exactly to 1. */
internal fun tutorialFocusArrival(progress: Float, boost: Float): Float {
    val phase = progress.coerceIn(0f, 1f)
    return if (phase <= 0.48f) {
        val local = phase / 0.48f
        0.68f + (1f + boost - 0.68f) * local
    } else {
        val local = (phase - 0.48f) / 0.52f
        1f + boost * (1f - local)
    }
}

internal fun tutorialFocusArrivalSpread(progress: Float, boost: Float): Float {
    val phase = progress.coerceIn(0f, 1f)
    return if (phase <= 0.48f) {
        val local = phase / 0.48f
        0.92f + (1f + boost * 0.72f - 0.92f) * local
    } else {
        val local = (phase - 0.48f) / 0.52f
        1f + boost * 0.72f * (1f - local)
    }
}

/** Unreached chapters carry no colour; completed chapters keep their own identity. */
internal fun tutorialRailColor(index: Int, current: Int, own: Color, active: Color, track: Color): Color =
    when {
        index < current -> own.copy(alpha = 0.30f)
        index == current -> active
        else -> track
    }

/** Orange remains luminous in decoration; its existing amber sibling carries small text. */
@Composable
internal fun TutorialAccent.ink(): Color = lerp(
    MaterialTheme.colorScheme.onBackground,
    if (this == TutorialAccent.PORTION) MaterialTheme.extendedColors.accents.amber else color(),
    0.72f,
)

/** Dark surfaces need less emitted colour; use the app theme, not the system setting. */
@Composable
internal fun tutorialLightIntensity(): Float =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.70f else 1f

internal data class TutorialScrimTone(
    val baseAlpha: Float,
    val edgeAlpha: Float,
    val localAlpha: Float,
)

/** Light theme keeps its warm ground; dark theme needs only a slight additional veil. */
internal fun tutorialScrimTone(backgroundLuminance: Float): TutorialScrimTone =
    if (backgroundLuminance < 0.5f) {
        TutorialScrimTone(baseAlpha = 0.14f, edgeAlpha = 0.035f, localAlpha = 0.04f)
    } else {
        TutorialScrimTone(baseAlpha = 0.21f, edgeAlpha = 0.05f, localAlpha = 0.055f)
    }

@Composable
internal fun tutorialScrimTone(): TutorialScrimTone =
    tutorialScrimTone(MaterialTheme.colorScheme.background.luminance())

/** START stays open and bright; targeted chapters retain the established contextual veil. */
internal fun TutorialFocusStyle.scrimMultiplier(): Float =
    if (this == TutorialFocusStyle.ORIENTATION) 0.68f else 1f

internal enum class TutorialSpotlightTransition { DIRECT, RELEASE_ACQUIRE }

/**
 * Direct interpolation is safe only when both rectangles still describe substantially the same
 * visual feature. Distinct controls release and reacquire instead of stretching through the gap.
 */
internal fun tutorialSpotlightTransition(from: Rect?, to: Rect?): TutorialSpotlightTransition {
    val source = from ?: return TutorialSpotlightTransition.RELEASE_ACQUIRE
    val destination = to ?: return TutorialSpotlightTransition.RELEASE_ACQUIRE
    if (!source.isUsableFocusRect() || !destination.isUsableFocusRect()) {
        return TutorialSpotlightTransition.RELEASE_ACQUIRE
    }
    val overlapWidth = (minOf(source.right, destination.right) - maxOf(source.left, destination.left))
        .coerceAtLeast(0f)
    val overlapHeight = (minOf(source.bottom, destination.bottom) - maxOf(source.top, destination.top))
        .coerceAtLeast(0f)
    val overlap = overlapWidth * overlapHeight /
        minOf(source.width * source.height, destination.width * destination.height)
    val fromAspect = source.width / source.height
    val toAspect = destination.width / destination.height
    val aspectShift = maxOf(fromAspect, toAspect) / minOf(fromAspect, toAspect)
    return if (overlap >= 0.30f && aspectShift <= 1.60f) {
        TutorialSpotlightTransition.DIRECT
    } else {
        TutorialSpotlightTransition.RELEASE_ACQUIRE
    }
}

private fun Rect.isUsableFocusRect(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
    width > 0f && height > 0f

/** Radius includes the aperture's padding; bounds always come from measured anchors. */
internal fun TutorialFocusStyle.radius(padding: Dp): Dp = when (this) {
    TutorialFocusStyle.ORIENTATION -> Space.sheetTopRadius
    TutorialFocusStyle.CARD -> Space.cardRadius + padding
    TutorialFocusStyle.CONTROL -> Space.buttonRadius + padding
    TutorialFocusStyle.RESULT -> Space.sheetTopRadius + padding
}
