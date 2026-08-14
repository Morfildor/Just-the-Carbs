package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Usual portions (development-pass brief §13, §29).
 *
 * The failure mode worth guarding against is a suggestion that is confidently wrong: offering "2"
 * for a product where 2 once meant slices and now means grams, or promoting a one-off amount into a
 * standing recommendation. Both would put a wrong number one tap away.
 */
class UsualPortionSelectorTest {

    private val base = Instant.parse("2026-08-14T10:00:00Z")
    private var nextId = 1L

    private fun usage(
        amount: String,
        count: Int,
        secondsAgo: Long = 0,
        mode: InputMode = InputMode.GRAMS,
        unitId: Long? = null,
        barcode: String = "5449000000996",
    ) = PortionUsage(
        id = nextId++,
        productBarcode = barcode,
        inputMode = mode,
        portionUnitId = unitId,
        amount = BigDecimal(amount),
        usageCount = count,
        lastUsedAt = base.minusSeconds(secondsAgo),
    )

    // ---- a single use must not become a suggestion ---------------------------------------------

    @Test
    fun `a portion used once is not suggested`() {
        val suggestions = UsualPortionSelector.suggest(listOf(usage("63", count = 1)))
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `a portion used twice is suggested`() {
        val suggestions = UsualPortionSelector.suggest(listOf(usage("60", count = 2)))
        assertEquals(1, suggestions.size)
        assertEquals(0, suggestions.first().amount.compareTo(BigDecimal("60")))
    }

    @Test
    fun `several one-off portions produce no suggestions at all`() {
        val suggestions = UsualPortionSelector.suggest(
            listOf(usage("63", 1), usage("47", 1), usage("112", 1)),
        )
        assertTrue(suggestions.isEmpty())
    }

    // ---- ordering -------------------------------------------------------------------------------

    @Test
    fun `frequency dominates recency`() {
        // 60 g used 7 times but a while ago; 85 g used twice, most recently.
        val frequent = usage("60", count = 7, secondsAgo = 86_400)
        val recent = usage("85", count = 2, secondsAgo = 60)

        val suggestions = UsualPortionSelector.suggest(listOf(recent, frequent))

        assertEquals(0, suggestions[0].amount.compareTo(BigDecimal("60")))
        assertEquals(0, suggestions[1].amount.compareTo(BigDecimal("85")))
    }

    @Test
    fun `recency breaks a frequency tie`() {
        val older = usage("40", count = 3, secondsAgo = 86_400)
        val newer = usage("55", count = 3, secondsAgo = 60)

        val suggestions = UsualPortionSelector.suggest(listOf(older, newer))

        assertEquals(0, suggestions[0].amount.compareTo(BigDecimal("55")))
        assertEquals(0, suggestions[1].amount.compareTo(BigDecimal("40")))
    }

    @Test
    fun `at most three suggestions are offered`() {
        val many = (1..8).map { usage("${it * 10}", count = 10 - it) }
        assertEquals(UsualPortionSelector.MAX_SUGGESTIONS, UsualPortionSelector.suggest(many).size)
        assertEquals(3, UsualPortionSelector.suggest(many).size)
    }

    // ---- variants must not be conflated ---------------------------------------------------------

    /**
     * The most dangerous confusion in this feature: "2" meaning two slices and "2" meaning two
     * grams are different portions that happen to share a number.
     */
    @Test
    fun `a countable count and a gram amount with the same number stay distinct`() {
        val twoSlices = usage("2", count = 5, mode = InputMode.PORTION_UNIT, unitId = 4)
        val twoGrams = usage("2", count = 3, mode = InputMode.GRAMS)

        val suggestions = UsualPortionSelector.suggest(listOf(twoSlices, twoGrams))

        assertEquals(2, suggestions.size)
        assertEquals(InputMode.PORTION_UNIT, suggestions[0].inputMode)
        assertEquals(4L, suggestions[0].portionUnitId)
        assertEquals(InputMode.GRAMS, suggestions[1].inputMode)
        assertEquals(null, suggestions[1].portionUnitId)
    }

    @Test
    fun `counts of different countable units stay distinct`() {
        val twoSlices = usage("2", count = 5, mode = InputMode.PORTION_UNIT, unitId = 4)
        val twoBiscuits = usage("2", count = 4, mode = InputMode.PORTION_UNIT, unitId = 9)

        val suggestions = UsualPortionSelector.suggest(listOf(twoSlices, twoBiscuits))

        assertEquals(2, suggestions.size)
        assertEquals(listOf(4L, 9L), suggestions.map { it.portionUnitId })
    }

    /**
     * Grams and millilitres never interconvert (§17). They are distinct here for free — a product
     * has exactly one basis, so a per-100-ml product's usage rows are ml and a per-100-g product's
     * are g, and rows are only ever read for one product at a time.
     */
    @Test
    fun `usage is read per product so g and ml products never share suggestions`() {
        val breadGrams = usage("60", count = 4, barcode = "bread")
        val juiceMl = usage("250", count = 4, barcode = "juice")

        val forBread = UsualPortionSelector.suggest(listOf(breadGrams, juiceMl).filter { it.productBarcode == "bread" })

        assertEquals(1, forBread.size)
        assertEquals(0, forBread.first().amount.compareTo(BigDecimal("60")))
    }

    @Test
    fun `a zero or negative amount is never suggested`() {
        val suggestions = UsualPortionSelector.suggest(
            listOf(usage("0", count = 9), usage("60", count = 2)),
        )
        assertEquals(1, suggestions.size)
        assertEquals(0, suggestions.first().amount.compareTo(BigDecimal("60")))
    }

    // ---- pruning --------------------------------------------------------------------------------

    @Test
    fun `qualifying variants are never pruned`() {
        val qualifying = usage("60", count = 5)
        assertTrue(UsualPortionSelector.prunable(listOf(qualifying)).isEmpty())
    }

    @Test
    fun `old one-off variants are pruned but recent ones are kept`() {
        val recent = (1..3).map { usage("${it}0", count = 1, secondsAgo = it.toLong()) }
        val stale = (1..4).map { usage("${it}00", count = 1, secondsAgo = 100_000L + it) }

        val prunable = UsualPortionSelector.prunable(recent + stale)

        assertEquals(4, prunable.size)
        assertTrue(prunable.all { it in stale })
    }

    @Test
    fun `pruning keeps qualifying variants even when they are old`() {
        val oldButUsual = usage("60", count = 6, secondsAgo = 1_000_000)
        val recentOneOff = usage("75", count = 1, secondsAgo = 10)

        val prunable = UsualPortionSelector.prunable(listOf(oldButUsual, recentOneOff))

        assertTrue(prunable.isEmpty())
        assertEquals(1, UsualPortionSelector.suggest(listOf(oldButUsual, recentOneOff)).size)
    }
}
