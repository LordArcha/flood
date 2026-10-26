package app.flood.wallpaper.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import app.flood.wallpaper.SettingsStore
import app.flood.wallpaper.media.PlaybackHub
import kotlinx.coroutines.flow.MutableStateFlow

enum class BeatSource {
    Idle,
    Playback,
    Microphone,
    NeedsPermission,
    Unavailable,
}

object BeatSync {
    val source = MutableStateFlow(BeatSource.Idle)
    private var receiverRegistered = false

    fun install(context: Context) {
        val app = context.applicationContext
        val power = app.getSystemService(PowerManager::class.java)
        BeatGate.screenOn = power.isInteractive
        if (receiverRegistered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                BeatGate.screenOn = intent.action == Intent.ACTION_SCREEN_ON
                sync(ctx)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    fun sync(context: Context) {
        val app = context.applicationContext
        val power = app.getSystemService(PowerManager::class.java)
        BeatGate.screenOn = power.isInteractive
        val settings = SettingsStore.read(app)
        if (!settings.beats || !BeatGate.audible()) {
            PlaybackVisualizer.stop()
            stopMicrophone(app)
            source.value = BeatSource.Idle
            PlaybackHub.energy = 0f
            PlaybackHub.pulse = 0f
            return
        }
        if (!hasAudioPermission(app)) {
            PlaybackVisualizer.stop()
            stopMicrophone(app)
            source.value = BeatSource.NeedsPermission
            PlaybackHub.energy = 0f
            PlaybackHub.pulse = 0f
            return
        }
        if (settings.useMicrophone) {
            PlaybackVisualizer.stop()
            BeatService.start(app)
            source.value = BeatSource.Microphone
        } else if (!PlaybackHub.state.value.playing) {
            stopMicrophone(app)
            PlaybackVisualizer.stop()
            source.value = BeatSource.Idle
            PlaybackHub.energy = 0f
            PlaybackHub.pulse = 0f
        } else {
            stopMicrophone(app)
            PlaybackVisualizer.refresh(app)
        }
    }

    private fun stopMicrophone(context: Context) {
        context.stopService(Intent(context, BeatService::class.java))
    }
}

internal fun hasAudioPermission(context: Context): Boolean {
    return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
}
