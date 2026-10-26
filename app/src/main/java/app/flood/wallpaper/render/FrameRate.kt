package app.flood.wallpaper.render

/**
 * Picks a wallpaper frame rate for panels from 1 Hz through 120 Hz, including a
 * 60/90 Hz screen. Motion keeps the display rate, capped at 120. A still picture
 * with beats off can idle at 1 Hz. Beats stay at the display rate so a hit is
 * not waiting on a one-hertz present, and so the renderer does not sleep at a
 * fixed 30 Hz on a panel that ignores the hint.
 */
internal object FrameRate {
    const val CAP_HZ = 120f
    const val IDLE_HZ = 1f
    private const val STILL = 0.02f

    fun target(
        displayHz: Float,
        motion: Float,
        pulse: Float,
        beats: Boolean,
        visible: Boolean = true,
    ): Float {
        if (!visible) return 0f
        val display = if (displayHz.isFinite()) displayHz.coerceAtLeast(1f) else 60f
        if (!still(motion, pulse) || beats) return minOf(display, CAP_HZ)
        return IDLE_HZ
    }

    /** True only when a short wait cannot stutter the picture. */
    fun idle(motion: Float, pulse: Float, beats: Boolean): Boolean {
        return still(motion, pulse) && !beats
    }

    private fun still(motion: Float, pulse: Float): Boolean {
        return motion <= STILL && pulse <= STILL
    }
}
