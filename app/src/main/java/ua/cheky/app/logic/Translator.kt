package ua.cheky.app.logic

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** Offline German -> Ukrainian translation with ML Kit. */
object DeUkTranslator {
    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.GERMAN)
                .setTargetLanguage(TranslateLanguage.UKRAINIAN)
                .build()
        )
    }
    private val cache = HashMap<String, String>()

    /** Downloads the language model once (~30 MB); afterwards works without internet. */
    suspend fun ensureModel() {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
    }

    suspend fun translate(text: String): String {
        cache[text]?.let { return it }
        ensureModel()
        val out = translator.translate(text).await()
        cache[text] = out
        return out
    }

    /** Product name from a receipt -> Ukrainian. */
    suspend fun translateProduct(de: String): String {
        val expanded = ReceiptText.expand(de)
        return translate(expanded).replaceFirstChar { it.uppercase() }
    }
}

/** Expands typical German receipt abbreviations into words a translator understands. */
object ReceiptText {
    private val ABBR = mapOf(
        "KAESE" to "Käse", "MOEHREN" to "Möhren", "BROETCHEN" to "Brötchen", "AEPFEL" to "Äpfel",
        "GEMUESE" to "Gemüse", "MUESLI" to "Müsli", "HAEHNCHEN" to "Hähnchen", "HAEHN" to "Hähnchen",
        "HAEH" to "Hähnchen", "HÄHN" to "Hähnchen", "SUESS" to "süß", "OEL" to "Öl", "SCHW" to "Schweine",
        "SCHWEINEF" to "Schweinefleisch", "HACKFL" to "Hackfleisch", "GEM" to "gemischt", "KL" to "klein",
        "GR" to "groß", "ST" to "Stück", "STK" to "Stück", "FL" to "Flasche", "PCK" to "Packung",
        "PKG" to "Packung", "VOLLM" to "Vollmilch", "H" to "haltbare", "H-MILCH" to "haltbare Milch",
        "JOGH" to "Joghurt", "JOGHU" to "Joghurt", "FRISCHK" to "Frischkäse", "SCHLAGS" to "Schlagsahne",
        "TK" to "tiefgekühlt", "GEFL" to "Geflügel", "ORIG" to "Original", "NAT" to "natur",
        "MINERALW" to "Mineralwasser", "KART" to "Kartoffeln", "ZWIEB" to "Zwiebeln", "TOM" to "Tomaten",
        "BAN" to "Bananen", "CHAMP" to "Champignons", "SCHOK" to "Schokolade", "SCHOKO" to "Schokolade",
        "VOLLK" to "Vollkorn", "WEIZENM" to "Weizenmehl", "SPUELM" to "Spülmittel", "WASCHM" to "Waschmittel",
        "TOILETTENP" to "Toilettenpapier", "ZAHNC" to "Zahncreme", "DUSCHG" to "Duschgel", "ROTW" to "Rotwein",
        "WEISSW" to "Weißwein", "APFELS" to "Apfelsaft", "ORANGENS" to "Orangensaft", "MOZZ" to "Mozzarella",
        "BUTTERM" to "Buttermilch", "EINWEG" to "Einweg", "MEHRW" to "Mehrweg", "BIO" to "Bio",
        "FETTARM" to "fettarm", "FRISCH" to "frisch", "GESCHN" to "geschnitten", "GER" to "gerieben",
        "GERAEUCH" to "geräuchert", "GEKOCHT" to "gekocht", "PAPR" to "Paprika", "GURK" to "Gurken",
        "KUECHENR" to "Küchenrolle", "MUELLB" to "Müllbeutel", "WEICHSP" to "Weichspüler",
    )
    private val unitRe = Regex("""^(\d+(?:[.,]\d+)?)(G|GR|KG|L|ML|CL|ST|STK|%)$""")

    /** Receipts often write umlauts as AE/OE/UE inside compound words. */
    private val UMLAUTS = listOf(
        "BROETCHEN" to "BRÖTCHEN", "KAESE" to "KÄSE", "GEMUESE" to "GEMÜSE", "MOEHRE" to "MÖHRE",
        "AEPFEL" to "ÄPFEL", "HAEHNCHEN" to "HÄHNCHEN", "MUESLI" to "MÜSLI", "SUESS" to "SÜSS",
        "KUECHE" to "KÜCHE", "SPUEL" to "SPÜL", "MUELL" to "MÜLL", "OEL" to "ÖL", "KUERBIS" to "KÜRBIS",
        "GEFLUEGEL" to "GEFLÜGEL", "SOSSE" to "SOßE", "GETRAENK" to "GETRÄNK", "KNOEDEL" to "KNÖDEL",
    )

    fun expand(name: String): String = name
        .replace(Regex("""\.(?=\p{L})"""), ". ") // "SCHW.KOTELETT" -> "SCHW. KOTELETT"
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { original ->
            val bare = original.trimEnd('.').uppercase()
                .let { s -> UMLAUTS.fold(s) { acc, (a, b) -> if (acc in ABBR) acc else acc.replace(a, b) } }
            val token = if (original == original.uppercase()) bare else original.trimEnd('.')
            ABBR[bare]
                ?: unitRe.matchEntire(bare)?.let { m ->
                    val unit = m.groupValues[2].lowercase().let { if (it == "gr") "g" else if (it == "stk" || it == "st") "Stück" else it }
                    if (unit == "%") token else "${m.groupValues[1]} $unit"
                }
                ?: if (token.any { it.isLetter() } && token == token.uppercase())
                    token.lowercase().replaceFirstChar { it.uppercase() } // German nouns are capitalised
                else token
        }
}
