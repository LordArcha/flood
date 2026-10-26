package app.flood.wallpaper.ui

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.flood.wallpaper.LookSettings
import app.flood.wallpaper.PictureSource
import app.flood.wallpaper.PictureStore
import app.flood.wallpaper.PresetSamples
import app.flood.wallpaper.PresetSlots
import app.flood.wallpaper.SettingsStore
import app.flood.wallpaper.art.ColorMode
import app.flood.wallpaper.audio.BeatGate
import app.flood.wallpaper.audio.BeatSource
import app.flood.wallpaper.audio.BeatSync
import app.flood.wallpaper.audio.PlaybackVisualizer
import app.flood.wallpaper.live.FluidWallpaperService
import app.flood.wallpaper.media.MediaListenerService
import app.flood.wallpaper.media.PlaybackHub

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloodTheme {
                FloodScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        BeatGate.activityVisible = true
        if (notificationAccessEnabled(this)) {
            MediaListenerService.ensure(this)
        }
        BeatSync.sync(this)
    }

    override fun onStop() {
        BeatGate.activityVisible = false
        BeatSync.sync(this)
        super.onStop()
    }
}

@Composable
private fun FloodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8FD6FF),
            onPrimary = Color(0xFF06202C),
            background = Color(0xFF0E1014),
            surface = Color(0xFF171A21),
            onBackground = Color(0xFFE8EEF4),
            onSurface = Color(0xFFE8EEF4),
        ),
        content = content,
    )
}

