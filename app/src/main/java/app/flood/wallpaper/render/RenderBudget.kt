package app.flood.wallpaper.render

/**
 * The fluid is a soft field, so it is drawn into a small buffer and scaled up.
 * The divisor matches a density-scaled wallpaper buffer: at least 2.5, otherwise
 * the display density. A 1080x2400 screen at density 2.625 draws about 411x914.
 * Size an offscreen buffer with this. The wallpaper surface stays full size:
 * this ROM rejects [android.view.SurfaceHolder.setFixedSize] on wallpapers.
 */
internal object RenderBudget {
    fun bufferSize(width: Int, height: Int, density: Float): Pair<Int, Int> {
        if (width <= 1 || height <= 1) return 1 to 1
        val divisor = density.coerceAtLeast(2.5f)
        val scaledWidth = (width / divisor).toInt().coerceIn(2, width)
        val scaledHeight = (height / divisor).toInt().coerceIn(2, height)
        return scaledWidth to scaledHeight
    }
}
