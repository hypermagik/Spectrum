package com.hypermagik.spectrum.analyzer.fft

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import com.hypermagik.spectrum.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.pow

class Peaks(context: Context, private val fft: FFT) {
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

    private var peakIndex = -1
    private var peakMagnitude = 0.0f
    private var peakFrequency = 0.0

    private var viewFrequencyStart = 0.0
    private var viewFrequencyEnd = 0.0
    private var frequencyStart = 0.0
    private var frequencyEnd = 0.0
    private var lastUpdate = 0L
    private var minUpdateInterval = 1000L / 20
    private var isDirty = true

    init {
        coordBuffer = ByteBuffer.allocateDirect(coords.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        coordBuffer.asFloatBuffer().apply { put(coords) }

        drawOrderBuffer = ByteBuffer.allocateDirect(drawOrder.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        drawOrderBuffer.asShortBuffer().apply { put(drawOrder) }

        bitmap.eraseColor(Color.TRANSPARENT)

        paint.color = context.resources.getColor(R.color.fft_peak, null)
        paint.strokeWidth = 2.0f
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 11f * context.resources.displayMetrics.density
        // paint.typeface = context.resources.getFont(R.font.cursed)
    }

    fun onSurfaceCreated(program: Int) {
        vPosition = GLES20.glGetAttribLocation(program, "vPosition")
        vColor = GLES20.glGetUniformLocation(program, "vColor")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uSampleTexture = GLES20.glGetUniformLocation(program, "sampleTexture")

        GLES20.glGenTextures(textures.size, textures, 0)
    }

    fun update() {
        val now = System.currentTimeMillis()
        if (now - lastUpdate < minUpdateInterval) {
            return
        }

        lastUpdate = now

        peakMagnitude = Float.NEGATIVE_INFINITY

        val startIndex = fft.fftSize * (viewFrequencyStart - frequencyStart) / (frequencyEnd - frequencyStart)
        val endIndex = fft.fftSize * (viewFrequencyEnd - frequencyStart) / (frequencyEnd - frequencyStart)

        val buffer = fft.getBufferForPeaks()
        for (i in startIndex.toInt() until endIndex.toInt()) {
            val offset = (i * fft.coordsPerVertex + 1) * Float.SIZE_BYTES
            val magnitude = buffer.getFloat(offset)
            if (magnitude > peakMagnitude) {
                peakMagnitude = magnitude
                peakIndex = i
            }
        }

        peakFrequency = frequencyStart + (frequencyEnd - frequencyStart) * peakIndex / (fft.fftSize - 1)

        bitmap.eraseColor(Color.TRANSPARENT)

        val midPoint = bitmap.width / 2.0f

        paint.style = Paint.Style.STROKE
        canvas.drawCircle(midPoint, midPoint, midPoint / 6.0f, paint)

        val text = if (abs(peakFrequency) < 1000) {
            String.format(Locale.getDefault(), "%.0fHz", peakFrequency)
        } else if (abs(peakFrequency) < 1000000) {
            String.format(Locale.getDefault(), "%.3fK", peakFrequency / 1000.0f)
        } else {
            String.format(Locale.getDefault(), "%.3fM", peakFrequency / 1000000.0f)
        }

        paint.style = Paint.Style.FILL
        canvas.drawText(text, midPoint, midPoint / 2.0f, paint)

        val halfWidth = bitmap.width.toFloat() / fft.viewWidth / 2.0f / fft.xScale
        var halfHeight = bitmap.height.toFloat() / fft.viewHeight / 2.0f

        // Convert to dB scale.
        halfHeight = 10.0.pow(((fft.maxDB - fft.minDB) * halfHeight / (fft.maxY - fft.minY)) / 20.0).toFloat()

        val x = peakIndex.toFloat() / (fft.fftSize - 1)

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

    fun forceUpdate() {
        lastUpdate = 0
        update()
    }

    @Synchronized
    fun setFrequencyRange(frequencyStart: Double, frequencyEnd: Double, viewFrequencyStart: Double, viewFrequencyEnd: Double) {
        this.frequencyStart = frequencyStart
        this.frequencyEnd = frequencyEnd
        this.viewFrequencyStart = viewFrequencyStart
        this.viewFrequencyEnd = viewFrequencyEnd
    }

    fun draw() {
        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glVertexAttribPointer(vPosition, 3, GLES20.GL_FLOAT, false, 3 * Float.SIZE_BYTES, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, coordBuffer)

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
