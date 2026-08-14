package app.carbscan.domain

import java.math.BigDecimal

/**
 * Converts a countable portion (e.g. "2 slices") into the gram/ml amount [CarbCalculator] already
 * accepts. This is a conversion layer only — it never computes a carbohydrate value itself, so the
 * app keeps exactly one calculation formula (countable-portions brief §1).
 */
object PortionResolver {

    fun resolve(count: BigDecimal, amountPerUnit: BigDecimal): BigDecimal {
        require(count.signum() >= 0) { "count must not be negative" }
        require(amountPerUnit.signum() >= 0) { "amountPerUnit must not be negative" }

        return count.multiply(amountPerUnit)
    }
}
