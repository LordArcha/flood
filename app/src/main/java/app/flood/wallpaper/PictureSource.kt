package app.flood.wallpaper

import android.graphics.Bitmap
import app.flood.wallpaper.art.CoverColors
import app.flood.wallpaper.art.CoverSampler

internal data class HeldPicture(
    val bitmap: Bitmap,
    val colors: CoverColors,
    val hash: Int,
    val generation: Int,
)

/**
 * The photo chosen on this device. The preview and the wallpaper share it.
 * An older bitmap is left for the collector so a frame can finish sampling it.
 */
internal object PictureSource {
    @Volatile
    var held: HeldPicture? = null
        private set

    fun adopt(bitmap: Bitmap) {
        if (bitmap.isRecycled || bitmap.width < 1 || bitmap.height < 1) return
        val hash = CoverSampler.quickHash(bitmap)
        val previous = held
        if (previous != null && previous.hash == hash && !previous.bitmap.isRecycled) {
            if (previous.bitmap !== bitmap) bitmap.recycle()
            return
        }
        held = HeldPicture(
            bitmap = bitmap,
            colors = CoverSampler.sample(bitmap),
            hash = hash,
            generation = (previous?.generation ?: 0) + 1,
        )
    }

    fun clear() {
        held = null
    }
}
