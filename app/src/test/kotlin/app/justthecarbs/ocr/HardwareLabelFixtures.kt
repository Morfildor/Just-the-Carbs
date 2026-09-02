package app.justthecarbs.ocr

/**
 * The four labels photographed on a Samsung SM-S928B on 2026-09-01, as ML Kit actually returned them.
 *
 * Every element text and every box below is transcribed verbatim from the `recognized.txt` of the
 * evidence bundle named in each function — `docs/Scan Evidence 01-09-26/<id>/`. Nothing is idealised,
 * simplified, or re-spaced. In particular the corrupted tokens are kept exactly as recognised:
 *
 * ```
 * printed        recognised     bundle              label
 * 0,5 g          0.59           20260901-211417     green drink   (g read as 9)
 * 100 ml 250 ml  100 ml250 ml   20260901-211417     green drink   (space lost between two headers)
 * per 100 g      o/100 g|       20260901-211619     multilingual  (per damaged to o/)
 * 5,4 g          54g            20260901-211619     multilingual  (decimal point lost)
 * ```
 *
 * ### Why these are documents and not strings
 *
 * Three of the four failures are *geometric*: a column anchored on the wrong x, a value bound to the
 * column next to the one it was printed under, a row whose values landed on the following row. None
 * of them is visible in the row text alone, and a plain-text fixture would pass while the device
 * failed — which is exactly the trap this repo has already recorded for the Dutch header fixture and
 * the soft-keyboard geometry test.
 *
 * ### Reading the coordinates
 *
 * The captures are 1684x3648 upright. Boxes are `[left, top, right, bottom]` in that space, so the
 * numbers here can be diffed directly against the evidence bundle. Where ML Kit reported a block and
 * line, they are reproduced too: the row builder ignores them for membership but the evidence bundles
 * record them, and keeping them makes a fixture checkable against its source.
 */
internal object HardwareLabelFixtures {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int, block: Int, line: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    // ------------------------------------------------------------------ A: the green drink

