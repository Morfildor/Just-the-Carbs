package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * Whether a per-100 carbohydrate figure is physically possible at all (1.0.3 P0).
 *
 * ## Why this exists separately from [NutritionValueValidator]
 *
 * It does not hold a rule of its own — it asks that validator and reports the answer as a boolean.
 * The reason for the indirection is the *shape* of the question, not the rule behind it.
 *
 * [NutritionValueValidator] is a gate: it takes a raw remote number and returns the accepted value
 * or null, which is exactly right when the app is deciding whether to *store* something. The
 * assisted reading path needs the same rule asked a different way — the value already exists as a
 * [BigDecimal] the user is looking at, and the question is whether to *offer an action* for it. A
 * caller forced to phrase that as `validate(x.toDouble(), basis) != null` would be converting a
 * decimal to a double purely to throw the result away, which is the kind of line that gets
 * "simplified" into a local `> 100` check the next time someone reads it. That is precisely how a
 * second, drifting copy of a safety rule gets born.
 *
 * So: one rule, in [NutritionValueValidator]; two phrasings of the question. Any change to the
 * ceilings happens there and is pinned as agreeing here by
 * `CarbPlausibilityTest.the barrier agrees with the remote value validator across the range`.
 *
 * ## What it deliberately cannot do
 *
 * There is no `correct()`, no `clamp()`, and no function here that returns a number. `790` does not
 * become `79.0` and does not become `7.90`. The decimal point is the single thing OCR is least
 * reliable about, so an app that repositions it is guessing at exactly what it has least standing
 * to guess — and unlike a refusal, a wrong repair is invisible: the user sees a plausible number
 * and has no reason to check it. A refusal costs a retype; a repair can cost a dose.
 */
object CarbPlausibility {

    /**
     * True when [value] could really be the carbohydrate content of 100 g/ml of a food.
     *
     * False is a statement about physics, not about likelihood: per 100 g the carbohydrate cannot
     * outweigh the food containing it, and per 100 ml no edible liquid approaches the density that
     * the ceiling there implies. Unusual-but-possible values are all true.
     */
    fun isPlausiblePer100(value: BigDecimal, basis: NutritionBasis): Boolean =
        NutritionValueValidator.validateCarbsPer100(value.toDouble(), basis) != null

    /**
     * True when [value] is possible under at least one basis.
     *
     * Asked as its own question because the two answers mean different things to a caller. A value
     * plausible under one basis and not the other is a value whose *unit* is in doubt — offer the
     * possible one. A value plausible under neither is not a carbohydrate figure at all, and no
     * choice of unit rescues it, so there is nothing to offer and the user needs a way to correct
     * it rather than a menu.
     */
    fun hasAnyPlausibleBasis(value: BigDecimal): Boolean =
        NutritionBasis.entries.any { isPlausiblePer100(value, it) }
}
