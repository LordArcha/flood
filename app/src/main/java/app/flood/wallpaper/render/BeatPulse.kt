package app.flood.wallpaper.render

import kotlin.math.exp

/**
 * A short kick in the fluid when a beat is detected.
 * It fades in a fraction of a second. Strength 0 does not kick.
 */
internal class BeatPulse {
    private var kick = 0f
    private var seen = 0

    fun advance(dt: Float, beatMark: Int, strength: Float): Float {
        if (beatMark != seen) {
            seen = beatMark
            kick = strength.coerceIn(0f, 2f) / 2f
        }
        val step = dt.coerceIn(0f, 0.05f)
        kick *= exp(-step / 0.16f)
        if (kick < 0.001f) kick = 0f
        return kick
    }
}