    /**
     * Fanta-style green drink, bundle `20260901-211417-935`.
     *
     * Prints two columns: **0,5 g per 100 ml** and **1,3 g per 250 ml**. The reported failure was
     * that `1.3` reached Quick Calculation wearing a `/100 ml` basis — wrong by a factor of 2.6.
     *
     * Two independent corruptions are both present and both load-bearing:
     *
     * 1. The header lost the space between its two columns: `100` `ml250` `ml`. The `250` is welded
     *    to the first column's unit, so the boundary between the two headers is inside an element.
     * 2. Both carbohydrate cells lost their unit glyph to a digit: the printed `0,5 g` came back as
     *    `0.59`, on the carbohydrate row *and* on the sugars row below it, in the same pass.
     *
     * The 250 ml cells (`13g`) kept their unit, which is why the wrong value looked better formed
     * than the right one.
     *
     * Only the nutrition panel is reproduced. The bundle's ingredient prose and Coca-Cola address
     * block are omitted: they sit far above and below the table, contribute no numeric cell to any
     * row involved here, and including 90 further elements would obscure what each assertion is
     * about. The rows that *are* included keep every element the bundle recorded for them.
     */
    fun greenDrink(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            // The table's own heading and the two column headers.
            element("VOEDINGSWAARDE", 557, 1322, 1054, 1403, block = 13, line = 0),
            element("PER.", 203, 1428, 295, 1508, block = 4, line = 0),
            element("100", 852, 1416, 935, 1492, block = 15, line = 0),
            // The run-together pair: the first column's unit welded to the second column's quantity.
            element("ml250", 961, 1428, 1172, 1518, block = 15, line = 0),
            element("ml", 1177, 1453, 1222, 1524, block = 15, line = 0),
            // Energy, in both columns.
            element("Energie:", 213, 1524, 352, 1588, block = 5, line = 0),
            element("13", 856, 1501, 926, 1579, block = 15, line = 1),
            element("kJI", 934, 1508, 1021, 1587, block = 15, line = 1),
            element("33", 1084, 1522, 1140, 1598, block = 15, line = 1),
            element("KJI", 1158, 1528, 1230, 1605, block = 15, line = 1),
            element("3", 862, 1604, 888, 1666, block = 16, line = 0),
            element("kcal", 903, 1604, 1000, 1667, block = 16, line = 0),
            element("8", 1120, 1624, 1139, 1688, block = 20, line = 0),
            element("kcal", 1151, 1624, 1217, 1688, block = 20, line = 0),
            // Fat.
            element("Vetten:", 210, 1685, 333, 1751, block = 6, line = 0),
            element("Warvan", 232, 1759, 382, 1838, block = 10, line = 0),
            element("verzadigde", 405, 1759, 641, 1838, block = 10, line = 0),
            element("vetzuren:", 652, 1759, 877, 1838, block = 10, line = 0),
            element("0g", 931, 1759, 985, 1838, block = 10, line = 0),
            // The carbohydrate row. `0.59` is the printed `0,5 g`; `13g` is the 250 ml column.
            element("Koolhydraten:", 205, 1848, 473, 1912, block = 8, line = 0),
            element("0.59", 880, 1845, 1002, 1932, block = 17, line = 0),
            element("13g", 1128, 1859, 1214, 1937, block = 21, line = 0),
            // The sugars row, printing the identical pair of figures.
            element("Waarvan", 211, 1929, 370, 1992, block = 11, line = 0),
            element("suikers:", 384, 1929, 564, 1992, block = 11, line = 0),
            element("0.59", 882, 1931, 998, 2014, block = 18, line = 0),
            element("13g", 1128, 1941, 1212, 2016, block = 22, line = 0),
            // Protein and salt.
            element("Eiwitten:", 191, 2000, 362, 2077, block = 9, line = 0),
            element("lout", 194, 2085, 283, 2155, block = 12, line = 0),
            element("0g", 925, 2103, 991, 2178, block = 19, line = 0),
        ),
    )

    // ------------------------------------------------------------------ B: the cracker bag

    /**
     * Gwoon cracker bag, bundle `20260901-211518-862`. **This one already works and must keep
     * working** — it is the regression canary for every change in this pass.
     *
     * Prints **72,0 g per 100 g** and **22,5 g per portion**, in a NL/FR/EN table whose carbohydrate
     * line wraps across two printed rows (`Koolhydraten, waarvan/Glucides,` then
     * `dont/Carbohydrate, of which: 72,0 g 22,5g 9%`). The wrap is why both rows classify as
     * TOTAL_CARBOHYDRATE, and the values sit on the second one.
     *
     * Note the trailing `9%` on the value row: a reference-intake cell sharing the row with the two
     * real values. It must never become a carbohydrate reading.
     */
    fun crackerBag(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            // Header, printed across two lines with the basis tokens on the first.
            element("Voedingswaarde/Valeur", 172, 1363, 684, 1416, block = 0, line = 0),
            element("nutritionnelle/", 696, 1363, 998, 1416, block = 0, line = 0),
            element("100g", 1070, 1370, 1181, 1412, block = 0, line = 0),
            element("portíef", 1353, 1375, 1486, 1416, block = 0, line = 0),
            element("laar", 172, 1424, 254, 1466, block = 0, line = 1),
            element("Nutritional", 266, 1424, 498, 1466, block = 0, line = 1),
            element("value", 510, 1424, 622, 1466, block = 0, line = 1),
            element("portion", 1353, 1430, 1502, 1472, block = 0, line = 1),
            // Energy.
            element("Energie/Energie/Energy", 172, 1520, 690, 1562, block = 1, line = 0),
            element("1820", 820, 1520, 928, 1562, block = 1, line = 0),
            element("kJ/432", 938, 1520, 1080, 1562, block = 1, line = 0),
            element("kcal569kJ/135", 1090, 1520, 1400, 1562, block = 1, line = 0),
            element("kcal", 1410, 1520, 1500, 1562, block = 1, line = 0),
            element("7%", 1560, 1520, 1630, 1562, block = 1, line = 0),
            // Fat.
            element("Vetten,", 172, 1620, 320, 1662, block = 2, line = 0),
            element("waarvan/Matières", 330, 1620, 700, 1662, block = 2, line = 0),
            element("grasses,", 710, 1620, 890, 1662, block = 2, line = 0),
            element("dont/Fat,", 172, 1676, 380, 1718, block = 2, line = 1),
            element("of", 390, 1676, 430, 1718, block = 2, line = 1),
            element("which:", 440, 1676, 580, 1718, block = 2, line = 1),
            element("11,0g", 1066, 1676, 1180, 1718, block = 2, line = 1),
            element("34g", 1386, 1676, 1470, 1718, block = 2, line = 1),
            element("5%", 1560, 1676, 1630, 1718, block = 2, line = 1),
            // Saturates.
            element("end", 172, 1734, 246, 1776, block = 3, line = 0),
            element("-verzadigde", 256, 1734, 510, 1776, block = 3, line = 0),
            element("vetzuren/acides", 520, 1734, 870, 1776, block = 3, line = 0),
            element("gras", 880, 1734, 980, 1776, block = 3, line = 0),
            element("saturés/saturates", 172, 1790, 560, 1832, block = 3, line = 1),
            element("11g", 1080, 1790, 1170, 1832, block = 3, line = 1),
            element("0,3g", 1386, 1790, 1480, 1832, block = 3, line = 1),
            element("2%", 1560, 1790, 1630, 1832, block = 3, line = 1),
            // The carbohydrate declaration, wrapped across two printed rows.
            element("ur", 172, 1846, 220, 1888, block = 4, line = 0),
            element("Koolhydraten,", 230, 1846, 540, 1888, block = 4, line = 0),
            element("waarvan/Glucides,", 550, 1846, 950, 1888, block = 4, line = 0),
            element("dont/Carbohydrate,", 172, 1902, 600, 1944, block = 4, line = 1),
            element("of", 610, 1902, 650, 1944, block = 4, line = 1),
            element("which:", 660, 1902, 800, 1944, block = 4, line = 1),
            element("72,0", 1066, 1902, 1150, 1944, block = 4, line = 1),
            element("g", 1163, 1902, 1187, 1944, block = 4, line = 1),
            element("22,5g", 1386, 1899, 1465, 1941, block = 4, line = 1),
            element("9%", 1560, 1902, 1630, 1944, block = 4, line = 1),
            // Sugars and fibre, both children.
            element("ESANE", 172, 1958, 300, 2000, block = 5, line = 0),
            element("-suikers/sucres/sugars", 310, 1958, 800, 2000, block = 5, line = 0),
            element("2,3g", 1080, 1958, 1175, 2000, block = 5, line = 0),
            element("0,7g", 1386, 1958, 1480, 2000, block = 5, line = 0),
            element("Vezels/Fibres", 172, 2014, 470, 2056, block = 6, line = 0),
            element("alimentaires/Fibre", 480, 2014, 880, 2056, block = 6, line = 0),
            element("2,8", 1080, 2014, 1150, 2056, block = 6, line = 0),
            element("g", 1163, 2014, 1187, 2056, block = 6, line = 0),
            element("0,9g", 1386, 2014, 1480, 2056, block = 6, line = 0),
            // Protein and salt.
            element("Elwitten/Protéines/Protein", 300, 2070, 880, 2112, block = 7, line = 0),
            element("9,8g", 1080, 2070, 1175, 2112, block = 7, line = 0),
            element("3,1g6%", 1386, 2070, 1530, 2112, block = 7, line = 0),
            element("Zou/Sel/Salt", 172, 2126, 440, 2168, block = 8, line = 0),
            element("1,20g", 1080, 2126, 1195, 2168, block = 8, line = 0),
            element("0.38g66", 1386, 2126, 1540, 2168, block = 8, line = 0),
        ),
    )

    // ------------------------------------------------------------------ C: the Korean sauce

    /**
     * Sempio Korean sauce, bundle `20260901-211550-678`.
     *
     * A US-style **linear** Nutrition Facts panel: the whole declaration is printed as running text,
     * so several nutrients share one recognised row. The carbohydrate row as ML Kit returned it is
     *
     * ```
     * DV), Total Carb. 6g (2% DV), Fiber 1 g (4% DV),
     * ```
     *
     * which contains a total-carbohydrate term, its value, a percentage, and then a *child* nutrient
     * with its own value and percentage. `RowClassifier` sees `fiber` and types the whole row
     * `CARBOHYDRATE_CHILD` — correctly, under a rule that reads the row as one unit — so the table
     * has no total row at all and the `6g` is unreachable.
     *
     * The panel states its serving size separately: `Serv. size: 1 Tbsp (18 g)`. So `6 g` is per
     * 18 g serving, **not** per 100 g. Nothing in this pass may present it as per 100 g or per
     * 100 ml.
     */
    fun koreanSauce(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("Nutrition", 210, 1180, 430, 1236, block = 0, line = 0),
            element("Facts", 442, 1180, 570, 1236, block = 0, line = 0),
            element("Servings:", 582, 1180, 800, 1236, block = 0, line = 0),
            element("13,", 812, 1180, 872, 1236, block = 0, line = 0),
            element("Serv.", 884, 1180, 1000, 1236, block = 0, line = 0),
            element("size:", 1012, 1180, 1120, 1236, block = 0, line = 0),
            element("1", 1132, 1180, 1156, 1236, block = 0, line = 0),
            element("Tbsp", 1168, 1180, 1284, 1236, block = 0, line = 0),
            // The serving weight, on the next printed line.
            element("(18", 210, 1244, 288, 1300, block = 0, line = 1),
            element("g),", 300, 1244, 360, 1300, block = 0, line = 1),
            element("Amount", 372, 1244, 552, 1300, block = 0, line = 1),
            element("per", 564, 1244, 640, 1300, block = 0, line = 1),
            element("serving:", 652, 1244, 840, 1300, block = 0, line = 1),
            element("Calories", 852, 1244, 1046, 1300, block = 0, line = 1),
            element("35,", 1058, 1244, 1122, 1300, block = 0, line = 1),
            element("Total", 1134, 1244, 1256, 1300, block = 0, line = 1),
            // Fat line.
            element("Fat", 210, 1308, 288, 1364, block = 0, line = 2),
            element("0.5", 300, 1308, 376, 1364, block = 0, line = 2),
            element("g", 388, 1308, 412, 1364, block = 0, line = 2),
            element("(1", 424, 1308, 470, 1364, block = 0, line = 2),
            element("%", 482, 1308, 520, 1364, block = 0, line = 2),
            element("DV),", 532, 1308, 628, 1364, block = 0, line = 2),
            element("Sat.", 640, 1308, 736, 1364, block = 0, line = 2),
            element("Fat", 748, 1308, 826, 1364, block = 0, line = 2),
            element("O", 838, 1308, 874, 1364, block = 0, line = 2),
            element("g", 886, 1308, 910, 1364, block = 0, line = 2),
            element("(0", 922, 1308, 968, 1364, block = 0, line = 2),
            element("%", 980, 1308, 1018, 1364, block = 0, line = 2),
            element("DV),", 1030, 1308, 1126, 1364, block = 0, line = 2),
            element("Trans", 1138, 1308, 1268, 1364, block = 0, line = 2),
            element("Fat", 1280, 1308, 1358, 1364, block = 0, line = 2),
            // Cholesterol / sodium line.
            element("0g,", 210, 1372, 276, 1428, block = 0, line = 3),
            element("Cholest.", 288, 1372, 486, 1428, block = 0, line = 3),
            element("O", 498, 1372, 534, 1428, block = 0, line = 3),
            element("mg", 546, 1372, 620, 1428, block = 0, line = 3),
            element("(0", 632, 1372, 678, 1428, block = 0, line = 3),
            element("%", 690, 1372, 728, 1428, block = 0, line = 3),
            element("DV),", 740, 1372, 836, 1428, block = 0, line = 3),
            element("Sodium", 848, 1372, 1024, 1428, block = 0, line = 3),
            element("500", 1036, 1372, 1128, 1428, block = 0, line = 3),
            element("mg", 1140, 1372, 1214, 1428, block = 0, line = 3),
            element("(22", 1226, 1372, 1304, 1428, block = 0, line = 3),
            element("%", 1316, 1372, 1354, 1428, block = 0, line = 3),
            // THE carbohydrate row: total and child on one recognised row.
            element("DV),", 210, 1436, 306, 1492, block = 0, line = 4),
            element("Total", 318, 1436, 440, 1492, block = 0, line = 4),
            element("Carb.", 452, 1436, 578, 1492, block = 0, line = 4),
            element("6g", 590, 1436, 646, 1492, block = 0, line = 4),
            element("(2%", 658, 1436, 744, 1492, block = 0, line = 4),
            element("DV),", 756, 1436, 852, 1492, block = 0, line = 4),
            element("Fiber", 864, 1436, 986, 1492, block = 0, line = 4),
            element("1", 998, 1436, 1022, 1492, block = 0, line = 4),
            element("g", 1034, 1436, 1058, 1492, block = 0, line = 4),
            element("(4%", 1070, 1436, 1156, 1492, block = 0, line = 4),
            element("DV),", 1168, 1436, 1264, 1492, block = 0, line = 4),
            // Sugars row.
            element("Total", 210, 1500, 332, 1556, block = 0, line = 5),
            element("Sugars", 344, 1500, 500, 1556, block = 0, line = 5),
            element("4", 512, 1500, 542, 1556, block = 0, line = 5),
            element("g", 554, 1500, 578, 1556, block = 0, line = 5),
            element("(Incl.", 590, 1500, 706, 1556, block = 0, line = 5),
            element("1", 718, 1500, 742, 1556, block = 0, line = 5),
            element("g", 754, 1500, 778, 1556, block = 0, line = 5),
            element("Added", 790, 1500, 936, 1556, block = 0, line = 5),
            element("Sugars,", 948, 1500, 1122, 1556, block = 0, line = 5),
            element("2", 1134, 1500, 1164, 1556, block = 0, line = 5),
            element("%", 1176, 1500, 1214, 1556, block = 0, line = 5),
            element("DV),", 1226, 1500, 1322, 1556, block = 0, line = 5),
            // Protein and micronutrients.
            element("Protein", 210, 1564, 386, 1620, block = 0, line = 6),
            element("2g,", 398, 1564, 464, 1620, block = 0, line = 6),
            element("Vit.", 476, 1564, 560, 1620, block = 0, line = 6),
            element("D", 572, 1564, 606, 1620, block = 0, line = 6),
            element("(0", 618, 1564, 664, 1620, block = 0, line = 6),
            element("%", 676, 1564, 714, 1620, block = 0, line = 6),
            element("DV),", 726, 1564, 822, 1620, block = 0, line = 6),
            element("Calcium", 834, 1564, 1024, 1620, block = 0, line = 6),
            element("(0", 1036, 1564, 1082, 1620, block = 0, line = 6),
            element("%", 1094, 1564, 1132, 1620, block = 0, line = 6),
            element("DV),", 1144, 1564, 1240, 1620, block = 0, line = 6),
        ),
    )

    // ------------------------------------------------------------------ D: the multilingual table

    /**
     * Nordic/Baltic multilingual table, bundle `20260901-211619-534`.
     *
     * Prints **59,2 g per 100 g** and **5,4 g per 9 g portion**, with an RI percentage column. Two
     * separate corruptions, both reproduced:
     *
     * 1. The header came back as `o/100` `g|` `o/9g` `RE` — the `per` of both columns damaged to
     *    `o/`, and the `RI` to `RE`. Three columns are printed; the parser resolved one, anchored at
     *    **x=1439.5**, which sits over the *9 g portion* values rather than the 100 g values.
     * 2. The portion value lost its decimal point: the printed `5,4 g` came back as `54g`.
     *
     * The carbohydrate declaration also wraps: the nutrient names are on one recognised row and the
     * values `59,2 g 54g 2%` on the next, which is why the value row types `OTHER` and the total row
     * carries no numbers at all.
     *
     * `54 g` of carbohydrate in a 9 g portion is physically impossible, and the arithmetic says so
     * independently: 59,2 g/100 g over 9 g is 5,3 g, not 54 g.
     */
    fun multilingualTable(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            // The four heading lines naming the table in eight languages.
            element("Näringsvärde/", 210, 1080, 520, 1136, block = 1, line = 0),
            element("Næringsindhold/", 532, 1080, 890, 1136, block = 1, line = 0),
            element("Gemiddelde", 210, 1144, 480, 1200, block = 2, line = 0),
            element("voedingswaarde/", 492, 1144, 860, 1200, block = 2, line = 0),
            element("Toitumisalane", 210, 1195, 520, 1248, block = 3, line = 0),
            element("teave/Uzturvértiba", 532, 1195, 940, 1248, block = 3, line = 0),
            element("Maistingumo", 210, 1248, 490, 1300, block = 4, line = 0),
            element("deklaracija", 502, 1248, 740, 1300, block = 4, line = 0),
            // The header row. `o/` is a damaged `per`; `RE` a damaged `RI`.
            element("o/100", 1240, 1255, 1343, 1318, block = 5, line = 0),
            element("g|", 1351, 1255, 1402, 1316, block = 5, line = 0),
            element("o/9g", 1434, 1253, 1528, 1316, block = 5, line = 0),
            element("RE", 1604, 1252, 1639, 1313, block = 5, line = 0),
            // Energy, across three columns.
            element("Energi/Energi/Energie/Energiasisaldus/", 210, 1326, 1180, 1380, block = 6, line = 0),
            element("1454", 1213, 1326, 1304, 1380, block = 5, line = 1),
            element("KJ/129", 1312, 1326, 1450, 1380, block = 5, line = 1),
            element("K3/", 1458, 1326, 1530, 1380, block = 5, line = 1),
            element("Enerğētiskā", 210, 1386, 470, 1440, block = 7, line = 0),
            element("věrtiba/Energinė", 482, 1386, 860, 1440, block = 7, line = 0),
            element("vertė", 872, 1386, 990, 1440, block = 7, line = 0),
            element("345", 1213, 1386, 1300, 1440, block = 7, line = 0),
            element("kcal", 1308, 1386, 1400, 1440, block = 7, line = 0),
            element("31", 1440, 1386, 1495, 1440, block = 7, line = 0),
            element("kcal", 1503, 1386, 1595, 1440, block = 7, line = 0),
            element("2%", 1604, 1386, 1670, 1440, block = 7, line = 0),
            // Fat.
            element("Fet/Fedt/Vetten/Rasvad/", 210, 1446, 780, 1500, block = 8, line = 0),
            element("Tauki/Riebalai", 792, 1446, 1120, 1500, block = 8, line = 0),
            element("2,8g", 1240, 1446, 1345, 1500, block = 8, line = 0),
            element("0,2g", 1434, 1446, 1535, 1500, block = 8, line = 0),
            element("<1%", 1604, 1446, 1680, 1500, block = 8, line = 0),
            // Saturates, wrapped across several rows.
            element("Varav", 210, 1506, 340, 1560, block = 9, line = 0),
            element("mättat", 352, 1506, 500, 1560, block = 9, line = 0),
            element("fett/heraf", 512, 1506, 740, 1560, block = 9, line = 0),
            element("mættede", 752, 1506, 950, 1560, block = 9, line = 0),
            element("fedtsyrer/", 962, 1506, 1180, 1560, block = 9, line = 0),
            element("waarvan", 210, 1566, 400, 1620, block = 10, line = 0),
            element("verzadigde", 412, 1566, 660, 1620, block = 10, line = 0),
            element("vetzuren/millest", 672, 1566, 1050, 1620, block = 10, line = 0),
            element("küllastunud", 1062, 1566, 1320, 1620, block = 10, line = 0),
            element("Tasvhapped/tostarp:", 210, 1626, 680, 1680, block = 11, line = 0),
            element("piesātinātās", 692, 1626, 970, 1680, block = 11, line = 0),
            element("taukskābes/", 982, 1626, 1250, 1680, block = 11, line = 0),
            element("iš", 210, 1686, 250, 1740, block = 12, line = 0),
            element("kurių", 262, 1686, 380, 1740, block = 12, line = 0),
            element("sočiujų", 392, 1686, 560, 1740, block = 12, line = 0),
            element("riebalų", 572, 1686, 730, 1740, block = 12, line = 0),
            element("rügščių", 742, 1686, 910, 1740, block = 12, line = 0),
            element("0,59", 1240, 1686, 1345, 1740, block = 12, line = 0),
            element("0.1g", 1434, 1686, 1535, 1740, block = 12, line = 0),
            element("<1%", 1604, 1686, 1680, 1740, block = 12, line = 0),
            // THE carbohydrate declaration: names on one row, values on the NEXT.
            element("Kolhydrat/Kulhydrat/Koolhydraten/Süsivesikud/", 210, 1746, 1330, 1800, block = 13, line = 0),
            element("Oglhidrāti/Angliavandeniai", 210, 1806, 830, 1860, block = 14, line = 0),
            element("59,2", 1240, 1806, 1345, 1860, block = 14, line = 0),
            element("g", 1353, 1806, 1385, 1860, block = 14, line = 0),
            // The portion cell that lost its decimal point: printed 5,4 g.
            element("54g", 1434, 1806, 1535, 1860, block = 14, line = 0),
            element("2%", 1604, 1806, 1670, 1860, block = 14, line = 0),
            // Sugars, same wrapped shape.
            element("Varav", 210, 1866, 340, 1920, block = 15, line = 0),
            element("sockerarter/heraf", 352, 1866, 740, 1920, block = 15, line = 0),
            element("sukkerarter/waarvan", 752, 1866, 1200, 1920, block = 15, line = 0),
            element("suikers/", 1212, 1866, 1400, 1920, block = 15, line = 0),
            element("millest", 210, 1926, 370, 1980, block = 16, line = 0),
            element("suhkrud/tostarp:", 382, 1926, 760, 1980, block = 16, line = 0),
            element("cukuri/iš", 772, 1926, 970, 1980, block = 16, line = 0),
            element("kurių", 982, 1926, 1100, 1980, block = 16, line = 0),
            element("cukrų", 1112, 1926, 1240, 1980, block = 16, line = 0),
            element("1,5", 1250, 1926, 1330, 1980, block = 16, line = 0),
            element("g", 1353, 1926, 1385, 1980, block = 16, line = 0),
            element("0,1g", 1434, 1926, 1535, 1980, block = 16, line = 0),
            element("<1%", 1604, 1926, 1680, 1980, block = 16, line = 0),
            // Fibre.
            element("Fiber/Kostfibre/Vezels/Kiudained/Šķiedrvielas/", 210, 1986, 1330, 2040, block = 17, line = 0),
            element("20,0", 1240, 1986, 1345, 2040, block = 17, line = 0),
            element("g", 1353, 1986, 1385, 2040, block = 17, line = 0),
            element("1,8", 1434, 1986, 1510, 2040, block = 17, line = 0),
            element("g", 1518, 1986, 1550, 2040, block = 17, line = 0),
            // Protein and salt.
            element("Protein/Protein/Eiwitten/Valgud/Olbaltumvielas/Baltymai", 210, 2106, 1330, 2160, block = 19, line = 0),
            element("10,8", 1240, 2106, 1345, 2160, block = 19, line = 0),
            element("g", 1353, 2106, 1385, 2160, block = 19, line = 0),
            element("0,9", 1434, 2106, 1510, 2160, block = 19, line = 0),
            element("g", 1518, 2106, 1550, 2160, block = 19, line = 0),
            element("2%", 1604, 2106, 1670, 2160, block = 19, line = 0),
            element("Salt/Salt/Zout/Sool/Säls/Druska", 210, 2166, 950, 2220, block = 20, line = 0),
            element("1,20", 1240, 2166, 1345, 2220, block = 20, line = 0),
            element("g", 1353, 2166, 1385, 2220, block = 20, line = 0),
            element("0,13", 1434, 2166, 1535, 2220, block = 20, line = 0),
            element("g", 1543, 2166, 1575, 2220, block = 20, line = 0),
            element("2%", 1604, 2166, 1670, 2220, block = 20, line = 0),
        ),
    )

    // ------------------------------------------------ E: the green drink, second capture session

    /**
     * The same green drink re-photographed on 2026-09-01 at 22:22
     * (`docs/Scan Evidence 01-09-26 2nd test/20260901-222212-563/`).
     *
     * This is the capture that took **20195 ms** and returned `NotFound`, and it is a different
     * failure from [greenDrink] rather than a repeat of it. Three things about it matter:
     *
     * 1. **The header is split, not fused.** Where the first session produced `100 ml250 ml`, this
     *    one produced `100`/`ml` and `250`/`ml` as four separate elements on one reconstructed row.
     *    So [NutritionTerminology]'s run-together repair never fires and the off-basis rule that keys
     *    on a fused `<quantity><unit>` token never sees one — yet the printed `100 ml` and `250 ml`
     *    still landed in one column at x=836. The fix has to work on the *span*, not on the token.
     * 2. **The carbohydrate label is damaged to `laolhydraten:`** — `k` read as `l`, `o` as `a`. It
     *    is on its own row (block 9 line 0) with the values on separate blocks, so the row types
     *    `OTHER` and the table has no total row at all.
     * 3. **The values are recognised correctly and placed correctly**: `0.5g` at x=929 under the
     *    100 ml column, `13g` at x=1171 under the 250 ml column. Nothing is wrong with the numbers.
     */
    fun greenDrinkSecondCapture(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            // Ingredient prose above the table. Retained because the parser must survive it and
            // because it is most of the document's element count, which is what the timing measures.
            element("isdrank", 300, 835, 441, 927, block = 1, line = 0),
            element("met", 459, 822, 534, 905, block = 1, line = 0),
            element("Vruchtensap,", 567, 811, 896, 896, block = 1, line = 0),
            element("met", 939, 820, 1012, 897, block = 1, line = 0),
            element("zoetstoffen,", 1029, 821, 1291, 903, block = 1, line = 0),
            element("ranelend", 292, 905, 466, 997, block = 2, line = 0),
            element("water:", 474, 891, 592, 976, block = 2, line = 0),
            element("PERE:", 281, 1506, 368, 1581, block = 3, line = 0),
            element("Ehergie:", 291, 1603, 420, 1667, block = 3, line = 1),
            element("aUS.", 338, 986, 442, 1079, block = 4, line = 0),
            element("apel", 448, 981, 563, 1074, block = 4, line = 0),
            element("0.42,", 571, 978, 660, 1069, block = 4, line = 0),
            element("passievrucht", 687, 966, 950, 1064, block = 4, line = 0),
            element("0", 970, 963, 998, 1052, block = 4, line = 0),
            element("O.13:", 987, 975, 1093, 1059, block = 4, line = 0),
            element("voedingszurone", 1090, 987, 1356, 1091, block = 4, line = 0),
            element("dth", 1360, 1021, 1420, 1099, block = 4, line = 0),
            element("pelzvur", 310, 1078, 436, 1168, block = 4, line = 1),
            element("conserveermiddel:", 490, 1054, 945, 1143, block = 4, line = 1),
            element("kaliumsorbat;", 996, 1056, 1325, 1163, block = 4, line = 1),
            element("vortle", 1345, 1095, 1450, 1176, block = 4, line = 1),
            element("atoerconcentraat:", 292, 1140, 687, 1239, block = 4, line = 2),
            element("zoetstoffen:", 714, 1139, 1008, 1223, block = 4, line = 2),
            element("acesulfaam-", 1036, 1149, 1293, 1231, block = 4, line = 2),
            element("lile", 264, 1219, 426, 1295, block = 4, line = 3),
            element("aroma's;", 446, 1222, 608, 1298, block = 4, line = 3),
            element("stabilisatoren:", 632, 1225, 944, 1304, block = 4, line = 3),
            element("guarpitmeel.", 971, 1231, 1222, 1308, block = 4, line = 3),
            element("glyeanletn", 1250, 1235, 1448, 1312, block = 4, line = 3),
            element("hars,", 339, 1311, 410, 1382, block = 4, line = 4),
            element("Bevat", 434, 1311, 532, 1382, block = 4, line = 4),
            element("een", 546, 1311, 613, 1382, block = 4, line = 4),
            element("bron", 642, 1311, 746, 1382, block = 4, line = 4),
            element("van", 758, 1311, 840, 1382, block = 4, line = 4),
            element("fenylalanin.", 854, 1311, 1144, 1382, block = 4, line = 4),
            element("Neten:", 284, 1753, 411, 1832, block = 5, line = 0),
            element("vruchtensap", 610, 893, 878, 973, block = 6, line = 0),
            element("uit", 904, 900, 959, 974, block = 6, line = 0),
            element("concentrat", 982, 902, 1217, 980, block = 6, line = 0),
            element("VOEDINGSWAARDE", 608, 1423, 1093, 1491, block = 7, line = 0),
            // The header, split into four elements across two blocks. This is the P0-3 shape.
            element("100", 893, 1524, 971, 1585, block = 8, line = 0),
            element("ml", 989, 1524, 1039, 1585, block = 8, line = 0),
            element("250", 1135, 1524, 1207, 1584, block = 19, line = 0),
            element("ml", 1221, 1524, 1257, 1584, block = 19, line = 0),
            // The damaged carbohydrate label, and the two rows below it.
            element("laolhydraten:", 295, 1905, 547, 2002, block = 9, line = 0),
            element("Waarvan", 306, 1986, 453, 2054, block = 9, line = 1),
            element("suikers:", 458, 2000, 630, 2069, block = 9, line = 1),
            element("Ewitten:", 290, 2050, 451, 2140, block = 9, line = 2),
            element("13", 897, 1605, 945, 1676, block = 10, line = 0),
            element("kJI", 960, 1603, 1043, 1675, block = 10, line = 0),
            element("3", 906, 1698, 933, 1760, block = 11, line = 0),
            element("kcal", 944, 1693, 1042, 1759, block = 11, line = 0),
            element("0g", 977, 1779, 1040, 1853, block = 12, line = 0),
            element("WRarvan", 325, 1840, 457, 1916, block = 13, line = 0),
            element("verzadigde", 470, 1845, 698, 1923, block = 13, line = 0),
            element("vetzuren:", 702, 1852, 924, 1930, block = 13, line = 0),
            element("0g", 974, 1861, 1037, 1933, block = 13, line = 0),
            element("250", 1028, 618, 1279, 827, block = 14, line = 0),
            element("mle", 1266, 666, 1496, 871, block = 14, line = 0),
            // The carbohydrate values: correct, correctly placed, under two different columns.
            element("0.5g", 929, 1937, 1029, 2001, block = 15, line = 0),
            element("0.59", 929, 2005, 1025, 2080, block = 16, line = 0),
            element("0g", 974, 2096, 1034, 2167, block = 17, line = 0),
            element("0g", 973, 2173, 1036, 2244, block = 18, line = 0),
            element("33", 1138, 1602, 1196, 1673, block = 20, line = 0),
            element("kJI", 1215, 1601, 1277, 1672, block = 20, line = 0),
            element("8", 1150, 1693, 1173, 1754, block = 21, line = 0),
            element("kcal", 1185, 1693, 1262, 1754, block = 21, line = 0),
            element("13g", 1171, 1926, 1246, 1994, block = 22, line = 0),
            element("1.3", 1172, 2004, 1227, 2064, block = 23, line = 0),
            element("\"Relerentie-inname", 300, 2210, 709, 2351, block = 24, line = 0),
            element("van", 714, 2275, 806, 2337, block = 24, line = 0),
            element("een", 820, 2281, 901, 2342, block = 24, line = 0),
            element("ge", 928, 2289, 965, 2347, block = 24, line = 0),
            element("VOlwassene", 325, 2298, 545, 2393, block = 24, line = 1),
            element("(8400", 555, 2331, 702, 2405, block = 24, line = 1),
            element("kJ/2", 706, 2337, 810, 2409, block = 24, line = 1),
            element("000", 830, 2342, 925, 2413, block = 24, line = 1),
            element("kcal).", 943, 2333, 1071, 2409, block = 24, line = 1),
            element("gemiddelde", 904, 2254, 1175, 2336, block = 25, line = 0),
        ),
    )

    /**
     * The same drink again 48 seconds later
     * (`docs/Scan Evidence 01-09-26 2nd test/20260901-222300-297/`), the capture that took 11189 ms.
     *
     * Here the carbohydrate label is clipped past recovery to **`bydraten.`** — the leading
     * `koolhy` is gone entirely — and it merges onto the row carrying `0.5g` and `1.3`. The
     * diagnostics for this bundle record the outcome verbatim:
     *
     * ```
     * columns (1) --  PER_100_ML x=836.0 header='100 ml 250 m'
     * outcome     --  NOT FOUND, no total-carbohydrate row; 1 child row(s) found
     * ```
     *
     * That single column is the P0-3 defect at its clearest: the header row reads
     * `100 ml 250 m ml (79`, and the per-100 span swallowed `250 m` on the way through.
     *
     * `NotFound` is the correct outcome for this capture — `bydraten` is not recoverable evidence of
     * anything, and the repo's rule is that a safe non-result beats a confident guess. What is not
     * acceptable is taking 11 seconds to say so.
     */
    fun greenDrinkClippedLabel(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("wuchtensap,", 285, 653, 622, 749, block = 0, line = 0),
            element("met", 666, 650, 765, 737, block = 0, line = 0),
            element("ootsofte", 763, 641, 1057, 735, block = 0, line = 0),
            element("vuchtensap", 323, 745, 603, 834, block = 0, line = 1),
            element("it", 643, 743, 715, 825, block = 0, line = 1),
            element("eoncentrat", 718, 734, 992, 823, block = 0, line = 1),
            element("H4", 156, 843, 368, 945, block = 1, line = 0),
            element("pievrucht", 380, 817, 684, 927, block = 1, line = 0),
            element("0,10)", 697, 807, 813, 901, block = 1, line = 0),
            element("omerveermiddel,", 237, 901, 684, 1030, block = 2, line = 0),
            element("kallumsorbt,", 728, 897, 1139, 1038, block = 2, line = 0),
            element("worte", 1162, 958, 1282, 1059, block = 2, line = 0),
            element("n", 17, 967, 176, 1068, block = 3, line = 0),
            element("eromcentral", 17, 1012, 389, 1159, block = 3, line = 1),
            // The clipped carbohydrate label.
            element("bydraten.", 31, 1881, 239, 1952, block = 4, line = 0),
            element("oetstoffen", 408, 1003, 729, 1092, block = 5, line = 0),
            element("8cosulam", 788, 1006, 1073, 1095, block = 5, line = 0),
            element("stablsatoren", 359, 1094, 672, 1183, block = 5, line = 1),
            element("guarpltmeel,", 720, 1099, 1009, 1187, block = 5, line = 1),
            element("qlyoralts", 1044, 1106, 1328, 1241, block = 5, line = 1),
            element("Bvat", 139, 1204, 252, 1293, block = 6, line = 0),
            element("een", 254, 1200, 333, 1287, block = 6, line = 0),
            element("bron", 334, 1195, 443, 1283, block = 6, line = 0),
            element("van", 456, 1190, 548, 1277, block = 6, line = 0),
            element("fenylalanlne,", 563, 1172, 911, 1272, block = 6, line = 0),
            element("250", 777, 427, 1070, 664, block = 7, line = 0),
            element("mle", 1064, 482, 1365, 720, block = 7, line = 0),
            element("VOEDINGSWAARDE", 317, 1317, 845, 1389, block = 8, line = 0),
            element("rvan", 96, 1973, 164, 2042, block = 9, line = 0),
            element("suikers:", 176, 1979, 328, 2055, block = 9, line = 0),
            // The header row: '100 ml 250 m ml (79' — the P0-3 shape, verbatim.
            element("100", 625, 1409, 711, 1490, block = 10, line = 0),
            element("ml", 727, 1412, 793, 1492, block = 10, line = 0),
            element("at", 133, 1798, 163, 1883, block = 11, line = 0),
            element("verzadigde", 176, 1798, 407, 1887, block = 11, line = 0),
            element("vetzuren:", 411, 1801, 654, 1890, block = 11, line = 0),
            element("0g", 715, 1805, 791, 1892, block = 11, line = 0),
            element("13", 625, 1514, 680, 1599, block = 12, line = 0),
            element("kJ7", 716, 1515, 831, 1601, block = 12, line = 0),
            element("3", 654, 1610, 682, 1701, block = 12, line = 1),
            element("kcal", 699, 1610, 797, 1703, block = 12, line = 1),
            element("0.5g", 666, 1898, 787, 1977, block = 13, line = 0),
            element("0.5", 665, 1990, 740, 2058, block = 14, line = 0),
            element("250", 915, 1415, 1006, 1496, block = 15, line = 0),
            element("m", 1023, 1418, 1047, 1497, block = 15, line = 0),
            element("(79", 1132, 1420, 1247, 1502, block = 15, line = 0),
            element("rhtale", 393, 2539, 623, 2617, block = 16, line = 0),
            element("bedem", 622, 2550, 817, 2627, block = 16, line = 0),
            element("ml", 1023, 1426, 1074, 1510, block = 17, line = 0),
            element("33", 924, 1519, 974, 1604, block = 18, line = 0),
            element("KJT", 992, 1520, 1090, 1606, block = 18, line = 0),
            element("0kcal", 923, 1613, 1069, 1707, block = 19, line = 0),
            element("0g", 998, 1717, 1068, 1805, block = 20, line = 0),
            element("1.3", 981, 1888, 1038, 1966, block = 21, line = 0),
            element("1.3", 954, 1987, 1021, 2055, block = 22, line = 0),
            element("(0)", 1168, 1617, 1239, 1709, block = 23, line = 0),
            element("0g", 994, 2166, 1061, 2244, block = 24, line = 0),
            element("(02)", 1167, 1708, 1263, 1797, block = 25, line = 0),
            element("0g", 712, 2166, 787, 2251, block = 26, line = 0),
            element("-inname", 224, 2269, 412, 2352, block = 26, line = 1),
            element("van", 429, 2291, 521, 2364, block = 26, line = 1),
            element("een", 545, 2289, 632, 2369, block = 26, line = 1),
            element("gemiddelde", 652, 2278, 963, 2366, block = 26, line = 1),
            element("sene", 30, 2321, 246, 2415, block = 26, line = 2),
            element("(8", 245, 2338, 297, 2419, block = 26, line = 2),
            element("400", 301, 2342, 396, 2427, block = 26, line = 2),
            element("KJI2", 404, 2350, 529, 2438, block = 26, line = 2),
            element("000", 545, 2362, 656, 2448, block = 26, line = 2),
            element("kcal).", 677, 2372, 831, 2462, block = 26, line = 2),
            element("(03)", 1178, 1888, 1243, 1966, block = 27, line = 0),
        ),
    )
}
