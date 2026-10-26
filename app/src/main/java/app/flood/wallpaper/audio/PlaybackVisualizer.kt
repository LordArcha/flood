package app.flood.wallpaper.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.flood.wallpaper.SettingsStore
import app.flood.wallpaper.media.PlaybackHub
import kotlin.math.sqrt

/**
 * Reads levels from the player that is actually producing audio.
 * This does not open the microphone. Android still requires the record-audio
 * permission before a visualizer may attach to playback.
 */
object PlaybackVisualizer {
    private val tracker = LevelOnset()
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var appContext: Context? = null
    private var visualizer: Visualizer? = null
    private var attachedSession = Int.MIN_VALUE
    private var callbackRegistered = false
    private var quietCallbacks = 0
    private var levelSamples = 0
    private var reportedLevel = false

    @Volatile
    private var active = false

    val playbackSilent = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val context = appContext ?: return
            handler.post { refresh(context) }
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        appContext = app
        val settings = SettingsStore.read(app)
        if (!settings.beats || settings.useMicrophone || !BeatGate.audible()) {
            stop()
            return
        }
        if (!hasAudioPermission(app)) {
            stop()
            BeatSync.source.value = BeatSource.NeedsPermission
            return
        }
        if (!PlaybackHub.state.value.playing) {
            stop()
            BeatSync.source.value = BeatSource.Idle
            PlaybackHub.energy = 0f
            PlaybackHub.pulse = 0f
            return
        }
        ensureCallback(app)
        // Other apps' session ids are not a public API, and the hidden field is
        // rejected by release lint. The output mix is session 0.
        synchronized(lock) {
            if (visualizer != null && attachedSession == 0) return
            releaseLocked()
            attachLocked(0)
        }
    }

    fun stop() {
        synchronized(lock) {
            releaseLocked()
        }
        quietCallbacks = 0
        playbackSilent.value = false
    }

    private fun ensureCallback(context: Context) {
        if (callbackRegistered) return
        val manager = context.getSystemService(AudioManager::class.java)
        manager.registerAudioPlaybackCallback(playbackCallback, handler)
        callbackRegistered = true
    }

    private fun attachLocked(requested: Int) {
        var used = requested
        var attached = openVisualizer(requested)
        if (attached == null && requested != 0) {
            used = 0
            attached = openVisualizer(0)
        }
        if (attached == null) {
            BeatSync.source.value = BeatSource.Unavailable
            Log.w(TAG, "Playback visualizer did not attach")
            return
        }
        visualizer = attached
        attachedSession = used
        active = true
        BeatSync.source.value = BeatSource.Playback
        Log.i(TAG, "Playback visualizer attached to session $used")
    }

    private fun openVisualizer(session: Int): Visualizer? {
        val created = try {
            Visualizer(session)
        } catch (error: Exception) {
            Log.w(TAG, "Visualizer session $session failed", error)
            return null
        }
        try {
            // This OS constructs the effect already enabled. setCaptureSize is
            // only legal before that, so turn it off, then size it, then enable.
            if (created.enabled) created.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            try {
                created.captureSize = range[1]
            } catch (error: IllegalStateException) {
                Log.w(TAG, "Capture size left at default for session $session", error)
            }
            created.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            val rate = (Visualizer.getMaxCaptureRate() / 2).coerceIn(1_000, 20_000)
            val status = created.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int,
                    ) {
                        onWaveform(waveform)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int,
                    ) = Unit
                },
                rate,
                true,
                false,
            )
            if (status != Visualizer.SUCCESS) {
                created.release()
                return null
            }
            created.enabled = true
            return created
        } catch (error: Exception) {
            Log.w(TAG, "Visualizer session $session failed", error)
            try {
                created.release()
            } catch (_: Exception) {
            }
            return null
        }
    }

    private fun onWaveform(waveform: ByteArray) {
        if (!active) return
        val context = appContext
        val settings = context?.let { SettingsStore.read(it) }
        val playing = PlaybackHub.state.value.playing
        if (settings == null || !playing) {
            quietCallbacks = 0
            playbackSilent.value = false
            PlaybackHub.energy = 0f
            PlaybackHub.pulse = 0f
            return
        }
        val rms = waveformLevel(waveform)
        levelSamples += 1
        if (!reportedLevel && levelSamples >= 8) {
            reportedLevel = true
            Log.i(TAG, "Playback level ${"%.3f".format(rms)}")
        }
        if (rms < 0.02f) {
            quietCallbacks += 1
            if (quietCallbacks > 40) playbackSilent.value = true
        } else {
            quietCallbacks = 0
            playbackSilent.value = false
        }
        tracker.update(rms, SystemClock.elapsedRealtime(), settings.sensitivity)
        PlaybackHub.energy = tracker.energy
        PlaybackHub.pulse = tracker.pulse
    }

    private fun releaseLocked() {
        active = false
        levelSamples = 0
        reportedLevel = false
        val current = visualizer
        visualizer = null
        attachedSession = Int.MIN_VALUE
        if (current == null) return
        try {
            current.enabled = false
        } catch (_: Exception) {
        }
        try {
            current.release()
        } catch (_: Exception) {
        }
    }

    private fun waveformLevel(waveform: ByteArray): Float {
        if (waveform.isEmpty()) return 0f
        var sum = 0.0
        for (value in waveform) {
            val sample = ((value.toInt() and 0xFF) - 128) / 128.0
            sum += sample * sample
        }
        return sqrt(sum / waveform.size).toFloat()
    }

    private const val TAG = "Flood"
}
