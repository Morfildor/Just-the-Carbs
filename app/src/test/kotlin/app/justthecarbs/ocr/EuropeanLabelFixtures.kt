package app.justthecarbs.ocr

import app.justthecarbs.ocr.DutchLabelFixtures.element

/**
 * Synthetic nutrition tables in the languages European packaging is printed in, shared by
 * [EuropeanLabelDiagnosticTest] (which prints) and `EuropeanNutritionTableTest` (which asserts).
 *
 * ## Where the words come from
 *
 * The nutrient names follow the nutrition declaration of Regulation (EU) No 1169/2011 (Annex XV) as
 * printed in each language, cross-checked against Open Food Facts' multilingual nutrient taxonomy
 * (`taxonomies/nutrients.txt`, read 2026-09-17). Headers use the phrase each language prints over a
 * column: `je 100 g`, `pour 100 g`, `w 100 g`, `100 g-ban`, `100 g:ssa`, `100 g'da`.
 *
 * Greek and Bulgarian are not here: ML Kit's bundled recognizer reads Latin script only, so no
 * vocabulary can make their labels readable. Irish and Maltese packaging is printed in English.
 * Hungarian is here and reads nothing on its own — see `NutritionTerminology`'s `hu` entry.
 *
 * ## Layout
 *
 * [DutchLabelFixtures]' rule applies: a header phrase is centred over the column it describes and a
 * leading title sits in the label column. The Dutch helper decides where a phrase starts from a fixed
 * word list, which would mis-place `je`, `pour` or `w`, so here each header phrase is given explicitly.
 */
internal object EuropeanLabelFixtures {

    data class Language(
        val code: String,
        val title: String,
        val per100g: String,
        val per100ml: String,
        val perServing: String,
        val reference: String,
        val energy: String,
        val fat: String,
        val saturates: String,
        val carbohydrate: String,
        val sugars: String,
        val fibre: String,
        val protein: String,
        val salt: String,
        /** Other printed names for the total: singular, plural, capitals. */
        val carbohydrateSpellings: List<String>,
        /** Child rows a merged row can put beside the total. */
        val childTerms: List<String>,
    )

