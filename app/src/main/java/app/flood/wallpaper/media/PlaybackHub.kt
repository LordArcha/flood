package app.flood.wallpaper.media

import android.graphics.Bitmap
import app.flood.wallpaper.art.CoverColors
import kotlinx.coroutines.flow.MutableStateFlow

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: Bitmap? = null,
    val colors: CoverColors = CoverColors.Default,
    val playing: Boolean = false,
    val artGeneration: Int = 0,
    val artHash: Int = 0,
)

object PlaybackHub {
    val state = MutableStateFlow(NowPlaying())

    @Volatile
    var energy: Float = 0f

    @Volatile
    var pulse: Float = 0f

    fun restore(title: String, artist: String, album: String, artwork: Bitmap, colors: CoverColors, artHash: Int) {
        state.value = NowPlaying(
            title = title,
            artist = artist,
            album = album,
            artwork = artwork,
            colors = colors,
            playing = false,
            artGeneration = 1,
            artHash = artHash,
        )
    }
}
