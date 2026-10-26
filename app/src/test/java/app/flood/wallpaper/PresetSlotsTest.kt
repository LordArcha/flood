package app.flood.wallpaper

import app.flood.wallpaper.art.ColorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetSlotsTest {
    @Test
    fun aLookRoundTripsWithoutTheMicOrTheCoverChoice() {
        val look = LookSettings(
            fluidScale = 0.4f,
            motion = 0.74f,
            blur = 0f,
            dim = 0.065f,
            glass = GlassStyle.Blur,
            beats = true,
            useMicrophone = true,
            sensitivity = 1.15f,
            beatStrength = 0.59f,
            pulse = 1.4f,
            colorMode = ColorMode.Tint,
            customColor = -7350529,
            keepArt = false,
        )
        val decoded = PresetSlots.decode(PresetSlots.encode(look))
        assertEquals(0.4f, decoded!!.fluidScale, 0.0001f)
        assertEquals(0.74f, decoded.motion, 0.0001f)
        assertEquals(0f, decoded.blur, 0.0001f)
        assertEquals(0.065f, decoded.dim, 0.0001f)
        assertEquals(GlassStyle.Blur, decoded.glass)
        assertTrue(decoded.beats)
        assertEquals(1.15f, decoded.sensitivity, 0.0001f)
        assertEquals(0.59f, decoded.beatStrength, 0.0001f)
        assertEquals(1.4f, decoded.pulse, 0.0001f)
        assertEquals(ColorMode.Tint, decoded.colorMode)
        assertEquals(-7350529, decoded.customColor)
        assertEquals(
            PresetSlots.encode(look),
            PresetSlots.encode(look.copy(useMicrophone = false, keepArt = true)),
        )

        val applied = PresetSlots.apply(
            decoded,
            LookSettings(useMicrophone = false, keepArt = true, beats = false),
        )
        assertFalse(applied.useMicrophone)
        assertTrue(applied.keepArt)
        assertTrue(applied.beats)
        assertEquals(0.4f, applied.fluidScale, 0.0001f)
    }

    @Test
    fun junkDoesNotBecomeALook() {
        assertNull(PresetSlots.decode(null))
        assertNull(PresetSlots.decode(""))
        assertNull(PresetSlots.decode("nope"))
        assertNull(PresetSlots.decode("2,0.4,0.74,0,0.065,blur,1,1.15,0.59,album,1"))
        assertNull(PresetSlots.decode("1,0.4,0.74,0,0.065,glass,1,1.15,0.59,album,1"))
        assertNull(PresetSlots.decode("1,0.4,0.74,0,0.065,blur,2,1.15,0.59,album,1"))
        assertNull(PresetSlots.decode("1,0.4,0.74,0,0.065,blur,1,1.15,0.59,rainbow,1"))
        assertNull(PresetSlots.decode("1,0.4,0.74,0,0.065,blur,1,1.15,0.59,album,nope"))
        assertNull(PresetSlots.decode("1,0.4,0.74,0,0.065,fluted,0,1,1,mono,1,extra"))
        assertNull(PresetSlots.decode("2,0.4,0.74,0,0.065,blur,1,1.15,0.59,album,1,nope"))
        val older = PresetSlots.decode("1,0.4,0.74,0,0.065,blur,1,1.15,0.59,album,-7350529")
        assertEquals(1f, older!!.pulse, 0.0001f)
    }

    @Test
    fun fiveSlotsIsTheCap() {
        assertEquals(5, PresetSlots.COUNT)
    }

    @Test
    fun aPictureLookRoundTrips() {
        val look = LookSettings(colorMode = ColorMode.Picture, useMicrophone = true, keepArt = false)
        val decoded = PresetSlots.decode(PresetSlots.encode(look))
        assertEquals(ColorMode.Picture, decoded!!.colorMode)
        assertFalse(decoded.useMicrophone)
        assertTrue(decoded.keepArt)
    }
}