    val LANGUAGES = listOf(
        Language(
            "en", "Nutrition", "per 100 g", "per 100 ml", "per portion (30 g)", "%RI*",
            "Energy", "Fat", "of which saturates", "Carbohydrate", "of which sugars", "Fibre", "Protein", "Salt",
            listOf("Carbohydrates", "CARBOHYDRATE"),
            listOf("of which sugars", "Sugars", "Fibre", "Starch", "Polyols"),
        ),
        Language(
            "nl", "Voedingswaarde", "per 100 g", "per 100 ml", "per portie (30 g)", "%RI*",
            "Energie", "Vetten", "waarvan verzadigde vetzuren", "Koolhydraten", "waarvan suikers",
            "Voedingsvezel", "Eiwitten", "Zout",
            listOf("Koolhydraat", "KOOLHYDRATEN"),
            listOf("waarvan suikers", "Suikers", "Vezels", "Zetmeel", "Polyolen"),
        ),
        Language(
            "de", "Nährwerte", "je 100 g", "je 100 ml", "pro Portion (30 g)", "%RM*",
            "Brennwert", "Fett", "davon gesättigte Fettsäuren", "Kohlenhydrate", "davon Zucker",
            "Ballaststoffe", "Eiweiß", "Salz",
            listOf("Kohlenhydrat", "KOHLENHYDRATE"),
            listOf("davon Zucker", "Zucker", "Ballaststoffe", "Stärke", "mehrwertige Alkohole"),
        ),
        Language(
            "fr", "Valeurs nutritionnelles", "pour 100 g", "pour 100 ml", "par portion (30 g)", "%AR*",
            "Énergie", "Matières grasses", "dont acides gras saturés", "Glucides", "dont sucres",
            "Fibres alimentaires", "Protéines", "Sel",
            listOf("GLUCIDES"),
            listOf("dont sucres", "Sucres", "Sucre", "Fibres alimentaires", "Amidon", "Polyols"),
        ),
        Language(
            "es", "Información nutricional", "por 100 g", "por 100 ml", "por ración (30 g)", "%IR*",
            "Valor energético", "Grasas", "de las cuales saturadas", "Hidratos de carbono",
            "de los cuales azúcares", "Fibra alimentaria", "Proteínas", "Sal",
            listOf("Carbohidratos", "HIDRATOS DE CARBONO"),
            listOf("de los cuales azúcares", "Azúcares", "Azúcar", "Fibra alimentaria", "Almidón", "Polialcoholes"),
        ),
        Language(
            "it", "Valori nutrizionali", "per 100 g", "per 100 ml", "per porzione (30 g)", "%AR*",
            "Energia", "Grassi", "di cui acidi grassi saturi", "Carboidrati", "di cui zuccheri",
            "Fibre", "Proteine", "Sale",
            listOf("CARBOIDRATI"),
            listOf("di cui zuccheri", "Zuccheri", "Zucchero", "Fibra alimentare", "Amido", "Polioli"),
        ),
        Language(
            "pt", "Declaração nutricional", "por 100 g", "por 100 ml", "por porção (30 g)", "%DR*",
            "Energia", "Lípidos", "dos quais saturados", "Hidratos de carbono", "dos quais açúcares",
            "Fibra", "Proteínas", "Sal",
            listOf("Carboidratos", "HIDRATOS DE CARBONO"),
            listOf("dos quais açúcares", "Açúcares", "Fibras", "Amido", "Polióis"),
        ),
        Language(
            "pl", "Wartość odżywcza", "w 100 g", "w 100 ml", "w porcji (30 g)", "%RWS*",
            "Wartość energetyczna", "Tłuszcz", "w tym kwasy tłuszczowe nasycone", "Węglowodany",
            "w tym cukry", "Błonnik", "Białko", "Sól",
            listOf("WĘGLOWODANY"),
            listOf("w tym cukry", "Cukry", "Cukier", "Błonnik", "Skrobia", "Alkohole wielowodorotlenowe"),
        ),
        Language(
            "cs", "Výživové údaje", "ve 100 g", "ve 100 ml", "v porci (30 g)", "%RHP*",
            "Energetická hodnota", "Tuky", "z toho nasycené mastné kyseliny", "Sacharidy",
            "z toho cukry", "Vláknina", "Bílkoviny", "Sůl",
            listOf("SACHARIDY"),
            listOf("z toho cukry", "Cukry", "Cukr", "Vláknina", "Škrob", "Polyalkoholy"),
        ),
        Language(
            "sk", "Výživové údaje", "v 100 g", "v 100 ml", "v porcii (30 g)", "%RHP*",
            "Energetická hodnota", "Tuky", "z toho nasýtené mastné kyseliny", "Sacharidy",
            "z toho cukry", "Vláknina", "Bielkoviny", "Soľ",
            listOf("SACHARIDY"),
            listOf("z toho cukry", "Cukry", "Cukor", "Vláknina", "Škrob", "Polyoly"),
        ),
        Language(
            "hu", "Tápérték", "100 g-ban", "100 ml-ben", "adagonként (30 g)", "%RBÉ*",
            "Energia", "Zsír", "amelyből telített zsírsavak", "Szénhidrát", "amelyből cukrok",
            "Rost", "Fehérje", "Só",
            listOf("SZÉNHIDRÁT", "Szénhidrátok"),
            listOf("amelyből cukrok", "Cukrok", "Cukor", "Rost", "Élelmi rost", "Keményítő", "Poliolok"),
        ),
        Language(
            "ro", "Declarație nutrițională", "la 100 g", "la 100 ml", "per porție (30 g)", "%DZR*",
            "Valoare energetică", "Grăsimi", "din care acizi grași saturați", "Glucide",
            "din care zaharuri", "Fibre", "Proteine", "Sare",
            listOf("GLUCIDE", "Carbohidrați"),
            listOf("din care zaharuri", "Zaharuri", "Zahăr", "Fibre", "Amidon", "Polioli"),
        ),
        Language(
            "da", "Næringsindhold", "pr. 100 g", "pr. 100 ml", "pr. portion (30 g)", "%RI*",
            "Energi", "Fedt", "heraf mættede fedtsyrer", "Kulhydrat", "heraf sukkerarter",
            "Kostfibre", "Protein", "Salt",
            listOf("Kulhydrater", "KULHYDRAT"),
            listOf("heraf sukkerarter", "Sukkerarter", "Sukker", "Kostfibre", "Stivelse", "Polyoler"),
        ),
        Language(
            "sv", "Näringsvärde", "per 100 g", "per 100 ml", "per portion (30 g)", "%RI*",
            "Energi", "Fett", "varav mättat fett", "Kolhydrat", "varav sockerarter",
            "Fiber", "Protein", "Salt",
            listOf("Kolhydrater", "KOLHYDRAT"),
            listOf("varav sockerarter", "Sockerarter", "Socker", "Fiber", "Stärkelse", "Polyoler"),
        ),
        Language(
            "no", "Næringsinnhold", "per 100 g", "per 100 ml", "per porsjon (30 g)", "%RI*",
            "Energi", "Fett", "hvorav mettede fettsyrer", "Karbohydrat", "hvorav sukkerarter",
            "Kostfiber", "Protein", "Salt",
            listOf("Karbohydrater", "KARBOHYDRAT"),
            listOf("hvorav sukkerarter", "Sukkerarter", "Sukker", "Kostfiber", "Stivelse", "Polyoler"),
        ),
        Language(
            "fi", "Ravintosisältö", "100 g:ssa", "100 ml:ssa", "annoksessa (30 g)", "%VS*",
            "Energia", "Rasva", "josta tyydyttyneitä rasvoja", "Hiilihydraatit", "josta sokereita",
            "Ravintokuitu", "Proteiini", "Suola",
            listOf("Hiilihydraatti", "HIILIHYDRAATIT"),
            listOf("josta sokereita", "joista sokereita", "Sokerit", "Sokeri", "Ravintokuitu", "Kuidut", "Tärkkelys", "Polyolit"),
        ),
        Language(
            "et", "Toitumisalane teave", "100 g kohta", "100 ml kohta", "portsjoni kohta (30 g)", "%RI*",
            "Energiasisaldus", "Rasvad", "millest küllastunud rasvhapped", "Süsivesikud",
            "millest suhkrud", "Kiudained", "Valgud", "Sool",
            listOf("SÜSIVESIKUD"),
            listOf("millest suhkrud", "Suhkrud", "Kiudained", "Tärklis", "Polüoolid"),
        ),
        Language(
            "lv", "Uzturvērtība", "100 g", "100 ml", "porcijā (30 g)", "%RD*",
            "Enerģētiskā vērtība", "Tauki", "tostarp piesātinātās taukskābes", "Ogļhidrāti",
            "tostarp cukuri", "Šķiedrvielas", "Olbaltumvielas", "Sāls",
            listOf("OGĻHIDRĀTI"),
            listOf("tostarp cukuri", "Cukuri", "Šķiedrvielas", "Ciete", "Polioli"),
        ),
        Language(
            "lt", "Maistinė vertė", "100 g", "100 ml", "porcijoje (30 g)", "%RSN*",
            "Energinė vertė", "Riebalai", "iš kurių sočiosios riebalų rūgštys", "Angliavandeniai",
            "iš kurių cukrūs", "Skaidulinės medžiagos", "Baltymai", "Druska",
            listOf("ANGLIAVANDENIAI"),
            listOf("iš kurių cukrūs", "Cukrūs", "Skaidulinės medžiagos", "Krakmolas", "Polioliai"),
        ),
        Language(
            "hr", "Hranjiva vrijednost", "na 100 g", "na 100 ml", "po porciji (30 g)", "%RU*",
            "Energija", "Masti", "od kojih zasićene masne kiseline", "Ugljikohidrati",
            "od kojih šećeri", "Vlakna", "Bjelančevine", "Sol",
            listOf("UGLJIKOHIDRATI"),
            listOf("od kojih šećeri", "Šećeri", "Vlakna", "Škrob", "Polioli"),
        ),
        Language(
            "sl", "Hranilna vrednost", "na 100 g", "na 100 ml", "na porcijo (30 g)", "%PV*",
            "Energijska vrednost", "Maščobe", "od tega nasičene maščobne kisline", "Ogljikovi hidrati",
            "od tega sladkorji", "Prehranske vlaknine", "Beljakovine", "Sol",
            listOf("OGLJIKOVI HIDRATI"),
            listOf("od tega sladkorji", "Sladkorji", "Prehranske vlaknine", "Škrob", "Polioli"),
        ),
        Language(
            "tr", "Besin Değerleri", "100 g'da", "100 ml'de", "Porsiyonda (30 g)", "%BRD*",
            "Enerji", "Yağ", "Doymuş yağ", "Karbonhidrat", "Şekerler",
            "Lif", "Protein", "Tuz",
            listOf("Karbonhidratlar", "KARBONHİDRAT"),
            listOf("Şekerler", "Şeker", "Lif", "Nişasta", "Polioller"),
        ),
    )

