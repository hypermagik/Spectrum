package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.hypermagik.spectrum.R
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Channel(context: Context) {
    private var vPosition = 0
    private var vColor = 0

    private val vertices = floatArrayOf(
        -100.0f, 1.0f,
        -100.0f, 0.0f,
        -100.0f, 0.0f,
        -100.0f, 1.0f,
        -100.0f, 0.0f,
        -100.0f, 1.0f,
    )
    private val fillDrawOrder = shortArrayOf(0, 1, 2, 3)
    private val edgeDrawOrder = shortArrayOf(0, 1, 1, 2, 2, 3, 3, 0)
    private val centerDrawOrder = shortArrayOf(4, 5)

    private var vertexBuffer: ByteBuffer
    private var fillDrawOrderBuffer: ByteBuffer
    private var edgeDrawOrderBuffer: ByteBuffer
    private var centerDrawOrderBuffer: ByteBuffer

    private var fillColor: FloatArray
    private var edgeColor: FloatArray
    private var centerColor: FloatArray

    private var fillColorNormal: FloatArray
    private var edgeColorNormal: FloatArray
    private var centerColorNormal: FloatArray

    private var fillColorHighlight: FloatArray
    private var edgeColorHighlight: FloatArray
    private var centerColorHighlight: FloatArray

    private val textPaint = Paint()

    private var viewMinFrequency = 0.0
    private var viewMaxFrequency = 0.0

    private var frequency = 0.0
    private var bandwidth = 0

    init {
        var color = context.resources.getColor(R.color.fft_channel_fill, null)
        fillColorNormal = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        color = context.resources.getColor(R.color.fft_channel_edge, null)
        edgeColorNormal = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        color = context.resources.getColor(R.color.fft_channel_center, null)
        centerColorNormal = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        color = context.resources.getColor(R.color.fft_channel_fill_highlight, null)
        fillColorHighlight = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        color = context.resources.getColor(R.color.fft_channel_edge_highlight, null)
        edgeColorHighlight = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        color = context.resources.getColor(R.color.fft_channel_center_highlight, null)
        centerColorHighlight = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)

        fillColor = fillColorNormal
        edgeColor = edgeColorNormal
        centerColor = centerColorNormal

        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 11.0f * context.resources.displayMetrics.density
        textPaint.setShadowLayer(3.0f, 0.0f, 0.0f, Color.BLACK)

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        vertexBuffer.asFloatBuffer().put(vertices)

        fillDrawOrderBuffer = ByteBuffer.allocateDirect(fillDrawOrder.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        fillDrawOrderBuffer.asShortBuffer().put(fillDrawOrder)

        edgeDrawOrderBuffer = ByteBuffer.allocateDirect(edgeDrawOrder.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        edgeDrawOrderBuffer.asShortBuffer().put(edgeDrawOrder)

        centerDrawOrderBuffer = ByteBuffer.allocateDirect(centerDrawOrder.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        centerDrawOrderBuffer.asShortBuffer().put(centerDrawOrder)
    }

    fun onSurfaceCreated(program: Int) {
        vPosition = GLES20.glGetAttribLocation(program, "vPosition")
        vColor = GLES20.glGetUniformLocation(program, "vColor")
    }

    fun setFrequency(frequency: Double, bandwidth: Int, viewMinFrequency: Double, viewMaxFrequency: Double) {
        this.viewMinFrequency = viewMinFrequency
        this.viewMaxFrequency = viewMaxFrequency

        if (bandwidth == 0 || viewMaxFrequency - viewMinFrequency == 0.0) {
            vertexBuffer.asFloatBuffer().put(vertices)
            return
        }

        this.frequency = frequency
        this.bandwidth = bandwidth

        val center = (frequency - viewMinFrequency) / (viewMaxFrequency - viewMinFrequency)
        val width = bandwidth.toDouble() / (viewMaxFrequency - viewMinFrequency)

        vertices[0] = (center - width / 2).toFloat()
        vertices[2] = (center - width / 2).toFloat()
        vertices[4] = (center + width / 2).toFloat()
        vertices[6] = (center + width / 2).toFloat()
        vertices[8] = center.toFloat()
        vertices[10] = center.toFloat()

        vertexBuffer.asFloatBuffer().put(vertices)
    }

    fun draw() {
        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, Float.SIZE_BYTES * 2, vertexBuffer)

        // Draw fill area.
        GLES20.glUniform4fv(vColor, 1, fillColor, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_FAN, fillDrawOrder.size, GLES20.GL_UNSIGNED_SHORT, fillDrawOrderBuffer)

        // Draw the lines.
        GLES20.glLineWidth(2.0f)

        GLES20.glUniform4fv(vColor, 1, edgeColor, 0)
        GLES20.glDrawElements(GLES20.GL_LINES, edgeDrawOrder.size, GLES20.GL_UNSIGNED_SHORT, edgeDrawOrderBuffer)

        GLES20.glUniform4fv(vColor, 1, centerColor, 0)
        GLES20.glDrawElements(GLES20.GL_LINES, centerDrawOrder.size, GLES20.GL_UNSIGNED_SHORT, centerDrawOrderBuffer)

        GLES20.glLineWidth(1.0f)

        GLES20.glDisableVertexAttribArray(vPosition)
    }

    fun highlight(highlight: Boolean) {
        if (highlight) {
            fillColor = fillColorHighlight
            edgeColor = edgeColorHighlight
            centerColor = centerColorHighlight
        } else {
            fillColor = fillColorNormal
            edgeColor = edgeColorNormal
            centerColor = centerColorNormal
        }
    }
}