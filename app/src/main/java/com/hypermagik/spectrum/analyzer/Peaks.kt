package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Bundle
import com.hypermagik.spectrum.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.pow

class Peaks(private val context: Context) {
    private var vPosition: Int = 0
    private var vColor: Int = 0
    private var aTexCoord: Int = 0
    private var uSampleTexture: Int = 0

    private var coordBuffer: ByteBuffer
    private var drawOrderBuffer: ByteBuffer

    private val coords = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, 0.0f,
    )
    private val drawOrder = shortArrayOf(0, 1, 2, 3)

    private var vertexBuffer: ByteBuffer = ByteBuffer.allocateDirect(4 * 3 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    private var textures = IntArray(1) { GLES20.GL_NONE }

    private val textureSize = (60.0f * context.resources.displayMetrics.density).toInt()
    private var bitmap = Bitmap.createBitmap(textureSize, textureSize, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint()

    private var history = FloatArray(0)

    private var peakIndex = -1
    private var peakMagnitude = 0.0f
    private var peakFrequency = 0.0

    private var viewWidth = 0
    private var viewHeight = 0
    private var fftSize = 0
    private var fftScale = 1.0f
    private var frequencyStart = 0.0
    private var frequencyEnd = 0.0
    private var fftMinY = 0.0f
    private var fftMaxY = 1.0f
    private var minDB = 0.0f
    private var maxDB = 0.0f
    private var lastUpdate = 0L
    private var minUpdateInterval = 1000L / 20
    private var isDirty = true

    private val isLandscape = context.resources.configuration.orientation == ORIENTATION_LANDSCAPE

    init {
        coordBuffer = ByteBuffer.allocateDirect(coords.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        coordBuffer.asFloatBuffer().apply { put(coords) }

        drawOrderBuffer = ByteBuffer.allocateDirect(drawOrder.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        drawOrderBuffer.asShortBuffer().apply { put(drawOrder) }

        bitmap.eraseColor(Color.TRANSPARENT)

        paint.color = Color.YELLOW
        paint.strokeWidth = 2.0f
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10.0f * context.resources.displayMetrics.density
        paint.typeface = context.resources.getFont(R.font.cursed)
    }

    fun onSurfaceCreated(program: Int) {
        vPosition = GLES20.glGetAttribLocation(program, "vPosition")
        vColor = GLES20.glGetUniformLocation(program, "vColor")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uSampleTexture = GLES20.glGetUniformLocation(program, "sampleTexture")

        GLES20.glGenTextures(textures.size, textures, 0)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height

        // FFT is set to full height in landscape mode
        // and to half height in portrait mode.
        fftMinY = if (isLandscape) 0.0f else 0.5f
        fftMaxY = 1.0f - Info.HEIGHT * context.resources.displayMetrics.density / height
    }

    fun saveInstanceState(bundle: Bundle) {
        bundle.putInt("peakIndex", peakIndex)
        bundle.putFloat("peakMagnitude", peakMagnitude)
        bundle.putDouble("peakFrequency", peakFrequency)
        bundle.putInt("fftSize", fftSize)
        bundle.putFloat("fftScale", fftScale)
        bundle.putFloat("minDB", minDB)
        bundle.putFloat("maxDB", maxDB)
        bundle.putFloat("minY", fftMinY)
        bundle.putFloat("maxY", fftMaxY)
    }

    fun restoreInstanceState(bundle: Bundle) {
        peakIndex = bundle.getInt("peakIndex")
        peakMagnitude = bundle.getFloat("peakMagnitude")
        peakFrequency = bundle.getDouble("peakFrequency")
        fftSize = bundle.getInt("fftSize")
        fftScale = bundle.getFloat("fftScale")
        minDB = bundle.getFloat("minDB")
        maxDB = bundle.getFloat("maxDB")
        fftMinY = bundle.getFloat("minY")
        fftMaxY = bundle.getFloat("maxY")
    }

    fun update(magnitudes: FloatArray) {
        if (history.size != magnitudes.size) {
            history = FloatArray(magnitudes.size)
        }

        for (i in magnitudes.indices) {
            history[i] = max(history[i] * 0.95f, magnitudes[i])
        }

        val now = System.currentTimeMillis()
        if (now - lastUpdate < minUpdateInterval) {
            return
        }

        lastUpdate = now
        fftSize = magnitudes.size
        peakMagnitude = Float.NEGATIVE_INFINITY

        for (i in history.indices) {
            if (peakMagnitude < history[i]) {
                peakMagnitude = history[i]
                peakIndex = i
            }
        }

        peakFrequency = frequencyStart + (frequencyEnd - frequencyStart) * peakIndex / (fftSize - 1)

        update()
    }

    private fun update() {
        bitmap.eraseColor(Color.TRANSPARENT)

        val midPoint = bitmap.width / 2.0f

        paint.style = Paint.Style.STROKE
        canvas.drawCircle(midPoint, midPoint, midPoint / 6.0f, paint)

        paint.style = Paint.Style.FILL
        canvas.drawText(String.format(Locale.getDefault(), "%.3fM", peakFrequency / 1000000.0f), midPoint, midPoint / 2.0f, paint)

        val halfWidth = bitmap.width.toFloat() / viewWidth / 2.0f / fftScale
        var halfHeight = bitmap.height.toFloat() / viewHeight / 2.0f

        // Convert to dB scale.
        halfHeight = 10.0.pow(((maxDB - minDB) * halfHeight / (fftMaxY - fftMinY)) / 20.0).toFloat()

        val x = peakIndex.toFloat() / (fftSize - 1)

        vertexBuffer.putFloat(x - halfWidth)
        vertexBuffer.putFloat(peakMagnitude * halfHeight)
        vertexBuffer.putFloat(3.0f)
        vertexBuffer.putFloat(x - halfWidth)
        vertexBuffer.putFloat(peakMagnitude / halfHeight)
        vertexBuffer.putFloat(3.0f)
        vertexBuffer.putFloat(x + halfWidth)
        vertexBuffer.putFloat(peakMagnitude / halfHeight)
        vertexBuffer.putFloat(3.0f)
        vertexBuffer.putFloat(x + halfWidth)
        vertexBuffer.putFloat(peakMagnitude * halfHeight)
        vertexBuffer.putFloat(3.0f)
        vertexBuffer.rewind()

        isDirty = true
    }

    @Synchronized
    fun setFrequencyRange(start: Double, end: Double, scale: Float) {
        frequencyStart = start
        frequencyEnd = end
        fftScale = scale

        lastUpdate = 0
        update()
    }

    @Synchronized
    fun setDBRange(start: Float, end: Float) {
        minDB = start
        maxDB = end

        lastUpdate = 0
        update()
    }

    fun draw() {
        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glVertexAttribPointer(
            vPosition, 3, GLES20.GL_FLOAT, false, 3 * Float.SIZE_BYTES, vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(
            aTexCoord, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, coordBuffer
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])

        if (isDirty) {
            isDirty = false
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0)
        }

        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST)
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST)

        GLES20.glUniform1i(uSampleTexture, 1)

        GLES20.glDrawElements(GLES20.GL_TRIANGLE_FAN, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawOrderBuffer)

        GLES20.glUniform1i(uSampleTexture, 0)

        GLES20.glDisableVertexAttribArray(vPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }
}
