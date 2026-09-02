package app.justthecarbs.ocr

import org.junit.Test

/**
 * Prints what each sixth-session capture now does, for the pass record. Asserts almost nothing.
 *
 * Deliberately a *diagnostic*, in the same style as this repo's other session diagnostics: the
 * outcome of a particular photograph is a property of that photograph, and pinning a table of them
 * turns a measurement into a brittle scoreboard. The properties that must hold are asserted in
 * [SixthSessionRegressionTest]; this exists so a person can see the before/after without a phone.
 */
class SixthSessionOutcomeTest {

    private fun describe(name: String, document: OcrDocument) {
        val report = NutritionTableInterpreter.interpret(document)
        val reading = report.reading
        val candidate = (reading as? LabelReading.Confident)?.candidate

        val scale = candidate?.let { ScaleAmbiguity.check(document, it) }
        val offered = RecoveryCandidates.of(document, DisputedCandidates.NONE)
            .joinToString(", ") { it.label }.ifEmpty { "(none)" }

        println("=== $name ===")
        println("  parser    : ${reading::class.simpleName} ${candidate?.value ?: ""} ${candidate?.basis ?: ""}")
        println("  scale     : $scale")
        println("  recovery  : $offered")
        println("  statedBasis: ${StatedBasis.of(document)}")
    }

    @Test
    fun `the two truffle sauce captures`() {
        describe("131511 conflicted", SixthSessionFixtures.sauceConflictedRuns())
        describe("131545 shared scale", SixthSessionFixtures.sauceSharedScaleCollapse())
    }
}
