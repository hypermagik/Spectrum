package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES10
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.opengles.GL10


class Waterfall(context: Context, private var fftSize: Int) {
    private var vPosition: Int = 0
    private var aTexCoord: Int = 0
    private var uSampleTexture: Int = 0
    private var uWaterfallOffset: Int = 0
    private var uWaterfallHeight: Int = 0

    private val vertices = floatArrayOf(
        0.0f, 0.5f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 0.5f,
    )
    private var vertexBuffer: ByteBuffer

    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)
    private var drawOrderBuffer: ByteBuffer

    private val texCoords = floatArrayOf(
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f,
    )
    private var texCoordsBuffer: ByteBuffer

    private var textures: IntArray = IntArray(2)

    private var sampleBuffer: ByteBuffer
    private var colors: IntArray

    private var height = 0
    private var currentLine = 0

    private lateinit var clearBuffer: ByteBuffer

    private var isRunning = false
    private var isVisible: Boolean = context.resources.configuration.orientation == ORIENTATION_PORTRAIT
    private var isDirty = true

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        vertexBuffer.asFloatBuffer().apply { put(vertices) }

        drawOrderBuffer = ByteBuffer.allocateDirect(drawOrder.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        drawOrderBuffer.asShortBuffer().apply { put(drawOrder) }

        texCoordsBuffer = ByteBuffer.allocateDirect(texCoords.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        texCoordsBuffer.asFloatBuffer().apply { put(texCoords) }

        sampleBuffer = ByteBuffer.allocateDirect(fftSize * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())

        colors = IntArray(256)
        for (i in 0..255) {
            if (i < 20) {
                colors[i] = Color.argb(255, 0, 0, 0)
            } else if (i in 20..69) {
                colors[i] = Color.argb(255, 0, 0, 140 * (i - 20) / 50)
            } else if (i in 70..99) {
                colors[i] = Color.argb(255, 60 * (i - 70) / 30, 125 * (i - 70) / 30, 115 * (i - 70) / 30 + 140)
            } else if (i in 100..149) {
                colors[i] = Color.argb(255, 195 * (i - 100) / 50 + 60, 130 * (i - 100) / 50 + 125, 255 - (255 * (i - 100) / 50))
            } else if (i in 150..249) {
                colors[i] = Color.argb(255, 255, 255 - 255 * (i - 150) / 100, 0)
            } else {
                colors[i] = Color.argb(255, 255, 255 * (i - 250) / 5, 255 * (i - 250) / 5)
            }
        }
    }

    fun onSurfaceCreated(program: Int) {
        vPosition = GLES20.glGetAttribLocation(program, "vPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uSampleTexture = GLES20.glGetUniformLocation(program, "sampleTexture")
        uWaterfallOffset = GLES20.glGetUniformLocation(program, "waterfallOffset")
        val uWaterfallSampler = GLES20.glGetUniformLocation(program, "waterfallSampler")
        uWaterfallHeight = GLES20.glGetUniformLocation(program, "waterfallHeight")

        GLES20.glGenTextures(2, textures, 0)

        // Create texture with color LUT.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])

        val bitmap = Bitmap.createBitmap(colors.size, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(colors, 0, colors.size, 0, 0, colors.size, 1)
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0)

        GLES20.glUniform1i(uWaterfallSampler, 1)
    }

    fun onSurfaceChanged(height: Int) {
        if (!isVisible) {
            return
        }

        if (this.height != height / 2) {
            this.height = height / 2
            currentLine = 0

            clearBuffer = ByteBuffer.allocateDirect(fftSize * this.height * Float.SIZE_BYTES)

            GLES20.glUniform1i(uWaterfallHeight, this.height)

            isDirty = true
        }
    }

    fun start() {
        isRunning = true
    }

    fun stop() {
        isRunning = false
    }

    fun update(magnitudes: FloatArray) {
        if (!isVisible) {
            return
        }

        if (fftSize != magnitudes.size) {
            fftSize = magnitudes.size
            sampleBuffer = ByteBuffer.allocateDirect(fftSize * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
            clearBuffer = ByteBuffer.allocateDirect(fftSize * height * Float.SIZE_BYTES)
            isDirty = true
        }

        for (i in magnitudes) {
            sampleBuffer.putFloat(i)
        }

        sampleBuffer.rewind()
    }

    fun draw() {
        if (!isVisible) {
            return
        }

        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glVertexAttribPointer(
            vPosition, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(
            aTexCoord, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, texCoordsBuffer
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST)
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST)

        if (isDirty) {
            isDirty = false
            currentLine = 0
            GLES20.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GLES20.GL_ALPHA, fftSize, height, 0, GLES20.GL_ALPHA, GLES10.GL_FLOAT, clearBuffer)
        }

        if (isRunning) {
            GLES20.glTexSubImage2D(GL10.GL_TEXTURE_2D, 0, 0, height - currentLine - 1, fftSize, 1, GLES20.GL_ALPHA, GLES20.GL_FLOAT, sampleBuffer)
            currentLine = (currentLine + 1) % height
            GLES20.glUniform1i(uWaterfallOffset, currentLine)
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST)
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST)
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE)

        GLES20.glUniform1i(uSampleTexture, 2)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawOrderBuffer)

        GLES20.glUniform1i(uSampleTexture, 0)

        GLES20.glDisableVertexAttribArray(vPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }
}
