package ua.cheky.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ua.cheky.app.model.Receipt
import ua.cheky.app.model.receiptFromJson
import ua.cheky.app.model.toJson
import java.io.File

/** Stores receipts and learned translations as small JSON files in app storage. */
class Repository(context: Context) {
    private val dir = context.filesDir
    private val receiptsFile = File(dir, "receipts.json")
    private val learnedFile = File(dir, "learned.json")

    fun load(): List<Receipt> = try {
        if (!receiptsFile.exists()) emptyList()
        else JSONArray(receiptsFile.readText()).let { arr ->
            (0 until arr.length()).map { receiptFromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Receipt>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        writeAtomic(receiptsFile, arr.toString())
    }

    /** German name (normalised) -> Ukrainian name the user corrected by hand. */
    fun loadLearned(): Map<String, String> = try {
        if (!learnedFile.exists()) emptyMap()
        else JSONObject(learnedFile.readText()).let { o ->
            o.keys().asSequence().associateWith { o.getString(it) }
        }
    } catch (e: Exception) {
        emptyMap()
    }

    fun saveLearned(map: Map<String, String>) {
        writeAtomic(learnedFile, JSONObject(map).toString())
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        fun key(de: String) = de.uppercase().trim().replace(Regex("\\s+"), " ")
    }
}
