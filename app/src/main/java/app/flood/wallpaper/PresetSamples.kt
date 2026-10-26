package app.flood.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import kotlin.math.max

/**
 * Photos chosen for presets, stored in the app. The look itself stays in
 * preferences. A slot with no photo has no file, so the chip stays a number.
 */
object PresetSamples {
    const val MAX_EDGE = 512

    fun file(context: Context, index: Int): File? {
        val name = sampleName(index) ?: return null
        return File(directory(context), name)
    }

    fun stamps(context: Context): List<Long> {
        return List(PresetSlots.COUNT) { index ->
            val image = file(context, index)
            if (image != null && image.exists()) image.lastModified() else 0L
        }
    }

    fun save(context: Context, index: Int, bitmap: Bitmap) {
        val image = file(context, index) ?: return
        writeJpeg(image, bitmap)
    }

    fun saveCurrent(context: Context, bitmap: Bitmap) {
        writeJpeg(currentFile(context), bitmap)
    }

    fun load(context: Context, index: Int, subsample: Int = 1): Bitmap? {
        val image = file(context, index) ?: return null
        return decode(image, subsample)
    }

    fun loadCurrent(context: Context): Bitmap? = decode(currentFile(context), subsample = 1)

    fun delete(context: Context, index: Int) {
        file(context, index)?.delete()
    }

    fun deleteCurrent(context: Context) {
        currentFile(context).delete()
    }

    internal fun sampleName(index: Int): String? {
        if (index !in 0 until PresetSlots.COUNT) return null
        return "slot_$index.jpg"
    }

    private fun currentFile(context: Context): File = File(directory(context), "current.jpg")

    private fun directory(context: Context): File {
        return File(context.applicationContext.filesDir, "preset_samples")
    }

    private fun writeJpeg(image: File, bitmap: Bitmap) {
        try {
            image.parentFile?.mkdirs()
            image.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, stream)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not save a preset picture", error)
        }
    }

    private fun decode(image: File, subsample: Int): Bitmap? {
        if (!image.exists()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = subsample.coerceAtLeast(1)
            }
            BitmapFactory.decodeFile(image.absolutePath, options)
        } catch (error: Exception) {
            Log.w(TAG, "Could not read a preset picture", error)
            null
        }
    }

    private const val TAG = "Flood"
}

internal fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    var sample = 1
    var edge = max(width, height)
    while (edge / sample > maxEdge) sample *= 2
    return sample
}
