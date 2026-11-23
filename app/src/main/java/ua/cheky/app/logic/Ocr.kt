package ua.cheky.app.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/** On-device text recognition with ML Kit (works offline). */
object Ocr {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun rows(context: Context, uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val text = client.process(image).await()
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            l.boundingBox?.let { OcrLine(l.text, it.left, it.top, it.bottom) }
        }
        return ReceiptParser.groupRows(lines)
    }
}

object Images {
    /** Decodes a photo, applies EXIF rotation and scales the longest side down to [maxSide]. */
    fun load(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Не вдалося відкрити фото")
        val rotation = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (e: Exception) {
            0
        }
        val scale = min(1f, maxSide.toFloat() / max(bmp.width, bmp.height))
        if (scale == 1f && rotation == 0) return bmp
        val m = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    fun jpeg(context: Context, uri: Uri, maxSide: Int = 1600): ByteArray =
        ByteArrayOutputStream().also { load(context, uri, maxSide).compress(Bitmap.CompressFormat.JPEG, 85, it) }
            .toByteArray()
}
