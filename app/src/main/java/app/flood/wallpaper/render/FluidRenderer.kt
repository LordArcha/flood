package app.flood.wallpaper.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import app.flood.wallpaper.LookSettings
import app.flood.wallpaper.PictureSource
import app.flood.wallpaper.art.ColorMode
import app.flood.wallpaper.art.CoverColors
import app.flood.wallpaper.art.CoverSampler
import app.flood.wallpaper.art.colorChannels
import app.flood.wallpaper.art.coverFromTint
import app.flood.wallpaper.media.PlaybackHub
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal class FluidRenderer {
    private var program = 0
    private var plateTex = 0
    private var oldTex = 0
    private var plateKey = Int.MIN_VALUE
    private var fade = 1f
    private var width = 1
    private var height = 1
    private var density = 2.5f
    private var blitProgram = 0
    private var sceneFbo = 0
    private var sceneTex = 0
    private var sceneW = 0
    private var sceneH = 0
    private var sceneFailedW = 0
    private var sceneFailedH = 0
    private var lastFrameNs = 0L
    private val beatPulse = BeatPulse()

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    fun onGlReady() {
        program = linkProgram(VERTEX_SHADER, FIELD_SHADER)
        blitProgram = linkProgram(VERTEX_SHADER, BLIT_SHADER)
        plateTex = 0
        oldTex = 0
        sceneFbo = 0
        sceneTex = 0
        sceneW = 0
        sceneH = 0
        sceneFailedW = 0
        sceneFailedH = 0
        plateKey = Int.MIN_VALUE
        fade = 1f
    }

    fun resize(nextWidth: Int, nextHeight: Int, nextDensity: Float) {
        if (nextWidth <= 0 || nextHeight <= 0) return
        width = nextWidth
        height = nextHeight
        density = nextDensity
    }

    fun frame(time: Float, settings: LookSettings, pan: Float) {
        val nowNs = System.nanoTime()
        val dt = if (lastFrameNs == 0L) {
            1f / 30f
        } else {
            ((nowNs - lastFrameNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        }
        lastFrameNs = nowNs

        if (program == 0) {
            clearWindow(0.08f, 0.07f, 0.12f)
            return
        }

        val now = PlaybackHub.state.value
        val held = if (settings.colorMode == ColorMode.Picture) PictureSource.held else null
        val artwork = held?.bitmap ?: now.artwork
        val colors = when (settings.colorMode) {
            ColorMode.Tint -> coverFromTint(settings.customColor)
            ColorMode.Picture -> held?.colors ?: now.colors
            ColorMode.Mono -> if (artwork == null) CoverColors.Mono else now.colors
            ColorMode.Album -> now.colors
        }
        ensurePlate(
            artwork,
            if (held != null) held.generation else now.artGeneration,
            if (held != null) held.hash else now.artHash,
            colors,
            settings,
        )
        fade = (fade + dt / 0.9f).coerceAtMost(1f)
        if (plateTex == 0) {
            val fallback = colorChannels(colors.secondary)
            clearWindow(fallback[0], fallback[1], fallback[2])
            return
        }

        val (fieldWidth, fieldHeight) = RenderBudget.bufferSize(width, height, density)
        val offscreen = fieldWidth < width || fieldHeight < height
        if (offscreen && blitProgram != 0 && prepareScene(fieldWidth, fieldHeight)) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneFbo)
            GLES20.glViewport(0, 0, sceneW, sceneH)
            drawField(time, settings, pan, sceneW, sceneH, dt)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            blitScene()
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            drawField(time, settings, pan, width, height, dt)
        }
    }

    fun release() {
        deleteTexture(plateTex)
        if (oldTex != plateTex) deleteTexture(oldTex)
        plateTex = 0
        oldTex = 0
        releaseScene()
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        if (blitProgram != 0) {
            GLES20.glDeleteProgram(blitProgram)
            blitProgram = 0
        }
    }

    private fun drawField(
        time: Float,
        settings: LookSettings,
        pan: Float,
        viewWidth: Int,
        viewHeight: Int,
        dt: Float,
    ) {
        val mode = when (settings.colorMode) {
            ColorMode.Album, ColorMode.Picture -> 0f
            ColorMode.Tint -> 1f
            ColorMode.Mono -> 2f
        }
        GLES20.glUseProgram(program)
        GLES20.glUniform1f(location("uTime"), time)
        GLES20.glUniform1f(location("uScale"), settings.fluidScale)
        GLES20.glUniform1f(location("uPulse"), PlaybackHub.pulse)
        GLES20.glUniform1f(location("uEnergy"), PlaybackHub.energy)
        GLES20.glUniform1f(location("uStrength"), settings.beatStrength.coerceIn(0f, 2f))
        GLES20.glUniform1f(location("uFade"), fade)
        GLES20.glUniform1f(location("uPan"), pan)
        GLES20.glUniform1f(location("uMode"), mode)
        GLES20.glUniform1f(location("uDim"), settings.dim.coerceIn(0f, 0.75f))
        GLES20.glUniform1f(location("uBlur"), settings.blur.coerceIn(0f, 1f))
        GLES20.glUniform1f(
            location("uKick"),
            beatPulse.advance(dt, PlaybackHub.beatMark, settings.pulse),
        )
        GLES20.glUniform2f(location("uResolution"), viewWidth.toFloat(), viewHeight.toFloat())
        bindPlate(0, plateTex, "uPlate")
        bindPlate(1, if (oldTex != 0) oldTex else plateTex, "uOld")
        drawQuad()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    private fun prepareScene(targetW: Int, targetH: Int): Boolean {
        if (sceneFbo != 0 && sceneW == targetW && sceneH == targetH) return true
        if (sceneFailedW == targetW && sceneFailedH == targetH) return false
        releaseScene()
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texture = textures[0]
        if (texture == 0) {
            noteSceneFailure(targetW, targetH, "Scene texture was not created")
            return false
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGetError()
        val pixels = ByteBuffer.allocateDirect(targetW * targetH * 4)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            targetW,
            targetH,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            pixels,
        )
        val texError = GLES20.glGetError()
        if (texError != GLES20.GL_NO_ERROR) {
            deleteTexture(texture)
            noteSceneFailure(targetW, targetH, "Scene texture failed: $texError")
            return false
        }
        val framebuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, framebuffers, 0)
        val framebuffer = framebuffers[0]
        if (framebuffer == 0) {
            deleteTexture(texture)
            noteSceneFailure(targetW, targetH, "Scene buffer was not created")
            return false
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            texture,
            0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            deleteTexture(texture)
            noteSceneFailure(targetW, targetH, "Scene buffer incomplete: $status")
            return false
        }
        sceneFbo = framebuffer
        sceneTex = texture
        sceneW = targetW
        sceneH = targetH
        sceneFailedW = 0
        sceneFailedH = 0
        Log.i(TAG, "Scene buffer ${targetW}x${targetH}")
        return true
    }

    private fun blitScene() {
        GLES20.glUseProgram(blitProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(blitProgram, "uImage"), 0)
        drawQuad()
    }

    private fun clearWindow(red: Float, green: Float, blue: Float) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(red, green, blue, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun releaseScene() {
        if (sceneFbo != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(sceneFbo), 0)
            sceneFbo = 0
        }
        deleteTexture(sceneTex)
        sceneTex = 0
        sceneW = 0
        sceneH = 0
    }

    private fun noteSceneFailure(targetW: Int, targetH: Int, message: String) {
        sceneFailedW = targetW
        sceneFailedH = targetH
        Log.e(TAG, message)
    }

    private fun ensurePlate(
        artwork: Bitmap?,
        generation: Int,
        artHash: Int,
        colors: CoverColors,
        settings: LookSettings,
    ) {
        val radius = CoverSampler.meltRadius(settings.blur)
        val cover = artwork?.takeUnless { it.isRecycled }
        val photo = settings.colorMode != ColorMode.Tint && cover != null
        val key = (if (photo) generation else 0) * 1009 +
            radius * 17 +
            settings.colorMode.ordinal * 131 +
            if (photo) artHash else colors.primary xor (colors.secondary shl 1) xor colors.accent
        if (key == plateKey && plateTex != 0) return

        val plate = if (photo) {
            CoverSampler.meltCover(cover, settings.blur)
        } else {
            val ink = if (settings.colorMode == ColorMode.Mono) CoverColors.Mono else colors
            CoverSampler.meltColors(ink, settings.blur)
        } ?: return
        val uploaded = upload(plate)
        if (uploaded == 0) return
        if (plateTex != 0 && plateTex != oldTex) deleteTexture(oldTex)
        oldTex = if (plateTex != 0) plateTex else uploaded
        plateTex = uploaded
        plateKey = key
        fade = if (oldTex == plateTex) 1f else 0f
    }

    private fun upload(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        if (id == 0) {
            bitmap.recycle()
            Log.e(TAG, "Plate texture was not created")
            return 0
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGetError()
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "Plate upload failed: $error")
            deleteTexture(id)
            return 0
        }
        return id
    }

    private fun bindPlate(unit: Int, texture: Int, uniform: String) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(location(uniform), unit)
    }

    private fun deleteTexture(texture: Int) {
        if (texture == 0) return
        GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    private fun location(name: String): Int = GLES20.glGetUniformLocation(program, name)

    private fun drawQuad() {
        quad.position(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun linkProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertex == 0 || fragment == 0) return 0
        val linked = GLES20.glCreateProgram()
        GLES20.glAttachShader(linked, vertex)
        GLES20.glAttachShader(linked, fragment)
        GLES20.glBindAttribLocation(linked, 0, "aPos")
        GLES20.glLinkProgram(linked)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader link failed: ${GLES20.glGetProgramInfoLog(linked)}")
            GLES20.glDeleteProgram(linked)
            return 0
        }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return linked
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private const val TAG = "Flood"
    }
}