    /** Value-column centres: per 100, per portion, percentage. */
    private val COLUMNS = listOf(800, 1100, 1400)
    private const val WIDTH = 1600

    /** Per-100 figure the tables print for carbohydrate, and the sugars figure a wrong read would show. */
    const val CARBS = "62,5"
    const val SUGARS = "24,0"

    private fun rows(language: Language, carbohydrate: String = language.carbohydrate) = listOf(
        language.energy to listOf("1650", "495", "20"),
        language.fat to listOf("12,0", "3,6", "17"),
        language.saturates to listOf("5,5", "1,7", "28"),
        carbohydrate to listOf(CARBS, "18,8", "7"),
        language.sugars to listOf(SUGARS, "7,2", "8"),
        language.fibre to listOf("3,1", "0,9", ""),
        language.protein to listOf("6,2", "1,9", "4"),
        language.salt to listOf("0,40", "0,12", "2"),
    )

    /**
     * The label's table. [columns] is how many value columns are printed (1 to 3); [percentSigns]
     * puts a `%` on each reference-intake cell, which some packages print and some leave to the header.
     */
    fun table(
        language: Language,
        columns: Int = 1,
        percentSigns: Boolean = true,
        carbohydrate: String = language.carbohydrate,
        per100: String = language.per100g,
    ): OcrDocument {
        val elements = mutableListOf<OcrElement>()
        elements += words(language.title, 40, 100, line = 0)
        val headers = listOf(per100, language.perServing, language.reference)
        (0 until columns).forEach { column -> elements += centred(headers[column], COLUMNS[column], 100, line = 0) }

        rows(language, carbohydrate).forEachIndexed { index, (label, values) ->
            val top = 200 + index * 60
            elements += words(label, 40, top, line = index + 1)
            (0 until columns).forEach { column ->
                val raw = values[column]
                if (raw.isNotEmpty()) {
                    val value = if (column == 2 && percentSigns) "$raw%" else raw
                    elements += centred(value, COLUMNS[column], top, line = index + 1, block = 4 + column)
                }
            }
        }
        return OcrDocument(width = WIDTH, height = 200 + 8 * 60 + 60, elements = elements)
    }

