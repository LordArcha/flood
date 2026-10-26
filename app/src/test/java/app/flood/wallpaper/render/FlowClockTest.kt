package app.flood.wallpaper.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowClockTest {
    @Test
    fun quietMotionMatchesTheFrameStep() {
        val clock = FlowClock()
        val time = clock.advance(0.016f, motion = 1f, pulse = 0f, strength = 1f)
        assertEquals(0.016f, time, 0.0001f)
    }

    @Test
    fun aBeatSpeedsTheClockForward() {
        val quiet = FlowClock().advance(0.016f, motion = 1f, pulse = 0f, strength = 1f)
        val stirred = FlowClock().advance(0.016f, motion = 1f, pulse = 1f, strength = 1f)
        assertTrue(stirred > quiet * 1.5f)
    }

    @Test
    fun strengthScalesTheSurge() {
        val soft = FlowClock().advance(0.016f, motion = 1f, pulse = 1f, strength = 0.2f)
        val hard = FlowClock().advance(0.016f, motion = 1f, pulse = 1f, strength = 1.5f)
        assertTrue(hard > soft * 1.8f)
    }

    @Test
    fun zeroStrengthIgnoresTheBeat() {
        val quiet = FlowClock().advance(0.016f, motion = 1f, pulse = 0f, strength = 1.5f)
        val muted = FlowClock().advance(0.016f, motion = 1f, pulse = 1f, strength = 0f)
        assertEquals(quiet, muted, 0.0001f)
    }

    @Test
    fun timeDoesNotRunBackwardAsTheBeatFades() {
        val clock = FlowClock()
        var previous = 0f
        val pulses = floatArrayOf(1f, 0.7f, 0.4f, 0.1f, 0f)
        for (pulse in pulses) {
            val time = clock.advance(0.016f, motion = 0.8f, pulse = pulse, strength = 1f)
            assertTrue(time >= previous)
            previous = time
        }
    }

    @Test
    fun aLongGapDoesNotTeleportTheField() {
        val clock = FlowClock()
        val time = clock.advance(2f, motion = 1.8f, pulse = 1f, strength = 1.6f)
        assertTrue(time < 0.5f)
    }
}
