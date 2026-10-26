package app.flood.wallpaper.audio

/**
 * Beats run only while Flood or its wallpaper is on screen.
 * The microphone service, when that option is on, stays in the foreground so
 * Android does not have to start it again from the background.
 */
object BeatGate {
    @Volatile
    var wallpaperVisible: Boolean = false

    @Volatile
    var activityVisible: Boolean = false

    @Volatile
    var screenOn: Boolean = true

    fun audible(): Boolean = screenOn && (wallpaperVisible || activityVisible)
}
