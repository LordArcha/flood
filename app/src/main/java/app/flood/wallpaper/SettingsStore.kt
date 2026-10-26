package app.flood.wallpaper

import android.content.Context
import android.content.SharedPreferences
import app.flood.wallpaper.art.ColorMode

enum class GlassStyle(val stored: String) {
    Blur("blur"),
    Fluted("fluted"),
    ;

    companion object {
        fun fromStored(value: String?): GlassStyle =
            entries.firstOrNull { it.stored == value } ?: Blur
    }
}

data class LookSettings(
    val fluidScale: Float = 1f,
    val motion: Float = 1f,
    val blur: Float = 0.05f,
    val dim: Float = 0.18f,
    val glass: GlassStyle = GlassStyle.Blur,
    val beats: Boolean = false,
    val useMicrophone: Boolean = false,
    val sensitivity: Float = 1f,
    val beatStrength: Float = 1f,
    val pulse: Float = 1f,
    val colorMode: ColorMode = ColorMode.Album,
    val customColor: Int = DEFAULT_TINT,
    val keepArt: Boolean = true,
)

const val DEFAULT_TINT: Int = 0xFF8FD6FF.toInt()

private const val PREFS_NAME = "flood_settings"
private const val KEY_FLUID = "fluid_scale"
private const val KEY_MOTION = "motion"
private const val KEY_BLUR = "blur"
private const val KEY_DIM = "dim"
private const val KEY_GLASS = "glass"
private const val KEY_BEATS = "beats"
private const val KEY_STRENGTH = "beat_strength"
private const val KEY_PULSE = "pulse"
private const val KEY_MIC = "use_microphone"
private const val KEY_SENSITIVITY = "sensitivity"
private const val KEY_MODE = "color_mode"
private const val KEY_COLOR = "custom_color"
private const val KEY_KEEP = "keep_art"
private const val KEY_PRESET = "preset_"

object SettingsStore {
    fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun read(context: Context): LookSettings = read(prefs(context))

    fun read(prefs: SharedPreferences): LookSettings {
        return LookSettings(
            fluidScale = prefs.getFloat(KEY_FLUID, 1f),
            motion = prefs.getFloat(KEY_MOTION, 1f),
            blur = prefs.getFloat(KEY_BLUR, 0.05f),
            dim = prefs.getFloat(KEY_DIM, 0.18f),
            glass = GlassStyle.fromStored(prefs.getString(KEY_GLASS, null)),
            beats = prefs.getBoolean(KEY_BEATS, false),
            useMicrophone = prefs.getBoolean(KEY_MIC, false),
            sensitivity = prefs.getFloat(KEY_SENSITIVITY, 1f),
            beatStrength = prefs.getFloat(KEY_STRENGTH, 1f),
            pulse = prefs.getFloat(KEY_PULSE, 1f),
            colorMode = ColorMode.fromStored(prefs.getString(KEY_MODE, null)),
            customColor = prefs.getInt(KEY_COLOR, DEFAULT_TINT),
            keepArt = prefs.getBoolean(KEY_KEEP, true),
        )
    }

    fun write(context: Context, settings: LookSettings) {
        prefs(context).edit()
            .putFloat(KEY_FLUID, settings.fluidScale)
            .putFloat(KEY_MOTION, settings.motion)
            .putFloat(KEY_BLUR, settings.blur)
            .putFloat(KEY_DIM, settings.dim)
            .putString(KEY_GLASS, settings.glass.stored)
            .putBoolean(KEY_BEATS, settings.beats)
            .putBoolean(KEY_MIC, settings.useMicrophone)
            .putFloat(KEY_SENSITIVITY, settings.sensitivity)
            .putFloat(KEY_STRENGTH, settings.beatStrength)
            .putFloat(KEY_PULSE, settings.pulse)
            .putString(KEY_MODE, settings.colorMode.stored)
            .putInt(KEY_COLOR, settings.customColor)
            .putBoolean(KEY_KEEP, settings.keepArt)
            .apply()
    }

    fun isPresetKey(key: String?): Boolean = key?.startsWith(KEY_PRESET) == true

    fun readPresets(context: Context): List<LookSettings?> = readPresets(prefs(context))

    fun readPresets(prefs: SharedPreferences): List<LookSettings?> {
        return List(PresetSlots.COUNT) { index ->
            PresetSlots.decode(prefs.getString(presetKey(index), null))
        }
    }

    fun writePreset(context: Context, index: Int, settings: LookSettings) {
        if (index !in 0 until PresetSlots.COUNT) return
        prefs(context).edit().putString(presetKey(index), PresetSlots.encode(settings)).apply()
    }

    private fun presetKey(index: Int) = "$KEY_PRESET$index"
}
