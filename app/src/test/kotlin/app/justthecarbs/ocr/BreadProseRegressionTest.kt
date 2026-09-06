package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** Nutrition lines from real_stokbrood_prose_dense_06.jpg, ML Kit emulator replay 2026-09-05. */
class BreadProseRegressionTest {
    @Test fun `wrapped fat value before carbohydrate cannot become the carbohydrate amount`() {
        val report = NutritionTableParser.parseWithDiagnostics(document)
        val reading = report.reading as? LabelReading.Confident
            ?: throw AssertionError("expected the printed carbohydrate: $report")
        assertEquals(BigDecimal("46"), reading.candidate.value.stripTrailingZeros())
    }

    private val document = OcrDocument(1125, 1320, listOf(
            OcrElement("allergenen", OcrBox(268, 824, 367, 850), 8, 12),
            OcrElement("(winkel)", OcrBox(375, 824, 447, 850), 8, 12),
            OcrElement("bevatten.", OcrBox(453, 824, 537, 850), 8, 12),
            OcrElement("Voedingswaarde", OcrBox(544, 824, 712, 850), 8, 12),
            OcrElement("per", OcrBox(717, 824, 748, 850), 8, 12),
            OcrElement("100g:", OcrBox(269, 854, 319, 881), 8, 13),
            OcrElement("energie", OcrBox(327, 854, 398, 881), 8, 13),
            OcrElement("1312kJ", OcrBox(406, 854, 471, 881), 8, 13),
            OcrElement("/312kcal,vetten", OcrBox(477, 854, 621, 881), 8, 13),
            OcrElement("7,8", OcrBox(629, 854, 657, 881), 8, 13),
            OcrElement("g,", OcrBox(663, 854, 679, 881), 8, 13),
            OcrElement("waar", OcrBox(686, 854, 739, 881), 8, 13),
            OcrElement("van", OcrBox(267, 887, 300, 912), 8, 14),
            OcrElement("verzadigde", OcrBox(307, 887, 411, 912), 8, 14),
            OcrElement("vetzuren", OcrBox(417, 887, 496, 912), 8, 14),
            OcrElement("1,1", OcrBox(508, 887, 537, 912), 8, 14),
            OcrElement("g,", OcrBox(546, 887, 561, 912), 8, 14),
            OcrElement("onverzadigde", OcrBox(561, 887, 687, 912), 8, 14),
            OcrElement("vetzu", OcrBox(700, 887, 745, 912), 8, 14),
            OcrElement("ren", OcrBox(268, 918, 297, 945), 8, 15),
            OcrElement("6,4", OcrBox(304, 918, 333, 945), 8, 15),
            OcrElement("g,", OcrBox(340, 918, 355, 945), 8, 15),
            OcrElement("koolhydraten", OcrBox(364, 918, 482, 945), 8, 15),
            OcrElement("46g,waarvan", OcrBox(490, 918, 609, 945), 8, 15),
            OcrElement("suikers", OcrBox(617, 918, 683, 945), 8, 15),
            OcrElement("1,0", OcrBox(690, 918, 717, 945), 8, 15),
            OcrElement("g.", OcrBox(723, 918, 739, 945), 8, 15),
            OcrElement("vezels", OcrBox(273, 950, 328, 976), 8, 16),
            OcrElement("4,7g,", OcrBox(338, 950, 388, 976), 8, 16),
            OcrElement("eiwitten", OcrBox(390, 950, 460, 976), 8, 16),
            OcrElement("12g,", OcrBox(469, 950, 512, 976), 8, 16),
            OcrElement("zout", OcrBox(520, 950, 559, 976), 8, 16),
            OcrElement("1,03", OcrBox(568, 950, 611, 976), 8, 16),
            OcrElement("g.", OcrBox(618, 950, 626, 976), 8, 16),
    ))
}
