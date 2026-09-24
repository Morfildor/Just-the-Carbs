package app.justthecarbs.ui.scan

import app.justthecarbs.domain.CarbPlausibility
import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * What the assisted typing steps offer for a figure **the user typed**, and what the keyboard's Done
 * key may do with it.
 *
 * Pure so the two rules below are pinned without a composition. It decides nothing new about which
 * values are acceptable: [CarbPlausibility] is still the only judge, asked per basis exactly as
 * before.
 *
 * ## The actions stay in place while nothing is typed
 *
 * [Actions.Pending] keeps the accept actions on screen, disabled, so the first keystroke does not
 * push the Back action down under the user's thumb. An impossible figure is different: it gets no
 * action at all and a sentence saying why ([Actions.Implausible]), because a disabled control on its
 * own would say nothing about what is wrong.
 *
 * ## Done submits only when there is no choice left to make
 *
 * [imeSubmission] is non-null only when the basis was fixed **before** the user typed (the label
 * stated it) and the figure is possible under it. With both bases open the user has to say which
 * they mean, and a basis that plausibility alone narrowed to is still not one the user chose, so in
 * both cases the keyboard submits nothing and the user taps the action.
 */
internal object TypedValueEntry {

    sealed interface Actions {
        /** Nothing parseable typed yet; [bases] are shown, not usable. */
        data class Pending(val bases: List<NutritionBasis>) : Actions

        /** [value] may be accepted under each of [bases], which is never empty. */
        data class Offered(val value: BigDecimal, val bases: List<NutritionBasis>) : Actions

        /** Typed, but impossible under every candidate basis. No accept action exists. */
        data object Implausible : Actions
    }

    /** Both separators, because the app is used where the comma is decimal. */
    fun parse(typed: String): BigDecimal? = typed.replace(',', '.').toBigDecimalOrNull()

    fun actions(typed: String, candidateBases: List<NutritionBasis>): Actions {
        val value = parse(typed) ?: return Actions.Pending(candidateBases)
        // Asked per basis, not once: 150 is impossible per 100 g and legitimate per 100 ml.
        val offered = candidateBases.filter { CarbPlausibility.isPlausiblePer100(value, it) }
        return if (offered.isEmpty()) Actions.Implausible else Actions.Offered(value, offered)
    }

    fun imeSubmission(typed: String, candidateBases: List<NutritionBasis>): Pair<BigDecimal, NutritionBasis>? {
        val basis = candidateBases.singleOrNull() ?: return null
        val offered = actions(typed, candidateBases) as? Actions.Offered ?: return null
        return offered.value to basis
    }
}
