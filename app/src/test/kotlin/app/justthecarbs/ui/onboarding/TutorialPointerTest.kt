package app.justthecarbs.ui.onboarding

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialPointerTest {
    private val viewport = Rect(0f, 0f, 1_080f, 2_400f)

    @Test
    fun `pointer coordinates are finite and remain outside both interiors`() {
        val teaching = Rect(120f, 1_260f, 960f, 1_680f)
        val target = Rect(72f, 480f, 1_008f, 680f)

        val geometry = pointer(teaching, target)!!

        listOf(geometry.start, geometry.control1, geometry.control2, geometry.end).forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite())
        }
        assertFalse(teaching.contains(geometry.start))
        assertFalse(target.contains(geometry.end))
        assertTrue(geometry.end.y > target.bottom)
    }

    @Test
    fun `pointer approaches a target below without entering it`() {
        val teaching = Rect(120f, 1_180f, 960f, 1_580f)
        val target = Rect(0f, 2_020f, 1_080f, 2_360f)

        val geometry = pointer(teaching, target)!!

        assertTrue(geometry.start.y > teaching.bottom)
        assertTrue(geometry.end.y < target.top)
        assertFalse(target.contains(geometry.end))
    }

    @Test
    fun `overlapping teaching and target omit the pointer`() {
        assertNull(pointer(Rect(120f, 900f, 960f, 1_400f), Rect(72f, 1_200f, 1_008f, 1_520f)))
    }

    @Test
    fun `unsafe exclusion omits the only route`() {
        val teaching = Rect(120f, 1_260f, 960f, 1_680f)
        val target = Rect(72f, 480f, 1_008f, 680f)
        val corridor = Rect(0f, 680f, 1_080f, 1_260f)

        assertNull(pointer(teaching, target, listOf(corridor)))
    }

    @Test
    fun `occupied direct corridor selects a safe side endpoint`() {
        val teaching = Rect(220f, 1_260f, 860f, 1_620f)
        val target = Rect(72f, 480f, 1_008f, 680f)
        val centeredProgress = Rect(300f, 920f, 780f, 1_180f)

        val geometry = pointer(teaching, target, listOf(centeredProgress))!!

        assertTrue(geometry.end.x < target.left || geometry.end.x > target.right)
        assertFalse(centeredProgress.contains(cubicPoint(geometry, 0.5f)))
    }

    @Test
    fun `short cramped gap omits the pointer`() {
        assertNull(pointer(Rect(120f, 1_010f, 960f, 1_400f), Rect(0f, 780f, 1_080f, 990f)))
    }

    @Test
    fun `normal phone barcode search label and meal targets all resolve`() {
        val homeTeaching = Rect(120f, 1_090f, 960f, 1_410f)
        val search = Rect(54f, 315f, 1_026f, 441f)
        val barcode = Rect(54f, 460f, 1_026f, 607f)
        val label = Rect(54f, 626f, 1_026f, 774f)
        val progress = Rect(300f, 930f, 780f, 1_045f)
        val homeTargets = listOf(search, barcode, label)

        homeTargets.forEach { target ->
            val geometry = normalPointer(
                teaching = homeTeaching,
                target = target,
                exclusions = homeTargets.filterNot { it == target } + progress,
            )
            assertTrue("ordinary HOME target must have a route: $target", geometry != null)
        }

        val meal = normalPointer(
            teaching = Rect(120f, 1_090f, 960f, 1_410f),
            target = Rect(54f, 758f, 1_026f, 885f),
            exclusions = listOf(Rect(300f, 930f, 780f, 1_045f)),
        )
        assertTrue("ordinary Add to meal target must have a route", meal != null)
    }

    private fun pointer(
        teaching: Rect,
        target: Rect,
        exclusions: List<Rect> = emptyList(),
    ): TutorialPointerGeometry? = tutorialPointerGeometry(
        teaching = teaching,
        target = target,
        viewport = viewport,
        exclusions = exclusions,
        startGap = 24f,
        endGap = 36f,
        edgeInset = 36f,
        viewportInset = 18f,
        minLength = 72f,
        maxLength = 1_050f,
    )

    private fun normalPointer(
        teaching: Rect,
        target: Rect,
        exclusions: List<Rect>,
    ): TutorialPointerGeometry? = tutorialPointerGeometry(
        teaching = teaching,
        target = target,
        viewport = viewport,
        exclusions = exclusions,
        startGap = 16f,
        endGap = 22f,
        edgeInset = 38f,
        viewportInset = 27f,
        minLength = 65f,
        maxLength = 1_188f,
    )
}
