package com.hypermagik.spectrum.analyzer.fft

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import com.hypermagik.spectrum.R
import com.hypermagik.spectrum.analyzer.Texture
import com.hypermagik.spectrum.utils.getFrequencyLabel
import java.util.Locale
import kotlin.math.min
import kotlin.math.round

class Grid(private val context: Context, private val fft: FFT) {
    private val lineColor: Int = context.resources.getColor(R.color.fft_grid_line, null)
    private var textColor: Int = context.resources.getColor(R.color.fft_grid_text, null)
    private var backgroundColor: Int = context.resources.getColor(R.color.fft_grid_background, null)
    private val borderColor = context.resources.getColor(R.color.fft_grid_line, null)

    private var paint: Paint = Paint()

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
        // paint.typeface = context.resources.getFont(R.font.cursed)
    }

    fun onSurfaceCreated(program: Int) {
        background = Texture(program)
        labels = Texture(program)
    }

    fun onSurfaceChanged(width: Int, height: Int, top: Float, bottom: Float) {
        this.width = width.toFloat()
        this.height = (top - bottom) * height
        val topPixel = (1.0f - top) * height

        background.setDimensions(width, this.height.toInt(), topPixel.toInt(), 0, width, height)
        labels.setDimensions(width, this.height.toInt(), topPixel.toInt(), 0, width, height)

        this.padding = 4.0f * context.resources.displayMetrics.density

        paint.textSize = 11.0f * context.resources.displayMetrics.density

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
        paint.pathEffect = DashPathEffect(floatArrayOf(20.0f, 10.0f), 0.0f)
        for (i in 0 until xCount) {
            canvas.drawLine(xCoord[i], 0.0f, xCoord[i], height, paint)
        }
        for (i in 0 until yCount) {
            canvas.drawLine(0.0f, yCoord[i], width, yCoord[i], paint)
        }
        paint.pathEffect = null

        paint.color = borderColor
        canvas.drawLine(0.0f, 0.0f, width, 0.0f, paint)
        canvas.drawLine(0.0f, height - 1, width, height - 1, paint)

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
    fun setFrequencyRange(start: Double, end: Double) {
        val multipliers = floatArrayOf(1.0f, 2.0f, 2.5f, 5.0f)
        var multiplierIndex = 0

        var base = 10
        var step = base
        val range = end - start
        while (range / step > xMaxLines - 1) {
            step = (base * multipliers[multiplierIndex]).toInt()
            multiplierIndex = (multiplierIndex + 1) % multipliers.size
            if (multiplierIndex == 0) {
                base *= 10
            }
        }

        xCount = 0
        val pixelsPerUnit = width / (end - start)

        val labelWidth = paint.measureText(String.format(Locale.getDefault(), "   %.3fM   ", end / 1000000.0)).toInt()
        val numLabels = (end - start).toInt() / step
        val maxLabels = min(numLabels, width.toInt() / labelWidth)
        if (maxLabels > 0) {
            val labelStep = (numLabels + maxLabels - 1) / maxLabels

            var i = if (start < 0) (start / step).toLong() * step.toDouble() else ((start + step - 1) / step).toLong() * step.toDouble()
            while (i < end) {
                val x = (i - start) * pixelsPerUnit
                xCoord[xCount] = x.toFloat()
                // Hide some labels if they are overlapping.
                if (round(i / step).toLong() % labelStep == 0L) {
                    xText[xCount] = getFrequencyLabel(i)
                } else {
                    xText[xCount] = ""
                }
                xCount += 1
                i += step
            }
        }

        isDirty = true
    }

    @Synchronized
    fun updateY() {
        var step = 1
        val range = round(fft.maxDB - fft.minDB)
        while (range / step > yMaxLines - 1) {
            step += 1
        }

        yCount = 0
        val pixelsPerUnit = height / (fft.maxDB - fft.minDB)

        val labelHeight = textVerticalCenterOffset * 5
        val numLabels = range.toInt() / step
        val maxLabels = min(numLabels, (height / labelHeight).toInt())
        if (maxLabels > 0) {
            val labelStep = (numLabels + maxLabels - 1) / maxLabels

            var i = fft.maxDB - (fft.maxDB - (step - 1) + step * if (fft.maxDB > 0) 1 else 0).toInt() / step * step.toFloat()
            while (i < range) {
                // Don't draw over the other axis' labels.
                val y = i * pixelsPerUnit
                if (y > height - bottomScaleSize - 3 * padding) {
                    break
                }
                yCoord[yCount] = y
                if (round((fft.maxDB - i) / step).toLong() % labelStep == 0L) {
                    yText[yCount] = String.format(Locale.getDefault(), "%.0f", fft.maxDB - i)
                } else {
                    yText[yCount] = ""
                }
                yCount += 1
                i += step
            }
        }

        isDirty = true
    }
}
