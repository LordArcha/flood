package app.flood.wallpaper.audio

import app.flood.wallpaper.media.PlaybackHub
import kotlin.math.exp

/**
 * Turns a stream of loudness samples into a short pulse on each sudden rise.
 * A steady tone does not keep retriggering once the running average catches up.
 */
class OnsetTracker {
    private var average = 0.02f
    private var lastBeatAt = -10_000L

    var pulse: Float = 0f
        private set

    var energy: Float = 0f
        private set

    fun update(rms: Float, nowMs: Long, sensitivity: Float): Float {
        val sens = sensitivity.coerceIn(0.4f, 2.2f)
        average += (rms - average) * 0.08f
        val threshold = average * (2.15f / sens) + 0.012f
        energy = ((rms - average * 0.85f) / (average + 0.03f)).coerceIn(0f, 1f)
        if (rms > threshold && rms > 0.02f && nowMs - lastBeatAt > 200) {
            pulse = 1f
            lastBeatAt = nowMs
            PlaybackHub.markBeat()
        } else if (pulse > 0f) {
            val age = (nowMs - lastBeatAt).coerceIn(0L, 8_000L)
            pulse = exp(-age / 1_000.0).toFloat()
            if (pulse < 0.02f) pulse = 0f
        }
        return pulse
    }
}

/**
 * Beat detector for the playback meter. That meter reports a normalized level
 * that only swells on a hit, so the pulse follows the rise instead of waiting
 * for the level to double.
 */
class LevelOnset {
    private var level = 0f
    private var lastBeatAt = -10_000L

    var pulse: Float = 0f
        private set

    var energy: Float = 0f
        private set

    fun update(rms: Float, nowMs: Long, sensitivity: Float): Float {
        val sens = sensitivity.coerceIn(0.4f, 2.2f)
        val previous = level
        level += (rms - level) * 0.5f
        val flux = level - previous
        // A smaller rise still counts. Sensitivity turns this down further.
        val need = 0.030f / sens
        energy = (rms / 0.45f).coerceIn(0f, 1f)
        if (flux > need && rms > 0.02f && nowMs - lastBeatAt > 240) {
            pulse = 1f
            lastBeatAt = nowMs
            PlaybackHub.markBeat()
        } else if (pulse > 0f) {
            pulse = playbackEnvelope(nowMs - lastBeatAt)
        }
        return pulse
    }
}

/** Holds a playback hit, then lets it ease off over several seconds. */
internal fun playbackEnvelope(ageMs: Long): Float {
    val age = ageMs.coerceIn(0L, 12_000L)
    val held = 300L
    val level = if (age <= held) 1.0 else exp(-(age - held) / 2_800.0)
    return if (level < 0.02) 0f else level.toFloat()
}
