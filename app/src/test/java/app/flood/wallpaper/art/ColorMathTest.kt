package app.flood.wallpaper.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    @Test
    fun redIsMoreSaturatedThanGray() {
        assertTrue(saturationOf(220, 30, 30) > saturationOf(120, 120, 120))
    }

    @Test
    fun tintKeepsTheChosenAccent() {
        val chosen = rgb(143, 214, 255)
        val colors = coverFromTint(chosen)
        assertEquals(chosen, colors.accent)
        assertTrue(colors.primary != colors.accent)
    }
}
