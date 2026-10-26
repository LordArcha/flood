package app.flood.wallpaper.ui

import android.content.Context
import android.opengl.GLSurfaceView
import app.flood.wallpaper.LookSettings
import app.flood.wallpaper.media.PlaybackHub
import app.flood.wallpaper.render.FlowClock
import app.flood.wallpaper.render.FluidRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class FluidPreviewView(context: Context) : GLSurfaceView(context) {
    val bridge = Bridge(resources.displayMetrics.density)

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(bridge)
        // One draw per refresh. Skipping a callback still swaps and shows a stale buffer.
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bridge.density = resources.displayMetrics.density
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }

    class Bridge(density: Float) : Renderer {
        @Volatile
        var settings = LookSettings()

        @Volatile
        var density = density

        private val fluid = FluidRenderer()
        private val clock = FlowClock()
        private var last = 0L
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            fluid.onGlReady()
            last = System.nanoTime()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceWidth = width
            surfaceHeight = height
            fluid.resize(width, height, density)
        }

        override fun onDrawFrame(gl: GL10?) {
            val now = System.nanoTime()
            if (last == 0L) last = now
            val dt = ((now - last) / 1_000_000_000.0).toFloat()
            last = now
            val look = settings
            val time = clock.advance(dt, look.motion, PlaybackHub.pulse, look.beatStrength)
            fluid.resize(surfaceWidth, surfaceHeight, density)
            fluid.frame(time, look, pan = 0f)
        }
    }
}