    /**
     * Per 100 g beside the percentage column, 260 px apart, with the carbohydrate row's gram figure
     * lost by the recognizer: only its bare percentage `7` is left on the row. That `7` must never be
     * read as grams, whether or not the percentage header was recognised.
     */
    fun tableWithLostCarbohydrateCell(language: Language): OcrDocument {
        val per100Centre = COLUMNS[0]
        val percentCentre = COLUMNS[0] + 260
        val elements = mutableListOf<OcrElement>()
        elements += words(language.title, 40, 100, line = 0)
        elements += centred(language.per100g, per100Centre, 100, line = 0)
        elements += centred(language.reference, percentCentre, 100, line = 0)
        rows(language).forEachIndexed { index, (label, values) ->
            val top = 200 + index * 60
            elements += words(label, 40, top, line = index + 1)
            if (label != language.carbohydrate) {
                elements += centred(values[0], per100Centre, top, line = index + 1, block = 4)
            }
            if (values[2].isNotEmpty()) {
                elements += centred(values[2], percentCentre, top, line = index + 1, block = 5)
            }
        }
        return OcrDocument(width = WIDTH, height = 200 + 8 * 60 + 60, elements = elements)
    }

    /** A drink: per 100 ml, 4,5 g carbohydrate, the same 4,5 g as sugars. */
    fun drink(language: Language): OcrDocument {
        val elements = mutableListOf<OcrElement>()
        elements += words(language.title, 40, 100, line = 0)
        elements += centred(language.per100ml, COLUMNS[0], 100, line = 0)
        listOf(
            language.energy to "78",
            language.carbohydrate to "4,5",
            language.sugars to "4,5",
            language.salt to "0,01",
        ).forEachIndexed { index, (label, value) ->
            val top = 200 + index * 60
            elements += words(label, 40, top, line = index + 1)
            elements += centred(value, COLUMNS[0], top, line = index + 1, block = 4)
        }
        return OcrDocument(width = WIDTH, height = 500, elements = elements)
    }

