@file:OptIn(ExperimentalMaterial3Api::class)

package ua.cheky.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.cheky.app.logic.Images
import ua.cheky.app.model.Categories
import ua.cheky.app.model.Item
import ua.cheky.app.model.Receipt
import ua.cheky.app.model.eur
import ua.cheky.app.model.round2
import java.io.File
import kotlin.math.abs

@Composable
fun ScanScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let { vm.addPhotos(listOf(it)) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.addPhotos(uris)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle(
                "Новий чек",
                "Сфотографуйте німецький чек — товари з'являться українською" +
                    if (vm.preciseMode) " (точний режим Claude)" else "",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val uri = newPhotoUri(ctx)
                        pendingUri = uri
                        camera.launch(uri)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("📷  Камера") }
                FilledTonalButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Text("🖼  Галерея")
                }
            }
        }
        if (vm.photos.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.photos) { Thumb(it) }
                    }
                    Text(
                        "Довгий чек? Додайте ще фото — частини об'єднаються.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = vm::analyze, enabled = !vm.busy) {
                            if (vm.busy) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Розпізнати і перекласти")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = vm::clearScan, enabled = !vm.busy) { Text("Очистити") }
                    }
                }
            }
        }
        vm.status?.let { msg ->
            item {
                Text(
                    msg,
                    color = if (vm.statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val d = vm.draft
        if (d != null) {
            item { DraftHeader(d, vm) }
            item {
                SectionCard {
                    d.items.forEachIndexed { i, itm ->
                        ItemRow(itm) { editIndex = i }
                        if (i < d.items.lastIndex) HorizontalDivider()
                    }
                    TextButton(onClick = { editIndex = vm.addItem() }) { Text("+ Додати позицію") }
                }
            }
            item { DraftTotals(d, vm) }
            item {
                SectionCard("За категоріями") {
                    Bars(d.items.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.price } })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = vm::saveDraft, enabled = !vm.draftSaved) {
                        Text(if (vm.draftSaved) "✓ Збережено" else "Зберегти чек")
                    }
                    if (vm.draftSaved) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = vm::clearScan) { Text("Новий чек") }
                    }
                }
            }
        } else if (vm.photos.isEmpty()) {
            item { EmptyHint() }
        }
    }

    editIndex?.let { i ->
        vm.draft?.items?.getOrNull(i)?.let { item ->
            ItemEditDialog(
                item = item,
                onDismiss = { editIndex = null },
                onSave = { vm.updateItem(i, it); editIndex = null },
                onDelete = { vm.removeItem(i); editIndex = null },
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    SectionCard("Як це працює") {
        Text("1. Зніміть чек рівно, при хорошому світлі, щоб він займав увесь кадр.")
        Text("2. Натисніть «Розпізнати» — товари перекладуться українською і розкладуться по категоріях.")
        Text("3. Виправте назву, якщо треба (торкніться рядка) — додаток запам'ятає ваш переклад.")
        Text("4. Збережіть — підсумок витрат у вкладці «Підсумок».")
    }
}

@Composable
private fun DraftHeader(d: Receipt, vm: AppViewModel) {
    SectionCard {
        OutlinedTextField(
            value = d.store, onValueChange = { vm.updateHeader(store = it) },
            label = { Text("Магазин") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = d.date, onValueChange = { vm.updateHeader(date = it) },
            label = { Text("Дата (РРРР-ММ-ДД)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ItemRow(item: Item, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.uk, fontWeight = FontWeight.SemiBold)
            if (item.de.isNotBlank()) {
                Text(item.de, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                eur(item.price), fontWeight = FontWeight.SemiBold,
                color = if (item.price < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (item.qty != 1.0) {
                Text(
                    "× ${fmtQty(item.qty)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DraftTotals(d: Receipt, vm: AppViewModel) {
    SectionCard {
        Row(Modifier.fillMaxWidth()) {
            Text("Разом", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(eur(d.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        if (abs(d.itemsSum - d.total) > 0.05) {
            Text(
                "⚠️ Сума позицій ${eur(d.itemsSum)} не збігається з підсумком чека. Перевірте рядки або фото.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { vm.updateHeader(total = d.itemsSum) }) { Text("Взяти суму позицій") }
        }
    }
}

@Composable
private fun ItemEditDialog(item: Item, onDismiss: () -> Unit, onSave: (Item) -> Unit, onDelete: () -> Unit) {
    var uk by remember { mutableStateOf(item.uk) }
    var de by remember { mutableStateOf(item.de) }
    var price by remember { mutableStateOf(if (item.price == 0.0) "" else "%.2f".format(item.price).replace('.', ',')) }
    var qty by remember { mutableStateOf(fmtQty(item.qty)) }
    var category by remember { mutableStateOf(item.category) }
    var menu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Позиція") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(uk, { uk = it }, label = { Text("Назва українською") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(de, { de = it }, label = { Text("Назва з чека (німецькою)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        price, { price = it }, label = { Text("Ціна €") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        qty, { qty = it }, label = { Text("К-сть") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(category) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Categories.ALL.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { category = c; menu = false })
                        }
                    }
                }
                Text("Знижки вводьте з мінусом, напр. -0,50", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    item.copy(
                        uk = uk.trim().ifBlank { de.trim() },
                        de = de.trim(),
                        price = round2(price.replace(',', '.').trim().toDoubleOrNull() ?: 0.0),
                        qty = qty.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0,
                        category = category,
                    )
                )
            }) { Text("Зберегти") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Скасувати") }
            }
        },
    )
}

@Composable
private fun Thumb(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { Images.load(ctx, uri, 300).asImageBitmap() }.getOrNull()
        }
    }
    Box(
        Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("🧾", textAlign = TextAlign.Center)
    }
}

fun fmtQty(q: Double): String =
    if (q == q.toLong().toDouble()) q.toLong().toString() else "%.3f".format(q).trimEnd('0').replace('.', ',')

private fun newPhotoUri(ctx: Context): Uri {
    val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
}
