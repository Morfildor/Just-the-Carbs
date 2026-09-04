package app.justthecarbs.ocr

/**
 * Documents rebuilt from the thirteenth phone session,
 * `docs/Scan evidence 04-09 1st test/`.
 *
 * Samsung SM-S928B, API 36, `1.0.3-debug`. Nineteen captures across twelve packages.
 *
 * ## Provenance
 *
 * Every document here is **generated** from its bundle's own `diagnostics.txt` element list by
 * `tools/derive-session-fixtures.py`, not transcribed and not trimmed. Each fixture carries the
 * bundle's full element count, asserted in [ThirteenthSessionBaselineTest], so a fixture that
 * stops reproducing its capture fails rather than quietly passing.
 *
 * ## Ground truth is the photograph, never the parser
 *
 * The expected value recorded for each fixture in [ThirteenthSessionCorpus] was read off
 * `capture.jpg` by eye. Several of these captures recognised a **wrong** number confidently
 * (`12` for a printed `7,2`; `13` for a printed `1,3`), and taking the parser's answer as truth
 * would have encoded exactly the defects this corpus exists to measure.
 */
internal object ThirteenthSessionFixtures {

    private fun element(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        blockId: Int,
        lineId: Int,
    ) = OcrElement(
        text = text,
        box = OcrBox(left = left, top = top, right = right, bottom = bottom),
        blockId = blockId,
        lineId = lineId,
    )

