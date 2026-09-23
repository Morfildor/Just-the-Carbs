package app.justthecarbs.ui.components

/**
 * How the calculator's identity header uses the height the portion controls leave it.
 *
 * Pure and in pixels, so the choice is JVM-testable: the header's own layout measures what it needs
 * and asks this function which arrangement fits.
 */
internal sealed interface IdentityLayout {
    /** A photo container [photoHeight] tall across the width, the product facts under it. */
    data class Hero(val photoHeight: Int) : IdentityLayout

    /** A square [thumbnail] container beside the facts. */
    data class Row(val thumbnail: Int) : IdentityLayout

    /** Nothing: the keyboard left no room for even the smallest row. */
    data object Hidden : IdentityLayout
}

/** One of the row's fixed thumbnail sizes and the row's height with it (the taller of the two). */
internal data class RowOption(val thumbnail: Int, val height: Int)

/**
 * Chooses the header's arrangement.
 *
 * Every size is one of a few predefined steps rather than any height at all, so the image does not
 * creep with every dp of spare room, and the same product on the same screen in the same state is
 * always drawn the same size. In order:
 *
 * 1. The largest of [heroHeights] that fits under [available] with its caption.
 * 2. The largest of [rowOptions] that fits.
 * 3. The smallest row, which is the header's floor while the keyboard is closed: the calculator
 *    reserves it before the portion controls take their height, so it may exceed [available] only
 *    when the portion controls themselves overflow and scroll.
 *
 * While the keyboard is open the room shrinks frame by frame as it animates, so there is no
 * stepping at all: the smallest row, or nothing when even that does not fit.
 *
 * @param available the height the calculator left for the header, or [Int.MAX_VALUE] if unbounded.
 * @param heroCaption the facts' height when laid out across the full width, under a hero photo.
 * @param captionGap the space between the hero photo and its caption.
 */
internal fun identityLayoutFor(
    heroCapable: Boolean,
    keyboardOpen: Boolean,
    available: Int,
    heroCaption: Int,
    captionGap: Int,
    heroHeights: List<Int>,
    rowOptions: List<RowOption>,
): IdentityLayout {
    val smallestRow = rowOptions.minBy { it.thumbnail }
    if (keyboardOpen) {
        return if (available < smallestRow.height) IdentityLayout.Hidden else IdentityLayout.Row(smallestRow.thumbnail)
    }
    if (heroCapable) {
        val room = if (available == Int.MAX_VALUE) Int.MAX_VALUE else available - heroCaption - captionGap
        heroHeights.filter { it <= room }.maxOrNull()?.let { return IdentityLayout.Hero(it) }
    }
    val row = rowOptions.filter { it.height <= available }.maxByOrNull { it.thumbnail } ?: smallestRow
    return IdentityLayout.Row(row.thumbnail)
}
