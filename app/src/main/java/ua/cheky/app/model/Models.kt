package ua.cheky.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.round

data class Item(
    val de: String,
    val uk: String,
    val qty: Double = 1.0,
    val price: Double,
    val category: String,
)

data class Receipt(
    val id: String,
    val store: String,
    val date: String, // YYYY-MM-DD
    val total: Double,
    val items: List<Item>,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val itemsSum: Double get() = round2(items.sumOf { it.price })
}

object Categories {
    const val DAIRY = "Молочні продукти"
    const val MEAT = "М'ясо і риба"
    const val VEG = "Овочі і фрукти"
    const val BREAD = "Хліб і випічка"
    const val GROCERY = "Бакалія"
    const val DRINKS = "Напої"
    const val SWEETS = "Солодощі і снеки"
    const val FROZEN = "Заморожені продукти"
    const val HOUSEHOLD = "Побутова хімія"
    const val HYGIENE = "Гігієна і косметика"
    const val PFAND = "Застава (Pfand)"
    const val DISCOUNT = "Знижки"
    const val OTHER = "Інше"

    val ALL = listOf(
        DAIRY, MEAT, VEG, BREAD, GROCERY, DRINKS, SWEETS, FROZEN,
        HOUSEHOLD, HYGIENE, PFAND, DISCOUNT, OTHER,
    )
}

fun round2(v: Double): Double = round(v * 100) / 100

fun eur(v: Double): String = String.format(Locale.GERMANY, "%.2f €", v)

/** Reads a number that may come as a JSON number or as a string like "1,19". */
fun JSONObject.num(key: String, def: Double = 0.0): Double = when (val v = opt(key)) {
    is Number -> v.toDouble().takeUnless { it.isNaN() } ?: def
    is String -> v.replace(',', '.').trim().toDoubleOrNull() ?: def
    else -> def
}

fun JSONObject.str(key: String): String =
    if (isNull(key)) "" else optString(key, "")

fun Item.toJson(): JSONObject = JSONObject()
    .put("de", de).put("uk", uk).put("qty", qty).put("price", price).put("category", category)

fun itemFromJson(o: JSONObject): Item {
    val de = o.str("de")
    val qty = o.num("qty", 1.0).let { if (it <= 0) 1.0 else it }
    val cat = o.str("category").let { if (it in Categories.ALL) it else Categories.OTHER }
    return Item(de, o.str("uk").ifBlank { de }, qty, o.num("price"), cat)
}

fun Receipt.toJson(): JSONObject = JSONObject()
    .put("id", id).put("store", store).put("date", date).put("total", total)
    .put("createdAt", createdAt)
    .put("items", JSONArray().also { arr -> items.forEach { arr.put(it.toJson()) } })

fun receiptFromJson(o: JSONObject): Receipt {
    val arr = o.optJSONArray("items") ?: JSONArray()
    return Receipt(
        id = o.str("id").ifBlank { "r" + System.nanoTime() },
        store = o.str("store").ifBlank { "Магазин" },
        date = o.str("date"),
        total = o.num("total"),
        items = (0 until arr.length()).map { itemFromJson(arr.getJSONObject(it)) },
        createdAt = o.optLong("createdAt", 0L),
    )
}