    /** `20260904-080926-938` */
    fun greenDrinkUnitLostZeroFiveNine(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("2659320", 356, 12, 463, 549, 0, 0),
            element("Vruchtensap,", 714, 683, 1073, 791, 1, 0),
            element("met", 1112, 713, 1177, 799, 1, 0),
            element("108tstofn,", 1215, 733, 1466, 877, 1, 0),
            element("Sorankelend", 319, 787, 567, 892, 1, 1),
            element("water:", 604, 770, 735, 863, 1, 1),
            element("vruchtensap", 769, 768, 1069, 884, 1, 1),
            element("uit", 1094, 810, 1160, 895, 1, 1),
            element("concentrat", 1166, 819, 1409, 927, 1, 1),
            element("perzik", 321, 877, 448, 981, 1, 2),
            element("0", 450, 869, 488, 953, 1, 2),
            element("52", 477, 859, 541, 948, 1, 2),
            element("appel", 577, 862, 682, 954, 1, 2),
            element("0.&%.", 710, 865, 816, 957, 1, 2),
            element("passievrucht", 838, 868, 1147, 964, 1, 2),
            element("01", 1171, 875, 1202, 964, 1, 2),
            element("saffoerconcentraat;", 309, 1046, 822, 1153, 1, 3),
            element("zoetstoffen:", 871, 1047, 1194, 1162, 1, 3),
            element("acesulfaam-(", 1215, 1088, 1474, 1197, 1, 3),
            element("npelzuur:", 345, 973, 554, 1063, 1, 4),
            element("conserveermiddel.", 616, 955, 1121, 1055, 1, 4),
            element("kaliumsorbat", 1173, 985, 1466, 1122, 1, 4),
            element("Neturlijke", 321, 1145, 511, 1236, 1, 5),
            element("aroma's:", 563, 1134, 757, 1225, 1, 5),
            element("stabilisatoren:", 785, 1122, 1135, 1244, 1, 5),
            element("guarpitmel.,", 1158, 1170, 1405, 1280, 1, 5),
            element("Frisdrank", 305, 699, 535, 797, 2, 0),
            element("met", 557, 685, 659, 768, 2, 0),
            element("houthars.", 300, 1231, 483, 1313, 3, 0),
            element("Bevat", 500, 1235, 644, 1316, 3, 0),
            element("een", 660, 1238, 754, 1318, 3, 0),
            element("bron", 771, 1240, 890, 1321, 3, 0),
            element("van", 907, 1243, 1001, 1322, 3, 0),
            element("fenylalanine.", 1018, 1245, 1326, 1329, 3, 0),
            element("PER:", 308, 1478, 419, 1555, 4, 0),
            element("Energie:", 302, 1574, 502, 1652, 4, 1),
            element("Vetten:", 308, 1759, 474, 1839, 5, 0),
            element("Kolhydraten:", 294, 1930, 632, 2034, 6, 0),
            element("Waarvan", 324, 2037, 523, 2110, 6, 1),
            element("suikers:", 537, 2044, 744, 2117, 6, 1),
            element("Eiwitten:", 306, 2116, 519, 2213, 7, 0),
            element("VOEDINGSWAARDE", 741, 1366, 1282, 1458, 8, 0),
            element("Lout:", 287, 2203, 414, 2300, 9, 0),
            element("waarvan", 325, 1858, 522, 1932, 10, 0),
            element("verzadigde", 539, 1861, 833, 1937, 10, 0),
            element("vetzuren:", 850, 1866, 1096, 1940, 10, 0),
            element("0g", 1150, 1870, 1212, 1941, 10, 0),
            element("100", 1072, 1482, 1157, 1562, 11, 0),
            element("ml", 1183, 1490, 1232, 1566, 11, 0),
            element("250", 1318, 1499, 1391, 1578, 11, 0),
            element("ml", 1392, 1504, 1441, 1580, 11, 0),
            element("2501", 1165, 520, 1566, 814, 12, 0),
            element("13", 1071, 1588, 1121, 1663, 13, 0),
            element("kJI", 1162, 1588, 1248, 1663, 13, 0),
            element("3", 1323, 1588, 1345, 1663, 13, 0),
            element("UT", 1385, 1588, 1429, 1663, 13, 0),
            element("3", 1098, 1683, 1122, 1761, 14, 0),
            element("kcal", 1136, 1683, 1235, 1763, 14, 0),
            element("0.59", 1100, 1956, 1218, 2045, 15, 0),
            element("0.5g", 1100, 2051, 1207, 2129, 16, 0),
            element("rademark", 759, 3084, 1060, 3172, 17, 0),
            element("0", 1147, 2144, 1172, 2208, 18, 0),
            element("Referentie-inname", 346, 2326, 834, 2435, 19, 0),
            element("van", 849, 2364, 953, 2443, 19, 0),
            element("een", 972, 2357, 1084, 2446, 19, 0),
            element("gemiddelde", 1090, 2327, 1342, 2433, 19, 0),
            element("26The", 415, 2961, 581, 3062, 20, 0),
            element("Coca-Cola", 603, 2985, 878, 3100, 20, 0),
            element("Company", 912, 2998, 1124, 3082, 20, 0),
            element("keal", 1325, 1687, 1427, 1768, 21, 0),
            element("Volwassene", 329, 2421, 630, 2504, 22, 0),
            element("(8", 647, 2426, 698, 2505, 22, 0),
            element("400", 714, 2427, 819, 2506, 22, 0),
            element("kJ/2", 836, 2429, 962, 2509, 22, 0),
            element("000", 992, 2431, 1085, 2511, 22, 0),
            element("kcal.", 1098, 2433, 1248, 2513, 22, 0),
            element("Koel,", 282, 2487, 415, 2587, 22, 1),
            element("droog", 411, 2511, 577, 2617, 22, 1),
            element("Ten", 298, 2576, 379, 2661, 22, 2),
            element("minste", 395, 2591, 560, 2690, 22, 2),
            element("houdbaar", 590, 2615, 849, 2704, 22, 2),
            element("tot:", 848, 2620, 961, 2706, 22, 2),
            element("zle", 983, 2623, 1044, 2707, 22, 2),
            element("bodem", 1075, 2593, 1235, 2693, 22, 2),
            element("bük.", 1237, 2578, 1328, 2667, 22, 2),
            element("|", 578, 2539, 600, 2617, 22, 3),
            element("en", 599, 2539, 666, 2617, 22, 3),
            element("uit", 682, 2537, 754, 2617, 22, 3),
            element("rechtstreeks:", 768, 2532, 1145, 2616, 22, 3),
            element("zonlcht", 1140, 2495, 1328, 2603, 22, 3),
            element("beva", 1319, 2475, 1426, 2568, 22, 3),
            element("RECYCLE", 368, 2675, 1349, 2964, 23, 0),
            element("EM", 1285, 2602, 1560, 2842, 23, 0),
            element("1PM", 659, 2916, 880, 3017, 24, 0),
            element("RECYCLABLE", 911, 2867, 1354, 2991, 24, 0),
            element("1.3", 1352, 2037, 1394, 2123, 25, 0),
            element("3009", 821, 3267, 931, 3351, 26, 0),
            element("AT", 957, 3261, 1007, 3342, 26, 0),
            element("TRottardaa", 991, 3220, 1256, 3340, 26, 0),
        ),
    )

    /** `20260904-080948-287` */
    fun greenDrinkConflictedZeroFive(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("S0112\"659320", 405, -13, 516, 686, 0, 0),
            element("Friserank", 384, 814, 592, 907, 1, 0),
            element("mei", 625, 801, 708, 877, 1, 0),
            element("chtensap,", 843, 804, 1088, 898, 1, 0),
            element("met", 1121, 831, 1193, 908, 1, 0),
            element("o", 1224, 842, 1251, 914, 1, 0),
            element("Serankelend", 381, 891, 615, 987, 1, 1),
            element("wate", 648, 877, 746, 961, 1, 1),
            element("Vruchtensap", 813, 879, 1087, 974, 1, 1),
            element("uit", 1101, 905, 1158, 981, 1, 1),
            element("Coneantr", 1176, 921, 1340, 1027, 1, 1),
            element("Nrik", 389, 988, 482, 1071, 1, 2),
            element(".57,", 506, 979, 589, 1061, 1, 2),
            element("appel", 599, 967, 725, 1053, 1, 2),
            element("6", 788, 961, 815, 1037, 1, 2),
            element("passievrucht", 874, 958, 1168, 1071, 1, 2),
            element(".", 1189, 995, 1222, 1076, 1, 2),
            element("conserveermiddel.", 669, 1052, 1139, 1127, 1, 3),
            element("kalimsoh", 1185, 1081, 1407, 1203, 1, 3),
            element("appelzeur:", 349, 1053, 596, 1160, 2, 0),
            element("satfoerconcentraat,", 361, 1117, 877, 1232, 3, 0),
            element("z0etstoffen,", 900, 1131, 1197, 1221, 3, 0),
            element("turlijke", 409, 1228, 587, 1307, 3, 1),
            element("aroma's:", 607, 1213, 798, 1294, 3, 1),
            element("stabilisatoren:", 839, 1209, 1152, 1314, 3, 1),
            element("guargitnas", 1176, 1244, 1371, 1336, 3, 1),
            element("houthars.", 358, 1308, 543, 1408, 3, 2),
            element("Bevat", 574, 1306, 705, 1380, 3, 2),
            element("een", 732, 1306, 817, 1380, 3, 2),
            element("bron", 842, 1306, 952, 1380, 3, 2),
            element("van", 966, 1310, 1055, 1390, 3, 2),
            element("feny'alanine.", 1061, 1319, 1353, 1419, 3, 2),
            element("PER:", 361, 1539, 475, 1620, 4, 0),
            element("Energie:", 355, 1635, 548, 1717, 4, 1),
            element("Vetten:", 361, 1823, 527, 1889, 5, 0),
            element("Kolhydraten:", 344, 1994, 704, 2090, 6, 0),
            element("waarvan", 405, 2093, 590, 2165, 6, 1),
            element("suikers:", 600, 2090, 812, 2162, 6, 1),
            element("Eiwitten:", 346, 2173, 570, 2264, 7, 0),
            element("VOEDINGSW", 802, 1418, 1162, 1519, 8, 0),
            element("ARDE", 1181, 1441, 1302, 1528, 8, 0),
            element("Zout:", 338, 2269, 470, 2358, 9, 0),
            element("casolfn-(", 1209, 1169, 1462, 1293, 10, 0),
            element("waarvan", 377, 1909, 581, 1993, 11, 0),
            element("verzadigde", 597, 1909, 890, 1993, 11, 0),
            element("vetzuren:", 907, 1909, 1145, 1993, 11, 0),
            element("0g", 1195, 1909, 1258, 1993, 11, 0),
            element("100", 1109, 1532, 1204, 1617, 12, 0),
            element("ml", 1210, 1545, 1263, 1624, 12, 0),
            element("|", 1294, 1556, 1324, 1632, 12, 0),
            element("25l", 1329, 1560, 1407, 1642, 12, 0),
            element("13", 1110, 1629, 1181, 1709, 12, 1),
            element("kJI33UI", 1187, 1637, 1455, 1735, 12, 1),
            element("3", 1122, 1730, 1147, 1798, 13, 0),
            element("kcal", 1161, 1730, 1247, 1798, 13, 0),
            element("09", 1183, 1823, 1260, 1907, 14, 0),
            element("D2126The", 370, 3057, 643, 3156, 15, 0),
            element("Cathibalaby", 655, 3070, 1027, 3173, 15, 0),
            element("NTASA", 408, 3146, 587, 3245, 15, 1),
            element("0.5", 1153, 2094, 1221, 2159, 16, 0),
            element("n", 1221, 1989, 1261, 2000, 17, 0),
            element("0.5g", 1154, 1995, 1250, 2077, 18, 0),
            element("Referentie-inname", 376, 2394, 894, 2472, 19, 0),
            element("van", 912, 2394, 1012, 2472, 19, 0),
            element("een", 1030, 2394, 1123, 2472, 19, 0),
            element("gemiddele", 1137, 2394, 1391, 2472, 19, 0),
            element("volwassene", 388, 2479, 694, 2574, 19, 1),
            element("l8", 707, 2477, 781, 2565, 19, 1),
            element("400", 796, 2474, 896, 2563, 19, 1),
            element("kJ/2", 904, 2470, 1033, 2560, 19, 1),
            element("000", 1045, 2467, 1145, 2556, 19, 1),
            element("kcal.", 1163, 2463, 1287, 2554, 19, 1),
            element("Koel,", 337, 2566, 468, 2662, 19, 2),
            element("droog", 482, 2582, 633, 2679, 19, 2),
            element("en", 664, 2589, 731, 2669, 19, 2),
            element("uit", 729, 2587, 805, 2667, 19, 2),
            element("rechtstreoks", 822, 2577, 1181, 2665, 19, 2),
            element("Ten", 350, 2659, 442, 2744, 19, 3),
            element("minste", 449, 2669, 632, 2763, 19, 3),
            element("houdbaar", 656, 2676, 922, 2767, 19, 3),
            element("tot:/ie", 923, 2667, 1126, 2770, 19, 3),
            element("bodem", 1135, 2646, 1284, 2748, 19, 3),
            element("bNk", 1293, 2631, 1395, 2726, 19, 3),
            element("RECYO", 450, 2772, 1174, 3007, 20, 0),
            element("CLEN", 1135, 2682, 1612, 2966, 20, 0),
            element("MRECYCLABLE", 885, 2929, 1427, 3079, 21, 0),
        ),
    )

    /** `20260904-081003-678` */
    fun lidlCartonStrategyBTwo(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("170958151", 230, 2267, 292, 2543, 0, 0),
            element("MIX", 776, 73, 854, 110, 1, 0),
            element("Blard", 755, 123, 873, 169, 1, 1),
            element("FSCF8C", 530, 193, 769, 251, 1, 2),
            element("COt40", 808, 188, 933, 242, 1, 2),
            element("Gemiddelde", 708, 861, 956, 935, 2, 0),
            element("voedingswaarde/", 972, 857, 1339, 932, 2, 0),
            element("Valeurs", 656, 947, 812, 1017, 2, 1),
            element("nutritionnelles", 836, 943, 1142, 1015, 2, 1),
            element("moyennes/", 1180, 941, 1393, 1011, 2, 1),
            element("Nährwerte/Información", 652, 1022, 1143, 1098, 2, 2),
            element("nutricional/", 1172, 1018, 1413, 1091, 2, 2),
            element("Valores", 726, 1109, 886, 1172, 2, 3),
            element("nutricionais", 919, 1106, 1152, 1170, 2, 3),
            element("médios", 1166, 1105, 1321, 1167, 2, 3),
            element("Energie", 352, 1290, 502, 1364, 3, 0),
            element("Energie/Energie", 529, 1293, 853, 1369, 3, 0),
            element("Valor", 359, 1366, 453, 1427, 3, 1),
            element("energetico", 475, 1368, 691, 1431, 3, 1),
            element("Energia", 705, 1372, 854, 1434, 3, 1),
            element("Vetten/", 365, 1451, 510, 1520, 4, 0),
            element("Matières", 522, 1451, 679, 1520, 4, 0),
            element("grasses/Fet/Grasas/", 710, 1451, 1117, 1520, 4, 0),
            element("Lípidos", 341, 1528, 487, 1588, 4, 1),
            element("Hidratos", 353, 1899, 521, 1968, 5, 0),
            element("de", 537, 1899, 579, 1968, 5, 0),
            element("carbono,", 607, 1899, 785, 1968, 5, 0),
            element("Hiaratos", 805, 1899, 968, 1968, 5, 0),
            element("de", 984, 1899, 1026, 1968, 5, 0),
            element("carbono", 1041, 1899, 1208, 1968, 5, 0),
            element("waarvan", 368, 1974, 540, 2043, 5, 1),
            element("suikers/aont", 552, 1971, 800, 2041, 5, 1),
            element("sucres/davon", 818, 1968, 1094, 2037, 5, 1),
            element("Zucker", 1105, 1966, 1229, 2034, 5, 1),
            element("de", 367, 2041, 411, 2112, 5, 2),
            element("los", 415, 2041, 482, 2112, 5, 2),
            element("cuales,", 498, 2041, 638, 2112, 5, 2),
            element("uzücares/aas", 658, 2041, 922, 2112, 5, 2),
            element("quais", 943, 2041, 1038, 2112, 5, 2),
            element("açúcares", 1058, 2041, 1228, 2112, 5, 2),
            element("Vezels/Fibres", 323, 2121, 610, 2204, 5, 3),
            element("alimentaires/", 638, 2118, 896, 2201, 5, 3),
            element("Ballaststoffe/", 922, 2115, 1184, 2198, 5, 3),
            element("Fibra", 342, 2205, 438, 2280, 5, 4),
            element("alimentaria/", 452, 2205, 709, 2280, 5, 4),
            element("Fibra", 724, 2205, 814, 2280, 5, 4),
            element("Ewitten/Protéines/EiweitProteinas", 334, 2286, 1102, 2371, 5, 5),
            element("Proteinas", 318, 2373, 534, 2435, 6, 0),
            element("Zout/Sel/Salz/Sal/Sal", 337, 2443, 787, 2528, 6, 1),
            element("o/100", 1276, 1197, 1385, 1272, 7, 0),
            element("9", 1395, 1201, 1418, 1272, 7, 0),
            element("168", 1267, 1281, 1343, 1348, 7, 1),
            element("KI", 1359, 1282, 1396, 1349, 7, 1),
            element("waarvan", 378, 1594, 535, 1663, 8, 0),
            element("verzadigde", 562, 1594, 777, 1663, 8, 0),
            element("vetzuren/dont", 792, 1594, 1062, 1663, 8, 0),
            element("acides", 1092, 1594, 1200, 1663, 8, 0),
            element("gras", 1230, 1594, 1303, 1663, 8, 0),
            element("saturés", 366, 1659, 496, 1726, 8, 1),
            element("/davon", 514, 1658, 647, 1724, 8, 1),
            element("oesattiate", 662, 1656, 861, 1723, 8, 1),
            element("Fettsauren/de", 869, 1653, 1150, 1721, 8, 1),
            element("los", 1162, 1652, 1218, 1718, 8, 1),
            element("cuales,", 361, 1738, 509, 1806, 8, 2),
            element("satuadas", 524, 1738, 710, 1806, 8, 2),
            element("dos", 751, 1738, 815, 1806, 8, 2),
            element("quais", 830, 1738, 926, 1806, 8, 2),
            element("saturados", 945, 1738, 1144, 1806, 8, 2),
            element("Koolhydraten/Glucides/Kohlenhydrate!", 326, 1806, 1149, 1893, 8, 3),
            element("Lidl", 235, 2597, 318, 2673, 9, 0),
            element("Stiftung", 317, 2600, 496, 2678, 9, 0),
            element("&", 495, 2605, 530, 2678, 9, 0),
            element("Co.", 535, 2606, 613, 2682, 9, 0),
            element("KG,", 612, 2609, 687, 2684, 9, 0),
            element("Stiftsbergstr,1,", 232, 2672, 532, 2759, 9, 1),
            element("DE-74167", 264, 2752, 434, 2827, 9, 2),
            element("Neckarsulm,", 464, 2758, 696, 2835, 9, 2),
            element("Alemania/Alenmanha", 241, 2821, 658, 2915, 9, 3),
            element("40", 1287, 1359, 1324, 1419, 10, 0),
            element("kea", 1346, 1361, 1414, 1422, 10, 0),
            element("<0,19", 1302, 1517, 1421, 1592, 11, 0),
            element("<0.19", 1299, 1731, 1428, 1808, 12, 0),
            element("12g", 1334, 1899, 1430, 1968, 13, 0),
            element("619", 1342, 2041, 1440, 2112, 14, 0),
            element("08q", 1320, 2204, 1417, 2265, 15, 0),
            element("184", 1310, 2358, 1418, 2425, 16, 0),
            element("0,25", 1292, 2443, 1398, 2518, 17, 0),
            element("9", 1398, 2449, 1427, 2519, 17, 0),
            element("DISPOSE", 1156, 2629, 1326, 2684, 18, 0),
            element("OF", 1338, 2627, 1389, 2678, 18, 0),
            element("CORRECTLY", 1161, 2690, 1382, 2747, 19, 0),
            element("RECICLAR", 1181, 2753, 1363, 2811, 20, 0),
            element("CORRECTAMENTE", 1108, 2811, 1430, 2874, 21, 0),
            element("C/PAP", 1207, 3142, 1331, 3201, 22, 0),
            element("RECICLAR", 1182, 2887, 1363, 2946, 23, 0),
            element("CORRETAMENTE", 1115, 2939, 1420, 3006, 23, 1),
        ),
    )

    /** `20260904-081018-275` */
    fun lidlCartonTwelve(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("LSLSS602Zl", 170, 2284, 233, 2556, 0, 0),
            element("Brd", 731, 61, 854, 119, 1, 0),
            element("FSC", 468, 142, 590, 193, 1, 1),
            element("FUO", 654, 138, 751, 189, 1, 1),
            element("D40", 769, 134, 911, 186, 1, 1),
            element("Lipidos", 290, 1506, 445, 1574, 2, 0),
            element("Gemiddelde", 679, 829, 941, 906, 3, 0),
            element("voedingswaarde/", 955, 829, 1332, 906, 3, 0),
            element("Valeurs", 626, 912, 787, 979, 3, 1),
            element("nutritionnelles", 801, 912, 1123, 979, 3, 1),
            element("moyennes/", 1140, 912, 1383, 979, 3, 1),
            element("Nährwerte/", 630, 989, 866, 1066, 3, 2),
            element("formación", 908, 989, 1132, 1066, 3, 2),
            element("nutricional/", 1149, 989, 1411, 1066, 3, 2),
            element("Valores", 701, 1079, 862, 1139, 3, 3),
            element("nutricionais", 876, 1079, 1137, 1139, 3, 3),
            element("médios", 1155, 1079, 1312, 1139, 3, 3),
            element("Energie/Enegie/Energie", 310, 1261, 828, 1344, 4, 0),
            element("Valor", 291, 1339, 401, 1411, 4, 1),
            element("energético/Energia", 416, 1340, 823, 1415, 4, 1),
            element("Vetten/Matières", 292, 1432, 638, 1506, 4, 2),
            element("grasses/Fett/Grasas/", 651, 1436, 1080, 1511, 4, 2),
            element("Proteínas", 282, 2392, 490, 2453, 5, 0),
            element("Zout/Sel/Salz/Sal/Sal", 284, 2482, 756, 2553, 5, 1),
            element("o/100", 1239, 1179, 1364, 1250, 6, 0),
            element("g", 1382, 1179, 1407, 1250, 6, 0),
            element("Lidi", 176, 2631, 259, 2708, 7, 0),
            element("Stiftung", 263, 2633, 441, 2712, 7, 0),
            element("&", 452, 2637, 490, 2712, 7, 0),
            element("Co.", 495, 2638, 567, 2714, 7, 0),
            element("KG,", 576, 2639, 652, 2716, 7, 0),
            element("Stiftsbergstr.", 176, 2708, 454, 2797, 7, 1),
            element("1,", 457, 2721, 490, 2798, 7, 1),
            element("DEZ4167", 172, 2784, 380, 2870, 7, 2),
            element("Neckarsulm,", 380, 2791, 651, 2878, 7, 2),
            element("Alemanla/Alemanba", 175, 2870, 618, 2947, 7, 3),
            element("168", 1253, 1268, 1325, 1332, 8, 0),
            element("KJ/", 1342, 1268, 1407, 1332, 8, 0),
            element("waarvan", 309, 1585, 487, 1652, 9, 0),
            element("verzadigde", 509, 1585, 734, 1652, 9, 0),
            element("vetzuren/dont", 752, 1585, 1041, 1652, 9, 0),
            element("acides", 1055, 1585, 1187, 1652, 9, 0),
            element("gras", 1202, 1585, 1291, 1652, 9, 0),
            element("saturés/davon", 315, 1659, 615, 1733, 9, 1),
            element("gesättigte", 632, 1659, 832, 1733, 9, 1),
            element("Fettsäuren/de", 852, 1659, 1125, 1733, 9, 1),
            element("las", 1145, 1659, 1213, 1733, 9, 1),
            element("cuales,", 313, 1736, 468, 1802, 9, 2),
            element("saturadas/dos", 483, 1736, 787, 1802, 9, 2),
            element("quais", 797, 1736, 908, 1802, 9, 2),
            element("saturados", 923, 1736, 1128, 1802, 9, 2),
            element("Koolhydraten/Glucides/Kohlenhydrate", 288, 1821, 1136, 1895, 9, 3),
            element("Hidratos", 287, 1891, 469, 1966, 9, 4),
            element("de", 487, 1891, 537, 1966, 9, 4),
            element("carbono/Hidratos", 552, 1891, 944, 1966, 9, 4),
            element("de", 954, 1891, 999, 1966, 9, 4),
            element("carbono", 1016, 1891, 1185, 1966, 9, 4),
            element("waarvan", 316, 1972, 493, 2048, 9, 5),
            element("suikers/dont", 509, 1972, 766, 2048, 9, 5),
            element("sucres/davon", 785, 1972, 1074, 2048, 9, 5),
            element("Zucker/", 1091, 1972, 1234, 2048, 9, 5),
            element("de", 331, 2052, 353, 2121, 9, 6),
            element("los", 373, 2052, 436, 2121, 9, 6),
            element("cuales,", 452, 2052, 594, 2121, 9, 6),
            element("azúcares/dos", 612, 2052, 894, 2121, 9, 6),
            element("quais", 906, 2052, 1021, 2121, 9, 6),
            element("açúcares", 1035, 2052, 1216, 2121, 9, 6),
            element("Vezels/Fibres", 283, 2137, 578, 2215, 9, 7),
            element("alimentaires/Ballaststoffe/", 590, 2137, 1166, 2215, 9, 7),
            element("Fibra", 285, 2219, 390, 2288, 9, 8),
            element("alimentaria/Fibra", 405, 2215, 779, 2287, 9, 8),
            element("Eiwitten/Protéines/EiweißProteinas/", 285, 2308, 1102, 2383, 9, 9),
            element("40", 1255, 1342, 1312, 1401, 10, 0),
            element("kcal", 1327, 1344, 1412, 1402, 10, 0),
            element("<0,19", 1286, 1503, 1416, 1581, 11, 0),
            element("<0,1g", 1288, 1734, 1414, 1806, 12, 0),
            element("12g", 1304, 1894, 1426, 1977, 13, 0),
            element("6.1g", 1318, 2055, 1414, 2119, 14, 0),
            element("0,8g", 1307, 2223, 1407, 2287, 15, 0),
            element("18g", 1316, 2385, 1415, 2452, 16, 0),
            element("0,25g", 1286, 2475, 1419, 2549, 17, 0),
            element("DISPOSE", 1161, 2655, 1329, 2715, 18, 0),
            element("OF", 1326, 2654, 1375, 2713, 18, 0),
            element("CORRECTLY", 1142, 2723, 1369, 2778, 19, 0),
            element("RECICLAR", 1161, 2790, 1349, 2846, 20, 0),
            element("CORRECTAMENTE", 1087, 2851, 1420, 2917, 21, 0),
            element("C/PAP", 1177, 3184, 1316, 3254, 22, 0),
            element("RECICLAR", 1161, 2923, 1356, 2979, 23, 0),
            element("CORRETAMENIE", 1089, 2979, 1414, 3029, 23, 1),
        ),
    )

    /** `20260904-081039-484` */
    fun crackersSeventyTwoNothing(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("Zout,", 17, 1499, 122, 1558, 0, 0),
            element("en", 13, 1727, 73, 1767, 1, 0),
            element("Te", 361, 990, 420, 1059, 2, 0),
            element("minste", 464, 991, 665, 1062, 2, 0),
            element("houdbaar", 689, 994, 971, 1066, 2, 0),
            element("tot", 989, 998, 1065, 1068, 2, 0),
            element("en'met:", 1085, 999, 1298, 1070, 2, 0),
            element("zie", 1320, 1002, 1389, 1072, 2, 0),
            element("zijkant.", 1409, 1003, 1576, 1075, 2, 0),
            element("Kijk,", 525, 1060, 633, 1124, 2, 1),
            element("ruik", 657, 1063, 751, 1127, 2, 1),
            element("en", 771, 1066, 833, 1128, 2, 1),
            element("proef", 857, 1068, 993, 1133, 2, 1),
            element("na", 1009, 1072, 1071, 1134, 2, 1),
            element("deze", 1092, 1074, 1211, 1138, 2, 1),
            element("datum.", 1232, 1077, 1410, 1143, 2, 1),
            element("Koel,", 543, 1128, 672, 1198, 2, 2),
            element("donker", 696, 1131, 879, 1202, 2, 2),
            element("en", 900, 1136, 962, 1204, 2, 2),
            element("droog", 986, 1137, 1133, 1208, 2, 2),
            element("bewaren.", 1153, 1141, 1394, 1212, 2, 2),
            element("Voedingswaarde", 228, 1267, 719, 1349, 3, 0),
            element("per", 742, 1282, 834, 1352, 3, 0),
            element("energie", 235, 1435, 453, 1499, 4, 0),
            element("vetten,", 231, 1580, 431, 1637, 5, 0),
            element("Waarvan", 448, 1583, 672, 1640, 5, 0),
            element("-", 255, 1646, 274, 1710, 5, 1),
            element("verzadigde", 277, 1646, 562, 1710, 5, 1),
            element("vetzuren", 579, 1646, 800, 1710, 5, 1),
            element("-", 238, 1721, 257, 1784, 5, 2),
            element("onverzadigde", 277, 1721, 631, 1784, 5, 2),
            element("vetzuren", 651, 1721, 865, 1784, 5, 2),
            element("koolhydraten,", 233, 1790, 636, 1853, 5, 3),
            element("waarvan", 652, 1790, 872, 1853, 5, 3),
            element("-", 257, 1859, 275, 1919, 6, 0),
            element("suikers", 282, 1859, 461, 1919, 6, 0),
            element("vezels", 236, 1928, 414, 1982, 7, 0),
            element("eiwitten", 239, 1999, 464, 2052, 8, 0),
            element("ZOut", 236, 2070, 361, 2123, 9, 0),
            element("100", 1080, 1282, 1192, 1357, 10, 0),
            element("g", 1181, 1288, 1219, 1358, 10, 0),
            element("1820", 977, 1434, 1102, 1502, 11, 0),
            element("kJ/", 1120, 1440, 1212, 1506, 11, 0),
            element("432", 997, 1512, 1095, 1565, 12, 0),
            element("kcal", 1110, 1512, 1216, 1565, 12, 0),
            element("11g", 1118, 1583, 1215, 1646, 13, 0),
            element("1,1g", 1092, 1650, 1211, 1715, 14, 0),
            element("9,9", 1076, 1721, 1162, 1785, 15, 0),
            element("g", 1177, 1721, 1209, 1785, 15, 0),
            element("2,3", 1076, 1859, 1170, 1929, 16, 0),
            element("g", 1178, 1859, 1209, 1929, 16, 0),
            element("2,8", 1082, 1926, 1159, 1991, 17, 0),
            element("g", 1169, 1926, 1207, 1991, 17, 0),
            element("9,8", 1079, 1987, 1157, 2051, 18, 0),
            element("g", 1179, 1987, 1207, 2051, 18, 0),
            element("2,20", 1047, 2066, 1157, 2123, 19, 0),
            element("g", 1179, 2066, 1207, 2123, 19, 0),
            element("4", 1391, 1289, 1427, 1344, 20, 0),
            element("crackers", 1442, 1289, 1668, 1344, 20, 0),
            element("(25", 1517, 1355, 1615, 1427, 21, 0),
            element("g)", 1629, 1361, 1682, 1430, 21, 0),
            element("458", 1473, 1438, 1574, 1500, 21, 1),
            element("kJ", 1601, 1440, 1652, 1500, 21, 1),
            element("109", 1477, 1510, 1569, 1565, 22, 0),
            element("kcal", 1589, 1511, 1677, 1566, 22, 0),
            element("2,8", 1551, 1576, 1637, 1645, 23, 0),
            element("g", 1651, 1584, 1682, 1649, 23, 0),
            element("0,3", 1551, 1647, 1634, 1712, 24, 0),
            element("g", 1651, 1652, 1677, 1714, 24, 0),
            element("2,5g", 1549, 1717, 1674, 1783, 25, 0),
            element("18", 1576, 1791, 1631, 1848, 26, 0),
            element("g", 1649, 1791, 1668, 1848, 26, 0),
            element("0,6g", 1546, 1854, 1674, 1923, 27, 0),
            element("0,79", 1542, 1923, 1668, 1992, 28, 0),
            element("2,5", 1545, 1995, 1625, 2057, 29, 0),
            element("g", 1644, 1995, 1667, 2057, 29, 0),
            element("0,55", 1507, 2062, 1621, 2119, 30, 0),
            element("g", 1639, 2062, 1653, 2119, 30, 0),
        ),
    )

    /** `20260904-081055-219` */
    fun yoghurtThreePointTwo(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("Gemiddelde", 538, 1128, 1018, 1243, 0, 0),
            element("voedingswaarde/", 1042, 1151, 1658, 1272, 0, 0),
            element("Valeurs", 674, 1255, 966, 1336, 0, 1),
            element("nutritionnelles", 992, 1262, 1539, 1349, 0, 1),
            element("Energie/Energie", 279, 1549, 638, 1639, 1, 0),
            element("Zout/Sel", 303, 2075, 479, 2152, 2, 0),
            element("moyennes", 925, 1392, 1310, 1466, 3, 0),
            element("Vetten/", 285, 1686, 443, 1757, 4, 0),
            element("Matières", 444, 1701, 642, 1776, 4, 0),
            element("grasses", 654, 1721, 833, 1794, 4, 0),
            element("Waarvan", 303, 1759, 480, 1825, 4, 1),
            element("verzadigde", 493, 1771, 745, 1841, 4, 1),
            element("vetzuren/", 759, 1788, 984, 1857, 4, 1),
            element("dont", 307, 1816, 397, 1874, 4, 2),
            element("acides", 408, 1824, 545, 1886, 4, 2),
            element("qras", 559, 1837, 658, 1895, 4, 2),
            element("saturés", 670, 1846, 836, 1910, 4, 2),
            element("Koolhydraten/Glucides", 297, 1884, 802, 1988, 4, 3),
            element("waarvan", 311, 1948, 486, 2015, 4, 4),
            element("suikers/dont", 497, 1963, 777, 2038, 4, 4),
            element("sucres", 788, 1988, 930, 2051, 4, 4),
            element("Eiwitten/Protéines", 300, 2009, 696, 2108, 4, 5),
            element("o/100g", 1000, 1509, 1187, 1580, 5, 0),
            element("503", 1001, 1587, 1089, 1651, 6, 0),
            element("k1/", 1111, 1590, 1185, 1652, 6, 0),
            element("121", 999, 1657, 1063, 1706, 6, 1),
            element("kcal", 1087, 1659, 1178, 1708, 6, 1),
            element("10,0", 1042, 1731, 1149, 1797, 7, 0),
            element("g", 1144, 1738, 1170, 1798, 7, 0),
            element("6,6", 1050, 1864, 1118, 1920, 8, 0),
            element("g", 1138, 1864, 1161, 1920, 8, 0),
            element("3,2", 1047, 1933, 1114, 1989, 9, 0),
            element("g", 1130, 1939, 1155, 1991, 9, 0),
            element("3,2", 1041, 1991, 1110, 2051, 10, 0),
            element("9", 1123, 2002, 1152, 2056, 10, 0),
            element("4,6", 1037, 2070, 1105, 2124, 11, 0),
            element("g", 1122, 2070, 1145, 2124, 11, 0),
            element("0,10", 1010, 2135, 1100, 2190, 12, 0),
            element("g", 1116, 2142, 1140, 2192, 12, 0),
            element("o/125g", 1334, 1514, 1490, 1580, 13, 0),
            element("%RI", 1556, 1510, 1630, 1573, 13, 0),
            element("Deze", 338, 2359, 427, 2422, 14, 0),
            element("verpakking", 433, 2376, 661, 2465, 14, 0),
            element("bevat", 681, 2408, 794, 2468, 14, 0),
            element("8", 810, 2419, 829, 2471, 14, 0),
            element("porties", 867, 2421, 1003, 2478, 14, 0),
            element("van", 1026, 2420, 1101, 2476, 14, 0),
            element("125g-/", 1118, 2419, 1233, 2476, 14, 0),
            element("Cel", 342, 2420, 402, 2476, 14, 1),
            element("emballage", 402, 2438, 615, 2511, 14, 1),
            element("contient", 630, 2467, 801, 2535, 14, 1),
            element("8", 831, 2481, 847, 2537, 14, 1),
            element("portions", 865, 2481, 1043, 2538, 14, 1),
            element("de", 1058, 2482, 1105, 2538, 14, 1),
            element("125g", 1125, 2481, 1204, 2538, 14, 1),
            element("629", 1323, 1597, 1402, 1650, 15, 0),
            element("kī/", 1420, 1597, 1482, 1650, 15, 0),
            element("152", 1323, 1655, 1394, 1712, 16, 0),
            element("kcal", 1408, 1652, 1482, 1709, 16, 0),
            element("8%", 1549, 1650, 1609, 1705, 16, 0),
            element("12,5", 1360, 1730, 1437, 1792, 17, 0),
            element("g", 1454, 1728, 1473, 1786, 17, 0),
            element("18%", 1526, 1720, 1601, 1782, 17, 0),
            element("8,3g", 1351, 1852, 1452, 1916, 18, 0),
            element("42%", 1500, 1844, 1585, 1906, 18, 0),
            element("4,0g", 1339, 1923, 1444, 1987, 19, 0),
            element("4,0", 1355, 1987, 1420, 2044, 20, 0),
            element("g", 1414, 1987, 1437, 2044, 20, 0),
            element("2%", 1513, 1914, 1575, 1973, 21, 0),
            element("RI", 325, 2170, 374, 2225, 22, 0),
            element("(reference", 368, 2175, 585, 2253, 22, 0),
            element("intake)", 594, 2205, 731, 2272, 22, 0),
            element("=", 766, 2224, 780, 2271, 22, 0),
            element("Referentie-inname", 811, 2223, 1211, 2277, 22, 0),
            element("van", 1234, 2230, 1300, 2278, 22, 0),
            element("een", 1324, 2220, 1382, 2267, 22, 0),
            element("Télérence", 319, 2279, 503, 2356, 22, 1),
            element("pour", 516, 2311, 622, 2376, 22, 1),
            element("un", 628, 2331, 675, 2384, 22, 1),
            element("adulte-type", 703, 2336, 957, 2391, 22, 1),
            element("(8400", 973, 2337, 1112, 2391, 22, 1),
            element("kJ/2000", 1116, 2337, 1286, 2391, 22, 1),
            element("kcal)", 1310, 2316, 1398, 2379, 22, 1),
            element("(AR)", 1399, 2305, 1480, 2366, 22, 1),
            element("gemiddelde", 328, 2227, 546, 2302, 22, 2),
            element("volwassene", 577, 2270, 825, 2327, 22, 2),
            element("(8400", 830, 2277, 968, 2331, 22, 2),
            element("kJ/2000", 994, 2282, 1164, 2337, 22, 2),
            element("kcal)/Apport", 1184, 2261, 1434, 2337, 22, 2),
            element("de", 1450, 2256, 1486, 2312, 22, 2),
            element("4%", 1508, 1975, 1566, 2018, 23, 0),
            element("5,8g", 1327, 2052, 1430, 2116, 24, 0),
            element("12%", 1486, 2040, 1558, 2100, 24, 0),
            element("0,13g", 1308, 2129, 1420, 2179, 25, 0),
            element("2%", 1489, 2110, 1554, 2158, 25, 0),
        ),
    )

    /** `20260904-081108-784` */
    fun yoghurtTubNothing(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("Gemiddelde", 78, 1438, 288, 1496, 0, 0),
            element("Voedingswaarde", 304, 1440, 663, 1499, 0, 0),
            element("/", 683, 1445, 699, 1500, 0, 0),
            element("Durchschnittlicher", 695, 1444, 1125, 1504, 0, 0),
            element("Nährwert/", 1152, 1449, 1367, 1507, 0, 0),
            element("Valeur", 76, 1493, 169, 1548, 0, 1),
            element("Nutritive", 188, 1494, 349, 1550, 0, 1),
            element("/", 368, 1497, 383, 1550, 0, 1),
            element("Nutritional", 399, 1497, 618, 1553, 0, 1),
            element("Facts", 621, 1500, 750, 1555, 0, 1),
            element("/Información", 759, 1502, 1067, 1560, 0, 1),
            element("Nutrícional/", 1103, 1506, 1352, 1563, 0, 1),
            element("Naringsvärde", 58, 1546, 310, 1605, 0, 2),
            element("/", 315, 1551, 335, 1605, 0, 2),
            element("Naeringsindhold", 340, 1551, 695, 1612, 0, 2),
            element("Enei", 72, 1621, 146, 1678, 1, 0),
            element("FEnergie", 153, 1624, 257, 1682, 1, 0),
            element("/", 270, 1628, 287, 1682, 1, 0),
            element("énergie", 297, 1629, 404, 1687, 1, 0),
            element("/Energía", 403, 1632, 542, 1691, 1, 0),
            element("/", 550, 1638, 564, 1692, 1, 0),
            element("Energy", 572, 1638, 681, 1696, 1, 0),
            element("/", 691, 1642, 706, 1696, 1, 0),
            element("Energi", 713, 1643, 810, 1701, 1, 0),
            element("a", 90, 1684, 105, 1732, 2, 0),
            element("Neten", 148, 1685, 223, 1736, 2, 0),
            element("/Fett", 230, 1688, 312, 1739, 2, 0),
            element("/Lipides", 308, 1691, 433, 1744, 2, 0),
            element("/", 441, 1696, 455, 1744, 2, 0),
            element("Fat", 462, 1697, 508, 1746, 2, 0),
            element("/", 529, 1699, 544, 1747, 2, 0),
            element("Grasas", 538, 1699, 639, 1751, 2, 0),
            element("/Fedt.", 649, 1703, 741, 1755, 2, 0),
            element("lormation:", 87, 2285, 360, 2372, 3, 0),
            element("doymu", 124, 1755, 204, 1803, 4, 0),
            element("yağ", 224, 1757, 277, 1803, 4, 0),
            element("/", 277, 1757, 290, 1803, 4, 0),
            element("waarvan", 305, 1757, 421, 1805, 4, 0),
            element("verzadigde", 437, 1759, 589, 1807, 4, 0),
            element("vetzuren/davon", 607, 1761, 859, 1811, 4, 0),
            element("gesättigte", 859, 1765, 1017, 1813, 4, 0),
            element("Fettsãuren/gras", 1036, 1767, 1279, 1817, 4, 0),
            element("Sature/", 1288, 1770, 1386, 1818, 4, 0),
            element("sturcted", 125, 1808, 240, 1858, 4, 1),
            element("fat", 250, 1811, 285, 1859, 4, 1),
            element("/", 297, 1813, 312, 1860, 4, 1),
            element("grosa", 323, 1813, 399, 1862, 4, 1),
            element("saturada", 399, 1815, 529, 1865, 4, 1),
            element("/", 540, 1818, 555, 1865, 4, 1),
            element("varav", 563, 1819, 639, 1867, 4, 1),
            element("mãättat", 641, 1821, 751, 1870, 4, 1),
            element("/heraf", 754, 1823, 866, 1873, 4, 1),
            element("mættede", 867, 1826, 1003, 1877, 4, 1),
            element("fedtsyrer", 1011, 1829, 1143, 1880, 4, 1),
            element("nhidat/oolhydraten", 119, 1869, 422, 1922, 4, 2),
            element("/", 433, 1874, 447, 1922, 4, 2),
            element("Kohlenhydrate", 457, 1874, 657, 1925, 4, 2),
            element("/", 657, 1877, 671, 1925, 4, 2),
            element("Gluides", 692, 1877, 798, 1927, 4, 2),
            element("/", 809, 1879, 823, 1927, 4, 2),
            element("Carbohydrate", 825, 1879, 1003, 1930, 4, 2),
            element("/", 1011, 1882, 1026, 1930, 4, 2),
            element("Carbohidratos", 1032, 1882, 1220, 1933, 4, 2),
            element("/Kolbydrater/", 1229, 1885, 1423, 1936, 4, 2),
            element("Nta", 1421, 1888, 1540, 1937, 4, 2),
            element("oarvan", 244, 1938, 327, 1990, 4, 3),
            element("sukers", 342, 1938, 431, 1990, 4, 3),
            element("/", 443, 1938, 458, 1990, 4, 3),
            element("davon", 460, 1938, 543, 1990, 4, 3),
            element("Zucker", 555, 1938, 636, 1990, 4, 3),
            element("/", 659, 1938, 674, 1990, 4, 3),
            element("dont", 676, 1938, 742, 1990, 4, 3),
            element("sucres/", 741, 1938, 866, 1990, 4, 3),
            element("sugars", 838, 1938, 959, 1990, 4, 4),
            element("/azúcares/", 964, 1938, 1128, 1990, 4, 4),
            element("varw", 1136, 1938, 1207, 1990, 4, 4),
            element("socker", 1223, 1938, 1300, 1990, 4, 4),
            element("/herat", 1314, 1938, 1396, 1990, 4, 4),
            element("sukierate", 1402, 1938, 1537, 1990, 4, 4),
            element("Protein", 107, 1975, 184, 2023, 4, 5),
            element("/Eiwiten", 189, 1980, 313, 2030, 4, 5),
            element("/Eiweiß/", 320, 1986, 471, 2038, 4, 5),
            element("Protéines", 468, 1994, 597, 2045, 4, 5),
            element("/", 614, 2002, 629, 2046, 4, 5),
            element("Proteína.", 646, 2003, 764, 2054, 4, 5),
            element("uL/", 118, 2024, 162, 2072, 4, 6),
            element("Zaut", 168, 2029, 215, 2076, 4, 6),
            element("/Salz", 221, 2033, 297, 2084, 4, 6),
            element("/", 302, 2041, 318, 2086, 4, 6),
            element("Sel", 334, 2043, 359, 2089, 4, 6),
            element("/Salt", 370, 2047, 446, 2098, 4, 6),
            element("/", 459, 2055, 475, 2100, 4, 6),
            element("Sal.", 482, 2057, 522, 2105, 4, 6),
            element("Allergie-Anformatie:", 101, 2188, 530, 2274, 5, 0),
            element("Bevat", 585, 2231, 711, 2290, 5, 0),
            element("koemelkeiwit", 759, 2233, 1073, 2286, 5, 0),
            element("en", 1122, 2231, 1189, 2282, 5, 0),
            element("lactose", 1217, 2214, 1380, 2274, 5, 0),
            element("Allergie-", 1484, 2192, 1632, 2249, 5, 0),
            element("lniormationen:", 96, 2234, 416, 2325, 5, 1),
            element("enthält", 474, 2281, 619, 2331, 5, 1),
            element("Kuhmilcheiweiß", 678, 2285, 1057, 2341, 5, 1),
            element("und", 1110, 2284, 1196, 2330, 5, 1),
            element("Laktose", 1230, 2270, 1401, 2323, 5, 1),
            element("alleye", 236, 2681, 372, 2760, 6, 0),
            element("Contains", 420, 2330, 636, 2389, 7, 0),
            element("Sifue", 119, 2394, 238, 2463, 8, 0),
            element("lgi", 321, 2422, 387, 2494, 8, 0),
            element("Ev", 462, 2437, 507, 2506, 8, 0),
            element("Yoğurt", 581, 2448, 735, 2528, 8, 0),
            element("geleneksel", 830, 2453, 1053, 2522, 8, 0),
            element("usüllere", 1141, 2444, 1288, 2508, 8, 0),
            element("an", 240, 2497, 298, 2554, 8, 1),
            element("yagi", 317, 2503, 399, 2563, 8, 1),
            element("inek", 433, 2513, 537, 2574, 8, 1),
            element("sütünden", 558, 2522, 763, 2592, 8, 1),
            element("yapilmiştır.", 796, 2531, 1039, 2600, 8, 1),
            element("Içinde", 1085, 2517, 1204, 2588, 8, 1),
            element("tat", 1238, 2513, 1286, 2580, 8, 1),
            element("Ne", 245, 2562, 287, 2612, 8, 2),
            element("konserve", 299, 2569, 479, 2640, 8, 2),
            element("edicd", 504, 2589, 595, 2649, 8, 2),
            element("hicbir", 620, 2595, 749, 2658, 8, 2),
            element("yabancı", 778, 2600, 950, 2660, 8, 2),
            element("madde", 987, 2592, 1114, 2653, 8, 2),
            element("yoktu.", 1141, 2587, 1277, 2648, 8, 2),
            element("caalig", 254, 2618, 410, 2700, 8, 3),
            element("le", 452, 2652, 494, 2714, 8, 3),
            element("yiyebilirsiniz.", 558, 2663, 831, 2729, 8, 3),
            element("Temizliğe,", 869, 2666, 1081, 2731, 8, 3),
            element("tazelige", 1118, 2654, 1274, 2719, 8, 3),
            element("Savdyo", 153, 2799, 275, 2870, 9, 0),
            element("Azami", 430, 2724, 561, 2778, 10, 0),
            element("cow's", 708, 2347, 848, 2392, 11, 0),
            element("milk", 926, 2346, 1022, 2389, 11, 0),
            element("protein", 1095, 2334, 1260, 2392, 11, 0),
            element("dikkat", 623, 2729, 764, 2795, 12, 0),
            element("Sarf", 836, 2741, 922, 2796, 13, 0),
            element("edilmektedi.", 990, 2717, 1274, 2792, 13, 0),
            element("1gurt", 245, 2830, 384, 2902, 13, 1),
            element("Door", 426, 2859, 514, 2921, 13, 1),
            element("Verse", 559, 2885, 654, 2933, 13, 1),
            element("volle", 692, 2885, 790, 2933, 13, 1),
            element("melk", 844, 2885, 926, 2933, 13, 1),
            element("met", 980, 2885, 1046, 2933, 13, 1),
            element("een", 1084, 2885, 1150, 2933, 13, 1),
            element("mld", 1199, 2860, 1270, 2917, 13, 1),
            element("Yoghurt", 523, 2803, 707, 2873, 13, 2),
            element("is", 748, 2815, 771, 2876, 13, 2),
            element("een", 836, 2822, 905, 2876, 13, 2),
            element("zachte", 952, 2813, 1089, 2870, 13, 2),
            element("romige", 1138, 2805, 1267, 2861, 13, 2),
            element("Zuren", 559, 2947, 674, 2998, 14, 0),
            element("ontstaat", 684, 2951, 854, 3004, 14, 0),
            element("deze", 873, 2947, 952, 3005, 14, 0),
            element("risse", 980, 2940, 1078, 2999, 14, 0),
            element("lekkenij", 1084, 2930, 1248, 2994, 14, 0),
            element("ovesa", 327, 3063, 518, 3133, 15, 0),
            element("39,2321", 534, 3092, 660, 3152, 15, 0),
            element("eand", 545, 3139, 635, 3194, 16, 0),
            element("INHOUD", 1077, 3036, 1292, 3109, 17, 0),
            element("and", 1329, 2331, 1407, 2368, 18, 0),
            element("Per/Pro", 1507, 1452, 1642, 1495, 19, 0),
            element("Por/Par", 1503, 1505, 1638, 1548, 19, 1),
            element("Z63", 1451, 1642, 1506, 1681, 20, 0),
            element("d/63", 1513, 1640, 1600, 1680, 20, 0),
            element("kcal", 1606, 1639, 1657, 1678, 20, 0),
            element("359", 1589, 1701, 1659, 1742, 20, 1),
            element("Pr.", 1500, 1558, 1548, 1603, 21, 0),
            element("100g", 1555, 1559, 1644, 1605, 21, 0),
            element("2000", 1117, 3083, 1424, 3214, 22, 0),
            element("g", 1406, 3079, 1468, 3181, 22, 0),
            element("Product", 1073, 3216, 1246, 3299, 23, 0),
            element("of", 1247, 3206, 1303, 3268, 23, 0),
            element("Holand", 1297, 3179, 1460, 3260, 23, 0),
            element("Allery", 1482, 2252, 1618, 2297, 24, 0),
            element("laciose.", 1471, 2295, 1614, 2352, 24, 1),
        ),
    )

    /** `20260904-081129-886` */
    fun proseThreePointThree(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("ties", -1, 1928, 81, 1991, 0, 0),
            element("(15", 98, 1932, 177, 1993, 0, 0),
            element("g)", 193, 1936, 245, 1996, 0, 0),
            element("ZUursel.", 23, 2079, 159, 2126, 1, 0),
            element("er", 0, 2136, 21, 2182, 1, 1),
            element("100", 37, 2137, 113, 2185, 1, 1),
            element("g", 125, 2141, 152, 2186, 1, 1),
            element("en", 167, 2142, 219, 2189, 1, 1),
            element("er", 0, 2196, 31, 2247, 1, 2),
            element("100", 46, 2197, 124, 2250, 1, 2),
            element("g", 134, 2201, 175, 2252, 1, 2),
            element("met", 0, 2314, 93, 2363, 2, 0),
            element("Vaak", 13, 2375, 109, 2417, 3, 0),
            element("oef.", 14, 2434, 85, 2476, 4, 0),
            element("ELEZ7", 43, 2720, 464, 2789, 5, 0),
            element("07:11", 180, 2813, 439, 2872, 5, 1),
            element("glulenvri", 193, 3101, 316, 3142, 6, 0),
            element("Voedingswaarde", 677, 1759, 1061, 1820, 7, 0),
            element("per", 1077, 1769, 1154, 1821, 7, 0),
            element("100", 1171, 1771, 1248, 1824, 7, 0),
            element("g", 1264, 1774, 1288, 1825, 7, 0),
            element("energie", 473, 1813, 651, 1865, 8, 0),
            element("781", 668, 1816, 732, 1867, 8, 0),
            element("kJ", 755, 1818, 797, 1867, 8, 0),
            element("/", 812, 1819, 833, 1868, 8, 0),
            element("188", 852, 1819, 921, 1870, 8, 0),
            element("kcal,", 938, 1821, 1029, 1871, 8, 0),
            element("vetten", 1046, 1822, 1186, 1874, 8, 0),
            element("16", 1207, 1825, 1248, 1875, 8, 0),
            element("g,", 1264, 1826, 1298, 1876, 8, 0),
            element("waarvan", 1317, 1827, 1482, 1879, 8, 0),
            element("verzadigde", 474, 1869, 704, 1920, 8, 1),
            element("vetzuren", 718, 1872, 890, 1923, 8, 1),
            element("10", 916, 1875, 972, 1923, 8, 1),
            element("g,", 970, 1876, 1005, 1924, 8, 1),
            element("waarvan", 1022, 1876, 1191, 1927, 8, 1),
            element("onverzadigde", 1206, 1878, 1474, 1930, 8, 1),
            element("vetzuren", 425, 1922, 599, 1973, 8, 2),
            element("4,0", 618, 1924, 683, 1973, 8, 2),
            element("g,", 700, 1926, 736, 1974, 8, 2),
            element("koolhydraten", 757, 1926, 1050, 1978, 8, 2),
            element("3,3", 1067, 1930, 1131, 1979, 8, 2),
            element("g,", 1149, 1932, 1182, 1980, 8, 2),
            element("waarvan", 1201, 1932, 1365, 1983, 8, 2),
            element("suikers", 1384, 1934, 1523, 1984, 8, 2),
            element("3,3", 494, 1979, 560, 2029, 8, 3),
            element("g,", 578, 1979, 614, 2030, 8, 3),
            element("vezels", 633, 1980, 774, 2031, 8, 3),
            element("0", 790, 1982, 813, 2032, 8, 3),
            element("g,", 831, 1982, 866, 2032, 8, 3),
            element("eiwitten", 886, 1982, 1063, 2034, 8, 3),
            element("7,8", 1079, 1984, 1137, 2035, 8, 3),
            element("g,", 1154, 1985, 1189, 2036, 8, 3),
            element("zout", 1206, 1986, 1300, 2036, 8, 3),
            element("0,80", 1315, 1987, 1402, 2037, 8, 3),
            element("g.", 1420, 1988, 1445, 2038, 8, 3),
            element("Vega", 630, 2327, 768, 2386, 9, 0),
            element("FOLIE", 614, 2447, 744, 2490, 10, 0),
            element("BIJ", 652, 2663, 703, 2693, 11, 0),
            element("RESTAFVAL", 575, 2680, 764, 2735, 12, 0),
            element("OVERIG", 575, 2777, 772, 2841, 13, 0),
            element("BIJ", 581, 2991, 629, 3029, 14, 0),
            element("PLASTIC", 639, 2988, 759, 3028, 14, 0),
            element("AFVAL", 629, 3031, 714, 3059, 14, 1),
            element("10810725A", 1176, 2116, 1361, 2153, 15, 0),
            element("Albert", 490, 3121, 618, 3183, 16, 0),
            element("Heijn", 632, 3118, 735, 3180, 16, 0),
            element("B.V.,", 767, 3115, 862, 3176, 16, 0),
            element("Provincialeweg", 865, 3108, 1170, 3174, 16, 0),
            element("1", 1221, 3107, 1238, 3166, 16, 0),
            element("1506", 539, 3178, 634, 3236, 16, 1),
            element("MA", 649, 3176, 719, 3233, 16, 1),
            element("ZAANDAM,", 730, 3170, 957, 3231, 16, 1),
            element("Nederland", 978, 3165, 1190, 3225, 16, 1),
            element("ah.nl/ah.be", 744, 3232, 983, 3295, 16, 2),
            element("8718907'425933>\$", 1286, 2131, 1375, 3077, 17, 0),
            element("DK", 1415, 3114, 1455, 3153, 18, 0),
            element("MI98", 1394, 3162, 1482, 3205, 19, 0),
            element("EU", 1419, 3208, 1455, 3250, 19, 1),
        ),
    )

    /** `20260904-081141-102` */
    fun fritessausThirteenPointTwo(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("07105071A-1", 278, 2262, 318, 2441, 0, 0),
            element("Inorediènten:", 445, 935, 713, 999, 1, 0),
            element("water,", 725, 943, 842, 1003, 1, 0),
            element("raapolie,", 854, 947, 1019, 1007, 1, 0),
            element("maiszetmeel,", 1046, 952, 1291, 1015, 1, 0),
            element("azijn.", 1287, 959, 1375, 1018, 1, 0),
            element("weipoeder", 423, 999, 617, 1060, 1, 1),
            element("(melk),", 629, 1003, 772, 1062, 1, 1),
            element("suiker,", 784, 1007, 910, 1066, 1, 1),
            element("gedroogde", 920, 1010, 1124, 1070, 1, 1),
            element("glucosestroop.", 1154, 1015, 1402, 1076, 1, 1),
            element("vrie", 389, 1053, 464, 1109, 1, 2),
            element("uitoopei-eigeel,", 476, 1056, 792, 1117, 1, 2),
            element("zOut,", 804, 1065, 896, 1121, 1, 2),
            element("gemodificeerd", 907, 1068, 1180, 1129, 1, 2),
            element("maiszetmeel", 1194, 1076, 1415, 1135, 1, 2),
            element("SDecerjen", 435, 1116, 635, 1176, 1, 3),
            element("(mosterd),", 648, 1120, 859, 1180, 1, 3),
            element("voedingszuur", 872, 1125, 1127, 1186, 1, 3),
            element("(citroenzuur).", 1145, 1131, 1375, 1191, 1, 3),
            element("dextrose,", 417, 1178, 583, 1232, 1, 4),
            element("conserveermiddelen", 595, 1181, 985, 1239, 1, 4),
            element("(E202,", 998, 1189, 1117, 1242, 1, 4),
            element("E211),", 1132, 1192, 1246, 1244, 1, 4),
            element("kruiden.", 1260, 1194, 1398, 1247, 1, 4),
            element("Qistextract,", 477, 1233, 683, 1293, 1, 5),
            element("verdikkingsmiddel", 693, 1238, 1042, 1301, 1, 5),
            element("(xanthaangom),", 1057, 1246, 1338, 1308, 1, 5),
            element("aroma's,", 656, 1298, 820, 1352, 1, 6),
            element("kleurstof", 834, 1301, 1001, 1355, 1, 6),
            element("(E161b).", 1013, 1304, 1158, 1358, 1, 6),
            element("Gemiddelde", 501, 1471, 712, 1522, 2, 0),
            element("voedingswaarde", 726, 1475, 1024, 1528, 2, 0),
            element("per", 1040, 1482, 1096, 1530, 2, 0),
            element("100", 1112, 1483, 1176, 1531, 2, 0),
            element("ml", 1192, 1484, 1230, 1532, 2, 0),
            element("Energetische", 501, 1556, 733, 1606, 2, 1),
            element("waarde", 746, 1559, 882, 1608, 2, 1),
            element("Fritessaus", 654, 691, 1203, 815, 3, 0),
            element("WK", 641, 815, 750, 884, 3, 1),
            element("2026", 776, 819, 936, 889, 3, 1),
            element("EDITIE", 960, 824, 1206, 897, 3, 1),
            element("Vetten", 496, 1671, 611, 1726, 4, 0),
            element("(g)", 622, 1679, 669, 1729, 4, 0),
            element("waarvan", 531, 1737, 683, 1784, 5, 0),
            element("verzadigde", 697, 1739, 896, 1786, 5, 0),
            element("vetzuren", 909, 1742, 1064, 1789, 5, 0),
            element("Koolhydraten", 499, 1811, 732, 1866, 6, 0),
            element("-", 500, 1875, 514, 1912, 7, 0),
            element("Waarvan", 529, 1874, 681, 1913, 7, 0),
            element("suikers", 695, 1876, 826, 1915, 7, 0),
            element("Vezels", 497, 1947, 608, 1989, 8, 0),
            element("Eiwitten", 497, 2015, 637, 2059, 9, 0),
            element("Zout", 496, 2083, 575, 2124, 10, 0),
            element("Gekoeld", 493, 2211, 640, 2266, 11, 0),
            element("bewaren", 653, 2216, 816, 2270, 11, 0),
            element("(max.", 830, 2220, 933, 2273, 11, 0),
            element("7°C).", 945, 2224, 1031, 2276, 11, 0),
            element("Ten", 491, 2268, 553, 2320, 11, 1),
            element("minste", 565, 2270, 687, 2323, 11, 1),
            element("houdbaar", 698, 2273, 873, 2328, 11, 1),
            element("tot:", 883, 2277, 941, 2329, 11, 1),
            element("zie", 952, 2279, 1003, 2331, 11, 1),
            element("dop.", 1014, 2280, 1088, 2332, 11, 1),
            element("2026", 543, 2381, 639, 2427, 12, 0),
            element("McDonald's", 651, 2385, 872, 2435, 12, 0),
            element("Smilde", 508, 2541, 626, 2599, 13, 0),
            element("Foods", 642, 2548, 745, 2604, 13, 0),
            element("B.V.", 749, 2553, 815, 2609, 13, 0),
            element("PO", 491, 2597, 544, 2645, 13, 1),
            element("Box", 553, 2601, 623, 2650, 13, 1),
            element("200", 632, 2607, 702, 2655, 13, 1),
            element("8440", 491, 2655, 586, 2701, 14, 0),
            element("AE", 593, 2662, 643, 2705, 14, 0),
            element("Heerenveen", 652, 2665, 877, 2720, 14, 0),
            element("1255", 1170, 1567, 1254, 1608, 15, 0),
            element("kJ/", 1268, 1569, 1331, 1609, 15, 0),
            element("304", 1181, 1622, 1246, 1663, 16, 0),
            element("kcal", 1260, 1624, 1327, 1664, 16, 0),
            element("250", 1041, 2649, 1135, 2737, 17, 0),
            element("mi", 1136, 2641, 1191, 2722, 17, 0),
            element("27,1", 1212, 1685, 1291, 1737, 18, 0),
            element("g", 1304, 1691, 1328, 1739, 18, 0),
            element("2,3g", 1237, 1741, 1329, 1796, 19, 0),
            element("13,2", 1218, 1824, 1293, 1873, 20, 0),
            element("g", 1306, 1824, 1325, 1873, 20, 0),
            element("6,4g", 1237, 1881, 1324, 1928, 21, 0),
            element("0.2", 1236, 1958, 1291, 2004, 22, 0),
            element("g", 1303, 1958, 1323, 2004, 22, 0),
            element("1,0", 1253, 2023, 1311, 2073, 23, 0),
            element("g", 1302, 2023, 1321, 2073, 23, 0),
            element("1,77", 1213, 2091, 1287, 2139, 23, 1),
            element("9", 1300, 2093, 1320, 2139, 23, 1),
            element("8005585", 1464, 2181, 1507, 2298, 24, 0),
        ),
    )

    /** `20260904-081151-032` */
    fun pickleFivePointFour(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("20E1ZURE", 250, 1102, 454, 1177, 0, 0),
            element("AUGURKENPARTEN", 194, 1160, 539, 1242, 0, 1),
            element("MET", 195, 1233, 236, 1278, 0, 2),
            element("SUIKER", 235, 1226, 327, 1275, 0, 2),
            element("EN", 336, 1224, 373, 1268, 0, 2),
            element("ZOETSTOE", 380, 1212, 533, 1266, 0, 2),
            element("670", 208, 1298, 379, 1447, 1, 0),
            element("9", 409, 1310, 478, 1452, 1, 0),
            element("uitlekgewicht", 143, 1458, 405, 1541, 2, 0),
            element("360", 410, 1458, 516, 1541, 2, 0),
            element("g", 544, 1458, 568, 1541, 2, 0),
            element("/", 585, 1458, 609, 1541, 2, 0),
            element("ca.", 145, 1538, 196, 1609, 2, 1),
            element("12", 197, 1540, 234, 1609, 2, 1),
            element("parten", 252, 1540, 405, 1613, 2, 1),
            element("(30", 423, 1544, 520, 1614, 2, 1),
            element("g)", 538, 1546, 590, 1617, 2, 1),
            element("Ingrediēnten:", 172, 1646, 386, 1705, 3, 0),
            element("augurk,", 402, 1655, 529, 1710, 3, 0),
            element("Nater,", 165, 1701, 240, 1745, 3, 1),
            element("azijn,", 249, 1704, 322, 1748, 3, 1),
            element("suiker,", 346, 1708, 437, 1753, 3, 1),
            element("zout", 461, 1713, 526, 1756, 3, 1),
            element("mosterdzaad,", 156, 1746, 352, 1800, 3, 2),
            element("ui,", 369, 1757, 412, 1803, 3, 2),
            element("zoetstof", 416, 1760, 552, 1811, 3, 2),
            element("scharinen", 142, 1790, 285, 1846, 3, 3),
            element("[E954),", 290, 1800, 429, 1856, 3, 3),
            element("aroma's,", 449, 1811, 582, 1866, 3, 3),
            element("teurstof", 147, 1838, 249, 1895, 3, 4),
            element("(ribofiavine", 251, 1846, 421, 1908, 3, 4),
            element("E101]):", 425, 1859, 566, 1918, 3, 4),
            element("Nlaarvan", 182, 1889, 292, 1948, 4, 0),
            element("toegévoegde", 291, 1898, 517, 1967, 4, 0),
            element("suikers", 172, 1939, 262, 1991, 4, 1),
            element("3,9", 263, 1948, 312, 1996, 4, 1),
            element("g", 340, 1955, 357, 2000, 4, 1),
            element("per", 347, 1956, 411, 2005, 4, 1),
            element("100", 425, 1963, 489, 2012, 4, 1),
            element("g", 500, 1970, 517, 2015, 4, 1),
            element("en", 169, 1993, 205, 2035, 4, 2),
            element("waanvan", 204, 1996, 317, 2047, 4, 2),
            element("toegevoegd", 315, 2007, 513, 2066, 4, 2),
            element("20ut", 238, 2043, 298, 2095, 4, 3),
            element("0,70", 299, 2051, 374, 2104, 4, 3),
            element("9.", 372, 2061, 407, 2109, 4, 3),
            element("Alergie", 160, 2116, 281, 2178, 5, 0),
            element("iniormatie:", 151, 2166, 311, 2233, 5, 1),
            element("eet", 153, 2215, 205, 2261, 5, 2),
            element("mosierd.", 217, 2226, 333, 2283, 5, 2),
            element("glutenvrij", 356, 2144, 451, 2183, 6, 0),
            element("lactosevriji", 466, 2155, 588, 2197, 6, 0),
            element("Ten", 675, 1090, 748, 1143, 7, 0),
            element("minste", 759, 1094, 901, 1151, 7, 0),
            element("houdbaar", 922, 1103, 1122, 1162, 7, 0),
            element("tot", 1128, 1114, 1188, 1165, 7, 0),
            element("einde:", 1206, 1117, 1319, 1172, 7, 0),
            element("zie", 1328, 1124, 1359, 1174, 7, 0),
            element("deksel.", 685, 1147, 831, 1203, 7, 1),
            element("Vaak", 842, 1155, 939, 1208, 7, 1),
            element("goed", 951, 1160, 1051, 1214, 7, 1),
            element("rna", 1066, 1165, 1112, 1217, 7, 1),
            element("deze", 1126, 1168, 1212, 1221, 7, 1),
            element("datum.", 1236, 1173, 1352, 1228, 7, 1),
            element("Kijk,", 717, 1204, 788, 1258, 7, 2),
            element("ruik", 797, 1207, 867, 1261, 7, 2),
            element("en", 890, 1211, 923, 1263, 7, 2),
            element("proef.", 928, 1213, 1046, 1268, 7, 2),
            element("Koel", 1066, 1218, 1148, 1272, 7, 2),
            element("en", 1162, 1222, 1207, 1274, 7, 2),
            element("donker", 1222, 1224, 1345, 1280, 7, 2),
            element("bewaren.", 729, 1256, 913, 1312, 7, 3),
            element("Na", 930, 1265, 985, 1315, 7, 3),
            element("openen", 997, 1268, 1142, 1322, 7, 3),
            element("gekoeld", 1157, 1275, 1301, 1330, 7, 3),
            element("bewaren", 665, 1308, 821, 1365, 7, 4),
            element("(max.", 826, 1316, 933, 1370, 7, 4),
            element("7C),", 944, 1321, 1035, 1374, 7, 4),
            element("beperkt", 1049, 1325, 1184, 1381, 7, 4),
            element("houdbaar.", 1195, 1332, 1355, 1390, 7, 4),
            element("Voedingswaarde", 666, 1384, 1029, 1445, 7, 5),
            element("uitgelekt", 665, 1440, 845, 1497, 7, 6),
            element("gewicht", 857, 1446, 1021, 1502, 7, 6),
            element("per", 665, 1505, 738, 1542, 8, 0),
            element("energie", 662, 1618, 823, 1672, 9, 0),
            element("vetten,", 674, 1725, 808, 1771, 10, 0),
            element("waarvan", 652, 1780, 813, 1826, 11, 0),
            element("-Verzadigde", 653, 1826, 893, 1886, 12, 0),
            element("vetzuren", 662, 1879, 816, 1934, 12, 1),
            element("-onverzadigde", 660, 1931, 934, 1991, 12, 2),
            element("vetzuren", 662, 1978, 802, 2039, 12, 3),
            element("koolhydraten,", 657, 2034, 953, 2100, 12, 4),
            element("waarvän", 634, 2090, 804, 2142, 12, 5),
            element("-", 655, 2143, 669, 2190, 12, 6),
            element("suikers", 672, 2143, 806, 2190, 12, 6),
            element("vezels", 637, 2193, 768, 2241, 13, 0),
            element("eiwitten", 632, 2243, 805, 2294, 14, 0),
            element("ZOut", 635, 2305, 722, 2342, 14, 1),
            element("100", 1058, 1503, 1136, 1558, 15, 0),
            element("g", 1146, 1514, 1172, 1562, 15, 0),
            element("127", 1030, 1626, 1084, 1675, 16, 0),
            element("kJ", 1109, 1629, 1152, 1677, 16, 0),
            element("30", 1042, 1680, 1083, 1727, 17, 0),
            element("kcal", 1093, 1681, 1167, 1731, 17, 0),
            element("0,2g", 1060, 1733, 1170, 1788, 18, 0),
            element("0,10", 1034, 1889, 1124, 1944, 19, 0),
            element("g", 1136, 1893, 1157, 1945, 19, 0),
            element("0,", 1057, 1998, 1075, 2050, 20, 0),
            element("10", 1086, 1998, 1130, 2050, 20, 0),
            element("g", 1133, 1998, 1154, 2050, 20, 0),
            element("5,4", 1055, 2054, 1115, 2100, 21, 0),
            element("g", 1130, 2057, 1151, 2101, 21, 0),
            element("5,4g", 1051, 2156, 1149, 2206, 22, 0),
            element("0,9", 1069, 2208, 1121, 2263, 23, 0),
            element("g", 1138, 2208, 1154, 2263, 23, 0),
            element("0,6", 1036, 2263, 1111, 2313, 24, 0),
            element("g", 1124, 2263, 1144, 2313, 24, 0),
            element("0,70", 1018, 2314, 1106, 2363, 25, 0),
            element("g", 1120, 2317, 1140, 2363, 25, 0),
            element("part", 1266, 1526, 1349, 1567, 26, 0),
            element("(30", 1232, 1564, 1300, 1621, 26, 1),
            element("g)", 1311, 1570, 1349, 1625, 26, 1),
            element("38", 1226, 1633, 1271, 1681, 27, 0),
            element("kJ", 1287, 1635, 1322, 1683, 27, 0),
            element("/", 1337, 1637, 1351, 1684, 27, 0),
            element("9", 1243, 1688, 1268, 1731, 28, 0),
            element("kcal", 1278, 1691, 1346, 1738, 28, 0),
            element("O,06", 1234, 1740, 1313, 1792, 29, 0),
            element("g", 1327, 1743, 1346, 1793, 29, 0),
            element("0,03", 1225, 1895, 1304, 1949, 30, 0),
            element("g", 1319, 1898, 1336, 1950, 30, 0),
            element("0,03", 1214, 1999, 1301, 2052, 31, 0),
            element("g", 1317, 2000, 1335, 2052, 31, 0),
            element("1,6", 1224, 2052, 1301, 2102, 32, 0),
            element("9", 1311, 2058, 1334, 2104, 32, 0),
            element("1,6", 1244, 2160, 1297, 2208, 33, 0),
            element("g", 1307, 2160, 1327, 2208, 33, 0),
            element("0,3", 1251, 2208, 1302, 2263, 34, 0),
            element("g", 1312, 2208, 1328, 2263, 34, 0),
            element("0,2g", 1235, 2264, 1326, 2313, 35, 0),
            element("0,20", 1208, 2312, 1295, 2362, 36, 0),
            element("g", 1305, 2312, 1325, 2362, 36, 0),
        ),
    )

    /** `20260904-081213-403` */
    fun coconutWaterNothingOne(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("reerk", 261, 1161, 411, 1243, 0, 0),
            element("eete", 211, 1224, 364, 1301, 0, 1),
            element("ptrente", 268, 1249, 417, 1327, 1, 0),
            element("iyenser:", 291, 1464, 406, 1527, 2, 0),
            element("ties", 308, 1746, 356, 1787, 3, 0),
            element("nden", 365, 1743, 407, 1784, 3, 0),
            element("2", 413, 1743, 425, 1781, 3, 0),
            element("earen,", 335, 2005, 422, 2052, 4, 0),
            element("pie", 244, 2206, 374, 2267, 5, 0),
            element("100ml", 505, 1134, 594, 1192, 6, 0),
            element("the", 508, 998, 547, 1051, 7, 0),
            element("nutrition", 558, 996, 683, 1050, 7, 0),
            element("bit/Nährwertelvaleurs", 696, 990, 1048, 1048, 7, 0),
            element("utritionnelles/hæringsindholldlvoedingswaarde", 530, 1025, 1231, 1141, 8, 0),
            element("Og", 507, 1398, 542, 1441, 9, 0),
            element("Energy/Energie/Energie/Energi", 504, 1193, 990, 1263, 10, 0),
            element("59kJ", 512, 1259, 583, 1318, 10, 1),
            element("(14kcal)", 589, 1253, 699, 1314, 10, 1),
            element("Og", 508, 1617, 543, 1660, 11, 0),
            element("Our", 725, 614, 777, 667, 12, 0),
            element("Fair", 796, 612, 863, 664, 12, 0),
            element("Trade", 888, 614, 1001, 678, 12, 0),
            element("coconut", 1007, 632, 1149, 701, 12, 0),
            element("woter", 1159, 674, 1236, 736, 12, 0),
            element("s", 1240, 703, 1266, 747, 12, 0),
            element("perfect", 677, 682, 818, 742, 12, 1),
            element("exercise", 833, 682, 985, 742, 12, 1),
            element("partner", 1011, 699, 1128, 761, 12, 1),
            element("for", 1128, 722, 1193, 782, 12, 1),
            element("when", 1182, 745, 1265, 812, 12, 1),
            element("100%", 722, 748, 832, 801, 12, 2),
            element("pure,", 848, 752, 941, 804, 12, 2),
            element("So", 965, 754, 1009, 804, 12, 2),
            element("next", 1021, 768, 1099, 818, 12, 2),
            element("tirne", 1105, 782, 1184, 845, 12, 2),
            element("yore", 1173, 807, 1267, 874, 12, 2),
            element("on", 823, 821, 853, 865, 12, 3),
            element("i", 861, 820, 874, 864, 12, 3),
            element("ice", 865, 815, 935, 868, 12, 3),
            element("and", 947, 820, 1017, 873, 12, 3),
            element("pop", 1033, 836, 1107, 894, 12, 3),
            element("open", 1109, 855, 1181, 911, 12, 3),
            element("some", 1193, 877, 1269, 934, 12, 3),
            element("Fat/Fett/Matières", 508, 1332, 756, 1385, 13, 0),
            element("grasses/FedtWetten", 768, 1330, 1076, 1383, 13, 0),
            element("3,2g", 509, 1791, 571, 1839, 14, 0),
            element("0g", 869, 1386, 912, 1436, 15, 0),
            element("of", 508, 1450, 534, 1499, 15, 1),
            element("which", 541, 1450, 624, 1500, 15, 1),
            element("saturates/davon", 635, 1451, 883, 1504, 15, 1),
            element("gesättigte", 896, 1455, 1048, 1507, 15, 1),
            element("Fettsäuren/", 1059, 1457, 1194, 1508, 15, 1),
            element("dont", 508, 1505, 570, 1554, 15, 2),
            element("acides", 579, 1506, 673, 1556, 15, 2),
            element("gras", 683, 1507, 750, 1556, 15, 2),
            element("saturés/heraf", 760, 1508, 970, 1559, 15, 2),
            element("mettede", 979, 1510, 1108, 1560, 15, 2),
            element("fedtsyrer", 1115, 1512, 1221, 1562, 15, 2),
            element("waarvan", 508, 1563, 625, 1612, 15, 3),
            element("verzadigde", 636, 1560, 808, 1610, 15, 3),
            element("vetzuren", 819, 1559, 951, 1608, 15, 3),
            element("stretch", 918, 130, 1065, 192, 16, 0),
            element("2,5g", 509, 1958, 572, 2006, 17, 0),
            element("250ml", 868, 1129, 973, 1171, 18, 0),
            element("Carbohydrate/Kohlenhydrate/Glucides!", 510, 1681, 1090, 1739, 19, 0),
            element("Kulydrat", 516, 1734, 639, 1790, 19, 1),
            element("Koolhydraten", 663, 1734, 865, 1790, 19, 1),
            element("0.2g", 508, 2204, 571, 2254, 20, 0),
            element("spin", 949, 518, 1042, 585, 21, 0),
            element("147kJ", 866, 1249, 958, 1305, 22, 0),
            element("(35kcal)", 963, 1253, 1089, 1309, 22, 0),
            element("Salt/", 515, 2276, 576, 2327, 23, 0),
            element("Salz/Sel/Zout", 577, 2278, 763, 2334, 23, 0),
            element("0,06g", 506, 2327, 592, 2382, 23, 1),
            element("Og", 870, 1612, 908, 1660, 24, 0),
            element("of", 510, 1858, 537, 1905, 25, 0),
            element("which", 544, 1858, 626, 1905, 25, 0),
            element("sugars/davon", 637, 1858, 844, 1905, 25, 0),
            element("Zucker/dont", 856, 1858, 1038, 1905, 25, 0),
            element("sures", 1046, 1858, 1135, 1905, 25, 0),
            element("heraf", 511, 1909, 582, 1950, 25, 1),
            element("sukkerarter/waarvan", 589, 1909, 907, 1950, 25, 1),
            element("suikers", 919, 1909, 1022, 1950, 25, 1),
            element("Potasium/Kalium", 508, 2401, 769, 2462, 26, 0),
            element("200mg", 490, 2445, 615, 2511, 26, 1),
            element("(10%)", 618, 2457, 712, 2520, 26, 1),
            element("6,2g", 870, 1962, 939, 2007, 27, 0),
            element("Fibre/Ballaststoffe/Fibres/KostfibreVezels", 511, 2032, 1126, 2076, 27, 1),
            element("81g", 870, 1790, 932, 1839, 28, 0),
            element("Protein/EiweiB/Protéines/Eiwitten", 511, 2156, 1013, 2208, 29, 0),
            element("Suikers.", 645, 3157, 753, 3214, 30, 0),
            element("Og", 870, 2088, 912, 2135, 31, 0),
            element("0,6g", 871, 2215, 943, 2262, 32, 0),
            element("0,16g", 871, 2342, 951, 2389, 33, 0),
            element("500mg", 869, 2460, 988, 2518, 34, 0),
            element("(25%)", 995, 2454, 1087, 2509, 34, 0),
            element("1%", 505, 2543, 549, 2592, 35, 0),
            element("Reference", 560, 2549, 700, 2611, 35, 0),
            element("Intake/1", 717, 2563, 840, 2612, 35, 0),
            element("%", 852, 2563, 865, 2610, 35, 0),
            element("der", 891, 2562, 924, 2610, 35, 0),
            element("Referenzmenge/", 951, 2531, 1170, 2606, 35, 0),
            element("1%", 504, 2592, 555, 2640, 35, 1),
            element("des", 555, 2598, 607, 2646, 35, 1),
            element("valeurs", 612, 2605, 723, 2660, 35, 1),
            element("nutritionnelles", 740, 2611, 955, 2660, 35, 1),
            element("de", 972, 2610, 986, 2653, 35, 1),
            element("relérence", 999, 2580, 1132, 2651, 35, 1),
            element("%al", 1162, 2563, 1210, 2614, 35, 1),
            element("Ielerenceindtag!", 505, 2638, 757, 2717, 35, 2),
            element("%", 795, 2660, 808, 2707, 35, 2),
            element("van", 818, 2659, 870, 2707, 35, 2),
            element("de", 887, 2658, 915, 2705, 35, 2),
            element("referentie-name.", 929, 2625, 1169, 2704, 35, 2),
            element("Bonl=1Portion", 524, 2726, 754, 2787, 36, 0),
            element("/1", 762, 2735, 807, 2788, 36, 0),
            element("portion/1porte", 801, 2736, 1044, 2797, 36, 0),
            element("Fut", 526, 2803, 578, 2859, 37, 0),
            element("juices", 585, 2812, 669, 2874, 37, 0),
            element("S", 661, 2827, 675, 2876, 37, 0),
            element("contain", 678, 2826, 788, 2876, 37, 0),
            element("only", 800, 2827, 861, 2876, 37, 0),
            element("naturaly", 873, 2826, 995, 2876, 37, 0),
            element("OcCUng", 1008, 2802, 1123, 2862, 37, 0),
            element("Sugars.", 541, 2863, 635, 2914, 37, 1),
            element("/", 644, 2871, 660, 2915, 37, 1),
            element("Fruchtsäfte", 659, 2871, 823, 2928, 37, 1),
            element("enthalten", 845, 2868, 981, 2923, 37, 1),
            element("aus", 555, 2913, 605, 2957, 37, 2),
            element("vorkommende", 611, 2917, 823, 2978, 37, 2),
            element("Zucker.", 838, 2924, 940, 2971, 37, 2),
            element("/Les", 949, 2910, 1019, 2966, 37, 2),
            element("j", 1020, 2907, 1040, 2949, 37, 2),
            element("ceb1", 1062, 2870, 1183, 2938, 37, 2),
            element("Coniennent", 573, 2962, 743, 3016, 37, 3),
            element("que", 753, 2977, 813, 3022, 37, 3),
            element("des", 824, 2983, 873, 3026, 37, 3),
            element("SUCres", 884, 2963, 969, 3019, 37, 3),
            element("présents.", 589, 3008, 710, 3065, 37, 4),
            element("Iugtsaft", 763, 3008, 885, 3078, 37, 4),
            element("indohd", 888, 2920, 1254, 3050, 37, 4),
            element("forekommende", 602, 3056, 824, 3121, 37, 5),
            element("Sukker", 847, 3057, 942, 3115, 37, 5),
            element("/Vchlens", 950, 3029, 1096, 3097, 37, 5),
            element("bevatten", 631, 3110, 746, 3162, 37, 6),
            element("alleen", 761, 3121, 845, 3169, 37, 6),
            element("van", 865, 3118, 911, 3161, 37, 6),
            element("nat/e", 923, 3092, 1017, 3152, 37, 6),
            element("a&Me", 1015, 3071, 1102, 3128, 37, 6),
            element("Board", 1012, 3412, 1075, 3458, 38, 0),
            element("FSCN", 855, 3405, 1180, 3579, 39, 0),
            element("sirt", 1307, 290, 1404, 363, 40, 0),
            element("sposh", 1321, 643, 1410, 704, 41, 0),
            element("6rfrehmet", 1333, 732, 1499, 839, 42, 0),
            element("being", 1335, 854, 1414, 919, 43, 0),
            element("eediyafe", 1337, 1100, 1519, 1189, 44, 0),
            element("900Hea", 1359, 1670, 1478, 1724, 45, 0),
            element("100H6", 1351, 1762, 1417, 1799, 46, 0),
            element("estn", 1420, 1762, 1509, 1799, 46, 0),
            element("GBIE", 1331, 1864, 1390, 1898, 47, 0),
            element("festje", 1342, 1948, 1399, 1993, 48, 0),
            element("and", 1405, 1943, 1441, 1984, 48, 0),
            element("ba", 1442, 1941, 1457, 1979, 48, 0),
            element("DE", 1339, 2068, 1362, 2108, 49, 0),
            element("CHAROs", 1364, 2051, 1504, 2104, 49, 0),
            element("As", 1335, 2157, 1363, 2201, 50, 0),
            element("al", 1372, 2150, 1399, 2193, 50, 0),
            element("ae", 1391, 2142, 1438, 2189, 50, 0),
        ),
    )

    /** `20260904-081226-187` */
    fun coconutWaterNothingTwo(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("1O0ml", 522, 737, 640, 812, 0, 0),
            element("sOkJ", 529, 893, 626, 966, 1, 0),
            element("(4kcal)", 586, 883, 774, 962, 1, 1),
            element("the", 527, 573, 605, 648, 2, 0),
            element("nutrition", 604, 540, 771, 633, 2, 0),
            element("biUNährwerte/valeurs", 803, 531, 1271, 617, 2, 0),
            element("Nutritionnelleshæriingsindhold/voeingsunarde", 526, 584, 1510, 770, 2, 1),
            element("0g", 508, 1383, 555, 1446, 3, 0),
            element("Our", 790, 35, 887, 106, 4, 0),
            element("Fair", 908, 35, 1012, 106, 4, 0),
            element("Trada", 1035, 35, 1192, 106, 4, 0),
            element("EnergyEnergie/Energie//Energi", 532, 787, 1194, 926, 5, 0),
            element("perfect", 777, 133, 947, 204, 6, 0),
            element("exercise", 971, 133, 1188, 204, 6, 0),
            element("partner", 1209, 155, 1385, 246, 6, 0),
            element("for", 1377, 205, 1457, 281, 6, 0),
            element("when", 1442, 241, 1558, 337, 6, 0),
            element("|3.2g", 502, 1619, 584, 1685, 7, 0),
            element("100%", 795, 205, 953, 280, 8, 0),
            element("pure.", 997, 209, 1117, 283, 8, 0),
            element("2,5g", 498, 1848, 581, 1913, 9, 0),
            element("FatFett", 537, 991, 678, 1066, 10, 0),
            element("Matières", 694, 991, 863, 1066, 10, 0),
            element("grasses/Fedt/Vetten", 868, 991, 1302, 1066, 10, 0),
            element("Og", 1005, 1060, 1065, 1131, 10, 1),
            element("of", 523, 1165, 546, 1232, 11, 0),
            element("which", 574, 1159, 682, 1230, 11, 0),
            element("saturates/davon", 693, 1146, 1025, 1226, 11, 0),
            element("gesätigte", 1057, 1150, 1255, 1238, 11, 0),
            element("e", 1238, 1163, 1270, 1232, 11, 0),
            element("Fettsäuren", 1270, 1167, 1442, 1263, 11, 0),
            element("dont", 509, 1229, 590, 1301, 12, 0),
            element("acides", 600, 1224, 730, 1297, 12, 0),
            element("gras", 739, 1220, 833, 1292, 12, 0),
            element("saturés/heraf", 846, 1208, 1144, 1288, 12, 0),
            element("n", 1158, 1207, 1179, 1275, 12, 0),
            element("waarvan", 509, 1300, 664, 1366, 12, 1),
            element("verzadigde", 680, 1297, 914, 1364, 12, 1),
            element("vetzuren", 929, 1296, 1117, 1361, 12, 1),
            element("Og", 491, 2015, 541, 2080, 13, 0),
            element("on", 962, 306, 1006, 366, 14, 0),
            element("ice", 1023, 306, 1104, 365, 14, 0),
            element("arnd", 1131, 303, 1240, 394, 14, 0),
            element("pop", 1235, 333, 1335, 416, 14, 0),
            element("open", 1351, 372, 1454, 455, 14, 0),
            element("some", 1448, 411, 1559, 497, 14, 0),
            element("250ml", 1009, 711, 1159, 781, 15, 0),
            element("02g", 484, 2179, 576, 2256, 16, 0),
            element("47kJ(35kcal)", 1005, 875, 1317, 976, 17, 0),
            element("Carbohydrate/Kohlenhydrate/Glucides/", 496, 1463, 1300, 1547, 18, 0),
            element("Kullydrat/Koollydraten", 505, 1542, 980, 1613, 18, 1),
            element("Salt/", 493, 2281, 581, 2353, 19, 0),
            element("Salz/SelW/Zout", 579, 2289, 833, 2376, 19, 0),
            element("006g", 478, 2348, 601, 2431, 19, 1),
            element("Polassium/Kalium", 463, 2440, 837, 2556, 19, 2),
            element("200rmg", 487, 2514, 632, 2602, 19, 3),
            element("(10%)", 620, 2540, 767, 2627, 19, 3),
            element("Suikers.", 618, 3475, 778, 3564, 20, 0),
            element("Og", 998, 1373, 1057, 1440, 21, 0),
            element("coconut", 1230, 76, 1404, 171, 22, 0),
            element("Hoter", 1417, 146, 1530, 237, 22, 0),
            element("I", 1505, 193, 1542, 244, 22, 0),
            element("So", 1165, 231, 1230, 298, 23, 0),
            element("next", 1240, 248, 1345, 324, 23, 0),
            element("tirne", 1341, 277, 1454, 370, 23, 0),
            element("yxre", 1431, 318, 1560, 419, 23, 0),
            element("of", 501, 1708, 538, 1774, 24, 0),
            element("which", 545, 1708, 656, 1775, 24, 0),
            element("sugars/davon", 669, 1710, 953, 1781, 24, 0),
            element("Zucker/dont", 968, 1714, 1225, 1785, 24, 0),
            element("suTes/", 1235, 1719, 1362, 1788, 24, 0),
            element("heraf", 501, 1778, 595, 1835, 24, 1),
            element("sukkerarter/waarvan", 604, 1780, 1039, 1844, 24, 1),
            element("suikers", 1055, 1788, 1205, 1846, 24, 1),
            element("8,1g", 989, 1620, 1078, 1691, 25, 0),
            element("6,2g", 982, 1859, 1086, 1923, 26, 0),
            element("Fibre/Ballaststoffe/Fibres/KostfibreNezels", 497, 1947, 1343, 2026, 26, 1),
            element("Protein/Eiweiß/Protéines/Eiwitten", 492, 2115, 1182, 2204, 27, 0),
            element("Og", 983, 2035, 1040, 2102, 28, 0),
            element("f", 1137, 1222, 1164, 1290, 29, 0),
            element("maettede", 1179, 1227, 1342, 1314, 29, 0),
            element("fetsyet", 1342, 1248, 1492, 1333, 29, 0),
            element("0,6g", 976, 2212, 1079, 2280, 30, 0),
            element("0,16g", 972, 2389, 1090, 2457, 31, 0),
            element("500mg", 968, 2557, 1128, 2633, 32, 0),
            element("(25%)", 1142, 2554, 1271, 2628, 32, 0),
            element("19%", 477, 2640, 540, 2708, 33, 0),
            element("Relerence", 545, 2651, 735, 2742, 33, 0),
            element("Intake/1%", 751, 2686, 974, 2755, 33, 0),
            element("der", 999, 2695, 1044, 2756, 33, 0),
            element("Referenzmenge", 1080, 2666, 1358, 2756, 33, 0),
            element("%", 506, 2713, 533, 2771, 33, 1),
            element("des", 543, 2719, 617, 2787, 33, 1),
            element("valeurs", 617, 2733, 768, 2816, 33, 1),
            element("nutritionnelles", 784, 2758, 1083, 2819, 33, 1),
            element("de", 1107, 2759, 1127, 2819, 33, 1),
            element("reférence", 1148, 2723, 1331, 2818, 33, 1),
            element("hd", 1355, 2703, 1433, 2777, 33, 1),
            element("elerenceindlag/", 488, 2775, 805, 2885, 33, 2),
            element("%", 856, 2825, 874, 2881, 33, 2),
            element("van", 894, 2825, 959, 2884, 33, 2),
            element("de", 986, 2828, 1020, 2886, 33, 2),
            element("referentie-rame.", 1042, 2788, 1378, 2888, 33, 2),
            element("250xd", 478, 2876, 593, 2964, 33, 3),
            element("=1", 587, 2901, 660, 2980, 33, 3),
            element("Porton", 660, 2914, 793, 2999, 33, 3),
            element("/", 801, 2935, 830, 3003, 33, 3),
            element("l1", 819, 2939, 865, 3015, 33, 3),
            element("portion", 881, 2933, 1026, 3013, 33, 3),
            element("/1", 1042, 2932, 1088, 3009, 33, 3),
            element("portie", 1092, 2928, 1197, 3007, 33, 3),
            element("Frut", 490, 2992, 570, 3069, 33, 4),
            element("jices", 553, 3009, 681, 3099, 33, 4),
            element("contain", 696, 3040, 849, 3115, 33, 4),
            element("only", 867, 3058, 944, 3124, 33, 4),
            element("naturally", 963, 3046, 1139, 3126, 33, 4),
            element("ocCurg", 1148, 3024, 1317, 3107, 33, 4),
            element("Sugars.", 505, 3071, 641, 3149, 33, 5),
            element("/", 644, 3095, 670, 3153, 33, 5),
            element("Fruchtsäfte", 663, 3097, 895, 3190, 33, 5),
            element("enthalten", 916, 3119, 1109, 3184, 33, 5),
            element("l", 1106, 3119, 1123, 3174, 33, 5),
            element("dus", 527, 3143, 594, 3204, 34, 0),
            element("vorkommende", 595, 3154, 886, 3254, 34, 0),
            element("Zucker./Les", 900, 3169, 1160, 3256, 34, 0),
            element("jus", 1158, 3157, 1228, 3221, 34, 0),
            element("oe", 1227, 3146, 1269, 3205, 34, 0),
            element("S", 1336, 3124, 1362, 3178, 34, 0),
            element("1", 1361, 3119, 1387, 3173, 34, 0),
            element("Coniennent", 550, 3207, 775, 3304, 34, 1),
            element("que", 788, 3257, 870, 3310, 34, 1),
            element("des", 887, 3256, 956, 3309, 34, 1),
            element("suCres", 960, 3254, 1090, 3308, 34, 1),
            element("llaelev", 1101, 3202, 1281, 3300, 34, 1),
            element("présents.I", 572, 3275, 771, 3367, 34, 2),
            element("l", 760, 3308, 784, 3379, 34, 2),
            element("E", 770, 3313, 797, 3371, 34, 2),
            element("Frugtsaft", 788, 3309, 953, 3388, 34, 2),
            element("ndeholda", 991, 3281, 1183, 3379, 34, 2),
            element("kun", 1186, 3263, 1259, 3333, 34, 2),
            element("nal", 1261, 3250, 1321, 3316, 34, 2),
            element("g", 1357, 3235, 1384, 3292, 34, 2),
            element("lorekommende", 583, 3344, 894, 3452, 34, 3),
            element("Sukker.", 919, 3375, 1042, 3440, 34, 3),
            element("/Vuchtersapen", 1071, 3297, 1368, 3423, 34, 3),
            element("bevatten", 616, 3415, 775, 3497, 34, 4),
            element("alleen", 809, 3446, 920, 3501, 34, 4),
            element("varn", 936, 3448, 1005, 3502, 34, 4),
            element("natwe", 1026, 3418, 1149, 3498, 34, 4),
            element("aawezge", 1151, 3367, 1338, 3463, 34, 4),
            element("2501l", 1165, 3530, 1362, 3652, 35, 0),
            element("ly", 1442, 2850, 1497, 2910, 36, 0),
            element("losy", 1442, 2771, 1496, 2851, 36, 0),
        ),
    )

    /** `20260904-081244-479` */
    fun blurredNonLabel(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
        ),
    )

    /** `20260904-081251-955` */
    fun sunflowerOilZero(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("Voedingswaarde", 248, 1547, 651, 1621, 0, 0),
            element("Goldsun", 674, 1552, 904, 1623, 0, 0),
            element("Zonnebloemolie", 928, 1554, 1357, 1628, 0, 0),
            element("per", 1373, 1559, 1446, 1628, 0, 0),
            element("100ml", 1454, 1559, 1558, 1630, 0, 0),
            element("Energie", 261, 1676, 441, 1741, 1, 0),
            element("Vet", 265, 1760, 338, 1812, 2, 0),
            element("Waarvan", 265, 1841, 454, 1912, 3, 0),
            element("verzadigd", 472, 1847, 735, 1920, 3, 0),
            element("vet", 759, 1857, 852, 1924, 3, 0),
            element("Koolhydraten", 264, 1912, 608, 1996, 4, 0),
            element("waarvan", 267, 2004, 452, 2064, 5, 0),
            element("suikers", 476, 2013, 660, 2073, 5, 0),
            element("Eiwit", 263, 2070, 391, 2139, 6, 0),
            element("Zout", 262, 2152, 377, 2218, 6, 1),
            element("8l71062413", 317, 2821, 726, 2961, 7, 0),
            element("303000", 716, 2881, 1006, 2951, 7, 0),
            element("3403", 1095, 1687, 1233, 1752, 8, 0),
            element("kJ", 1261, 1687, 1312, 1752, 8, 0),
            element("/", 1330, 1687, 1356, 1752, 8, 0),
            element("828", 1374, 1687, 1459, 1752, 8, 0),
            element("kcal", 1472, 1687, 1540, 1752, 8, 0),
            element("91,9", 1092, 1773, 1216, 1838, 9, 0),
            element("g", 1239, 1777, 1268, 1838, 9, 0),
            element("9,2", 1090, 1858, 1172, 1919, 10, 0),
            element("g", 1195, 1862, 1222, 1920, 10, 0),
            element("0,0", 1089, 1941, 1178, 2001, 11, 0),
            element("g", 1200, 1941, 1225, 2001, 11, 0),
            element("0,0", 1118, 2019, 1195, 2089, 12, 0),
            element("g", 1192, 2023, 1219, 2090, 12, 0),
            element("0,0", 1085, 2100, 1175, 2165, 13, 0),
            element("g", 1195, 2106, 1225, 2168, 13, 0),
            element("0,00", 1085, 2182, 1210, 2251, 14, 0),
            element("g", 1216, 2180, 1256, 2246, 14, 0),
            element("33980919K", 1179, 2715, 1226, 2959, 15, 0),
            element("VERPAKKING", 1285, 2648, 1478, 2740, 16, 0),
        ),
    )

    /** `20260904-081307-240` */
    fun lidlDrinkSixPointTwoUnitLost(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("70720929", 141, 2161, 206, 2368, 0, 0),
            element("L&DL", 291, 1314, 488, 1381, 1, 0),
            element("Energie", 255, 1646, 459, 1736, 2, 0),
            element("Vetten", 262, 1756, 430, 1825, 2, 1),
            element("Gemiddelde", 721, 1253, 1135, 1349, 3, 0),
            element("voedingswaarde", 648, 1362, 1215, 1470, 3, 1),
            element("Koolhydraten", 251, 1938, 620, 2016, 4, 0),
            element("Waarvan", 313, 2028, 506, 2105, 4, 1),
            element("suikers", 529, 2022, 682, 2096, 4, 1),
            element("Vezels", 261, 2125, 431, 2198, 5, 0),
            element("waarvan", 323, 1838, 508, 1914, 6, 0),
            element("verzadigde", 544, 1838, 799, 1914, 6, 0),
            element("vetzuren", 817, 1838, 1004, 1914, 6, 0),
            element("Ewitten", 248, 2224, 475, 2308, 7, 0),
            element("Zout", 259, 2328, 388, 2399, 7, 1),
            element("Ø/100", 976, 1551, 1147, 1632, 8, 0),
            element("ml", 1174, 1551, 1221, 1632, 8, 0),
            element("1113/26", 923, 1646, 1130, 1736, 9, 0),
            element("kcal", 1131, 1646, 1230, 1736, 9, 0),
            element("Lidl", 400, 2587, 503, 2676, 10, 0),
            element("Nederland", 544, 2589, 819, 2680, 10, 0),
            element("GmbH,", 820, 2593, 1019, 2682, 10, 0),
            element("Postbus", 553, 2692, 761, 2776, 10, 1),
            element("198,", 779, 2697, 891, 2778, 10, 1),
            element("1270", 492, 2795, 617, 2875, 10, 2),
            element("AD", 632, 2798, 715, 2876, 10, 2),
            element("Huizen", 732, 2800, 925, 2880, 10, 2),
            element("BIJ", 657, 3572, 754, 3628, 11, 0),
            element("VERPAKKING", 392, 3104, 1009, 3199, 12, 0),
            element("0g", 1139, 1758, 1219, 1834, 13, 0),
            element("0g", 1144, 1841, 1219, 1914, 14, 0),
            element("6,20", 1080, 1936, 1226, 2013, 15, 0),
            element("6,09", 1087, 2020, 1242, 2107, 16, 0),
            element("0g", 1143, 2127, 1225, 2208, 17, 0),
            element("0g", 1147, 2237, 1243, 2308, 18, 0),
            element("0,01g", 1077, 2327, 1220, 2419, 19, 0),
        ),
    )

    /** `20260904-081335-279` */
    fun cocoaTwelvePointSix(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("Voedingswaarde", 73, 337, 793, 460, 0, 0),
            element("per", 830, 335, 977, 447, 0, 0),
            element("100g", 1009, 330, 1224, 444, 0, 0),
            element("Nutritional", 77, 457, 512, 577, 0, 1),
            element("value", 586, 457, 824, 577, 0, 1),
            element("per", 825, 457, 972, 577, 0, 1),
            element("100g", 1005, 457, 1221, 577, 0, 1),
            element("Nährwert", 75, 585, 458, 705, 0, 2),
            element("pro", 490, 581, 637, 695, 0, 2),
            element("100g", 672, 575, 890, 691, 0, 2),
            element("Valeurs", 71, 709, 373, 815, 1, 0),
            element("nutritionnelles", 444, 709, 1084, 815, 1, 0),
            element("par", 1131, 709, 1277, 815, 1, 0),
            element("100g", 1287, 709, 1499, 815, 1, 0),
            element("Valor", 71, 828, 271, 935, 1, 1),
            element("nutritivo", 302, 825, 682, 933, 1, 1),
            element("por", 716, 823, 863, 928, 1, 1),
            element("100g", 898, 820, 1120, 927, 1, 1),
            element("Energie", 94, 1012, 272, 1110, 2, 0),
            element("Energy/Energie/", 306, 1003, 770, 1106, 2, 0),
            element("Energie/Energia:", 71, 1123, 532, 1213, 2, 1),
            element("Vetten/Fat/Fette/Lipides/Grasas:", 70, 1223, 1000, 1310, 3, 0),
            element("saturados:", 118, 1645, 416, 1707, 4, 0),
            element("waarvan", 119, 1334, 355, 1411, 5, 0),
            element("verzadigde", 380, 1334, 710, 1411, 5, 0),
            element("vetten/of", 738, 1334, 1015, 1411, 5, 0),
            element("which", 1033, 1334, 1211, 1411, 5, 0),
            element("saturated", 1231, 1334, 1522, 1411, 5, 0),
            element("fats/davon", 120, 1438, 414, 1512, 5, 1),
            element("gesättigte", 435, 1438, 740, 1512, 5, 1),
            element("Fettsäuren/dont", 762, 1438, 1251, 1512, 5, 1),
            element("acides", 1266, 1438, 1463, 1512, 5, 1),
            element("gras", 115, 1540, 232, 1617, 5, 2),
            element("saturés/de", 251, 1540, 572, 1617, 5, 2),
            element("las", 607, 1540, 697, 1617, 5, 2),
            element("Cuales", 703, 1540, 899, 1617, 5, 2),
            element("ácidos", 920, 1540, 1118, 1617, 5, 2),
            element("grasos", 1137, 1540, 1343, 1617, 5, 2),
            element("1635", 1108, 1119, 1260, 1195, 6, 0),
            element("kJ/391", 1312, 1123, 1508, 1200, 6, 0),
            element("kcal", 1521, 1127, 1634, 1202, 6, 0),
            element("Zout/Salt/Salz/Sel/Sal:", 90, 2230, 722, 2316, 7, 0),
            element(".....2.1,0g", 1221, 1227, 1622, 1317, 8, 0),
            element("Koolhydraten/Carbohydrates/Kohlenhydrate/", 53, 1744, 1349, 1823, 9, 0),
            element("GlucidesiHidratos", 82, 1833, 537, 1911, 9, 1),
            element("de", 557, 1840, 626, 1912, 9, 1),
            element("carbono..", 647, 1841, 900, 1916, 9, 1),
            element("........1", 1261, 1641, 1495, 1727, 10, 0),
            element("3.2g", 1531, 1634, 1635, 1714, 10, 0),
            element("waarvan", 123, 1946, 354, 2019, 11, 0),
            element("Suikers/of", 375, 1946, 682, 2019, 11, 0),
            element("which", 700, 1946, 874, 2019, 11, 0),
            element("sugars/davon", 895, 1946, 1305, 2019, 11, 0),
            element("Zucker/", 1322, 1946, 1559, 2019, 11, 0),
            element("dont", 129, 2044, 247, 2125, 11, 1),
            element("sucres/de", 262, 2044, 554, 2125, 11, 1),
            element("los", 585, 2044, 679, 2125, 11, 1),
            element("cuales", 701, 2044, 877, 2125, 11, 1),
            element("azúcares:.....0,4g", 897, 2044, 1618, 2125, 11, 1),
            element("Eiwitten/Protein/EiweiB/Protėines/Proteínas:...22,2g", 87, 2125, 1615, 2222, 11, 2),
            element("250", 145, 3294, 439, 3474, 12, 0),
            element("g", 427, 3322, 519, 3481, 12, 0),
            element("e", 609, 3340, 787, 3507, 12, 0),
            element("In", 137, 3482, 194, 3542, 13, 0),
            element("Nederland:", 207, 3490, 532, 3583, 13, 0),
            element("Verkoop,", 136, 3560, 395, 3652, 13, 1),
            element("Ma", 443, 3593, 486, 3661, 13, 1),
            element("12.6g", 1463, 1848, 1624, 1913, 14, 0),
            element("ingredient:", 96, 2386, 428, 2475, 15, 0),
            element("Cacaopoeder*,", 496, 2386, 956, 2475, 15, 0),
            element("zuurteregelaar", 953, 2386, 1399, 2475, 15, 0),
            element("(E501),", 1424, 2386, 1632, 2475, 15, 0),
            element("ingredient:", 97, 2467, 431, 2551, 15, 1),
            element("Cocoa", 458, 2472, 663, 2553, 15, 1),
            element("powder*,", 683, 2475, 976, 2557, 15, 1),
            element("acidity", 1008, 2479, 1213, 2561, 15, 1),
            element("regulator", 1240, 2482, 1523, 2564, 15, 1),
            element("(E501),", 109, 2559, 323, 2641, 15, 2),
            element("Ingrédient:", 330, 2561, 690, 2644, 15, 2),
            element("Poudre", 724, 2565, 942, 2647, 15, 2),
            element("de", 968, 2568, 1037, 2647, 15, 2),
            element("cacao*,", 1063, 2568, 1292, 2650, 15, 2),
            element("régulateur", 1323, 2571, 1620, 2652, 15, 2),
            element("d", 121, 2645, 143, 2721, 15, 3),
            element("acidité", 166, 2645, 361, 2724, 15, 3),
            element("(E", 370, 2648, 434, 2724, 15, 3),
            element("501),", 460, 2648, 624, 2727, 15, 3),
            element("Ingrediente:", 655, 2651, 1056, 2731, 15, 3),
            element("Polvo", 1087, 2656, 1259, 2735, 15, 3),
            element("de", 1284, 2658, 1359, 2735, 15, 3),
            element("cacao", 1388, 2660, 1555, 2738, 15, 3),
            element("tegulador", 110, 2711, 390, 2805, 15, 4),
            element("acidez", 434, 2725, 627, 2816, 15, 4),
            element("(E601)", 633, 2733, 847, 2824, 15, 4),
            element("..0,1g", 1440, 2243, 1617, 2316, 16, 0),
            element("Rainforest", 111, 2806, 446, 2889, 17, 0),
            element("Alliance", 464, 2816, 707, 2895, 17, 0),
            element("Certified", 730, 2823, 991, 2904, 17, 0),
            element("www.ra.org", 1035, 2831, 1415, 2915, 17, 0),
            element("Ten", 130, 2904, 219, 2973, 17, 1),
            element("minste", 243, 2912, 408, 2987, 17, 1),
            element("houdbaar", 428, 2925, 689, 3008, 17, 1),
            element("tot:", 718, 2940, 811, 3015, 17, 1),
            element("zie", 839, 2938, 907, 3013, 17, 1),
            element("onderzijde", 947, 2933, 1225, 3012, 17, 1),
            element("verpakking", 1244, 2927, 1551, 3006, 17, 1),
            element("/", 1576, 2927, 1597, 3000, 17, 1),
            element("Best", 146, 2978, 240, 3049, 17, 2),
            element("before:", 265, 2990, 437, 3069, 17, 2),
            element("see", 473, 3017, 563, 3083, 17, 2),
            element("end", 591, 3017, 697, 3083, 17, 2),
            element("of", 727, 3017, 766, 3083, 17, 2),
            element("pack", 783, 3017, 926, 3083, 17, 2),
            element("/", 945, 3017, 964, 3083, 17, 2),
            element("Mindesterns", 1010, 3016, 1299, 3083, 17, 2),
            element("haltbar", 1347, 3001, 1522, 3071, 17, 2),
            element("bis:", 135, 3050, 218, 3121, 17, 3),
            element("siehe", 235, 3061, 371, 3139, 17, 3),
            element("Bodendeckel", 407, 3082, 760, 3154, 17, 3),
            element("/A", 767, 3092, 873, 3156, 17, 3),
            element("consommer", 883, 3096, 1202, 3167, 17, 3),
            element("de", 1242, 3086, 1303, 3158, 17, 3),
            element("préfé-", 1335, 3076, 1479, 3153, 17, 3),
            element("rence", 130, 3132, 285, 3202, 17, 4),
            element("avant", 305, 3147, 455, 3215, 17, 4),
            element("la", 459, 3159, 512, 3219, 17, 4),
            element("date", 544, 3167, 642, 3230, 17, 4),
            element("figurant", 663, 3167, 893, 3247, 17, 4),
            element("au", 928, 3177, 985, 3244, 17, 4),
            element("dessOus", 1013, 3163, 1242, 3240, 17, 4),
            element("/", 1261, 3163, 1282, 3227, 17, 4),
            element("Consumir", 1324, 3148, 1555, 3224, 17, 4),
            element("preferente", 150, 3203, 415, 3286, 17, 5),
            element("antes", 437, 3228, 575, 3300, 17, 5),
            element("de:", 608, 3242, 684, 3308, 17, 5),
            element("ver", 709, 3251, 785, 3316, 17, 5),
            element("en", 820, 3253, 874, 3316, 17, 5),
            element("la", 892, 3251, 946, 3314, 17, 5),
            element("parte", 971, 3246, 1113, 3312, 17, 5),
            element("inferior", 1130, 3241, 1330, 3309, 17, 5),
        ),
    )

    /** `20260904-081407-814` */
    fun peanutButterNothing(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("denten:", 2, 1363, 179, 1456, 0, 0),
            element("suikers", 106, 1612, 245, 1684, 1, 0),
            element("0g", 62, 1698, 141, 1769, 2, 0),
            element("en", 140, 1699, 180, 1769, 2, 0),
            element("tegevoegd", 16, 1778, 236, 1858, 3, 0),
            element("100g.", 81, 1864, 203, 1941, 3, 1),
            element("gsche", -4, 1945, 183, 2016, 3, 2),
            element("g", 17, 2039, 113, 2105, 4, 0),
            element("BV", 2, 2154, 174, 2235, 5, 0),
            element("Neg", 57, 2246, 140, 2312, 6, 0),
            element("11", 142, 2253, 196, 2316, 6, 0),
            element("TNDAM", -7, 2294, 233, 2417, 6, 1),
            element("Allergie-intormate", 410, 728, 1024, 841, 7, 0),
            element("bevat", 436, 840, 601, 920, 7, 1),
            element("pida's.", 627, 834, 857, 915, 7, 1),
            element("Kan", 895, 831, 1015, 909, 7, 1),
            element("amandel,", 348, 919, 608, 1023, 7, 2),
            element("cashewno0t", 651, 921, 1012, 1002, 7, 2),
            element("od", 1041, 932, 1124, 1005, 7, 2),
            element("hazelnoot", 388, 1004, 677, 1083, 7, 3),
            element("bevatten,", 730, 1004, 1015, 1083, 7, 3),
            element("Goed", 439, 1136, 579, 1216, 8, 0),
            element("dooroeren", 610, 1143, 973, 1231, 8, 0),
            element("VOor", 987, 1158, 1122, 1237, 8, 0),
            element("gebruik.", 1157, 1164, 1367, 1245, 8, 0),
            element("Ten", 329, 1272, 433, 1351, 9, 0),
            element("minste", 458, 1276, 689, 1359, 9, 0),
            element("houdbaar", 735, 1285, 1065, 1371, 9, 0),
            element("tot", 1089, 1297, 1194, 1375, 9, 0),
            element("en", 1190, 1300, 1272, 1377, 9, 0),
            element("met:", 1288, 1303, 1417, 1382, 9, 0),
            element("zie", 304, 1359, 378, 1440, 9, 1),
            element("zijkant.", 415, 1361, 597, 1444, 9, 1),
            element("Vaak", 656, 1367, 785, 1448, 9, 1),
            element("goed", 800, 1370, 965, 1452, 9, 1),
            element("na", 996, 1374, 1087, 1454, 9, 1),
            element("deze", 1093, 1376, 1233, 1457, 9, 1),
            element("datum", 1254, 1380, 1426, 1462, 9, 1),
            element("Kijk,", 372, 1450, 479, 1531, 9, 2),
            element("ruik", 497, 1452, 614, 1534, 9, 2),
            element("en", 635, 1456, 704, 1536, 9, 2),
            element("proef", 744, 1458, 895, 1541, 9, 2),
            element("Koel;", 941, 1462, 1121, 1546, 9, 2),
            element("donker", 1129, 1467, 1324, 1551, 9, 2),
            element("en", 1342, 1472, 1390, 1552, 9, 2),
            element("droog", 648, 1542, 841, 1623, 9, 3),
            element("bewaren.", 830, 1547, 1125, 1631, 9, 3),
            element("Voedingswaarde", 418, 1660, 1007, 1755, 10, 0),
            element("per", 1044, 1674, 1141, 1758, 10, 0),
            element("100", 1182, 1677, 1294, 1761, 10, 0),
            element("a", 1323, 1680, 1348, 1762, 10, 0),
            element("energie", 415, 1757, 697, 1839, 10, 1),
            element("2578", 708, 1760, 865, 1840, 10, 1),
            element("kJ", 894, 1762, 962, 1841, 10, 1),
            element("/", 990, 1763, 1019, 1842, 10, 1),
            element("622", 1044, 1763, 1162, 1843, 10, 1),
            element("kcal.", 1190, 1764, 1312, 1845, 10, 1),
            element("vetten", 356, 1847, 553, 1928, 10, 2),
            element("50", 581, 1852, 672, 1930, 10, 2),
            element("g,", 708, 1854, 756, 1931, 10, 2),
            element("waarvan", 761, 1855, 1037, 1938, 10, 2),
            element("verzadigde", 1066, 1861, 1386, 1945, 10, 2),
            element("vetzuren", 314, 1937, 554, 2016, 10, 3),
            element("9,5", 582, 1941, 683, 2018, 10, 3),
            element("g,", 710, 1943, 767, 2018, 10, 3),
            element("koolhydraten", 800, 1944, 1261, 2026, 10, 3),
            element("t1", 1292, 1952, 1333, 2028, 10, 3),
            element("g.", 1375, 1953, 1421, 2029, 10, 3),
            element("waarvan", 303, 2026, 543, 2105, 10, 4),
            element("suikers", 567, 2030, 790, 2108, 10, 4),
            element("5,5", 817, 2035, 922, 2110, 10, 4),
            element("g,", 946, 2037, 1000, 2111, 10, 4),
            element("vezels", 1028, 2038, 1240, 2116, 10, 4),
            element("8,1", 1260, 2042, 1340, 2118, 10, 4),
            element("g.", 1367, 2044, 1407, 2118, 10, 4),
            element("eiwitten", 471, 2115, 750, 2200, 10, 5),
            element("28", 775, 2124, 855, 2203, 10, 5),
            element("g,", 880, 2127, 937, 2205, 10, 5),
            element("zout", 964, 2129, 1116, 2211, 10, 5),
            element("0", 1139, 2134, 1174, 2211, 10, 5),
            element("g.", 1198, 2136, 1249, 2214, 10, 5),
            element("NL-BIO-01", 269, 2238, 449, 2293, 11, 0),
            element("Niet-EU", 268, 2299, 403, 2359, 12, 0),
            element("Landbouw", 414, 2310, 629, 2376, 12, 0),
            element("682508238", 1219, 2263, 1443, 2328, 13, 0),
            element("VERPAKKING", 1138, 2341, 1430, 2426, 14, 0),
            element("IN", 1252, 2610, 1308, 2661, 15, 0),
            element("GLASBAK", 1196, 2632, 1363, 2703, 15, 1),
        ),
    )

    /** `20260904-081421-421` */
    fun peanutButterEleven(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("60", -3, 1078, 138, 1222, 0, 0),
            element("enten", 4, 1289, 145, 1374, 1, 0),
            element("e", 24, 1548, 46, 1617, 2, 0),
            element("suikers", 57, 1538, 198, 1615, 2, 0),
            element("Wgen", 17, 1635, 160, 1710, 3, 0),
            element("NOegd", 14, 1724, 192, 1788, 4, 0),
            element("e0g.", -15, 1796, 182, 1887, 5, 0),
            element("sche", 19, 1887, 149, 1962, 5, 1),
            element("eg", -2, 2199, 86, 2274, 6, 0),
            element("11", 102, 2201, 140, 2276, 6, 0),
            element("AIDAM", -8, 2265, 178, 2366, 6, 1),
            element("Allergie-informarfe", 331, 618, 996, 738, 7, 0),
            element("bevat", 377, 731, 539, 818, 7, 1),
            element("pinda's.", 561, 718, 812, 810, 7, 1),
            element("Kan", 849, 712, 970, 797, 7, 1),
            element("amandel,", 305, 808, 564, 926, 7, 2),
            element("cashewnoot", 593, 812, 992, 892, 7, 2),
            element("en", 1019, 812, 1086, 891, 7, 2),
            element("hazelnoot", 341, 899, 641, 991, 7, 3),
            element("bevatten,", 694, 886, 978, 977, 7, 3),
            element("Goed", 394, 1041, 533, 1119, 8, 0),
            element("doorroeren", 560, 1044, 924, 1127, 8, 0),
            element("VOor", 951, 1053, 1091, 1130, 8, 0),
            element("gebruik", 1118, 1056, 1364, 1136, 8, 0),
            element("gornij", 1136, 651, 1294, 724, 9, 0),
            element("lactoseri", 1312, 680, 1465, 752, 9, 0),
            element("Ten", 267, 1178, 390, 1259, 10, 0),
            element("minste", 414, 1182, 651, 1265, 10, 0),
            element("houdbaar", 689, 1188, 1052, 1275, 10, 0),
            element("tot", 1073, 1197, 1165, 1278, 10, 0),
            element("en", 1181, 1200, 1264, 1279, 10, 0),
            element("met", 1288, 1202, 1433, 1284, 10, 0),
            element("zie", 272, 1288, 349, 1377, 11, 0),
            element("zijkant.", 371, 1276, 577, 1372, 11, 0),
            element("Vaak", 602, 1268, 745, 1361, 11, 0),
            element("goed", 786, 1280, 959, 1365, 11, 0),
            element("na", 982, 1280, 1054, 1365, 11, 0),
            element("cHeze", 1099, 1291, 1249, 1372, 11, 0),
            element("datum.", 1271, 1305, 1447, 1389, 11, 0),
            element("Kik,", 314, 1359, 437, 1445, 11, 1),
            element("ruik", 441, 1362, 568, 1447, 11, 1),
            element("en", 584, 1365, 678, 1449, 11, 1),
            element("proef,", 703, 1367, 889, 1454, 11, 1),
            element("Koel,", 927, 1371, 1092, 1458, 11, 1),
            element("donker", 1117, 1375, 1321, 1461, 11, 1),
            element("en", 1353, 1379, 1403, 1463, 11, 1),
            element("droog", 616, 1464, 798, 1549, 11, 2),
            element("bewaren", 821, 1464, 1089, 1549, 11, 2),
            element("Voedingswaarde", 391, 1590, 982, 1689, 12, 0),
            element("per", 1014, 1603, 1136, 1692, 12, 0),
            element("100", 1179, 1606, 1300, 1695, 12, 0),
            element("g", 1329, 1609, 1356, 1696, 12, 0),
            element("energie", 398, 1684, 646, 1769, 12, 1),
            element("2578", 699, 1687, 854, 1771, 12, 1),
            element("kJ", 890, 1689, 962, 1772, 12, 1),
            element("/", 992, 1690, 1016, 1772, 12, 1),
            element("622", 1054, 1691, 1168, 1775, 12, 1),
            element("kcal.", 1190, 1692, 1329, 1776, 12, 1),
            element("vetten", 325, 1774, 526, 1857, 12, 2),
            element("50", 550, 1780, 641, 1860, 12, 2),
            element("g,", 678, 1783, 726, 1862, 12, 2),
            element("waarvan", 778, 1785, 1031, 1870, 12, 2),
            element("verzadigde", 1065, 1792, 1392, 1878, 12, 2),
            element("vetzuren", 277, 1865, 514, 1956, 12, 3),
            element("9,5", 543, 1871, 651, 1959, 12, 3),
            element("g,", 676, 1873, 741, 1960, 12, 3),
            element("koolhydraten", 761, 1875, 1262, 1970, 12, 3),
            element("11", 1293, 1885, 1370, 1972, 12, 3),
            element("g.", 1374, 1887, 1407, 1973, 12, 3),
            element("waarvan", 261, 1961, 502, 2045, 12, 4),
            element("suikers", 529, 1966, 762, 2050, 12, 4),
            element("5,5", 790, 1971, 898, 2051, 12, 4),
            element("g,", 927, 1974, 983, 2053, 12, 4),
            element("vezels", 1014, 1975, 1236, 2059, 12, 4),
            element("8,1", 1294, 1980, 1368, 2060, 12, 4),
            element("g.", 1377, 1982, 1423, 2061, 12, 4),
            element("eiwitten", 430, 2060, 719, 2148, 12, 5),
            element("28", 743, 2069, 828, 2151, 12, 5),
            element("g,", 854, 2071, 915, 2153, 12, 5),
            element("zout", 942, 2073, 1104, 2158, 12, 5),
            element("0", 1129, 2079, 1166, 2159, 12, 5),
            element("g.", 1194, 2080, 1249, 2161, 12, 5),
            element("NL-BI0-01", 222, 2187, 408, 2244, 13, 0),
            element("Niet-EU", 220, 2253, 363, 2317, 14, 0),
            element("Landbouw", 369, 2266, 594, 2337, 14, 0),
            element("682508238", 1224, 2226, 1456, 2283, 15, 0),
            element("VERPAKKING", 1133, 2306, 1446, 2385, 16, 0),
            element("IN", 1268, 2585, 1315, 2636, 17, 0),
            element("GLASBAK", 1194, 2615, 1375, 2684, 17, 1),
        ),
    )

    /** `20260904-081435-300` */
    fun mayonnaiseThirteen(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("citroensap,", 450, 857, 607, 916, 0, 0),
            element("aroma's,", 619, 862, 741, 920, 0, 0),
            element("antioxidant", 755, 866, 916, 924, 0, 0),
            element("(E385),", 928, 871, 1033, 927, 0, 0),
            element("paprika-extract,", 1046, 874, 1280, 935, 0, 0),
            element("zonnebloemolie", 1293, 881, 1505, 941, 0, 0),
            element("Bron", 400, 926, 458, 984, 1, 0),
            element("van", 475, 924, 516, 982, 1, 0),
            element("omega", 524, 921, 618, 981, 1, 0),
            element("3", 631, 921, 648, 978, 1, 0),
            element("/", 659, 920, 676, 977, 1, 0),
            element("Ihgrédients:", 682, 915, 854, 977, 1, 0),
            element("Huile", 870, 912, 926, 971, 1, 0),
            element("de", 950, 917, 986, 972, 1, 0),
            element("colza", 995, 920, 1075, 978, 1, 0),
            element("78%,", 1084, 925, 1153, 983, 1, 0),
            element("eau,", 1177, 932, 1236, 989, 1, 0),
            element("EUF", 1242, 936, 1307, 994, 1, 0),
            element("et", 1320, 942, 1341, 996, 1, 0),
            element("jaune", 1343, 943, 1433, 1002, 1, 0),
            element("d", 1443, 951, 1462, 1005, 1, 0),
            element("EVF", 1465, 952, 1531, 1010, 1, 0),
            element("(de", 1535, 956, 1565, 1011, 1, 0),
            element("Ihgredienten:", 649, 738, 834, 801, 2, 0),
            element("Raapzaadolie", 852, 744, 1042, 807, 2, 0),
            element("78%,", 1058, 749, 1130, 808, 2, 0),
            element("water,", 1146, 752, 1234, 811, 2, 0),
            element("El", 1250, 755, 1278, 812, 2, 0),
            element("en", 1293, 756, 1326, 813, 2, 0),
            element("mles", 338, 974, 429, 1031, 3, 0),
            element("levées", 442, 976, 548, 1034, 3, 0),
            element("en", 563, 979, 598, 1035, 3, 0),
            element("plein", 612, 981, 682, 1037, 3, 0),
            element("air)", 697, 983, 746, 1040, 3, 0),
            element("79%,", 761, 985, 845, 1042, 3, 0),
            element("vinaigre,", 856, 988, 980, 1046, 3, 0),
            element("SUCre,", 997, 992, 1091, 1049, 3, 0),
            element("sel,", 1102, 995, 1152, 1051, 3, 0),
            element("aróme,", 1171, 997, 1267, 1054, 3, 0),
            element("jus", 1282, 1000, 1323, 1056, 3, 0),
            element("de", 1340, 1001, 1373, 1057, 3, 0),
            element("citron", 1387, 1002, 1465, 1060, 3, 0),
            element("conrantrf", 1477, 1005, 1600, 1064, 3, 0),
            element("dont", 178, 1740, 233, 1793, 4, 0),
            element("acides", 239, 1740, 322, 1793, 4, 0),
            element("gras", 331, 1740, 389, 1793, 4, 0),
            element("saturés", 398, 1740, 501, 1793, 4, 0),
            element("/", 510, 1740, 525, 1793, 4, 0),
            element("EIGEEL", 531, 791, 641, 851, 5, 0),
            element("(Vrie-uitloop)", 648, 796, 848, 859, 5, 0),
            element("7,9%,", 858, 804, 940, 863, 5, 0),
            element("arin,", 946, 808, 1025, 866, 5, 0),
            element("suiker,", 1035, 811, 1132, 870, 5, 0),
            element("zout,", 1141, 816, 1212, 873, 5, 0),
            element("geconcentreerd", 1222, 819, 1441, 883, 5, 0),
            element("antioygène", 314, 1044, 457, 1105, 6, 0),
            element("(E385),", 476, 1041, 571, 1101, 6, 0),
            element("extrait", 576, 1040, 666, 1099, 6, 0),
            element("de", 681, 1038, 716, 1096, 6, 0),
            element("poivron,", 719, 1036, 840, 1096, 6, 0),
            element("huile", 842, 1034, 916, 1093, 6, 0),
            element("de", 918, 1034, 953, 1091, 6, 0),
            element("tournesol.", 960, 1040, 1106, 1102, 6, 0),
            element("Source", 1109, 1047, 1208, 1106, 6, 0),
            element("d'oméga", 1221, 1052, 1333, 1111, 6, 0),
            element("3/Zitaten:", 1347, 1058, 1491, 1127, 6, 0),
            element("Ragol", 1503, 1074, 1580, 1137, 6, 0),
            element("70%,", 1581, 1083, 1651, 1143, 6, 0),
            element("Tinwaser,", 273, 1104, 433, 1163, 6, 1),
            element("El", 467, 1102, 486, 1156, 6, 1),
            element("und", 508, 1100, 559, 1154, 6, 1),
            element("EIGELB", 579, 1095, 682, 1152, 6, 1),
            element("(eier", 707, 1095, 771, 1155, 6, 1),
            element("aus", 796, 1098, 839, 1157, 6, 1),
            element("Freilandhaltung)", 865, 1099, 1092, 1163, 6, 1),
            element("7.9%,", 1120, 1106, 1200, 1167, 6, 1),
            element("Esig,", 1215, 1108, 1298, 1169, 6, 1),
            element("Lucker,", 1315, 1112, 1423, 1176, 6, 1),
            element("Speisesalz,", 1444, 1125, 1595, 1194, 6, 1),
            element("Aroma,", 1604, 1143, 1693, 1206, 6, 1),
            element("Trmensatkonzentat,", 237, 1161, 548, 1229, 6, 2),
            element("ntioxnidationsmitel", 559, 1161, 845, 1229, 6, 2),
            element("(E385),", 854, 1161, 975, 1229, 6, 2),
            element("Paprkaentrakt,", 969, 1161, 1189, 1229, 6, 2),
            element("Sonnenblumendl", 1201, 1161, 1432, 1229, 6, 2),
            element("allman's", 215, 1210, 381, 1271, 6, 3),
            element("saus,", 390, 1214, 475, 1273, 6, 3),
            element("bereid", 485, 1216, 589, 1276, 6, 3),
            element("volgens", 599, 1219, 730, 1279, 6, 3),
            element("Engels", 741, 1222, 854, 1282, 6, 3),
            element("mayonaise", 865, 1225, 1053, 1286, 6, 3),
            element("recept/", 1058, 1229, 1188, 1289, 6, 3),
            element("Sauce", 1200, 1232, 1298, 1292, 6, 3),
            element("Hellmann's,", 1311, 1235, 1497, 1296, 6, 3),
            element("préparốs", 1505, 1239, 1639, 1299, 6, 3),
            element("cal", 1644, 1242, 1678, 1300, 6, 3),
            element("larecette", 197, 1273, 339, 1332, 6, 4),
            element("de", 348, 1276, 385, 1332, 6, 4),
            element("mayonnaise", 395, 1277, 589, 1337, 6, 4),
            element("anglaise", 600, 1281, 742, 1339, 6, 4),
            element("/", 753, 1284, 766, 1340, 6, 4),
            element("Sauce", 778, 1284, 879, 1342, 6, 4),
            element("nach", 890, 1286, 970, 1344, 6, 4),
            element("englischem", 981, 1288, 1174, 1347, 6, 4),
            element("Mayonnaise-Rezept", 1204, 1292, 1512, 1354, 6, 4),
            element("zubereitat", 1515, 1298, 1669, 1356, 6, 4),
            element("Veedngswaarden/", 172, 1371, 418, 1441, 6, 5),
            element("Valeurs", 173, 1436, 264, 1493, 6, 6),
            element("nutritonelles", 270, 1431, 461, 1490, 6, 6),
            element("/", 470, 1431, 480, 1485, 6, 6),
            element("Nahnwertinformation", 169, 1500, 438, 1553, 6, 7),
            element("Energe", 177, 1556, 263, 1624, 6, 8),
            element("/Enerjie", 266, 1551, 384, 1620, 6, 8),
            element("Veten/", 178, 1619, 275, 1674, 6, 9),
            element("Matires", 283, 1619, 390, 1674, 6, 9),
            element("grasses", 395, 1619, 502, 1674, 6, 9),
            element("/Fett", 512, 1619, 589, 1674, 6, 9),
            element("Waanvan", 180, 1679, 286, 1733, 6, 10),
            element("verzadigde", 295, 1679, 439, 1733, 6, 10),
            element("vetzuren", 449, 1679, 567, 1733, 6, 10),
            element("/", 577, 1679, 586, 1733, 6, 10),
            element("Ewiten/", 170, 1977, 286, 2033, 7, 0),
            element("Protéines/Eweiß", 286, 1979, 532, 2038, 7, 0),
            element("out/", 154, 2038, 227, 2089, 7, 1),
            element("Sel", 233, 2039, 272, 2090, 7, 1),
            element("/", 281, 2040, 293, 2090, 7, 1),
            element("Salz", 301, 2040, 356, 2090, 7, 1),
            element("Ineg", 157, 2096, 225, 2161, 8, 0),
            element("3/", 249, 2099, 289, 2163, 8, 0),
            element("Oméga3", 289, 2100, 406, 2166, 8, 0),
            element("taron", 176, 1800, 250, 1854, 9, 0),
            element("gesättigte", 257, 1800, 389, 1854, 9, 0),
            element("Fettsäuren", 399, 1800, 546, 1854, 9, 0),
            element("Kodltydraten", 159, 1851, 324, 1917, 9, 1),
            element("/Glucides", 333, 1854, 470, 1919, 9, 1),
            element("/", 478, 1858, 492, 1920, 9, 1),
            element("Kohlenhydrate", 501, 1858, 705, 1923, 9, 1),
            element("Per", 728, 1373, 777, 1425, 10, 0),
            element("100", 789, 1374, 838, 1426, 10, 0),
            element("ml/", 849, 1375, 909, 1428, 10, 0),
            element("Par", 727, 1431, 775, 1488, 10, 1),
            element("100", 788, 1433, 838, 1490, 10, 1),
            element("ml/", 847, 1435, 908, 1491, 10, 1),
            element("Pro", 727, 1500, 773, 1548, 10, 2),
            element("100", 787, 1500, 836, 1548, 10, 2),
            element("ml", 848, 1500, 883, 1548, 10, 2),
            element("2852", 636, 1552, 704, 1611, 11, 0),
            element("kJ", 716, 1554, 747, 1611, 11, 0),
            element("/", 770, 1555, 787, 1612, 11, 0),
            element("682", 788, 1555, 841, 1612, 11, 0),
            element("kcal", 845, 1555, 905, 1613, 11, 0),
            element("66834219", 339, 3033, 475, 3085, 12, 0),
            element("759", 838, 1622, 902, 1682, 13, 0),
            element("wanvan", 175, 1917, 278, 1974, 14, 0),
            element("suikers", 289, 1919, 384, 1976, 14, 0),
            element("/", 394, 1921, 406, 1976, 14, 0),
            element("dont", 415, 1920, 474, 1976, 14, 0),
            element("sucres", 485, 1922, 578, 1978, 14, 0),
            element("/", 587, 1923, 600, 1978, 14, 0),
            element("davon", 609, 1923, 693, 1980, 14, 0),
            element("Zucker", 703, 1925, 800, 1982, 14, 0),
            element("1,3g", 830, 1927, 897, 1982, 14, 0),
            element("589", 822, 1802, 905, 1870, 15, 0),
            element("13g", 828, 1865, 901, 1927, 16, 0),
            element("10g", 829, 1992, 897, 2052, 17, 0),
            element("12g", 825, 2051, 897, 2114, 18, 0),
            element("68g", 824, 2117, 895, 2177, 19, 0),
            element("Per", 997, 1377, 1046, 1434, 20, 0),
            element("portie*/", 1054, 1379, 1199, 1440, 20, 0),
            element("%*", 1249, 1386, 1296, 1443, 20, 0),
            element("per", 1305, 1388, 1351, 1444, 20, 0),
            element("portie*", 1360, 1390, 1476, 1449, 20, 0),
            element("Par", 978, 1439, 1026, 1495, 20, 1),
            element("portion*|", 1041, 1440, 1216, 1501, 20, 1),
            element("%*", 1235, 1446, 1284, 1502, 20, 1),
            element("par", 1296, 1448, 1338, 1504, 20, 1),
            element("portion/", 1355, 1449, 1507, 1509, 20, 1),
            element("Pro", 997, 1497, 1046, 1556, 20, 2),
            element("Portion*", 1055, 1499, 1196, 1561, 20, 2),
            element("428", 941, 1557, 993, 1615, 20, 3),
            element("kJ", 1006, 1559, 1037, 1616, 20, 3),
            element("/102", 1049, 1559, 1123, 1618, 20, 3),
            element("kcal", 1136, 1560, 1193, 1618, 20, 3),
            element("11g", 1136, 1630, 1194, 1688, 21, 0),
            element("099", 1113, 1807, 1195, 1873, 22, 0),
            element("<0,59", 1088, 1864, 1194, 1935, 23, 0),
            element("<0,59", 1086, 1930, 1191, 1995, 24, 0),
            element("<0,5g", 1088, 1994, 1189, 2057, 25, 0),
            element("0179", 1095, 2054, 1191, 2125, 26, 0),
            element("10g", 1121, 2122, 1188, 2182, 27, 0),
            element("%*", 1249, 1505, 1298, 1563, 28, 0),
            element("pro", 1306, 1506, 1353, 1565, 28, 0),
            element("Portion*", 1361, 1508, 1492, 1569, 28, 0),
            element("\"haReferenie-imame", 124, 2198, 448, 2266, 29, 0),
            element("van", 455, 2207, 501, 2268, 29, 0),
            element("een", 510, 2208, 556, 2269, 29, 0),
            element("gemidelde", 566, 2210, 721, 2272, 29, 0),
            element("volwassene", 729, 2214, 889, 2277, 29, 0),
            element("(8400", 898, 2218, 975, 2279, 29, 0),
            element("kJ/", 994, 2221, 1025, 2280, 29, 0),
            element("2000", 1034, 2222, 1104, 2283, 29, 0),
            element("kcal),", 1105, 2224, 1178, 2284, 29, 0),
            element("/*", 1187, 2226, 1225, 2285, 29, 0),
            element("%", 1234, 2227, 1261, 2286, 29, 0),
            element("d'Apport", 1269, 2228, 1378, 2290, 29, 0),
            element("tetlerencz", 137, 2262, 278, 2325, 29, 1),
            element("pour", 284, 2266, 334, 2326, 29, 1),
            element("n", 339, 2268, 368, 2327, 29, 1),
            element("adulte-ype", 377, 2268, 527, 2331, 29, 1),
            element("(8400", 536, 2272, 611, 2333, 29, 1),
            element("kJ/2000", 621, 2274, 731, 2336, 29, 1),
            element("kcal),", 741, 2277, 814, 2338, 29, 1),
            element("/*%", 823, 2280, 899, 2340, 29, 1),
            element("der", 908, 2282, 951, 2343, 29, 1),
            element("Referenzmenge", 961, 2283, 1169, 2348, 29, 1),
            element("für", 1177, 2289, 1216, 2348, 29, 1),
            element("einen", 1225, 2290, 1295, 2351, 29, 1),
            element("úriodnitlicien", 144, 2321, 346, 2387, 29, 2),
            element("Erwachsenen(8400kJ", 364, 2328, 661, 2397, 29, 2),
            element("/", 659, 2338, 671, 2397, 29, 2),
            element("200", 680, 2338, 746, 2400, 29, 2),
            element("kcal),", 757, 2340, 829, 2402, 29, 2),
            element("/*1", 828, 2343, 938, 2405, 29, 2),
            element("porie", 942, 2346, 1014, 2408, 29, 2),
            element("=", 1028, 2349, 1046, 2408, 29, 2),
            element("15", 1046, 2350, 1076, 2409, 29, 2),
            element("ml=1etlepel", 1088, 2351, 1287, 2417, 29, 2),
            element("leyalang", 144, 2385, 276, 2448, 29, 3),
            element("beat", 295, 2390, 354, 2450, 29, 3),
            element("ca", 357, 2392, 393, 2452, 29, 3),
            element("29", 403, 2393, 434, 2453, 29, 3),
            element("pories)", 451, 2394, 541, 2456, 29, 3),
            element("/", 557, 2398, 570, 2457, 29, 3),
            element("**", 578, 2398, 614, 2458, 29, 3),
            element("1", 626, 2400, 637, 2459, 29, 3),
            element("porion", 648, 2400, 742, 2462, 29, 3),
            element("=", 750, 2404, 768, 2463, 29, 3),
            element("15", 778, 2404, 809, 2464, 29, 3),
            element("ml=1", 819, 2406, 903, 2467, 29, 3),
            element("cuillere", 915, 2408, 1017, 2471, 29, 3),
            element("('emblage", 1026, 2411, 1193, 2475, 29, 3),
            element("contient", 1202, 2417, 1306, 2480, 29, 3),
            element("M", 189, 2448, 209, 2510, 30, 0),
            element("B", 260, 2450, 280, 2512, 30, 0),
            element("porions)", 280, 2450, 401, 2516, 30, 0),
            element("/", 413, 2455, 433, 2517, 30, 0),
            element("\"*", 437, 2456, 459, 2518, 30, 0),
            element("1", 468, 2457, 480, 2519, 30, 0),
            element("Porion", 489, 2457, 584, 2523, 30, 0),
            element("=", 590, 2461, 609, 2523, 30, 0),
            element("15ml=1", 619, 2461, 745, 2528, 30, 0),
            element("Eslofel", 755, 2466, 875, 2532, 30, 0),
            element("(nhaltergibt", 883, 2470, 1058, 2538, 30, 0),
            element("ca.", 1065, 2476, 1105, 2540, 30, 0),
            element("29", 1114, 2478, 1148, 2541, 30, 0),
            element("Porionen.", 1156, 2479, 1296, 2546, 30, 0),
            element("W", 166, 2541, 219, 2603, 31, 0),
            element("getnuk", 225, 2542, 321, 2605, 31, 0),
            element("seal", 330, 2545, 385, 2607, 31, 0),
            element("onder", 393, 2546, 471, 2609, 31, 0),
            element("de", 480, 2548, 513, 2609, 31, 0),
            element("dop", 522, 2550, 573, 2612, 31, 0),
            element("verwijderen.", 583, 2551, 759, 2617, 31, 0),
            element("/", 769, 2556, 782, 2617, 31, 0),
            element("Enlever", 792, 2556, 900, 2620, 31, 0),
            element("l'opercule", 909, 2559, 1052, 2624, 31, 0),
            element("sous", 1061, 2563, 1128, 2626, 31, 0),
            element("le", 1138, 2565, 1163, 2626, 31, 0),
            element("bouchon", 1173, 2566, 1292, 2631, 31, 0),
            element("avant", 1301, 2569, 1378, 2632, 31, 0),
            element("ag", 214, 2600, 252, 2663, 31, 1),
            element("l", 266, 2602, 279, 2663, 31, 1),
            element("Vos", 285, 2603, 330, 2665, 31, 1),
            element("der", 336, 2604, 382, 2667, 31, 1),
            element("Verwendung", 390, 2606, 562, 2673, 31, 1),
            element("Verschiluss", 571, 2612, 729, 2679, 31, 1),
            element("aufschrauben", 736, 2618, 935, 2685, 31, 1),
            element("und", 943, 2625, 998, 2687, 31, 1),
            element("Schutzfolie", 1006, 2627, 1166, 2694, 31, 1),
            element("antenen.", 1174, 2632, 1316, 2698, 31, 1),
            element("No", 207, 2662, 227, 2720, 31, 2),
            element("qpenen", 245, 2663, 333, 2726, 31, 2),
            element("gekoeld", 332, 2667, 442, 2731, 31, 2),
            element("bewaren,", 448, 2673, 579, 2738, 31, 2),
            element("/", 587, 2680, 600, 2738, 31, 2),
            element("Conserver", 609, 2681, 753, 2747, 31, 2),
            element("au", 760, 2689, 797, 2748, 31, 2),
            element("frais", 805, 2691, 873, 2752, 31, 2),
            element("après", 881, 2694, 963, 2756, 31, 2),
            element("ouverture.l", 971, 2699, 1139, 2766, 31, 2),
            element("kadh", 204, 2716, 273, 2781, 31, 3),
            element("den", 278, 2722, 338, 2785, 31, 3),
            element("Uffnen", 351, 2727, 437, 2792, 31, 3),
            element("in", 439, 2733, 477, 2796, 31, 3),
            element("Kúhilschrank", 482, 2736, 663, 2809, 31, 3),
            element("aufbewahren.", 670, 2749, 864, 2823, 31, 3),
            element("8", 566, 3024, 593, 3117, 32, 0),
            element("\"710604278725)", 621, 3024, 1395, 3117, 32, 0),
            element("06/04/202", 741, 3146, 1042, 3226, 32, 1),
            element("7", 1051, 3163, 1073, 3228, 32, 1),
            element("L61010C", 740, 3234, 1012, 3305, 32, 2),
            element("817", 1027, 3243, 1130, 3308, 32, 2),
            element("12:20", 1156, 3247, 1304, 3314, 32, 2),
            element("lenniste", 613, 3360, 744, 3425, 33, 0),
            element("houdbaar", 756, 3362, 870, 3426, 33, 0),
            element("tot", 880, 3365, 911, 3427, 33, 0),
            element("/A", 920, 3366, 956, 3428, 33, 0),
            element("onsonner", 959, 3366, 1104, 3431, 33, 0),
            element("de", 1110, 3369, 1140, 3431, 33, 0),
            element("pelrence", 1145, 3369, 1277, 3434, 33, 0),
            element("avanl:", 751, 3424, 832, 3485, 33, 1),
            element("/", 840, 3424, 858, 3485, 33, 1),
            element("Mindeslens", 874, 3424, 994, 3485, 33, 1),
            element("halbar", 1005, 3424, 1091, 3485, 33, 1),
            element("bis", 1105, 3424, 1135, 3485, 33, 1),
            element("5%", 1443, 1577, 1496, 1626, 34, 0),
            element("16", 1428, 1638, 1459, 1687, 35, 0),
            element("%", 1467, 1638, 1492, 1687, 35, 0),
            element("5%", 1441, 1812, 1495, 1870, 36, 0),
            element("<1%", 1412, 1876, 1493, 1932, 37, 0),
            element("Unilever", 1538, 1609, 1652, 1665, 38, 0),
            element("Ne", 1652, 1617, 1679, 1666, 38, 0),
            element("BI.Postbu", 1539, 1672, 1679, 1726, 39, 0),
            element("3000", 1540, 1735, 1606, 1784, 40, 0),
            element("AD", 1614, 1737, 1648, 1785, 40, 0),
            element("Ro", 1656, 1737, 1678, 1785, 40, 0),
            element("Unilever", 1550, 1812, 1646, 1870, 41, 0),
            element("Be", 1665, 1812, 1684, 1870, 41, 0),
            element("Hellnan's", 1547, 1876, 1687, 1932, 42, 0),
            element("<1%", 1413, 1941, 1495, 1989, 42, 1),
            element("Industriela", 1540, 1941, 1671, 1989, 42, 1),
            element("<1%", 1412, 2001, 1493, 2050, 42, 2),
            element("Industrel", 1539, 2001, 1667, 2050, 42, 2),
            element("A", 1354, 2150, 1376, 2226, 43, 0),
            element("Unilevew", 1374, 2144, 1646, 2225, 43, 0),
            element("B", 1663, 2143, 1677, 2219, 43, 0),
            element("3%", 1439, 2062, 1493, 2111, 44, 0),
            element("Anderlecht", 1537, 2062, 1676, 2113, 44, 0),
            element("AERN4", 1518, 2266, 1638, 2322, 45, 0),
            element("VL48ELCOM", 1460, 2491, 1658, 2571, 46, 0),
            element("VEGETARISCH", 1428, 2590, 1688, 2652, 46, 1),
        ),
    )
}
