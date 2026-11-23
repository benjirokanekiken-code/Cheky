package ua.cheky.app.logic

import ua.cheky.app.model.Categories as C

/**
 * Keyword categories for German product names.
 * "^X" = a word starting with X, "=X" = exactly the word X, otherwise substring.
 * Order matters: the first matching group wins.
 */
object Categorizer {
    private val rules: List<Pair<String, List<String>>> = listOf(
        C.PFAND to listOf("PFAND", "LEERGUT", "^EINWEG", "^MEHRWEG"),
        C.FROZEN to listOf("=TK", "TIEFK", "PIZZA", "POMMES", "=EIS", "EISCREME", "RAHMSPINAT", "FISCHSTÄBCHEN", "KROKETTEN"),
        C.SWEETS to listOf("SCHOKO", "KEKS", "CHIPS", "GUMMI", "BONBON", "RIEGEL", "WAFFEL", "PRALINE", "POPCORN", "SNACK",
            "KAUGUMMI", "HARIBO", "MILKA", "NUTELLA", "^NÜSSE", "NUSSMISCH", "ERDNUSS", "SALZSTANG", "LAKRITZ", "KUCHEN", "TORTE", "CRACKER"),
        C.DRINKS to listOf("WASSER", "SAFT", "COLA", "LIMO", "BIER", "^WEIN", "ROTW", "WEISSW", "WEIßW", "ROSEW", "SEKT", "SCHORLE",
            "FANTA", "SPRITE", "MINERAL", "SPRUDEL", "ENERGY", "EISTEE", "NEKTAR", "^PILS", "WODKA", "VODKA", "KÖLSCH", "RADLER", "SMOOTHIE"),
        C.DAIRY to listOf("MILCH", "KÄSE", "KAESE", "JOGHURT", "^JOGH", "QUARK", "BUTTER", "SAHNE", "SKYR", "KEFIR", "MOZZ", "GOUDA",
            "EDAMER", "FRISCHK", "SCHMAND", "CREME FR", "CRÈME FR", "EIER", "FETA", "PARMESAN", "EMMENTALER", "PUDDING", "GRIESS", "HALLOUMI"),
        C.MEAT to listOf("HÄHN", "HAEHN", "HUHN", "PUTE", "RIND", "SCHWEIN", "^SCHW", "HACK", "WURST", "SALAMI", "SCHINKEN", "SPECK",
            "LACHS", "THUNFISCH", "FISCH", "GARNELE", "HERING", "FILET", "STEAK", "KOTELETT", "BRATW", "WIENER", "LEBERW",
            "GEFLÜGEL", "GEFL", "KASSLER", "AUFSCHNITT", "FRIKADELLE", "GYROS", "MAKRELE", "FORELLE", "LEBERKÄSE"),
        C.BREAD to listOf("BROT", "BRÖTCHEN", "BROETCHEN", "TOAST", "BAGUETTE", "CROISSANT", "BREZEL", "BREZN", "LAUGEN", "SEMMEL",
            "CIABATTA", "^WECK", "BERLINER", "DONUT", "MUFFIN", "PLUNDER", "ZWIEBACK"),
        C.VEG to listOf("BANANE", "APFEL", "ÄPFEL", "AEPFEL", "BIRNE", "TOMATE", "GURKE", "KARTOFFEL", "ZWIEBEL", "PAPRIKA", "SALAT",
            "MÖHRE", "MOEHRE", "KAROTTE", "ZITRONE", "LIMETTE", "ORANGE", "TRAUBE", "ERDBEER", "KOHL", "BROKKOLI", "BROCCOLI",
            "ZUCCHINI", "PILZ", "CHAMPIGNON", "KNOBLAUCH", "AVOCADO", "MANDARINE", "CLEMENTINE", "KIWI", "BEEREN", "SPINAT",
            "LAUCH", "RADIESCHEN", "MELONE", "OBST", "GEMÜSE", "GEMUESE", "INGWER", "AUBERGINE", "PFIRSICH", "NEKTARINE",
            "ANANAS", "MANGO", "KÜRBIS", "SPARGEL", "PETERSILIE", "DILL", "SCHNITTLAUCH", "RUCOLA"),
        C.GROCERY to listOf("NUDEL", "SPAGHETTI", "PASTA", "PENNE", "FUSILLI", "=REIS", "^REIS", "MEHL", "ZUCKER", "SALZ", "ÖL", "OEL",
            "ESSIG", "MÜSLI", "MUESLI", "HAFER", "FLOCKEN", "KAFFEE", "=TEE", "TEE ", "HONIG", "MARMELADE", "KONFITÜRE", "KETCHUP",
            "SENF", "MAYO", "SOSSE", "SOßE", "SAUCE", "GEWÜRZ", "PFEFFER", "LINSEN", "BOHNEN", "^MAIS", "KONSERVE", "CORNFLAKES",
            "BRÜHE", "TOMATENMARK", "PASSATA", "BUCHWEIZEN", "HEFE", "BACKPULVER", "KAKAO", "OLIVEN", "ERBSEN", "KICHERERB"),
        C.HOUSEHOLD to listOf("SPÜL", "SPUEL", "WASCHMITTEL", "WASCHPULVER", "REINIGER", "TOILETTENPAP", "KÜCHENROLLE", "KUECHENR",
            "MÜLLBEUTEL", "MUELLB", "ALUFOLIE", "FRISCHHALTE", "SCHWAMM", "WEICHSPÜL", "ENTKALK", "TABS", "WC-", "BACKPAPIER",
            "SERVIETTE", "KERZE", "BATTERIE", "GLÜHBIRNE"),
        C.HYGIENE to listOf("SHAMPOO", "DUSCH", "SEIFE", "ZAHNP", "ZAHNB", "ZAHNC", "^DEO", "CREME", "WINDEL", "TAMPON", "BINDEN",
            "RASIER", "TASCHENT", "WATTE", "PFLASTER", "LOTION", "HAARGEL", "SPÜLUNG", "SONNENSCH", "FEUCHTT", "MUNDSP"),
    )

    fun categorize(de: String): String {
        val up = de.uppercase()
        val tokens = up.split(Regex("[^A-ZÄÖÜß0-9]+")).filter { it.isNotEmpty() }
        if (rules[0].second.any { hit(it, up, tokens) }) return C.PFAND
        if (ReceiptParser.isDiscount(de)) return C.DISCOUNT
        for ((cat, keys) in rules.drop(1)) {
            if (keys.any { hit(it, up, tokens) }) return cat
        }
        return C.OTHER
    }

    private fun hit(k: String, up: String, tokens: List<String>) = when {
        k.startsWith("^") -> tokens.any { it.startsWith(k.substring(1)) }
        k.startsWith("=") -> tokens.any { it == k.substring(1) }
        else -> up.contains(k)
    }
}
