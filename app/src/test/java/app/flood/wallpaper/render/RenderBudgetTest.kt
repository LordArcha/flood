package app.flood.wallpaper.render

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderBudgetTest {
    @Test
    fun phoneScreenUsesAboutOneSixthOfThePixels() {
        val (width, height) = RenderBudget.bufferSize(1080, 2400, 2.625f)
        assertEquals(411, width)
        assertEquals(914, height)
    }

    @Test
    fun lowDensityStillScalesDown() {
        val (width, height) = RenderBudget.bufferSize(800, 1280, 1f)
        assertEquals(320, width)
        assertEquals(512, height)
    }
}
