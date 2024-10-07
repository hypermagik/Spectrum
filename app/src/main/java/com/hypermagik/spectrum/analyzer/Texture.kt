package com.hypermagik.spectrum.analyzer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.opengles.GL10

class Texture(program: Int) {
    val width: Int
        get() = bitmap.width

    private lateinit var bitmap: Bitmap
    private var canvas = Canvas()

    private var vPosition = 0
    private var aTexCoord = 0
    private var uSampleTexture = 0

    private val vertices = floatArrayOf(
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f,
    )
    private val drawOrder = shortArrayOf(0, 1, 2, 3)
    private val coords = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, 0.0f,
    )

    private var vertexBuffer: ByteBuffer
    private var coordsBuffer: ByteBuffer
    private val drawOrderBuffer: ByteBuffer

    private var textures = IntArray(1) { GLES20.GL_NONE }

    init {
        vPosition = GLES20.glGetAttribLocation(program, "vPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uSampleTexture = GLES20.glGetUniformLocation(program, "sampleTexture")

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        vertexBuffer.asFloatBuffer().put(vertices)

        coordsBuffer = ByteBuffer.allocateDirect(coords.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        coordsBuffer.asFloatBuffer().put(coords)

        drawOrderBuffer = ByteBuffer.allocateDirect(drawOrder.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        drawOrderBuffer.asShortBuffer().put(drawOrder)

        GLES20.glGenTextures(1, textures, 0)
    }

    fun setDimensions(width: Int, height: Int, top: Int, left: Int, viewWidth: Int, viewHeight: Int) {
        val l = left.toFloat() / viewWidth
        val r = (left + width).toFloat() / viewWidth
        val t = 1.0f - top.toFloat() / viewHeight
        val b = 1.0f - (top + height).toFloat() / viewHeight

        vertexBuffer.putFloat(0 * Float.SIZE_BYTES, l)
        vertexBuffer.putFloat(1 * Float.SIZE_BYTES, t)
        vertexBuffer.putFloat(2 * Float.SIZE_BYTES, l)
        vertexBuffer.putFloat(3 * Float.SIZE_BYTES, b)
        vertexBuffer.putFloat(4 * Float.SIZE_BYTES, r)
        vertexBuffer.putFloat(5 * Float.SIZE_BYTES, b)
        vertexBuffer.putFloat(6 * Float.SIZE_BYTES, r)
        vertexBuffer.putFloat(7 * Float.SIZE_BYTES, t)

        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvas.setBitmap(bitmap)
    }

    fun clear() {
        bitmap.eraseColor(Color.TRANSPARENT)
    }

    fun getCanvas(): Canvas {
        return canvas
    }

    fun update() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0])

        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    fun draw() {
        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glVertexAttribPointer(
            vPosition, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(
            aTexCoord, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, coordsBuffer
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR)
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR)

        GLES20.glUniform1i(uSampleTexture, 1)

        GLES20.glDrawElements(GLES20.GL_TRIANGLE_FAN, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawOrderBuffer)

        GLES20.glUniform1i(uSampleTexture, 0)

        GLES20.glDisableVertexAttribArray(vPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }
}
