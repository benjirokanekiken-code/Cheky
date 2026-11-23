package ua.cheky.app.logic

import kotlin.math.abs

data class OcrLine(val text: String, val left: Int, val top: Int, val bottom: Int) {
    val cy: Int get() = (top + bottom) / 2
    val h: Int get() = bottom - top
}

data class RawItem(val name: String, val qty: Double, val price: Double)

data class ParsedReceipt(
    val store: String?,
    val date: String?,
    val total: Double?,
    val items: List<RawItem>,
)

/**
 * Turns OCR output of a German supermarket receipt into item rows.
 * Works with the usual layout "NAME ..... 1,99 A" (Aldi, Lidl, Rewe, Edeka, Penny, Netto, dm…).
 */
object ReceiptParser {
    private val IC = RegexOption.IGNORE_CASE

    private val priceRe = Regex("""(-?\d{1,4}[.,]\d{2})\s*(-)?\s*(?:[A-Z*]{1,2}\d?)?\s*$""")
    private val qtyRe = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(?:stk\.?|st\.?|x|×|\*)\s*(?:x\s*)?(?:€|eur)?\s*(\d+[.,]\d{2})""", IC)
    private val weightRe = Regex("""^\s*(\d+[.,]\d{1,3})\s*kg\s*[x×*]\s*(?:€|eur)?\s*(\d+[.,]\d{2})""", IC)
    private val totalRe = Regex("""\b(summe|zu zahlen|gesamtbetrag|gesamt|total|zahlbetrag|endbetrag)\b""", IC)
    private val skipRe = Regex(
        """\b(mwst|ust|steuer|netto|brutto|bar|ec|karte|girocard|kartenzahlung|rückgeld|rueckgeld|gegeben|visa|mastercard|tse|beleg|kasse|bon|uhr|filiale|tel|www|danke|zwischensumme|posten|artikel|kundenbeleg|terminal|betrag|eur|euro|payback|punkte|seriennummer|transaktion|signatur)\b""",
        IC,
    )
    private val dateRe = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})\b""")
    private val discountRe = Regex("rabatt|preisvorteil|coupon|nachlass|gutschein|leergut|pfandrück|pfandrueck|pfandbon", IC)

    private val STORES = listOf(
        "ALDI" to "Aldi", "LIDL" to "Lidl", "REWE" to "Rewe", "EDEKA" to "Edeka",
        "KAUFLAND" to "Kaufland", "PENNY" to "Penny", "NETTO" to "Netto", "NORMA" to "Norma",
        "ROSSMANN" to "Rossmann", "DM-DROGERIE" to "dm", "DM DROGERIE" to "dm", "GLOBUS" to "Globus",
        "TEGUT" to "tegut", "MARKTKAUF" to "Marktkauf", "MÜLLER" to "Müller", "ACTION" to "Action",
        "DENNS" to "denn's", "ALNATURA" to "Alnatura", "FAMILA" to "Famila", "BUDNI" to "Budni",
        "NAH & GUT" to "Nah & Gut", "E CENTER" to "E center", "HIT " to "HIT",
    )

    fun isDiscount(name: String) = discountRe.containsMatchIn(name)

    /** Groups OCR lines that sit on the same horizontal row, left to right. */
    fun groupRows(lines: List<OcrLine>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val medH = lines.map { it.h }.sorted()[lines.size / 2].coerceAtLeast(1)
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (l in lines.sortedBy { it.cy }) {
            val last = rows.lastOrNull()
            if (last != null && abs(last.map { it.cy }.average() - l.cy) < medH * 0.55) last.add(l)
            else rows.add(mutableListOf(l))
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString("  ") { it.text } }
    }

    fun parse(rows: List<String>): ParsedReceipt {
        var store: String? = null
        var date: String? = null
        var total: Double? = null
        val items = mutableListOf<RawItem>()
        var pendingQty: Double? = null

        for (raw in rows) {
            val row = raw.trim()
            if (row.isEmpty()) continue
            val up = row.uppercase()

            if (store == null) store = STORES.firstOrNull { up.contains(it.first) }?.second
            if (date == null) dateRe.find(row)?.let { m ->
                val (d, mo, y) = m.destructured
                val year = if (y.length == 2) "20$y" else y
                if (mo.toInt() in 1..12 && d.toInt() in 1..31) {
                    date = "%s-%02d-%02d".format(year, mo.toInt(), d.toInt())
                }
            }
            if (total != null) continue // everything after the total is payment info

            if (totalRe.containsMatchIn(row) && !up.contains("ZWISCHEN")) {
                priceRe.find(row)?.let { total = num(it.groupValues[1]) }
                continue
            }

            val qm = qtyRe.find(row) ?: weightRe.find(row)
            if (qm != null) {
                val q = num(qm.groupValues[1])
                val unit = num(qm.groupValues[2])
                val last = items.lastOrNull()
                // "2 Stk x 1,19" printed under the item line (Rewe, Edeka)
                if (last != null && last.qty == 1.0 && abs(last.price - q * unit) < 0.06) {
                    items[items.lastIndex] = last.copy(qty = q)
                    continue
                }
                // quantity line printed above the item, or carrying the line price itself
                val tail = row.substring(qm.range.last + 1)
                val linePrice = priceRe.find(tail)?.groupValues?.get(1)?.let(::num)
                if (linePrice != null && last != null && last.price == 0.0) {
                    items[items.lastIndex] = last.copy(qty = q, price = linePrice)
                } else {
                    pendingQty = q
                }
                continue
            }

            if (skipRe.containsMatchIn(row) && !isDiscount(row) && !up.contains("PFAND")) continue

            val pm = priceRe.find(row)
            if (pm == null) {
                // a name without a price: the price may come on the weight line below
                val name = clean(row)
                if (name.count { it.isLetter() } >= 3 && items.size < 200 && looksLikeProduct(name)) {
                    items += RawItem(name, pendingQty ?: 1.0, 0.0)
                    pendingQty = null
                }
                continue
            }
            val name = clean(row.substring(0, pm.range.first))
            if (name.count { it.isLetter() } < 2) continue

            var price = num(pm.groupValues[1])
            if (pm.groupValues[2] == "-" && price > 0) price = -price
            if (isDiscount(name) && price > 0) price = -price
            items += RawItem(name, pendingQty ?: 1.0, price)
            pendingQty = null
        }
        // drop names that never got a price
        return ParsedReceipt(store, date, total, items.filter { it.price != 0.0 })
    }

    private fun looksLikeProduct(s: String) = s.uppercase() == s && s.length in 3..40

    private fun clean(s: String) = s
        .replace(Regex("""^\d{4,}\s*"""), "") // article numbers
        .replace(Regex("""\s{2,}"""), " ")
        .trim(' ', '*', '.', ':', '-')

    fun num(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0
}
