package app.justthecarbs.ui.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.hypot

/** A cubic editorial pointer from the teaching statement toward a measured feature. */
internal data class TutorialPointerGeometry(
    val start: Offset,
    val control1: Offset,
    val control2: Offset,
    val end: Offset,
)

/**
 * Finds the shortest safe route between disjoint edges of [teaching] and [target].
 *
 * All distances are supplied in pixels so this remains a pure geometry function. Returning null is
 * intentional: a cramped or colliding arrow is worse than no arrow.
 */
internal fun tutorialPointerGeometry(
    teaching: Rect,
    target: Rect,
    viewport: Rect,
    exclusions: List<Rect> = emptyList(),
    startGap: Float,
    endGap: Float,
    edgeInset: Float,
    viewportInset: Float,
    minLength: Float,
    maxLength: Float,
): TutorialPointerGeometry? {
    if (!teaching.isUsable() || !target.isUsable() || !viewport.isUsable()) return null
    if (teaching.overlaps(target)) return null

    val candidates = buildList {
        if (target.bottom <= teaching.top) {
            val start = Offset(
                target.center.x.coerceIn(teaching.left + edgeInset, teaching.right - edgeInset),
                teaching.top - startGap,
            )
            val end = Offset(
                teaching.center.x.coerceIn(target.left + edgeInset, target.right - edgeInset),
                target.bottom + endGap,
            )
            if (end.y < start.y) add(verticalCurve(start, end, viewport))
        }
        if (target.top >= teaching.bottom) {
            val start = Offset(
                target.center.x.coerceIn(teaching.left + edgeInset, teaching.right - edgeInset),
                teaching.bottom + startGap,
            )
            val end = Offset(
                teaching.center.x.coerceIn(target.left + edgeInset, target.right - edgeInset),
                target.top - endGap,
            )
            if (end.y > start.y) add(verticalCurve(start, end, viewport))
        }
        if (target.right <= teaching.left) {
            val start = Offset(
                teaching.left - startGap,
                target.center.y.coerceIn(teaching.top + edgeInset, teaching.bottom - edgeInset),
            )
            val end = Offset(
                target.right + endGap,
                teaching.center.y.coerceIn(target.top + edgeInset, target.bottom - edgeInset),
            )
            if (end.x < start.x) add(horizontalCurve(start, end, viewport))
        }
        if (target.left >= teaching.right) {
            val start = Offset(
                teaching.right + startGap,
                target.center.y.coerceIn(teaching.top + edgeInset, teaching.bottom - edgeInset),
            )
            val end = Offset(
                target.left - endGap,
                teaching.center.y.coerceIn(target.top + edgeInset, target.bottom - edgeInset),
            )
            if (end.x > start.x) add(horizontalCurve(start, end, viewport))
        }
        val sideStartY = when {
            target.bottom <= teaching.top -> insetCoordinate(
                teaching.top, teaching.bottom, edgeInset, teaching.top + edgeInset,
            )
            target.top >= teaching.bottom -> insetCoordinate(
                teaching.top, teaching.bottom, edgeInset, teaching.bottom - edgeInset,
            )
            else -> insetCoordinate(teaching.top, teaching.bottom, edgeInset, target.center.y)
        }
        val sideEndY = when {
            target.bottom <= teaching.top -> insetCoordinate(
                target.top, target.bottom, edgeInset, target.bottom - edgeInset,
            )
            target.top >= teaching.bottom -> insetCoordinate(
                target.top, target.bottom, edgeInset, target.top + edgeInset,
            )
            else -> insetCoordinate(target.top, target.bottom, edgeInset, teaching.center.y)
        }
        val leftEndX = target.left - endGap
        if (leftEndX >= viewport.left + viewportInset) {
            add(
                sideCurve(
                    start = Offset(teaching.left - startGap, sideStartY),
                    end = Offset(leftEndX, sideEndY),
                    targetDirection = 1f,
                    viewport = viewport,
                    viewportInset = viewportInset,
                ),
            )
        }
        val rightEndX = target.right + endGap
        if (rightEndX <= viewport.right - viewportInset) {
            add(
                sideCurve(
                    start = Offset(teaching.right + startGap, sideStartY),
                    end = Offset(rightEndX, sideEndY),
                    targetDirection = -1f,
                    viewport = viewport,
                    viewportInset = viewportInset,
                ),
            )
        }
    }

    val safeViewport = Rect(
        viewport.left + viewportInset,
        viewport.top + viewportInset,
        viewport.right - viewportInset,
        viewport.bottom - viewportInset,
    )
    return candidates
        .sortedBy { distance(it.start, it.end) }
        .firstOrNull { geometry ->
            val length = distance(geometry.start, geometry.end)
            length in minLength..maxLength &&
                sampleCubic(geometry).all { point ->
                    safeViewport.contains(point) &&
                        !teaching.contains(point) &&
                        !target.contains(point) &&
                        exclusions.none { it.contains(point) }
                }
        }
}

