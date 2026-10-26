package app.flood.wallpaper.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.flood.wallpaper.R
import app.flood.wallpaper.SettingsStore
import app.flood.wallpaper.media.PlaybackHub
import app.flood.wallpaper.ui.MainActivity
import kotlin.math.sqrt

class BeatService : Service() {
    private val tracker = OnsetTracker()
    private val buffer = ShortArray(2048)

    @Volatile
    private var running = false

    @Volatile
    private var recorder: AudioRecord? = null

    private var captureThread: Thread? = null
    private var listeningShown = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> BeatGate.screenOn = false
                Intent.ACTION_SCREEN_ON -> BeatGate.screenOn = true
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val power = getSystemService(PowerManager::class.java)
        BeatGate.screenOn = power.isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SettingsStore.write(this, SettingsStore.read(this).copy(beats = false, useMicrophone = false))
            PlaybackVisualizer.stop()
            shutdown()
            return START_NOT_STICKY
        }
        if (!SettingsStore.read(this).useMicrophone) {
            shutdown()
            return START_NOT_STICKY
        }
        if (!SettingsStore.read(this).beats) {
            shutdown()
            return START_NOT_STICKY
        }
        if (!promoteToForeground()) return START_NOT_STICKY
        if (captureThread == null) startLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        captureThread?.join(700)
        captureThread = null
        releaseRecorder()
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        val settings = SettingsStore.read(this)
        if (!settings.beats || settings.useMicrophone) {
            PlaybackHub.energy = 0f
            PlaybackHub.pulse = 0f
        }
        super.onDestroy()
    }

    private fun promoteToForeground(): Boolean {
        return try {
            val notification = buildNotification(listening = false)
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (error: Exception) {
            Log.e(TAG, "Could not keep beat listening in the foreground", error)
            stopSelf()
            false
        }
    }

    private fun startLoop() {
        running = true
        captureThread = Thread(::captureLoop, "FloodBeats").also { it.start() }
    }

    private fun captureLoop() {
        var wasListening = false
        while (running) {
            val settings = SettingsStore.read(this)
            val allow = settings.beats && settings.useMicrophone && BeatGate.audible() && hasMicPermission()
            if (!allow) {
                if (wasListening) {
                    releaseRecorder()
                    wasListening = false
                    PlaybackHub.energy = 0f
                    PlaybackHub.pulse = 0f
                    showListening(false)
                }
                sleepQuietly(200)
                continue
            }
            if (!wasListening) {
                val created = openRecorder()
                if (created == null) {
                    sleepQuietly(500)
                    continue
                }
                recorder = created
                try {
                    created.startRecording()
                } catch (error: Exception) {
                    Log.w(TAG, "Microphone did not start", error)
                    releaseRecorder()
                    sleepQuietly(500)
                    continue
                }
                wasListening = true
                showListening(true)
            }
            val active = recorder
            if (active == null) {
                wasListening = false
                continue
            }
            val read = try {
                active.read(buffer, 0, buffer.size)
            } catch (_: Exception) {
                -1
            }
            if (read < 0) {
                releaseRecorder()
                wasListening = false
                sleepQuietly(200)
                continue
            }
            if (read == 0) continue
            var sum = 0.0
            for (index in 0 until read) {
                val sample = buffer[index] / 32768.0
                sum += sample * sample
            }
            val rms = sqrt(sum / read).toFloat()
            tracker.update(rms, SystemClock.elapsedRealtime(), settings.sensitivity)
            PlaybackHub.energy = tracker.energy
            PlaybackHub.pulse = tracker.pulse
        }
        releaseRecorder()
    }

    private fun openRecorder(): AudioRecord? {
        if (!hasMicPermission()) return null
        val rate = 44_100
        val minBuffer = AudioRecord.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else {
                record
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Microphone permission is missing", error)
            null
        }
    }

    private fun releaseRecorder() {
        val active = recorder
        recorder = null
        if (active == null) return
        try {
            active.stop()
        } catch (_: Exception) {
        }
        active.release()
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun showListening(listening: Boolean) {
        if (listeningShown == listening) return
        listeningShown = listening
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(listening))
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(listening: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BeatService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (listening) R.string.notif_listening_text else R.string.notif_paused_text
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flood)
            .setContentTitle(getString(R.string.notif_listening_title))
            .setContentText(getString(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(text)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun shutdown() {
        running = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        captureThread?.join(700)
        captureThread = null
        releaseRecorder()
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val TAG = "Flood"
        private const val CHANNEL_ID = "flood_beats"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "app.flood.wallpaper.STOP_BEATS"

        fun start(context: Context) {
            val intent = Intent(context, BeatService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BeatService::class.java).setAction(ACTION_STOP))
        }
    }
}
