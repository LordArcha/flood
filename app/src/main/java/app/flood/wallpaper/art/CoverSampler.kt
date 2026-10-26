package app.flood.wallpaper.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader

object CoverSampler {
    private const val PLATE = 160
    fun sample(bitmap: Bitmap): CoverColors {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return CoverColors.Default

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        var best = CoverColors.Default.accent
        var bestSat = -1f
        var second = CoverColors.Default.secondary
        var secondSat = -1f
        val stepX = (width / 24).coerceAtLeast(1)
        val stepY = (height / 24).coerceAtLeast(1)

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 255
                val g = (pixel shr 8) and 255
                val b = pixel and 255
                rSum += r
                gSum += g
                bSum += b
                count++
                val sat = saturationOf(r, g, b)
                if (sat > bestSat) {
                    second = best
                    secondSat = bestSat
                    best = pixel
                    bestSat = sat
                } else if (sat > secondSat) {
                    second = pixel
                    secondSat = sat
                }
                x += stepX
            }
            y += stepY
        }

        if (count == 0) return CoverColors.Default
        val average = rgb(
            (rSum / count).toInt(),
            (gSum / count).toInt(),
            (bSum / count).toInt(),
        )
        return CoverColors(
            primary = scaleColor(average, 0.45f),
            secondary = second,
            accent = best,
        )
    }

    fun quickHash(bitmap: Bitmap): Int {
        if (bitmap.width == 0 || bitmap.height == 0 || bitmap.isRecycled) return 0
        var hash = bitmap.width * 31 + bitmap.height
        val stepX = (bitmap.width / 8).coerceAtLeast(1)
        val stepY = (bitmap.height / 8).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                hash = hash * 31 + bitmap.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        return hash
    }

    fun meltRadius(blur: Float): Int = (12 + blur.coerceIn(0f, 1f) * 20f).toInt()

    /** Center-cropped cover, softened into color pools. Null if the bitmap is already gone. */
    fun meltCover(source: Bitmap, blur: Float): Bitmap? {
        if (source.isRecycled || source.width < 1 || source.height < 1) return null
        val size = PLATE
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val scale = maxOf(size / source.width.toFloat(), size / source.height.toFloat())
            val drawnWidth = source.width * scale
            val drawnHeight = source.height * scale
            val left = (size - drawnWidth) / 2f
            val top = (size - drawnHeight) / 2f
            canvas.drawBitmap(
                source,
                null,
                RectF(left, top, left + drawnWidth, top + drawnHeight),
                paint,
            )
            boxBlur(output, meltRadius(blur), passes = 3)
            output
        } catch (error: RuntimeException) {
            output.recycle()
            null
        }
    }

    /** Full-frame color wash used when there is no cover, or for a tint. */
    fun meltColors(colors: CoverColors, blur: Float): Bitmap {
        val size = PLATE
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(visibleColor(colors.secondary))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val span = size.toFloat()
        paintBlob(canvas, paint, span * 0.66f, span * 0.34f, span * 0.78f, visibleColor(colors.accent))
        paintBlob(canvas, paint, span * 0.28f, span * 0.72f, span * 0.70f, visibleColor(colors.primary))
        paintBlob(canvas, paint, span * 0.50f, span * 0.14f, span * 0.55f, visibleColor(colors.accent))
        boxBlur(output, (12 + blur.coerceIn(0f, 1f) * 16f).toInt(), passes = 3)
        return output
    }

    fun soften(source: Bitmap, size: Int = 80): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, Rect(0, 0, size, size), paint)
        boxBlur(output, radius = 12, passes = 4)
        return output
    }

    private fun paintBlob(canvas: Canvas, paint: Paint, cx: Float, cy: Float, radius: Float, color: Int) {
        val solid = color or (0xFF shl 24)
        val clear = color and 0x00FFFFFF
        paint.shader = RadialGradient(cx, cy, radius, solid, clear, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }

    private fun visibleColor(color: Int): Int {
        var red = (color shr 16) and 255
        var green = (color shr 8) and 255
        var blue = color and 255
        val maxChannel = maxOf(red, green, blue)
        if (maxChannel in 1..79) {
            val scale = 96f / maxChannel
            red = (red * scale).toInt().coerceIn(0, 255)
            green = (green * scale).toInt().coerceIn(0, 255)
            blue = (blue * scale).toInt().coerceIn(0, 255)
        }
        return rgb(red, green, blue)
    }

    private fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        val scratch = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(passes) {
            blurHorizontal(pixels, scratch, width, height, radius)
            blurVertical(scratch, pixels, width, height, radius)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun blurHorizontal(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until height) {
            var red = 0
            var green = 0
            var blue = 0
            val row = y * width
            for (offset in -radius..radius) {
                val color = src[row + offset.coerceIn(0, width - 1)]
                red += (color shr 16) and 255
                green += (color shr 8) and 255
                blue += color and 255
            }
            for (x in 0 until width) {
                dst[row + x] = (0xFF shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
                val remove = src[row + (x - radius).coerceIn(0, width - 1)]
                val add = src[row + (x + radius + 1).coerceIn(0, width - 1)]
                red += ((add shr 16) and 255) - ((remove shr 16) and 255)
                green += ((add shr 8) and 255) - ((remove shr 8) and 255)
                blue += (add and 255) - (remove and 255)
            }
        }
    }

    private fun blurVertical(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = src[offset.coerceIn(0, height - 1) * width + x]
                red += (color shr 16) and 255
                green += (color shr 8) and 255
                blue += color and 255
            }
            for (y in 0 until height) {
                dst[y * width + x] = (0xFF shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
                val remove = src[(y - radius).coerceIn(0, height - 1) * width + x]
                val add = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                red += ((add shr 16) and 255) - ((remove shr 16) and 255)
                green += ((add shr 8) and 255) - ((remove shr 8) and 255)
                blue += (add and 255) - (remove and 255)
            }
        }
    }
}