internal fun cubicPoint(geometry: TutorialPointerGeometry, fraction: Float): Offset {
    val t = fraction.coerceIn(0f, 1f)
    val inverse = 1f - t
    val a = inverse * inverse * inverse
    val b = 3f * inverse * inverse * t
    val c = 3f * inverse * t * t
    val d = t * t * t
    return Offset(
        x = a * geometry.start.x + b * geometry.control1.x + c * geometry.control2.x + d * geometry.end.x,
        y = a * geometry.start.y + b * geometry.control1.y + c * geometry.control2.y + d * geometry.end.y,
    )
}

internal fun cubicTangent(geometry: TutorialPointerGeometry, fraction: Float): Offset {
    val t = fraction.coerceIn(0f, 1f)
    val inverse = 1f - t
    return Offset(
        x = 3f * inverse * inverse * (geometry.control1.x - geometry.start.x) +
            6f * inverse * t * (geometry.control2.x - geometry.control1.x) +
            3f * t * t * (geometry.end.x - geometry.control2.x),
        y = 3f * inverse * inverse * (geometry.control1.y - geometry.start.y) +
            6f * inverse * t * (geometry.control2.y - geometry.control1.y) +
            3f * t * t * (geometry.end.y - geometry.control2.y),
    )
}

private fun verticalCurve(start: Offset, end: Offset, viewport: Rect): TutorialPointerGeometry {
    val delta = end - start
    val bend = (distance(start, end) * 0.10f).coerceAtMost(viewport.width * 0.045f)
    val direction = if ((start.x + end.x) / 2f <= viewport.center.x) -1f else 1f
    return TutorialPointerGeometry(
        start,
        Offset(start.x + direction * bend, start.y + delta.y * 0.36f),
        Offset(end.x + direction * bend, end.y - delta.y * 0.28f),
        end,
    )
}

private fun horizontalCurve(start: Offset, end: Offset, viewport: Rect): TutorialPointerGeometry {
    val delta = end - start
    val bend = (distance(start, end) * 0.10f).coerceAtMost(viewport.height * 0.045f)
    val direction = if ((start.y + end.y) / 2f <= viewport.center.y) -1f else 1f
    return TutorialPointerGeometry(
        start,
        Offset(start.x + delta.x * 0.36f, start.y + direction * bend),
        Offset(end.x - delta.x * 0.28f, end.y + direction * bend),
        end,
    )
}

private fun sideCurve(
    start: Offset,
    end: Offset,
    targetDirection: Float,
    viewport: Rect,
    viewportInset: Float,
): TutorialPointerGeometry {
    val horizontalHandle = (viewport.width * 0.025f).coerceAtLeast(1f)
    val outerX = (end.x - targetDirection * horizontalHandle).coerceIn(
        viewport.left + viewportInset,
        viewport.right - viewportInset,
    )
    val routeX = if (targetDirection > 0f) {
        (viewport.left + viewportInset + viewport.width * 0.015f).coerceAtMost(end.x + horizontalHandle)
    } else {
        (viewport.right - viewportInset - viewport.width * 0.015f).coerceAtLeast(end.x - horizontalHandle)
    }
    return TutorialPointerGeometry(
        start = start,
        control1 = Offset(
            routeX,
            start.y + (end.y - start.y) * 0.34f,
        ),
        control2 = Offset(
            outerX,
            end.y - (end.y - start.y) * 0.12f,
        ),
        end = end,
    )
}

private fun sampleCubic(geometry: TutorialPointerGeometry): List<Offset> =
    (0..POINTER_SAFETY_SAMPLES).map { cubicPoint(geometry, it / POINTER_SAFETY_SAMPLES.toFloat()) }

private fun Rect.isUsable(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() && width > 0f && height > 0f

private fun distance(first: Offset, second: Offset): Float = hypot(second.x - first.x, second.y - first.y)

private fun insetCoordinate(min: Float, max: Float, inset: Float, preferred: Float): Float {
    val low = min + inset
    val high = max - inset
    return if (low <= high) preferred.coerceIn(low, high) else (min + max) / 2f
}

private const val POINTER_SAFETY_SAMPLES = 24
