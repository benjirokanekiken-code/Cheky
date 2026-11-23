package ua.cheky.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import ua.cheky.app.data.Repository
import ua.cheky.app.logic.Categorizer
import ua.cheky.app.logic.ClaudeClient
import ua.cheky.app.logic.DeUkTranslator
import ua.cheky.app.logic.Images
import ua.cheky.app.logic.Ocr
import ua.cheky.app.logic.ReceiptParser
import ua.cheky.app.logic.ReceiptText
import ua.cheky.app.model.Categories
import ua.cheky.app.model.Item
import ua.cheky.app.model.Receipt
import ua.cheky.app.model.itemFromJson
import ua.cheky.app.model.num
import ua.cheky.app.model.round2
import ua.cheky.app.model.str
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val repo = Repository(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val learned = repo.loadLearned().toMutableMap()
    private val editedNames = mutableSetOf<String>()

    var receipts by mutableStateOf(repo.load()); private set
    var photos by mutableStateOf<List<Uri>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf<String?>(null); private set
    var statusIsError by mutableStateOf(false); private set
    var draft by mutableStateOf<Receipt?>(null); private set
    var draftSaved by mutableStateOf(false); private set
    var apiKey by mutableStateOf(prefs.getString("api_key", "").orEmpty()); private set

    var translation by mutableStateOf(""); private set
    var translating by mutableStateOf(false); private set
    var modelStatus by mutableStateOf<String?>(null); private set

    val preciseMode: Boolean get() = apiKey.isNotBlank()

    // ---------- photos & recognition ----------

    fun addPhotos(uris: List<Uri>) {
        if (draftSaved) { draft = null; draftSaved = false }
        photos = photos + uris
        setStatus(null)
    }

    fun clearScan() {
        photos = emptyList(); draft = null; draftSaved = false; setStatus(null)
    }

    fun analyze() {
        if (photos.isEmpty() || busy) return
        viewModelScope.launch {
            busy = true
            setStatus(null)
            try {
                val r = if (preciseMode) analyzeWithClaude() else analyzeOffline()
                draft = r
                draftSaved = false
                editedNames.clear()
                if (r.items.isEmpty()) setStatus("Не знайшов товарів. Спробуйте рівніше і світліше фото.", true)
                else setStatus(null)
            } catch (e: Exception) {
                setStatus("Помилка: ${e.message ?: "не вдалося розпізнати чек"}", true)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun analyzeOffline(): Receipt {
        setStatus("Розпізнаю текст…")
        val parts = photos.map { ReceiptParser.parse(Ocr.rows(ctx, it)) }
        val raw = parts.flatMap { it.items }
        val modelOk = try {
            setStatus("Готую перекладач…"); DeUkTranslator.ensureModel(); true
        } catch (e: Exception) {
            false
        }
        val items = raw.mapIndexed { i, r ->
            setStatus("Перекладаю ${i + 1} з ${raw.size}…")
            val uk = learned[Repository.key(r.name)]
                ?: if (modelOk) runCatching { DeUkTranslator.translateProduct(r.name) }.getOrElse { ReceiptText.expand(r.name) }
                else ReceiptText.expand(r.name)
            Item(r.name, uk, r.qty, r.price, Categorizer.categorize(r.name))
        }
        if (!modelOk) setStatus("Переклад недоступний без інтернету при першому запуску — назви залишено німецькою.", true)
        val total = parts.mapNotNull { it.total }.lastOrNull() ?: items.sumOf { it.price }
        return Receipt(
            id = newId(),
            store = parts.firstNotNullOfOrNull { it.store } ?: "Магазин",
            date = parts.firstNotNullOfOrNull { it.date } ?: LocalDate.now().toString(),
            total = round2(total),
            items = items,
        )
    }

    private suspend fun analyzeWithClaude(): Receipt {
        setStatus("Надсилаю фото в Claude…")
        val images = withContext(Dispatchers.IO) { photos.map { Images.jpeg(ctx, it) } }
        val o = ClaudeClient.parseReceipt(apiKey, images)
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).map { itemFromJson(arr.getJSONObject(it)) }
            .map { item -> learned[Repository.key(item.de)]?.let { item.copy(uk = it) } ?: item }
        val date = o.str("date").takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) } ?: LocalDate.now().toString()
        return Receipt(
            id = newId(),
            store = o.str("store").ifBlank { "Магазин" },
            date = date,
            total = o.num("total").takeIf { it != 0.0 } ?: round2(items.sumOf { it.price }),
            items = items,
        )
    }

    // ---------- editing the draft ----------

    fun updateItem(index: Int, item: Item) {
        val d = draft ?: return
        val old = d.items.getOrNull(index) ?: return
        if (old.uk != item.uk && item.de.isNotBlank()) editedNames += item.de
        draft = d.copy(items = d.items.toMutableList().also { it[index] = item })
        draftSaved = false
    }

    fun removeItem(index: Int) {
        val d = draft ?: return
        draft = d.copy(items = d.items.filterIndexed { i, _ -> i != index })
        draftSaved = false
    }

    fun addItem(): Int {
        val d = draft ?: Receipt(newId(), "Магазин", LocalDate.now().toString(), 0.0, emptyList())
        draft = d.copy(items = d.items + Item("", "Новий товар", 1.0, 0.0, Categories.OTHER))
        draftSaved = false
        return draft!!.items.lastIndex
    }

    fun updateHeader(store: String? = null, date: String? = null, total: Double? = null) {
        val d = draft ?: return
        draft = d.copy(store = store ?: d.store, date = date ?: d.date, total = total ?: d.total)
        draftSaved = false
    }

    fun saveDraft() {
        val d = draft ?: return
        val list = receipts.filter { it.id != d.id } + d
        repo.save(list)
        receipts = list
        d.items.filter { it.de in editedNames }.forEach { learned[Repository.key(it.de)] = it.uk }
        if (editedNames.isNotEmpty()) repo.saveLearned(learned)
        editedNames.clear()
        draftSaved = true
    }

    fun openReceipt(r: Receipt) {
        photos = emptyList(); draft = r; draftSaved = true; setStatus(null)
    }

    fun deleteReceipt(id: String) {
        receipts = receipts.filter { it.id != id }
        repo.save(receipts)
        if (draft?.id == id) { draft = null; draftSaved = false }
    }

    fun deleteAll() {
        receipts = emptyList(); repo.save(receipts); draft = null
    }

    // ---------- translator ----------

    fun translate(text: String) {
        if (text.isBlank() || translating) return
        viewModelScope.launch {
            translating = true
            translation = ""
            translation = try {
                if (preciseMode) ClaudeClient.translate(apiKey, text) else DeUkTranslator.translate(text)
            } catch (e: Exception) {
                "Не вдалося перекласти: ${e.message ?: "немає з'єднання"}"
            }
            translating = false
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            modelStatus = "Завантажую словник…"
            modelStatus = try {
                DeUkTranslator.ensureModel(); "✓ Словник завантажено — переклад працює офлайн"
            } catch (e: Exception) {
                "Не вдалося завантажити: ${e.message}"
            }
        }
    }

    // ---------- settings ----------

    fun saveApiKey(key: String) {
        apiKey = key.trim()
        prefs.edit().putString("api_key", apiKey).apply()
    }

    private fun setStatus(msg: String?, error: Boolean = false) {
        status = msg; statusIsError = error
    }

    private fun newId() = "r" + System.currentTimeMillis().toString(36)
}