@Composable
private fun FloodScreen() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(SettingsStore.read(context)) }
    var presets by remember { mutableStateOf(SettingsStore.readPresets(context)) }
    var samples by remember { mutableStateOf(PresetSamples.stamps(context)) }
    var accessGranted by remember { mutableStateOf(notificationAccessEnabled(context)) }
    var wallpaperSet by remember { mutableStateOf(floodIsWallpaper(context)) }
    val now by PlaybackHub.state.collectAsState()

    DisposableEffect(context) {
        val prefs = SettingsStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            if (!SettingsStore.isPresetKey(key)) {
                settings = SettingsStore.read(shared)
            }
            presets = SettingsStore.readPresets(shared)
            samples = PresetSamples.stamps(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessGranted = notificationAccessEnabled(context)
                wallpaperSet = floodIsWallpaper(context)
                settings = SettingsStore.read(context)
                presets = SettingsStore.readPresets(context)
                samples = PresetSamples.stamps(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pendingSlot by remember { mutableStateOf(-1) }
    var activePreset by remember { mutableStateOf(-1) }
    val picturePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val slot = pendingSlot
        PictureStore.import(context, uri) { ready ->
            if (!ready) return@import
            val latest = SettingsStore.read(context).copy(colorMode = ColorMode.Picture)
            SettingsStore.write(context, latest)
            if (slot in 0 until PresetSlots.COUNT) {
                savePreset(context, slot, latest)
                activePreset = slot
            }
        }
    }

    fun pickPicture(slot: Int = -1) {
        pendingSlot = slot
        picturePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun saveCurrent(index: Int) {
        if (settings.colorMode == ColorMode.Picture && PictureSource.held == null) {
            pickPicture(index)
            return
        }
        savePreset(context, index, settings)
        activePreset = index
    }

    val source by BeatSync.source.collectAsState()
    val listenerConnected by MediaListenerService.connected.collectAsState()
    val playbackSilent by PlaybackVisualizer.playbackSilent.collectAsState()
    val audioLauncher = rememberMicLauncher { granted ->
        if (!granted) return@rememberMicLauncher
        val next = SettingsStore.read(context).copy(beats = true)
        settings = next
        SettingsStore.write(context, next)
        BeatSync.sync(context)
    }

    fun update(next: LookSettings) {
        settings = next
        SettingsStore.write(context, next)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Flood", style = MaterialTheme.typography.headlineLarge)
            Text(
                "The current album melts into a slow liquid. A beat stirs the flow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(28.dp)),
                factory = { FluidPreviewView(it) },
                update = { it.bridge.settings = settings },
            )
            Text(
                text = when {
                    now.title.isNotBlank() && now.artist.isNotBlank() -> "${now.title} · ${now.artist}"
                    now.title.isNotBlank() -> now.title
                    else -> "Nothing playing yet"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (accessGranted) {
                Text(
                    if (listenerConnected) {
                        "Listening to players that post a media session. Spotify, YouTube Music, Apple Music, Tidal, and SoundCloud all do."
                    } else {
                        "The listener is not connected, so the song and beats stay idle."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                OutlinedButton(
                    onClick = { MediaListenerService.restart(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Restart listener")
                }
            } else {
                Text(
                    "Notification access lets Flood read the song and cover from whichever player is running. Playback stays in that app.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Allow notification access")
                }
            }
            Button(
                onClick = { openWallpaperPicker(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (wallpaperSet) "Wallpaper is set" else "Set as wallpaper")
            }
            Text("Presets", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap an empty number to save this look. A photo shows here only when Picture is selected. Tap a saved preset to use it. Hold one to replace it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            PresetRow(
                presets = presets,
                samples = samples,
                active = activePreset,
                onTap = { index, saved ->
                    val stored = saved
                    val ready = stored != null &&
                        (stored.colorMode != ColorMode.Picture || samples.getOrElse(index) { 0L } != 0L)
                    if (stored == null || !ready) {
                        saveCurrent(index)
                    } else {
                        val next = PresetSlots.apply(stored, settings)
                        val beatsChanged = next.beats != settings.beats
                        if (next.colorMode == ColorMode.Picture && !PictureStore.useSlot(context, index)) {
                            pickPicture(index)
                        } else {
                            activePreset = index
                            update(next)
                            if (beatsChanged) BeatSync.sync(context)
                        }
                    }
                },
                onHold = { index -> saveCurrent(index) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SliderRow("Fluid scale", settings.fluidScale, 0.4f..2f) { update(settings.copy(fluidScale = it)) }
                Text(
                    "Low is a calm liquid. High is more turbulent, like gas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            SliderRow("Speed", settings.motion, 0f..1.8f) { update(settings.copy(motion = it)) }
            SliderRow("Blur", settings.blur, 0f..1f) { update(settings.copy(blur = it)) }
            SliderRow("Darken", settings.dim, 0f..0.55f) { update(settings.copy(dim = it)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live beats", style = MaterialTheme.typography.titleMedium)
                    Text(
                        beatStatus(settings.beats, settings.useMicrophone, source, playbackSilent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked = settings.beats,
                    onCheckedChange = { want ->
                        if (!want) {
                            update(settings.copy(beats = false, useMicrophone = false))
                            BeatSync.sync(context)
                        } else if (!hasMicPermission(context)) {
                            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            update(settings.copy(beats = true))
                            BeatSync.sync(context)
                        }
                    },
                )
            }
            if (settings.beats) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Microphone", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Off unless you turn this on. It hears the room instead of the player.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Switch(
                        checked = settings.useMicrophone,
                        onCheckedChange = { want ->
                            update(settings.copy(useMicrophone = want))
                            BeatSync.sync(context)
                        },
                    )
                }
                SliderRow("Beat sensitivity", settings.sensitivity, 0.5f..2f) {
                    update(settings.copy(sensitivity = it))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SliderRow("Beat strength", settings.beatStrength, 0f..2f) {
                        update(settings.copy(beatStrength = it))
                    }
                    Text(
                        "How hard a beat surges and brightens the flow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            Text("Color", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.colorMode == ColorMode.Album,
                    onClick = { update(settings.copy(colorMode = ColorMode.Album)) },
                    label = { Text("Album") },
                )
                FilterChip(
                    selected = settings.colorMode == ColorMode.Tint,
                    onClick = { update(settings.copy(colorMode = ColorMode.Tint)) },
                    label = { Text("Tint") },
                )
                FilterChip(
                    selected = settings.colorMode == ColorMode.Mono,
                    onClick = { update(settings.copy(colorMode = ColorMode.Mono)) },
                    label = { Text("Mono") },
                )
                FilterChip(
                    selected = settings.colorMode == ColorMode.Picture,
                    onClick = {
                        if (PictureSource.held == null) pickPicture()
                        else update(settings.copy(colorMode = ColorMode.Picture))
                    },
                    label = { Text("Picture") },
                )
            }
            if (settings.colorMode == ColorMode.Tint) {
                TintChoices(settings.customColor) { update(settings.copy(customColor = it, colorMode = ColorMode.Tint)) }
            }
            if (settings.colorMode == ColorMode.Picture) {
                Text(
                    "The fluid melts this photo instead of the album cover.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                OutlinedButton(
                    onClick = { pickPicture() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose a picture")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Remember the last cover", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.keepArt,
                    onCheckedChange = { update(settings.copy(keepArt = it)) },
                )
            }
            OutlinedButton(
                onClick = { shareNowPlaying(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share now playing")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun savePreset(context: Context, index: Int, look: LookSettings) {
    if (look.colorMode == ColorMode.Picture && PictureSource.held != null) {
        PictureStore.keepWithPreset(context, index)
    } else {
        PresetSamples.delete(context, index)
    }
    SettingsStore.writePreset(context, index, look)
}

@Composable
private fun PresetRow(
    presets: List<LookSettings?>,
    samples: List<Long>,
    active: Int,
    onTap: (Int, LookSettings?) -> Unit,
    onHold: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEachIndexed { index, saved ->
            val stamp = samples.getOrElse(index) { 0L }
            val picture = if (saved?.colorMode == ColorMode.Picture) rememberPresetPicture(index, stamp) else null
            val filled = saved != null && (saved.colorMode != ColorMode.Picture || picture != null)
            val shape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (index == active) 2.dp else 1.dp,
                        color = when {
                            index == active -> MaterialTheme.colorScheme.primary
                            filled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                        },
                        shape = shape,
                    )
                    .combinedClickable(
                        onClick = { onTap(index, saved) },
                        onLongClick = { onHold(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (picture != null) {
                    Image(
                        bitmap = picture,
                        contentDescription = "Preset ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(
                    "${index + 1}",
                    color = if (picture != null) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = if (picture != null) {
                        MaterialTheme.typography.labelMedium.copy(
                            shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 1f), 6f),
                        )
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberPresetPicture(index: Int, stamp: Long): ImageBitmap? {
    val context = LocalContext.current
    return remember(index, stamp) {
        if (stamp == 0L) null else PresetSamples.load(context, index, subsample = 4)?.asImageBitmap()
    }
}

@Composable
private fun TintChoices(selected: Int, onPick: (Int) -> Unit) {
    val colors = listOf(0xFFE07A5F, 0xFF8FD6FF, 0xFFB388FF, 0xFF9BE37C, 0xFFFFD166)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { color ->
            val value = color.toInt()
            Box(
                modifier = Modifier
                    .size(if (value == selected) 36.dp else 28.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .clickable { onPick(value) },
            )
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label  ${"%.2f".format(value)}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun rememberMicLauncher(onResult: (Boolean) -> Unit) =
    androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)

private fun beatStatus(
    beats: Boolean,
    microphone: Boolean,
    source: BeatSource,
    playbackSilent: Boolean,
): String {
    if (!beats) {
        return "Follows the audio that is playing, including headphones. The microphone stays off."
    }
    if (microphone) {
        return "The microphone is on while Flood is on screen. Nothing is recorded."
    }
    return when (source) {
        BeatSource.NeedsPermission -> "Android needs permission to measure playback. The microphone stays closed."
        BeatSource.Unavailable -> "This player blocked the playback meter. Turn on the microphone if you want the room instead."
        BeatSource.Playback -> if (playbackSilent) {
            "No level is coming from this player. The microphone is still off."
        } else {
            "Reading the player. Nothing is recorded, and the microphone stays off."
        }
        BeatSource.Idle -> "Waiting for a player. The microphone stays off."
        BeatSource.Microphone -> "The microphone is on while Flood is on screen. Nothing is recorded."
    }
}

private fun hasMicPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private fun notificationAccessEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.split(":").any { it.contains(context.packageName) }
}

private fun floodIsWallpaper(context: Context): Boolean {
    return try {
        WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName
    } catch (_: Exception) {
        false
    }
}

private fun openWallpaperPicker(context: Context) {
    val component = ComponentName(context, FluidWallpaperService::class.java)
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    try {
        context.startActivity(direct)
    } catch (_: Exception) {
        context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
    }
}
