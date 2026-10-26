package app.flood.wallpaper

import android.app.Application
import app.flood.wallpaper.audio.BeatSync
import app.flood.wallpaper.media.CoverStore

class FloodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoverStore.restore(this)
        PictureStore.restore(this)
        BeatSync.install(this)
    }
}