    /**
     * The total row and a child row collapsed into one reconstructed row, the child's figure in the
     * same column: the geometry [DutchLabelFixtures.mergedTotalAndChildRow] explains.
     */
    fun mergedRow(language: Language, child: String): OcrDocument {
        val elements = mutableListOf<OcrElement>()
        elements += centred(language.per100g, COLUMNS[0], 100, line = 0)
        // 40 px boxes overlapping by 25: more than the row builder can separate.
        elements += words(language.carbohydrate, 40, 200, line = 1, height = 40)
        elements += centred("62", COLUMNS[0], 200, line = 1, block = 4, height = 40)
        elements += words(child, 60, 215, line = 1, height = 40)
        elements += centred("35", COLUMNS[0], 215, line = 1, block = 4, height = 40)
        elements += words(language.protein, 40, 320, line = 2)
        elements += centred("6,2", COLUMNS[0], 320, line = 2, block = 4)
        return OcrDocument(width = WIDTH, height = 500, elements = elements)
    }

    /**
     * No header row: each nutrient row states its own basis, `<name> <per 100 g> <value> g`. The
     * `100` of the basis phrase must never be read as the carbohydrate figure.
     */
    fun inlineBasisRows(language: Language): OcrDocument {
        val elements = mutableListOf<OcrElement>()
        listOf(
            language.carbohydrate to CARBS,
            language.protein to "6,2",
        ).forEachIndexed { index, (label, value) ->
            val top = 100 + index * 60
            val text = "$label ${language.per100g} $value g"
            elements += words(text, 40, top, line = index)
        }
        return OcrDocument(width = WIDTH, height = 300, elements = elements)
    }

    /**
     * The whole declaration as running text, wrapped across rows the way a small pack prints it:
     * `<title> <per 100 g>: <energy> 1650 kJ, <fat> 12 g, <carbohydrate> 46 g, <sugars> 1,0 g, ...`.
     */
    fun prose(language: Language): OcrDocument {
        val text = "${language.title} ${language.per100g}: ${language.energy} 1650 kJ, ${language.fat} 12 g, " +
            "${language.carbohydrate} 46 g, ${language.sugars} 1,0 g, ${language.protein} 6,2 g, ${language.salt} 0,4 g"
        val elements = mutableListOf<OcrElement>()
        var x = 40
        var line = 0
        text.split(' ').forEach { word ->
            val width = 14 * word.length
            if (x + width > WIDTH - 40) {
                x = 40
                line++
            }
            elements += element(word, x, 100 + line * 45, x + width, 130 + line * 45, line = line)
            x += width + 10
        }
        return OcrDocument(width = WIDTH, height = 200 + line * 45, elements = elements)
    }

    private fun words(text: String, left: Int, top: Int, line: Int, height: Int = 30): List<OcrElement> {
        var x = left
        return text.split(' ').map { word ->
            val e = element(word, x, top, x + 14 * word.length, top + height, line = line)
            x += 14 * word.length + 10
            e
        }
    }

    private fun centred(
        text: String,
        centre: Int,
        top: Int,
        line: Int,
        block: Int = 0,
        height: Int = 30,
    ): List<OcrElement> {
        val parts = text.split(' ')
        var x = centre - parts.sumOf { 22 * it.length + 12 } / 2
        return parts.map { word ->
            val e = element(word, x, top, x + 22 * word.length, top + height, line = line, block = block)
            x += 22 * word.length + 12
            e
        }
    }
}
