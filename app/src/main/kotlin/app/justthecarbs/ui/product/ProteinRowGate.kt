package app.justthecarbs.ui.product

/**
 * Whether the calculator's optional protein row may be on screen: the portion field wins over it
 * (design spec 2026-09-24, section 4).
 *
 * The row is withheld whenever the portion zone, with the row added, would drop below a touch
 * target. The question is asked of the room as if the row were absent, so the answer does not
 * depend on whether the row is showing, and that is what keeps it stable (2026-09-25 review):
 *
 * - [zoneRoomPx] is the zone's room as last measured, [UNMEASURED] before the first layout.
 * - [occupyingPx] is the row's measured height while it is on screen and 0 while it is not, so
 *   `zoneRoomPx + occupyingPx` is the same room either way.
 * - [lastMeasuredPx] is the row's height the last time it was measured, kept after it leaves. The
 *   first version reset this to 0 on leaving, so a row taller than [estimatePx] (a wrapped "No
 *   online value", a figure stacked under its label) was costed at the smaller estimate once hidden,
 *   judged to fit, shown, judged not to fit, and so on every frame, with TalkBack re-announcing.
 *
 * Pure, so the whole cycle is a JVM test.
 */
internal object ProteinRowGate {
    const val UNMEASURED = -1

    fun fits(zoneRoomPx: Int, occupyingPx: Int, lastMeasuredPx: Int, estimatePx: Int, floorPx: Int): Boolean {
        if (zoneRoomPx == UNMEASURED) return true
        val roomWithoutRow = zoneRoomPx + occupyingPx
        val cost = maxOf(estimatePx, lastMeasuredPx)
        return roomWithoutRow - cost >= floorPx
    }
}
