package ua.cheky.app.logic

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ua.cheky.app.model.Categories
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional "precise mode": sends the receipt photo to the Claude API.
 * Only used when the user has entered their own API key in Settings.
 */
object ClaudeClient {
    private const val MODEL = "claude-sonnet-5-5"

    private val RECEIPT_PROMPT = """
Ти — помічник, що розбирає німецькі касові чеки (Aldi, Lidl, Rewe, Edeka, Kaufland, Penny, Netto, dm, Rossmann тощо) і перекладає їх українською.
Витягни всі товари з чека (якщо фото кілька — це частини одного чека по порядку).
Правила:
- Розшифровуй скорочення (напр. "H-MILCH" = ультрапастеризоване молоко, "SCHW.KOTEL." = свиняча котлета).
- "uk" — природна українська назва товару, коротко (2–5 слів), з об'ємом/вагою якщо є.
- price — підсумкова сума за рядок у євро (число). Якщо є "2 x 1,19", то qty=2, price=2.38.
- Знижки (Rabatt, Preisvorteil, Coupon) — окремий рядок з від'ємною ціною, категорія "Знижки".
- Pfand — категорія "Застава (Pfand)"; Leergut/Pfandrückgabe — від'ємна ціна.
- Не включай Summe, Zwischensumme, MwSt, Bar, EC-Karte, Rückgeld.
- category — одна з: ${Categories.ALL.joinToString(", ")}.
- date — YYYY-MM-DD або null. store — назва магазину. total — сума "Summe"/"zu zahlen".
Поверни ЛИШЕ JSON: {"store":string,"date":string|null,"total":number,"items":[{"de":string,"uk":string,"qty":number,"price":number,"category":string}]}
""".trimIndent()

    suspend fun parseReceipt(apiKey: String, images: List<ByteArray>): JSONObject = withContext(Dispatchers.IO) {
        val content = JSONArray()
        images.forEach { bytes ->
            content.put(
                JSONObject().put("type", "image").put(
                    "source", JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                )
            )
        }
        content.put(JSONObject().put("type", "text").put("text", RECEIPT_PROMPT))
        val text = call(apiKey, content, 4096)
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("Claude не повернув список товарів")
        JSONObject(text.substring(start, end + 1))
    }

    suspend fun translate(apiKey: String, text: String): String = withContext(Dispatchers.IO) {
        val prompt = "Переклади з німецької на українську. Якщо це назва товару або склад продукту — дай природний переклад, " +
            "коротко поясни незрозумілі терміни, а алергени (молоко, глютен, горіхи, соя, яйця тощо) познач окремим рядком " +
            "\"Алергени: …\". Відповідай лише українською, без вступу.\n\nТекст:\n$text"
        call(apiKey, JSONArray().put(JSONObject().put("type", "text").put("text", prompt)), 1500).trim()
    }

    private fun call(apiKey: String, content: JSONArray, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("x-api-key", apiKey.trim())
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val resp = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(resp).getJSONObject("error").getString("message") }
                    .getOrDefault("HTTP $code")
                throw IOException(if (code == 401) "Невірний API-ключ" else msg)
            }
            val arr = JSONObject(resp).getJSONArray("content")
            return (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optString("type") == "text" }
                .joinToString("") { it.getString("text") }
        } finally {
            c.disconnect()
        }
    }
}
