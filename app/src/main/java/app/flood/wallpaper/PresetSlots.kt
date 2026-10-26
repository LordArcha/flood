package app.flood.wallpaper

import app.flood.wallpaper.art.ColorMode

/**
 * Five saved looks. The string is versioned and has no JSON library, so the
 * unit tests can round-trip it. The microphone switch and the saved cover are
 * session choices, not part of a look.
 */
object PresetSlots {
    const val COUNT = 5
    private const val VERSION = "2"

    fun encode(settings: LookSettings): String {
        return listOf(
            VERSION,
            settings.fluidScale.toString(),
            settings.motion.toString(),
            settings.blur.toString(),
            settings.dim.toString(),
            settings.glass.stored,
            if (settings.beats) "1" else "0",
            settings.sensitivity.toString(),
            settings.beatStrength.toString(),
            settings.colorMode.stored,
            settings.customColor.toString(),
            settings.pulse.toString(),
        ).joinToString(",")
    }

    fun decode(raw: String?): LookSettings? {
        if (raw.isNullOrEmpty()) return null
        val parts = raw.split(',')
        val pulse = when {
            parts.size == 11 && parts[0] == "1" -> 1f
            parts.size == 12 && parts[0] == VERSION -> number(parts[11]) ?: return null
            else -> return null
        }
        val fluid = number(parts[1]) ?: return null
        val motion = number(parts[2]) ?: return null
        val blur = number(parts[3]) ?: return null
        val dim = number(parts[4]) ?: return null
        val glass = GlassStyle.entries.firstOrNull { it.stored == parts[5] } ?: return null
        val beats = when (parts[6]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val sensitivity = number(parts[7]) ?: return null
        val strength = number(parts[8]) ?: return null
        val mode = ColorMode.entries.firstOrNull { it.stored == parts[9] } ?: return null
        val color = parts[10].toIntOrNull() ?: return null
        return LookSettings(
            fluidScale = fluid,
            motion = motion,
            blur = blur,
            dim = dim,
            glass = glass,
            beats = beats,
            sensitivity = sensitivity,
            beatStrength = strength,
            colorMode = mode,
            customColor = color,
            pulse = pulse,
        )
    }

    /** Puts a saved look on the current session without changing the mic or the stored cover. */
    fun apply(saved: LookSettings, current: LookSettings): LookSettings {
        return saved.copy(
            useMicrophone = current.useMicrophone,
            keepArt = current.keepArt,
        )
    }

    private fun number(text: String): Float? {
        val value = text.toFloatOrNull() ?: return null
        return if (value.isFinite()) value else null
    }
}
