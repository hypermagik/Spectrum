package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import com.hypermagik.spectrum.R

class Grid(val context: Context) {
    private val lineColor: Int = context.resources.getColor(R.color.fft_grid_line, null)
    private var textColor: Int = context.resources.getColor(R.color.fft_grid_text, null)
    private var backgroundColor: Int = context.resources.getColor(R.color.fft_grid_background, null)

    private var paint: Paint = Paint()

    private var top = 0f
    private var width = 0f
    private var height = 0f
    private var padding = 16f
    private var leftMarginSize = 0f
    private var bottomMarginSize = 0f
    private var verticalTextCenterOffset = 0f

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
        this.height = if (isLandscape) height.toFloat() else height / 2f

        paint.textSize = 12f * context.resources.displayMetrics.density

        val rect = Rect()
        paint.getTextBounds("-199", 0, 4, rect)

        leftMarginSize = 0f
        bottomMarginSize = 0f
        verticalTextCenterOffset = rect.height() / 2f

        update()
    }

    private fun update() {
        background.clear()
        var canvas = background.getCanvas()

        paint.color = backgroundColor
        canvas.drawRect(0f, 0f, width, height, paint)

        paint.color = lineColor
        canvas.drawLine(0f, height - 1, width, height - 1, paint)

        val x = floatArrayOf(1 * width / 4f, 2 * width / 4f, 3 * width / 4f)
        val y = floatArrayOf(1 * (height - top) / 4f, 2 * (height - top) / 4f, 3 * (height - top) / 4f)

        val xText = arrayOf("99.0M", "100.0M", "101.0M")
        val yText = arrayOf("-30", "-60", "-90")

        paint.color = lineColor
        paint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        for (i in x.indices) {
            canvas.drawLine(x[i], top, x[i], height - bottomMarginSize, paint)
        }
        for (i in y.indices) {
            canvas.drawLine(leftMarginSize, top + y[i], width, top + y[i], paint)
        }

        paint.pathEffect = null

        background.update()

        labels.clear()
        canvas = labels.getCanvas()

        paint.color = textColor
        paint.setShadowLayer(3f, 0f, 0f, backgroundColor)

        paint.textAlign = Paint.Align.CENTER
        for (i in x.indices) {
            canvas.drawText(xText[i], x[i], height - padding, paint)
        }
        paint.textAlign = Paint.Align.LEFT
        for (i in y.indices) {
            canvas.drawText(yText[i], padding, top + y[i] + verticalTextCenterOffset, paint)
        }

        paint.setShadowLayer(0f, 0f, 0f, backgroundColor)

        labels.update()
    }

    fun drawBackground() {
        if (isDirty) {
            isDirty = false
            update()
        }

        background.draw()
    }

    fun drawLabels() {
        labels.draw()
    }
}
