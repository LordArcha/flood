package app.flood.wallpaper.art

enum class ColorMode(val stored: String) {
    Album("album"),
    Tint("tint"),
    Mono("mono"),
    Picture("picture"),
    ;

    companion object {
        fun fromStored(value: String?): ColorMode =
            entries.firstOrNull { it.stored == value } ?: Album
    }
}

data class CoverColors(
    val primary: Int,
    val secondary: Int,
    val accent: Int,
) {
    companion object {
        val Default = CoverColors(
            primary = rgb(18, 32, 58),
            secondary = rgb(61, 122, 158),
            accent = rgb(232, 140, 90),
        )

        val Mono = CoverColors(
            primary = rgb(18, 20, 24),
            secondary = rgb(70, 76, 84),
            accent = rgb(210, 214, 220),
        )
    }
}

fun rgb(r: Int, g: Int, b: Int): Int {
    return (0xFF shl 24) or ((r and 255) shl 16) or ((g and 255) shl 8) or (b and 255)
}

fun colorChannels(color: Int): FloatArray {
    return floatArrayOf(
        ((color shr 16) and 255) / 255f,
        ((color shr 8) and 255) / 255f,
        (color and 255) / 255f,
    )
}

fun saturationOf(r: Int, g: Int, b: Int): Float {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    if (max == 0) return 0f
    return (max - min) / max.toFloat()
}

fun coverFromTint(color: Int): CoverColors {
    val r = (color shr 16) and 255
    val g = (color shr 8) and 255
    val b = color and 255
    return CoverColors(
        primary = rgb((r * 0.22f).toInt(), (g * 0.22f).toInt(), (b * 0.28f).toInt()),
        secondary = rgb((r * 0.55f).toInt(), (g * 0.55f).toInt(), (b * 0.62f).toInt()),
        accent = color or (0xFF shl 24),
    )
}

fun scaleColor(color: Int, factor: Float): Int {
    val r = (((color shr 16) and 255) * factor).toInt().coerceIn(0, 255)
    val g = (((color shr 8) and 255) * factor).toInt().coerceIn(0, 255)
    val b = ((color and 255) * factor).toInt().coerceIn(0, 255)
    return rgb(r, g, b)
}
