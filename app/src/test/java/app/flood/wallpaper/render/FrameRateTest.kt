package app.flood.wallpaper.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRateTest {
    @Test
    fun motionOnANinetyHertzPanelUsesThatRate() {
        assertEquals(90f, FrameRate.target(90f, motion = 0.74f, pulse = 0f, beats = false))
    }

    @Test
    fun motionOnASixtyHertzModeStaysThere() {
        assertEquals(60f, FrameRate.target(60f, motion = 0.74f, pulse = 0f, beats = true))
    }

    @Test
    fun motionCapsAtOneHundredTwenty() {
        assertEquals(120f, FrameRate.target(144f, motion = 1f, pulse = 0f, beats = true))
    }

    @Test
    fun aPulseUsesTheDisplayRateUpToTheCap() {
        assertEquals(90f, FrameRate.target(90f, motion = 0f, pulse = 0.5f, beats = false))
        assertEquals(120f, FrameRate.target(120f, motion = 0f, pulse = 0.5f, beats = false))
    }

    @Test
    fun aStillPictureWithoutBeatsIdlesAtOneHertz() {
        assertEquals(1f, FrameRate.target(120f, motion = 0f, pulse = 0f, beats = false))
        assertEquals(1f, FrameRate.target(90f, motion = 0.02f, pulse = 0f, beats = false))
    }

    @Test
    fun beatsStayReadyWhileThePictureIsStill() {
        assertEquals(90f, FrameRate.target(90f, motion = 0f, pulse = 0f, beats = true))
        assertFalse(FrameRate.idle(motion = 0f, pulse = 0f, beats = true))
    }

    @Test
    fun aHiddenWallpaperClearsTheRatePreference() {
        assertEquals(0f, FrameRate.target(90f, motion = 1f, pulse = 1f, beats = true, visible = false))
    }

    @Test
    fun idleIsOnlyAStillPictureWithBeatsOff() {
        assertTrue(FrameRate.idle(motion = 0f, pulse = 0f, beats = false))
        assertFalse(FrameRate.idle(motion = 0.74f, pulse = 0f, beats = false))
        assertFalse(FrameRate.idle(motion = 0f, pulse = 0.5f, beats = false))
    }
}
