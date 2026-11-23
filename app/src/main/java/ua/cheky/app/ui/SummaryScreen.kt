@file:OptIn(ExperimentalMaterial3Api::class)

package ua.cheky.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.cheky.app.model.Categories
import ua.cheky.app.model.Receipt
import ua.cheky.app.model.eur
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val PERIODS = listOf("month" to "Цей місяць", "prev" to "Минулий", "year" to "Цей рік", "all" to "Увесь час")
private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
fun SummaryScreen(vm: AppViewModel, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var period by rememberSaveable { mutableStateOf("month") }
    var details by remember { mutableStateOf<Receipt?>(null) }

    val list = remember(vm.receipts, period) {
        vm.receipts.filter { inPeriod(it.date, period) }
            .sortedWith(compareByDescending<Receipt> { it.date }.thenByDescending { it.createdAt })
    }
    val items = list.flatMap { it.items }
    val total = list.sumOf { it.total }
    val byCat = items.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.price } }
    val byShop = list.groupBy { it.store }.mapValues { e -> e.value.sumOf { it.total } }
    val top = items
        .filter { it.price > 0 && it.category != Categories.PFAND && it.category != Categories.DISCOUNT }
        .groupBy { it.uk.lowercase().trim() }
        .map { (_, g) -> Triple(g.first().uk, g.sumOf { it.qty }, g.sumOf { it.price }) }
        .sortedWith(compareByDescending<Triple<String, Double, Double>> { it.second }.thenByDescending { it.third })
        .take(10)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Підсумок", "Скільки і на що ми витратили") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PERIODS.forEach { (key, label) ->
                    FilterChip(selected = period == key, onClick = { period = key }, label = { Text(label) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi(eur(total), "витрачено", Modifier.weight(1f))
                Kpi(list.size.toString(), "чеків", Modifier.weight(1f))
                Kpi(eur(if (list.isEmpty()) 0.0 else total / list.size), "середній чек", Modifier.weight(1f))
            }
        }
        if (list.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        if (vm.receipts.isEmpty()) "Ще немає збережених чеків. Додайте перший у вкладці «Чек»."
                        else "За цей період чеків немає.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item { SectionCard("За категоріями") { Bars(byCat) } }
            item { SectionCard("За магазинами") { Bars(byShop) } }
            item {
                SectionCard("Найчастіші покупки") {
                    top.forEach { (name, n, sum) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(name, Modifier.weight(1f))
                            Text("${fmtQty(n)} × · ${eur(sum)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionCard("Чеки") {
                    list.forEachIndexed { i, r ->
                        Row(Modifier.fillMaxWidth().clickable { details = r }.padding(vertical = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(r.store, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${fmtDate(r.date)} · ${r.items.size} позицій",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(eur(r.total), fontWeight = FontWeight.Bold)
                        }
                        if (i < list.lastIndex) HorizontalDivider()
                    }
                }
            }
            item {
                OutlinedButton(onClick = {
                    val text = buildShareText(PERIODS.first { it.first == period }.second, total, list.size, byCat, top)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    ctx.startActivity(Intent.createChooser(send, "Поділитися підсумком"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Поділитися підсумком") }
            }
        }
    }

    details?.let { r ->
        ReceiptDialog(
            r,
            onDismiss = { details = null },
            onEdit = { vm.openReceipt(r); details = null; onOpen() },
            onDelete = { vm.deleteReceipt(r.id); details = null },
        )
    }
}

@Composable
private fun Kpi(value: String, label: String, modifier: Modifier) {
    Card(
        modifier, shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReceiptDialog(r: Receipt, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${r.store} · ${fmtDate(r.date)}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                r.items.forEach { it ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(it.uk, Modifier.weight(1f))
                        Text(eur(it.price))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("Разом", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(eur(r.total), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Редагувати") } },
        dismissButton = {
            Row {
                TextButton(onClick = { if (confirm) onDelete() else { confirm = true } }) {
                    Text(if (confirm) "Точно видалити?" else "Видалити", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Закрити") }
            }
        },
    )
}

private fun inPeriod(date: String, period: String): Boolean {
    if (period == "all") return true
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
    val now = LocalDate.now()
    return when (period) {
        "year" -> d.year == now.year
        "month" -> d.year == now.year && d.month == now.month
        "prev" -> now.minusMonths(1).let { d.year == it.year && d.month == it.month }
        else -> true
    }
}

private fun fmtDate(date: String) = runCatching { LocalDate.parse(date).format(DATE_FMT) }.getOrDefault(date)

private fun buildShareText(
    period: String, total: Double, count: Int,
    byCat: Map<String, Double>, top: List<Triple<String, Double, Double>>,
) = buildString {
    appendLine("🧾 Покупки — $period")
    appendLine("Витрачено: ${eur(total)} ($count чеків)")
    appendLine()
    appendLine("За категоріями:")
    byCat.entries.sortedByDescending { it.value }.forEach { appendLine("• ${it.key}: ${eur(it.value)}") }
    if (top.isNotEmpty()) {
        appendLine()
        appendLine("Найчастіше купуємо:")
        top.take(5).forEach { appendLine("• ${it.first} — ${fmtQty(it.second)} раз(и)") }
    }
}
