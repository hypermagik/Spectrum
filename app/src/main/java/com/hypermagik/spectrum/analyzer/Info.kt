package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
    private val textPadding = 6.0f * context.resources.displayMetrics.density
    private val textY1 = textPadding + 14.0f * context.resources.displayMetrics.density
    private val textY1s = textPadding + 6.0f * context.resources.displayMetrics.density
    private val textY2 = height - textPadding
    private val textY2m: Float
    private val textY2u: Float
    private var textY = textY1

    private val demodulatorTextSize = 11.0f * context.resources.displayMetrics.density
    private val demodulatorTextY = height + demodulatorTextSize + (6.0f * context.resources.displayMetrics.density)

    private val decimalSymbols = DecimalFormatSymbols(Locale.ITALY)

    private var paint = Paint()
    private var demodulatorTextPaint = Paint()

    private lateinit var texture: Texture

    private var frequency = 0L
    private var isFrequencyLocked = false
    private var gain = 0
    private var fftSize = 0
    private var sampleRate = 0
    private var analyzerInput: String? = null
    private var channelFrequency = 0L
    private var demodulatorName: String? = null
    private var demodulatorText: String? = null
    private var averageWorkerUsage = 0.0
    private var maxWorkerUsage = 0.0

    private var referenceTime = System.nanoTime()
    private var frameCounter = 0
    private var fps = 0.0f

    private var isRunning = false
    private var isDirty = true

    init {
        paint.typeface = context.resources.getFont(R.font.cursed)
        paint.textSize = 2 * textSize / 3

        val bounds = Rect()
        paint.getTextBounds("0", 0, 1, bounds)

        val padding = (height - 3 * bounds.height() - 2 * textPadding) / 2
        textY2m = textY2 - bounds.height() - padding
        textY2u = textY2m - bounds.height() - padding

        demodulatorTextPaint.color = Color.WHITE
        demodulatorTextPaint.textSize = demodulatorTextSize
        demodulatorTextPaint.textAlign = Paint.Align.RIGHT
        demodulatorTextPaint.setShadowLayer(3.0f, 0.0f, 0.0f, backgroundColor)
    }

    fun onSurfaceCreated(program: Int) {
        texture = Texture(program)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        texture.setDimensions(width, (this.height + demodulatorTextY).toInt(), 0, 0, width, height)

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

        if (isRunning) {
            paint.color = Color.YELLOW
            canvas.drawLine(0.0f, height - 1, texture.width * maxWorkerUsage.toFloat().coerceIn(0.0f, 1.0f), height - 1, paint)
            paint.color = textColor
            canvas.drawLine(0.0f, height - 1, texture.width * averageWorkerUsage.toFloat().coerceIn(0.0f, 1.0f), height - 1, paint)
        }

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

        text = "000.000.000.000"
        putText(canvas, text, textX, textY2u, 2 * textSize / 3, shadowColor, Paint.Align.RIGHT)

        if (channelFrequency != 0L) {
            text = DecimalFormat("###,###,###,###", decimalSymbols).format(channelFrequency)
            putText(canvas, text, textX, textY2u, 2 * textSize / 3, textColor, Paint.Align.RIGHT)
        }

        demodulatorName?.run {
            putText(canvas, this, textX, textY2m, 2 * textSize / 3, textColor, Paint.Align.RIGHT)
        } ?: run {
            putText(canvas, "No demodulator", textX, textY2m, 2 * textSize / 3, shadowColor, Paint.Align.RIGHT)
        }

        analyzerInput?.run {
            putText(canvas, this, textX, textY2, 2 * textSize / 3, textColor, Paint.Align.RIGHT)
        } ?: run {
            putText(canvas, "Source", textX, textY2, 2 * textSize / 3, if (channelFrequency == 0L) shadowColor else textColor, Paint.Align.RIGHT)
        }

        demodulatorText?.run {
            textX = texture.width.toFloat() - spacer
            textY = demodulatorTextY
            canvas.drawText(trim(), textX, textY, demodulatorTextPaint)
        }

        texture.update()
    }

    fun start(demodulatorName: String?) {
        frameCounter = 0
        referenceTime = System.nanoTime()
        analyzerInput = ""
        channelFrequency = 0
        this.demodulatorName = demodulatorName
        demodulatorText = ""
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
        bundle.putLong("frequency", frequency)
        bundle.putBoolean("isFrequencyLocked", isFrequencyLocked)
        bundle.putInt("gain", gain)
        bundle.putInt("fftSize", fftSize)
        bundle.putInt("sampleRate", sampleRate)
        bundle.putString("analyzerInput", analyzerInput)
        bundle.putString("demodulatorName", demodulatorName)
        bundle.putString("demodulatorText", demodulatorText)
    }

    fun restoreInstanceState(bundle: Bundle) {
        frequency = bundle.getLong("frequency")
        isFrequencyLocked = bundle.getBoolean("isFrequencyLocked")
        gain = bundle.getInt("gain")
        fftSize = bundle.getInt("fftSize")
        sampleRate = bundle.getInt("sampleRate")
        analyzerInput = bundle.getString("analyzerInput", analyzerInput)
        demodulatorName = bundle.getString("demodulatorName", demodulatorName)
        demodulatorText = bundle.getString("demodulatorText", demodulatorText)
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

    fun setAnalyzerInput(output: String?) {
        if (analyzerInput != output) {
            analyzerInput = output
            isDirty = true
        }
    }

    fun setChannelFrequency(channelFrequency: Long) {
        if (this.channelFrequency != channelFrequency) {
            this.channelFrequency = channelFrequency
            isDirty = true
        }
    }

    fun setDemodulatorText(text: String?) {
        if (text != null) {
            demodulatorText = text
            isDirty = true
        }
    }

    fun setWorkerUsage(averageUsage: Double, maxUsage: Double) {
        if (averageWorkerUsage != averageUsage || maxWorkerUsage != maxUsage) {
            averageWorkerUsage = averageUsage
            maxWorkerUsage = maxUsage
            isDirty = true
        }
    }
}
