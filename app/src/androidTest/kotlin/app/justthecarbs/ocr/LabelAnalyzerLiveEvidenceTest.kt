package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class LabelAnalyzerLiveEvidenceTest {
    private val candidate = CarbCandidate(
        sourceLine = "Koolhydraten 61.9 g", label = "Koolhydraten", value = BigDecimal("61.9"),
        basis = NutritionBasis.PER_100_G, score = 120, geometry = OcrBox(10, 10, 100, 40),
        evidence = emptyList(),
    )

    @Test fun everyRawFrameReachesEvidenceEvenWhenUiStabilitySuppressesIt() {
        val buffer = LiveEvidenceBuffer()
        val surfaced = mutableListOf<LabelReading>()
        val analyzer = LabelAnalyzer(
            onReading = { surfaced += it },
            onObservation = { buffer.record(it.reading, it.timestampMs, it.aimEpoch) },
        )
        try {
            repeat(3) { analyzer.publishLiveReading(LabelReading.Confident(candidate), 1000L + it * 50, 0) }
            analyzer.publishLiveReading(LabelReading.NotFound, 1200, 0)
            assertNull("NotFound must invalidate pre-shutter consensus", buffer.stableConsensus(1250))
            val ambiguity = LabelReading.Ambiguous(listOf(candidate, candidate.copy(value = BigDecimal("6.19"))))
            analyzer.publishLiveReading(ambiguity, 1300, 0)
            assertEquals("even the first ambiguous frame is evidence", ambiguity, buffer.snapshot().last().reading)
            assertEquals("UI stability behavior is preserved", 3, surfaced.size)
        } finally {
            analyzer.close()
        }
    }

    @Test fun aFrameStartedBeforeRetakeCannotPublishIntoTheNewAim() {
        var epoch = 0L
        val observed = mutableListOf<LiveEvidenceBuffer.Observation>()
        val surfaced = mutableListOf<LabelReading>()
        val analyzer = LabelAnalyzer(
            onReading = { surfaced += it }, onObservation = { observed += it }, aimEpoch = { epoch },
        )
        try {
            analyzer.pause()
            epoch++
            analyzer.resume()
            analyzer.publishLiveReading(LabelReading.Confident(candidate), 1000, 0)
            assertEquals(emptyList<LiveEvidenceBuffer.Observation>(), observed)
            assertEquals(emptyList<LabelReading>(), surfaced)
            analyzer.publishLiveReading(LabelReading.Confident(candidate), 1100, epoch)
            assertEquals(1100L, observed.single().timestampMs)
            assertEquals(epoch, observed.single().aimEpoch)
        } finally {
            analyzer.close()
        }
    }
}
