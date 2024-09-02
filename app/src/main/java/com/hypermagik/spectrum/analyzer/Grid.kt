package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import com.hypermagik.spectrum.R
import java.util.Locale
import kotlin.math.min
import kotlin.math.round

class Grid(val context: Context) {
    private val lineColor: Int = context.resources.getColor(R.color.fft_grid_line, null)
    private var textColor: Int = context.resources.getColor(R.color.fft_grid_text, null)
    private var backgroundColor: Int = context.resources.getColor(R.color.fft_grid_background, null)

    private var paint: Paint = Paint()

    private var top = 0.0f
    private var width = 0.0f
    private var height = 0.0f
    private var padding = 0.0f
    private var textVerticalCenterOffset = 0.0f
    private var bottomScaleSize = 0.0f

    var leftScaleSize = 0.0f
        private set

    private var xMaxLines = 12
    private var yMaxLines = 12
    private var xCoord = FloatArray(xMaxLines)
    private var yCoord = FloatArray(yMaxLines)
    private var xText = Array(xMaxLines) { "" }
    private var yText = Array(yMaxLines) { "" }
    private var xCount = 0
    private var yCount = 0

    private var isDirty = true

    private lateinit var background: Texture
    private lateinit var labels: Texture

    init {
        paint.typeface = context.resources.getFont(R.font.cursed)
    }

    fun onSurfaceCreated(program: Int) {
        background = Texture(program)
        labels = Texture(program)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        background.setDimensions(width, height)
        labels.setDimensions(width, height)

        val isLandscape = context.resources.configuration.orientation == ORIENTATION_LANDSCAPE

        this.top = Info.HEIGHT * context.resources.displayMetrics.density
        this.width = width.toFloat()
        this.height = if (isLandscape) height.toFloat() else height / 2.0f
        this.padding = 4.0f * context.resources.displayMetrics.density

        paint.textSize = 10.0f * context.resources.displayMetrics.density

        val rect = Rect()
        paint.getTextBounds("-199", 0, 4, rect)

        textVerticalCenterOffset = rect.height() / 2.0f
        leftScaleSize = rect.width().toFloat()
        bottomScaleSize = rect.height().toFloat()

        update()
    }

    private fun update() {
        background.clear()
        var canvas = background.getCanvas()

        paint.color = backgroundColor
        canvas.drawRect(0.0f, 0.0f, width, height, paint)

        paint.color = lineColor
        canvas.drawLine(0.0f, height - 1, width, height - 1, paint)

        paint.color = lineColor
        paint.pathEffect = DashPathEffect(floatArrayOf(20.0f, 10.0f), 0.0f)
        for (i in 0 until xCount) {
            canvas.drawLine(xCoord[i], top, xCoord[i], height, paint)
        }
        for (i in 0 until yCount) {
            canvas.drawLine(0.0f, yCoord[i], width, yCoord[i], paint)
        }

        paint.pathEffect = null

        background.update()

        labels.clear()
        canvas = labels.getCanvas()

        paint.color = textColor
        paint.setShadowLayer(3.0f, 0.0f, 0.0f, backgroundColor)

        paint.textAlign = Paint.Align.CENTER
        for (i in 0 until xCount) {
            canvas.drawText(xText[i], xCoord[i], height - padding, paint)
        }
        paint.textAlign = Paint.Align.LEFT
        for (i in 0 until yCount) {
            canvas.drawText(yText[i], padding, yCoord[i] + textVerticalCenterOffset, paint)
        }

        paint.setShadowLayer(0.0f, 0.0f, 0.0f, backgroundColor)

        labels.update()
    }

    fun drawBackground() {
        if (isDirty) {
            synchronized(this) {
                isDirty = false
                update()
            }
        }

        background.draw()
    }

    fun drawLabels() {
        labels.draw()
    }

    @Synchronized
    fun setFrequencyRange(start: Float, end: Float) {
        var step = 1000
        val range = end - start
        while (range / step > xMaxLines) {
            step *= 2
        }

        xCount = 0
        val pixelsPerUnit = width / (end - start)

        val labelWidth = paint.measureText(String.format(Locale.getDefault(), " %.3fM ", end / 1000000.0f)).toInt()
        val numLabels = (end - start).toInt() / step
        val maxLabels = min(numLabels, width.toInt() / labelWidth)
        val labelStep = (numLabels + maxLabels) / maxLabels

        var i = ((start + step - 1) / step).toLong() * step.toFloat()
        while (i < end) {
            val x = (i - start) * pixelsPerUnit
            xCoord[xCount] = x
            // Hide some labels if they are overlapping.
            if (round(i / step).toLong() % labelStep == 0L) {
                xText[xCount] = String.format(Locale.getDefault(), "%.3fM", i / 1000000.0f)
            } else {
                xText[xCount] = ""
            }
            xCount += 1
            i += step
        }

        isDirty = true
    }

    @Synchronized
    fun setDBRange(start: Float, end: Float) {
        var step = 1
        val range = round(end - start)
        while (range / step > yMaxLines) {
            step += 1
        }

        yCount = 0
        val usableHeight = height - top
        val pixelsPerUnit = usableHeight / (end - start)

        var i = end - (end - (step - 1) + step * if (end > 0) 1 else 0).toInt() / step * step.toFloat()
        while (i < range) {
            // Don't draw over the other axis' labels.
            val y = top + i * pixelsPerUnit
            if (y > height - bottomScaleSize - 3 * padding) {
                break
            }
            yCoord[yCount] = y
            yText[yCount] = String.format(Locale.getDefault(), "%.0f", end - i)
            yCount += 1
            i += step
        }

        isDirty = true
    }
}
