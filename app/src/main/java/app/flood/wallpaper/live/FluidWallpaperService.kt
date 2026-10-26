package app.flood.wallpaper.live

import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import app.flood.wallpaper.LookSettings
import app.flood.wallpaper.SettingsStore
import app.flood.wallpaper.audio.BeatGate
import app.flood.wallpaper.audio.BeatSync
import app.flood.wallpaper.media.PlaybackHub
import app.flood.wallpaper.render.EglCore
import app.flood.wallpaper.render.FlowClock
import app.flood.wallpaper.render.FluidRenderer
import app.flood.wallpaper.render.FrameRate

class FluidWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = FluidEngine()

    private inner class FluidEngine : Engine() {
        private var thread: GlThread? = null
        private var look: LookController? = null
        private val displayManager: DisplayManager? =
            applicationContext.getSystemService(DisplayManager::class.java)
        private var displayListener: DisplayManager.DisplayListener? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Wallpaper surfaces on this ROM reject setFixedSize. The renderer
            // draws into a smaller buffer and scales it onto this surface.
            look = LookController(applicationContext)
            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit
                override fun onDisplayRemoved(displayId: Int) = Unit
                override fun onDisplayChanged(displayId: Int) {
                    thread?.refreshHz = displayRefreshHz()
                }
            }
            displayListener = listener
            displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            val density = applicationContext.resources.displayMetrics.density
            val refresh = displayRefreshHz()
            val existing = thread
            if (existing == null || !existing.isAlive) {
                thread = GlThread(holder.surface, width, height, density, refresh) {
                    look?.settings ?: LookSettings()
                }.also { it.start() }
            } else {
                existing.width = width
                existing.height = height
                existing.density = density
                existing.refreshHz = refresh
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopThread()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            thread?.visible = visible
            BeatGate.wallpaperVisible = visible
            BeatSync.sync(applicationContext)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            thread?.pan = xOffset - 0.5f
        }

        override fun onDestroy() {
            BeatGate.wallpaperVisible = false
            BeatSync.sync(applicationContext)
            displayListener?.let { displayManager?.unregisterDisplayListener(it) }
            displayListener = null
            stopThread()
            look?.close()
            look = null
            super.onDestroy()
        }

        private fun displayRefreshHz(): Float {
            val rate = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: return 60f
            return if (rate.isFinite() && rate >= 1f) rate else 60f
        }

        private fun stopThread() {
            val running = thread
            thread = null
            running?.shutdown()
        }
    }

    private class LookController(context: android.content.Context) {
        private val prefs = SettingsStore.prefs(context)

        @Volatile
        var settings: LookSettings = SettingsStore.read(prefs)

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (SettingsStore.isPresetKey(key)) return@OnSharedPreferenceChangeListener
            settings = SettingsStore.read(prefs)
        }

        init {
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }

        fun close() {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private class GlThread(
        private val surface: Surface,
        width: Int,
        height: Int,
        density: Float,
        initialRefreshHz: Float,
        private val settings: () -> LookSettings,
    ) : Thread("FloodGL") {
        @Volatile var running = true
        @Volatile var visible = true
        @Volatile var width = width
        @Volatile var height = height
        @Volatile var density = density
        @Volatile var pan = 0f
        @Volatile var refreshHz = initialRefreshHz

        private var requestedRate = Float.NaN

        override fun run() {
            var egl: EglCore? = null
            var renderer: FluidRenderer? = null
            try {
                egl = EglCore(surface)
                egl.makeCurrent()
                renderer = FluidRenderer().also {
                    it.onGlReady()
                    it.resize(width, height, density)
                }
                val clock = FlowClock()
                var last = System.nanoTime()
                while (running && surface.isValid) {
                    if (!visible || width <= 0 || height <= 0) {
                        requestRate(0f)
                        sleepQuietly(40)
                        last = System.nanoTime()
                        continue
                    }
                    val look = settings()
                    val pulse = PlaybackHub.pulse
                    val rate = FrameRate.target(refreshHz, look.motion, pulse, look.beats)
                    requestRate(rate)
                    val now = System.nanoTime()
                    val dt = ((now - last) / 1_000_000_000.0).toFloat()
                    last = now
                    val time = clock.advance(dt, look.motion, pulse, look.beatStrength)
                    val started = System.nanoTime()
                    renderer.resize(width, height, density)
                    renderer.frame(time, look, pan)
                    if (!egl.swap()) break
                    // Swap already waits for the next refresh. Sleep only while the
                    // picture is still, in short slices, so a beat or a slider
                    // resumes on the next slice. A sleep on the moving path lands
                    // between refreshes and the motion judders.
                    if (FrameRate.idle(look.motion, pulse, look.beats)) {
                        waitOutIdle(rate, (System.nanoTime() - started) / 1_000_000L)
                        last = System.nanoTime()
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Wallpaper render stopped", error)
            } finally {
                try {
                    renderer?.release()
                } catch (_: Exception) {
                }
                egl?.release()
            }
        }

        private fun requestRate(rate: Float) {
            if (rate == requestedRate) return
            requestedRate = rate
            if (Build.VERSION.SDK_INT < 30) return
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    surface.setFrameRate(
                        rate,
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                        Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
                    )
                } else {
                    surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                }
            } catch (_: RuntimeException) {
                // A rejected hint must not stop the wallpaper.
            }
        }

        private fun waitOutIdle(rateHz: Float, alreadyMs: Long) {
            val periodMs = (1000f / rateHz.coerceAtLeast(1f)).toLong().coerceIn(16L, 1000L)
            var left = periodMs - alreadyMs
            while (left > 0L && running && visible) {
                val look = settings()
                if (!FrameRate.idle(look.motion, PlaybackHub.pulse, look.beats)) return
                val slice = minOf(16L, left)
                sleepQuietly(slice)
                left -= slice
            }
        }

        fun shutdown() {
            running = false
            // A 1 Hz present can block inside swap. Ask for the panel rate so
            // the thread notices the stop without waiting out that second.
            val wake = if (refreshHz.isFinite() && refreshHz >= 1f) minOf(refreshHz, FrameRate.CAP_HZ) else 60f
            requestRate(wake)
            interrupt()
            try {
                join(2000)
            } catch (_: InterruptedException) {
                interrupt()
            }
        }

        private fun sleepQuietly(millis: Long) {
            try {
                sleep(millis)
            } catch (_: InterruptedException) {
                interrupt()
            }
        }
    }

    companion object {
        private const val TAG = "Flood"
    }
}
