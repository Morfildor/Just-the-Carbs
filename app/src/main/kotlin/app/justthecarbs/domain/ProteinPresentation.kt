package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * What the calculator's optional protein row shows, decided from data alone.
 *
 * Protein is a second reading, never a second answer (design spec 2026-09-24). This is the data half
 * of that spec's state table in one pure function, so every row of the table is a JVM test. The two
 * layout gates, the open keyboard and a window too short to keep the portion field, belong to the
 * screen, which is the layer that knows them; they only ever turn a [Value] or [NoOnlineValue] into
 * nothing, never the reverse.
 *
 * The rules, in order:
 * - the setting is off, there is no product, or there is no answer yet: nothing;
 * - the product is user-authored (typed in, read from a label, a quick calculation): nothing. No
 *   protein is stored for those in this version, and saying "no online value" about them would be
 *   untrue;
 * - the portion is a direct-carb unit: nothing. No weight exists on that path, so no protein figure
 *   can, and none is invented;
 * - a protein figure exists for the portion: [Value];
 * - otherwise the online record lists none: [NoOnlineValue]. Never 0 g, never an estimate.
 */
sealed interface ProteinPresentation {

    data object Hidden : ProteinPresentation

    /** The exact protein in the portion, unrounded; the screen formats it with the carb style. */
    data class Value(val exact: BigDecimal) : ProteinPresentation

    /** The online record has no usable protein value. A fact about the record, not the package. */
    data object NoOnlineValue : ProteinPresentation

    companion object {
        fun of(
            enabled: Boolean,
            product: Product?,
            exactProtein: BigDecimal?,
            hasAnswer: Boolean,
            directCarbPortion: Boolean,
        ): ProteinPresentation {
            if (!enabled || product == null || !hasAnswer) return Hidden
            if (product.dataSource.isUserAuthored) return Hidden
            if (directCarbPortion) return Hidden
            if (exactProtein != null) return Value(exactProtein)
            // A product that has a figure but no protein result has no resolved gram portion.
            if (product.proteinPer100 != null) return Hidden
            return NoOnlineValue
        }
    }
}
