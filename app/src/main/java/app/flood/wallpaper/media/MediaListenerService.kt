package app.flood.wallpaper.media

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.flood.wallpaper.SettingsStore
import app.flood.wallpaper.art.CoverSampler
import app.flood.wallpaper.audio.BeatSync
import java.util.concurrent.Executors
import kotlin.math.max

class MediaListenerService : NotificationListenerService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val decodeExecutor = Executors.newSingleThreadExecutor()
    private val publishLock = Any()
    private var manager: MediaSessionManager? = null
    private var bound: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindBest(controllers.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishFrom(bound)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishFrom(bound)
        }

        override fun onSessionDestroyed() {
            bound = null
            refreshSessions()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val component = ComponentName(this, MediaListenerService::class.java)
        val sessionManager = getSystemService(MediaSessionManager::class.java)
        manager = sessionManager
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component, mainHandler)
            bindBest(sessionManager.getActiveSessions(component))
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification access is not granted", error)
        }
    }

    override fun onListenerDisconnected() {
        bound?.unregisterCallback(controllerCallback)
        bound = null
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        manager = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isMediaNotification(sbn)) return
        val current = PlaybackHub.state.value
        if (current.artwork != null && current.playing) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        if (title.isBlank()) return
        val artist = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bitmap = largeIconBitmap(sbn)
        if (bitmap == null && current.title == title) return
        accept(title, artist, "", bitmap, playing = current.playing)
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        return template?.contains("MediaStyle") == true
    }

    private fun largeIconBitmap(sbn: StatusBarNotification): Bitmap? {
        return try {
            val icon = sbn.notification.getLargeIcon() ?: return null
            val drawable = icon.loadDrawable(this) ?: return null
            val width = drawable.intrinsicWidth.coerceIn(1, 1024)
            val height = drawable.intrinsicHeight.coerceIn(1, 1024)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        } catch (error: Exception) {
            Log.w(TAG, "Could not read the notification icon", error)
            null
        }
    }

    override fun onDestroy() {
        decodeExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshSessions() {
        val sessionManager = manager ?: return
        val component = ComponentName(this, MediaListenerService::class.java)
        try {
            bindBest(sessionManager.getActiveSessions(component))
        } catch (error: SecurityException) {
            Log.w(TAG, "Could not read media sessions", error)
        }
    }

    private fun bindBest(controllers: List<MediaController>) {
        val next = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
        if (next?.sessionToken == bound?.sessionToken && next != null) {
            publishFrom(next)
            return
        }
        bound?.unregisterCallback(controllerCallback)
        bound = next
        next?.registerCallback(controllerCallback, mainHandler)
        if (next == null) {
            val previous = PlaybackHub.state.value
            if (previous.playing) {
                PlaybackHub.state.value = previous.copy(playing = false)
                mainHandler.post { BeatSync.sync(this) }
            }
        } else {
            publishFrom(next)
        }
    }

    private fun publishFrom(controller: MediaController?) {
        if (controller == null) return
        val metadata = controller.metadata
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            .ifBlank { metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty() }
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            .ifBlank { metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty() }
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val direct = sequenceOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        ).mapNotNull { key -> metadata?.getBitmap(key)?.safeCopy() }.firstOrNull()

        if (direct != null || metadata == null) {
            accept(title, artist, album, direct, playing)
            return
        }

        val uri = sequenceOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
        ).mapNotNull { key -> metadata.getString(key) }.firstOrNull()

        if (uri == null) {
            accept(title, artist, album, null, playing)
            return
        }

        decodeExecutor.execute {
            val decoded = decodeUri(Uri.parse(uri))
            mainHandler.post { accept(title, artist, album, decoded, playing) }
        }
    }

    private fun accept(title: String, artist: String, album: String, artwork: Bitmap?, playing: Boolean) {
        synchronized(publishLock) {
            acceptLocked(title, artist, album, artwork, playing)
        }
    }

    private fun acceptLocked(title: String, artist: String, album: String, artwork: Bitmap?, playing: Boolean) {
        val previous = PlaybackHub.state.value
        val hash = artwork?.let { CoverSampler.quickHash(it) }
        val sameArt = artwork != null && hash == previous.artHash && previous.artwork != null
        if (sameArt) artwork.recycle()

        val nextArt = if (artwork != null && !sameArt) artwork else previous.artwork
        val nextColors = if (artwork != null && !sameArt) CoverSampler.sample(artwork) else previous.colors
        val nextHash = if (artwork != null && !sameArt) hash ?: 0 else previous.artHash
        val nextGen = if (artwork != null && !sameArt) previous.artGeneration + 1 else previous.artGeneration

        PlaybackHub.state.value = NowPlaying(
            title = title.ifBlank { previous.title },
            artist = artist.ifBlank { previous.artist },
            album = album.ifBlank { previous.album },
            artwork = nextArt,
            colors = nextColors,
            playing = playing,
            artGeneration = nextGen,
            artHash = nextHash,
        )
        if (previous.playing != playing) {
            mainHandler.post { BeatSync.sync(this) }
        }

        if (artwork != null && !sameArt && SettingsStore.read(this).keepArt) {
            val stored = nextArt ?: return
            decodeExecutor.execute { CoverStore.save(this, stored, title, artist, album) }
        }
    }

    private fun decodeUri(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, 512)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not read artwork", error)
            null
        }
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val edge = max(width, height)
        while (edge / sample > maxEdge) sample *= 2
        return sample
    }

    private fun Bitmap.safeCopy(): Bitmap? {
        if (isRecycled) return null
        return try {
            val config = if (config == Bitmap.Config.HARDWARE) Bitmap.Config.ARGB_8888 else config ?: Bitmap.Config.ARGB_8888
            copy(config, false)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "Flood"
    }
}
