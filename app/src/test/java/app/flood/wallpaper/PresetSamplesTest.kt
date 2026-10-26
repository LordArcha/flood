package app.flood.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetSamplesTest {
    @Test
    fun namesStayInsideTheFiveSlots() {
        assertEquals("slot_0.jpg", PresetSamples.sampleName(0))
        assertEquals("slot_4.jpg", PresetSamples.sampleName(4))
        assertNull(PresetSamples.sampleName(-1))
        assertNull(PresetSamples.sampleName(5))
    }

    @Test
    fun largePhotosAreSampledDownToThePlate() {
        assertEquals(1, sampleSize(512, 512, PresetSamples.MAX_EDGE))
        assertEquals(2, sampleSize(1024, 100, PresetSamples.MAX_EDGE))
        assertEquals(8, sampleSize(4000, 3000, PresetSamples.MAX_EDGE))
        assertEquals(1, sampleSize(0, 0, PresetSamples.MAX_EDGE))
    }
}
