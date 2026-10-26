package app.flood.wallpaper.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.flood.wallpaper.art.CoverSampler
import org.json.JSONObject
import java.io.File
import kotlin.math.max

object CoverStore {
    private const val TAG = "Flood"
    private const val IMAGE = "last_cover.jpg"
    private const val META = "last_meta.json"

    fun save(context: Context, bitmap: Bitmap, title: String, artist: String, album: String) {
        try {
            val scaled = scaleDown(bitmap, 512)
            File(context.filesDir, IMAGE).outputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 86, stream)
            }
            if (scaled !== bitmap) scaled.recycle()
            val json = JSONObject()
                .put("title", title.take(200))
                .put("artist", artist.take(200))
                .put("album", album.take(200))
            File(context.filesDir, META).writeText(json.toString())
        } catch (error: Exception) {
            Log.w(TAG, "Could not save the cover", error)
        }
    }

    fun restore(context: Context) {
        val image = File(context.filesDir, IMAGE)
        if (!image.exists()) return
        try {
            val bitmap = BitmapFactory.decodeFile(image.absolutePath) ?: return
            val metaFile = File(context.filesDir, META)
            val meta = if (metaFile.exists()) JSONObject(metaFile.readText()) else JSONObject()
            PlaybackHub.restore(
                title = meta.optString("title"),
                artist = meta.optString("artist"),
                album = meta.optString("album"),
                artwork = bitmap,
                colors = CoverSampler.sample(bitmap),
                artHash = CoverSampler.quickHash(bitmap),
            )
        } catch (error: Exception) {
            Log.w(TAG, "Could not restore the cover", error)
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= maxEdge) return bitmap
        val scale = maxEdge / edge.toFloat()
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
