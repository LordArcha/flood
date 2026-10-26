package app.flood.wallpaper.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnsetTrackerTest {
    @Test
    fun silenceDoesNotPulse() {
        val tracker = OnsetTracker()
        var now = 0L
        var pulse = 0f
        repeat(30) {
            now += 50
            pulse = tracker.update(0.001f, now, sensitivity = 1f)
        }
        assertTrue(pulse < 0.2f)
    }

    @Test
    fun aSuddenRiseTriggersOnePulse() {
        val tracker = OnsetTracker()
        var now = 0L
        repeat(20) {
            now += 50
            tracker.update(0.01f, now, sensitivity = 1f)
        }
        val pulse = tracker.update(0.25f, now + 50, sensitivity = 1f)
        assertTrue(pulse > 0.9f)
    }

    @Test
    fun aSteadyToneDoesNotKeepRetriggering() {
        val tracker = OnsetTracker()
        var now = 0L
        var previous = 0f
        var triggers = 0
        repeat(80) {
            now += 50
            val pulse = tracker.update(0.2f, now, sensitivity = 1f)
            if (pulse > previous && pulse > 0.95f) triggers += 1
            previous = pulse
        }
        assertEquals(1, triggers)
    }

    @Test
    fun playbackSwingsTriggerOnThePeaksOnly() {
        val tracker = LevelOnset()
        var now = 0L
        var previous = 0f
        var triggers = 0
        repeat(40) { index ->
            now += 100
            val rms = if (index % 5 == 0) 0.45f else 0.22f
            val pulse = tracker.update(rms, now, sensitivity = 1f)
            if (pulse > previous && pulse > 0.95f) triggers += 1
            previous = pulse
        }
        assertEquals(8, triggers)
    }

    @Test
    fun playbackSteadyLevelDoesNotKeepPulsing() {
        val tracker = LevelOnset()
        var now = 0L
        var previous = 0f
        var triggers = 0
        repeat(40) {
            now += 100
            val pulse = tracker.update(0.3f, now, sensitivity = 1f)
            if (pulse > previous && pulse > 0.95f) triggers += 1
            previous = pulse
        }
        assertEquals(1, triggers)
    }

    @Test
    fun aPlaybackHitStaysStrongThenEasesOff() {
        assertEquals(1f, playbackEnvelope(200L), 0.0001f)
        assertTrue(playbackEnvelope(1_000L) > 0.7f)
        assertTrue(playbackEnvelope(3_000L) > 0.3f)
        assertTrue(playbackEnvelope(6_000L) > 0.1f)
        assertTrue(playbackEnvelope(6_000L) < 0.2f)
        assertEquals(0f, playbackEnvelope(12_000L), 0.0001f)
    }

    @Test
    fun aSecondSpikeInsideTheRefractoryWindowDoesNotReset() {
        val tracker = OnsetTracker()
        var now = 0L
        repeat(20) {
            now += 50
            tracker.update(0.01f, now, sensitivity = 1f)
        }
        tracker.update(0.3f, now + 50, sensitivity = 1f)
        val pulse = tracker.update(0.3f, now + 120, sensitivity = 1f)
        assertTrue(pulse < 0.95f)
    }
}
