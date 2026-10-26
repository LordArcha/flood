package app.flood.wallpaper.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.core.content.FileProvider
import app.flood.wallpaper.art.CoverSampler
import app.flood.wallpaper.media.NowPlaying
import app.flood.wallpaper.media.PlaybackHub
import java.io.File

fun shareNowPlaying(context: Context) {
    val card = renderShareCard(PlaybackHub.state.value)
    val directory = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(directory, "now-playing.png")
    file.outputStream().use { stream -> card.compress(Bitmap.CompressFormat.PNG, 100, stream) }
    card.recycle()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

fun renderShareCard(now: NowPlaying): Bitmap {
    val width = 1080
    val height = 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val artwork = now.artwork
    if (artwork != null && !artwork.isRecycled) {
        val soft = CoverSampler.soften(artwork, 128)
        canvas.drawBitmap(soft, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        soft.recycle()
    } else {
        val paint = Paint()
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            now.colors.primary,
            now.colors.accent,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    val shade = Paint()
    shade.shader = LinearGradient(
        0f,
        height * 0.42f,
        0f,
        height.toFloat(),
        0x00000000,
        0xE0101014.toInt(),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)

    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF4F7FB.toInt()
        textSize = 72f
        isFakeBoldText = true
    }
    val artist = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD5DDE6.toInt()
        textSize = 42f
    }
    val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8FD6FF.toInt()
        textSize = 36f
    }
    val titleText = now.title.ifBlank { "Flood" }
    val artistText = now.artist.ifBlank { "Nothing playing" }
    canvas.drawText(ellipsize(title, titleText, width - 160f), 80f, height - 280f, title)
    canvas.drawText(ellipsize(artist, artistText, width - 160f), 80f, height - 210f, artist)
    canvas.drawText("Flood", 80f, height - 120f, mark)
    return bitmap
}

private fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var trimmed = text
    while (trimmed.isNotEmpty() && paint.measureText("$trimmed…") > maxWidth) {
        trimmed = trimmed.dropLast(1)
    }
    return "$trimmed…"
}
