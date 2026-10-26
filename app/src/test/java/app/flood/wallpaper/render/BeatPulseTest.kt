package app.flood.wallpaper.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatPulseTest {
    @Test
    fun aDetectedBeatKicksThenFades() {
        val pulse = BeatPulse()
        val hit = pulse.advance(0.016f, beatMark = 1, strength = 2f)
        assertTrue(hit > 0.8f)
        var later = hit
        repeat(20) { later = pulse.advance(0.016f, beatMark = 1, strength = 2f) }
        assertTrue(later < hit * 0.25f)
    }

    @Test
    fun zeroStrengthDoesNotKick() {
        val pulse = BeatPulse()
        assertEquals(0f, pulse.advance(0.016f, beatMark = 1, strength = 0f), 0.0001f)
    }

    @Test
    fun theSameMarkDoesNotKickAgain() {
        val pulse = BeatPulse()
        pulse.advance(0.016f, beatMark = 1, strength = 2f)
        repeat(80) { pulse.advance(0.016f, beatMark = 1, strength = 2f) }
        assertEquals(0f, pulse.advance(0.016f, beatMark = 1, strength = 2f), 0.0001f)
    }
}
