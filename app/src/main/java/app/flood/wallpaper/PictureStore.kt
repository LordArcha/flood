package app.flood.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import kotlin.math.max

/** Copies a device photo into the app and keeps the live plate in step with a preset slot. */
object PictureStore {
    fun restore(context: Context) {
        val bitmap = PresetSamples.loadCurrent(context) ?: return
        PictureSource.adopt(bitmap)
    }

    fun import(context: Context, uri: Uri, onReady: (Boolean) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val bitmap = decodeUri(app, uri)
            main.post {
                if (bitmap == null) {
                    onReady(false)
                    return@post
                }
                publish(app, bitmap)
                onReady(true)
            }
        }
    }

    /** Points the live plate at the photo stored with this slot. False when the slot has none. */
    fun useSlot(context: Context, index: Int): Boolean {
        val app = context.applicationContext
        val bitmap = PresetSamples.load(app, index)
        if (bitmap == null) {
            PictureSource.clear()
            PresetSamples.deleteCurrent(app)
            return false
        }
        publish(app, bitmap)
        return true
    }

    fun keepWithPreset(context: Context, index: Int) {
        val live = PictureSource.held?.bitmap ?: return
        PresetSamples.save(context, index, live)
    }

    private fun publish(context: Context, bitmap: Bitmap) {
        PictureSource.adopt(bitmap)
        val live = PictureSource.held?.bitmap ?: return
        PresetSamples.saveCurrent(context, live)
    }

    private fun decodeUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, PresetSamples.MAX_EDGE)
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            scaleDown(decoded, PresetSamples.MAX_EDGE)
        } catch (error: Exception) {
            Log.w(TAG, "Could not read the chosen picture", error)
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= maxEdge || edge == 0) return bitmap
        val scale = maxEdge / edge.toFloat()
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val TAG = "Flood"
}
