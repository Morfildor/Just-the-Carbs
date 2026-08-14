package app.carbscan.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * How often one particular portion has been used for one product (brief §13, §22).
 *
 * An aggregate, not an event: a count and a last-used time, never a list of occasions. The app can
 * therefore answer "what is this product's usual portion?" and cannot answer "when did you eat?".
 */
data class PortionUsage(
    val id: Long = 0,
    val productBarcode: String,
    val inputMode: InputMode,
    /** The countable unit [amount] counts, or null in [InputMode.GRAMS]. */
    val portionUnitId: Long?,
    /** A count ("2" slices) in PORTION_UNIT mode, or a base amount ("60") in GRAMS mode. */
    val amount: BigDecimal,
    val usageCount: Int,
    val lastUsedAt: Instant,
)

/**
 * Picks the *Usual* shortcuts for a product (brief §13).
 *
 * Deterministic, local, and pure — no AI, no model, no network. The rules, in order:
 *
 * 1. **A single isolated use never becomes a suggestion.** Offering a shortcut after one use would
 *    turn "I once weighed 63 g" into a standing recommendation. [MIN_USES_TO_SUGGEST] is the whole
 *    of the brief's "repeated portions may become suggestions".
 * 2. **Frequency dominates**; recency only breaks ties. The portion used most is the usual one even
 *    if something else was used last.
 * 3. **At most [MAX_SUGGESTIONS]**, because a row of shortcuts the user has to read is not a
 *    shortcut.
 *
 * A suggestion is only ever *offered*. Nothing here calculates anything or fills a field on its
 * own — the user taps, and only then does a portion get entered (§13).
 */
object UsualPortionSelector {

    /** Two uses, not one: the first use is evidence of nothing, the second is a pattern. */
    const val MIN_USES_TO_SUGGEST = 2

    /** Three, per the brief's explicit maximum. */
    const val MAX_SUGGESTIONS = 3

    /**
     * The usual portions for one product, best first.
     *
     * Gram, millilitre and countable variants stay distinct throughout: a "2" that meant two slices
     * is never offered as two grams, because [PortionUsage.inputMode] and
     * [PortionUsage.portionUnitId] are part of a variant's identity, not decoration on it.
     */
    fun suggest(usages: List<PortionUsage>): List<PortionUsage> =
        usages
            .filter { it.usageCount >= MIN_USES_TO_SUGGEST && it.amount.signum() > 0 }
            .sortedWith(
                compareByDescending<PortionUsage> { it.usageCount }
                    .thenByDescending { it.lastUsedAt },
            )
            .take(MAX_SUGGESTIONS)

    /**
     * Variants worth deleting for a product (§22's "prune low-value old variants").
     *
     * Keeps anything that already qualifies as a suggestion, plus the most recent [KEEP_RECENT]
     * single-use variants so a portion on its way to becoming usual is not pruned before it gets
     * there. Everything else is noise: a one-off amount typed months ago will never become a
     * suggestion, and keeping it only grows a record of what the user ate.
     */
    fun prunable(usages: List<PortionUsage>): List<PortionUsage> {
        val qualifying = usages.filter { it.usageCount >= MIN_USES_TO_SUGGEST }.toSet()
        val recentSingles = usages
            .filterNot { it in qualifying }
            .sortedByDescending { it.lastUsedAt }
            .take(KEEP_RECENT)
            .toSet()
        return usages.filterNot { it in qualifying || it in recentSingles }
    }

    private const val KEEP_RECENT = 3
}
