package app.flood.wallpaper.render

/**
 * Advances the fluid clock. Beat strength is how hard a hit surges the speed.
 * The surge rises quickly and eases off. The clock never runs backward:
 * subtracting a fading beat from the shader time slides the picture back.
 */
internal class FlowClock {
    private var glide = 0f
    private var time = 0f

    fun advance(dt: Float, motion: Float, pulse: Float, strength: Float): Float {
        val step = dt.coerceIn(0f, 0.05f)
        val power = strength.coerceIn(0f, 1.6f)
        val target = power * pulse.coerceIn(0f, 1f)
        val rising = target > glide
        val blend = if (rising) (step * 36f).coerceIn(0f, 1f) else (step * 0.45f).coerceIn(0f, 1f)
        glide += (target - glide) * blend
        val advance = step * motion.coerceAtLeast(0f) * (1f + glide * 8.8f)
        time += advance.coerceAtMost(0.12f)
        return time
    }
}
