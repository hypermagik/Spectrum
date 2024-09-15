package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import com.hypermagik.spectrum.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

class Info(context: Context) {
    companion object {
        const val HEIGHT = 2 * 14.0f + 3 * 6.0f
    }

    private val textColor = context.resources.getColor(R.color.fft_info_text, null)
    private val shadowColor = context.resources.getColor(R.color.fft_info_shadow, null)
    private val borderColor = context.resources.getColor(R.color.fft_grid_line, null)
    private val backgroundColor = context.resources.getColor(R.color.fft_info_background, null)

    private val height = HEIGHT * context.resources.displayMetrics.density

    private val textSize = 18.0f * context.resources.displayMetrics.density
    private val textY1 = (6.0f + 14.0f) * context.resources.displayMetrics.density
    private val textY1s = (6.0f + 6.0f) * context.resources.displayMetrics.density
    private val textY2 = height - 6.0f * context.resources.displayMetrics.density
    private var textY = textY1

    private val decimalSymbols = DecimalFormatSymbols(Locale.ITALY)

    private var paint = Paint()

    private lateinit var texture: Texture

    private var frequency = 0L
    private var isFrequencyLocked = false
    private var gain = 0
    private var fftSize = 0
    private var sampleRate = 0
    private var inputName = "Source"
    private var inputDetails = ""

    private var referenceTime = System.nanoTime()
    private var frameCounter = 0
    private var fps = 0.0f

    private var isRunning = false
    private var isDirty = true

    init {
        paint.typeface = context.resources.getFont(R.font.cursed)
    }

    fun onSurfaceCreated(program: Int) {
        texture = Texture(program)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        texture.setDimensions(width, height)

        updateText()
    }

    fun draw() {
        if (isDirty) {
            isDirty = false
            updateText()
        }

        texture.draw()
    }

    fun updateFPS() {
        frameCounter++

        val now = System.nanoTime()
        val delta = now - referenceTime

        if (delta >= 1e9) {
            fps = frameCounter * 1e9f / delta
            referenceTime = now
            frameCounter = 0
            isDirty = true
        }
    }

    private fun putText(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align) {
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        canvas.drawText(text, x, y, paint)
    }

    private fun putText(canvas: Canvas, text: String, x: Float, size: Float, color: Int, align: Paint.Align) {
        putText(canvas, text, x, textY, size, color, align)
    }

    private fun updateText() {
        texture.clear()

        val canvas = texture.getCanvas()

        paint.color = backgroundColor
        canvas.drawRect(0.0f, 0.0f, texture.width.toFloat(), height, paint)

        paint.color = borderColor
        canvas.drawLine(0.0f, height - 1, texture.width.toFloat(), height - 1, paint)

        paint.textSize = textSize / 2
        val spacer = paint.measureText(" ")

        var textX = spacer
        textY = textY1

        var text = "000.000.000.000"
        putText(canvas, text, textX, textSize, shadowColor, Paint.Align.LEFT)
        textX += paint.measureText(text)
        text = DecimalFormat("###,###,###,###", decimalSymbols).format(frequency)
        putText(canvas, text, textX, textSize, textColor, Paint.Align.RIGHT)
        text = " •"
        putText(canvas, text, textX, textY1s, textSize / 2, if (isFrequencyLocked) textColor else shadowColor, Paint.Align.LEFT)
        text = " HZ "
        putText(canvas, text, textX, textSize / 2, textColor, Paint.Align.LEFT)
        textX += paint.measureText(text)

        text = " 000"
        putText(canvas, text, textX, textSize, shadowColor, Paint.Align.LEFT)
        textX += paint.measureText(text)
        text = (if (gain > 0) "+" else "") + gain.coerceIn(-99, 99).toString()
        putText(canvas, text, textX, textSize, textColor, Paint.Align.RIGHT)
        text = " DB"
        putText(canvas, text, textX, textSize / 2, textColor, Paint.Align.LEFT)

        textX = texture.width.toFloat() - spacer
        putText(canvas, inputName, textX, textSize, textColor, Paint.Align.RIGHT)

        textX = spacer
        textY = textY2

        val sampleRateInHz = sampleRate < 1000000

        text = "000.000"
        putText(canvas, text, textX, textSize, shadowColor, Paint.Align.LEFT)
        textX += paint.measureText(text)
        text = DecimalFormat("###,###", decimalSymbols).format(if (sampleRateInHz) sampleRate else sampleRate / 1000)
        putText(canvas, text, textX, textSize, textColor, Paint.Align.RIGHT)
        text = if (sampleRateInHz) " HZ " else " KHZ"
        putText(canvas, text, textX, textSize / 2, textColor, Paint.Align.LEFT)
        textX += paint.measureText(text)

        text = " 00000"
        putText(canvas, text, textX, textSize, shadowColor, Paint.Align.LEFT)
        textX += paint.measureText(text)
        text = DecimalFormat("#####", decimalSymbols).format(fftSize)
        putText(canvas, text, textX, textSize, textColor, Paint.Align.RIGHT)
        text = " FFT"
        putText(canvas, text, textX, textSize / 2, textColor, Paint.Align.LEFT)
        textX += paint.measureText(text)

        text = " 000"
        putText(canvas, text, textX, textSize, shadowColor, Paint.Align.LEFT)
        textX += paint.measureText(text)
        val showFPS = isRunning && fps > 0
        if (showFPS) {
            text = String.format(Locale.getDefault(), "%d", fps.roundToInt())
            putText(canvas, text, textX, textSize, textColor, Paint.Align.RIGHT)
        }
        text = " FPS"
        putText(canvas, text, textX, textSize / 2, if (showFPS) textColor else shadowColor, Paint.Align.LEFT)

        textX = texture.width.toFloat() - spacer
        putText(canvas, inputDetails, textX, textSize, textColor, Paint.Align.RIGHT)

        texture.update()
    }

    fun start() {
        frameCounter = 0
        referenceTime = System.nanoTime()
        isRunning = true
        isDirty = true
    }

    fun stop(restart: Boolean) {
        if (!restart) {
            fps = 0.0f
        }
        isRunning = false
        isDirty = true
    }

    fun saveInstanceState(bundle: Bundle) {
        bundle.putString("inputName", inputName)
        bundle.putString("inputDetails", inputDetails)
    }

    fun restoreInstanceState(bundle: Bundle) {
        inputName = bundle.getString("inputName", inputName)
        inputDetails = bundle.getString("inputDetails", inputDetails)
        isDirty = true
    }

    fun setFrequency(frequency: Long) {
        if (this.frequency != frequency) {
            this.frequency = frequency
            isDirty = true
        }
    }

    fun setFrequencyLock(isFrequencyLocked: Boolean) {
        if (this.isFrequencyLocked != isFrequencyLocked) {
            this.isFrequencyLocked = isFrequencyLocked
            isDirty = true
        }
    }

    fun setSampleRate(sampleRate: Int) {
        if (this.sampleRate != sampleRate) {
            this.sampleRate = sampleRate
            isDirty = true
        }
    }

    fun setGain(gain: Int) {
        if (this.gain != gain) {
            this.gain = gain
            isDirty = true
        }
    }

    fun setFFTSize(fftSize: Int) {
        if (this.fftSize != fftSize) {
            this.fftSize = fftSize
            isDirty = true
        }
    }

    fun setInputInfo(name: String, details: String) {
        if (inputName != name || inputDetails != details) {
            inputName = name
            inputDetails = details
            isDirty = true
        }
    }
}
