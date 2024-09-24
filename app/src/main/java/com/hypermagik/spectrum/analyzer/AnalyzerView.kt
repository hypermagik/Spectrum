package com.hypermagik.spectrum.analyzer

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.PreferencesWrapper
import com.hypermagik.spectrum.R
import com.hypermagik.spectrum.analyzer.fft.FFT
import com.hypermagik.spectrum.utils.TAG
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min
import kotlin.math.round

class AnalyzerView(context: Context, private val preferences: Preferences) :
    GLSurfaceView(context), GLSurfaceView.Renderer {

    constructor(context: Context) : this(context, Preferences(null))

    private val isLandscape = context.resources.configuration.orientation == ORIENTATION_LANDSCAPE

    private var program: Int = 0

    private var info = Info(context)
    private var fft = FFT(context, preferences)
    private var waterfall = Waterfall(context, preferences)
    private val channel = Channel(context)

    private var minFrequency: Long = 0
    private var maxFrequency: Long = 1000000
    private var isFrequencyLocked = false
    private var isSourceInput = true

    private var minDB = -135.0f
    private var maxDB = 15.0f
    private var minDBRange = 10.0f
    private var maxDBRange = maxDB - minDB

    var frequency = preferences.sourceSettings.frequency
        private set
    var sampleRate = preferences.sourceSettings.sampleRate
        private set
    var realSamples = false
        private set

    private var gain = PreferencesWrapper.Gain(preferences)

    private var viewFrequency = frequency.toDouble()
    private var viewBandwidth = sampleRate.toDouble()
    private var viewDBCenter = preferences.dbCenter
    private var viewDBRange = preferences.dbRange

    private var channelFrequency = 0.0
    private var channelBandwidth = 0
    private val hasChannel: Boolean get() = isSourceInput && channelBandwidth > 0

    enum class ScrollTarget { None, X, Y, Channel }

    private var scrollTarget = ScrollTarget.None

    var isReady = false
        private set

    private var isRunning = false

    private var scaleGestureDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector
    private var gestureHandler = GestureHandler(this)

    init {
        setEGLContextClientVersion(2)

        setRenderer(this)

        renderMode = RENDERMODE_WHEN_DIRTY

        scaleGestureDetector = ScaleGestureDetector(context, gestureHandler)
        gestureDetector = GestureDetector(context, gestureHandler)
        gestureDetector.setOnDoubleTapListener(gestureHandler)

        updateInfoBar()
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, R.raw.vertex_shader)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, R.raw.fragment_shader)

        program = GLES20.glCreateProgram()

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)

        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)

        if (status[0] == GLES20.GL_FALSE) {
            val log = GLES20.glGetProgramInfoLog(program)

            Log.e(TAG, "Could not link program: $log")

            // TODO: show the error

            assert(false)
        }

        GLES20.glUseProgram(program)

        val uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        GLES20.glUniform1i(uTexture, 0)

        GLES20.glEnable(GL10.GL_BLEND)
        GLES20.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        info.onSurfaceCreated(program)
        fft.onSurfaceCreated(program)
        waterfall.onSurfaceCreated(program)
        channel.onSurfaceCreated(program)
    }

    private fun loadShader(type: Int, resource: Int): Int {
        val text = context.resources.openRawResource(resource).bufferedReader().readText()

        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, text)
            GLES20.glCompileShader(shader)
        }
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(-width, -height, width * 2, height * 2)

        info.onSurfaceChanged(width, height)

        // FFT is set to full height in landscape mode
        // and to half height in portrait mode.
        val fftTop = 1.0f - (Info.HEIGHT * context.resources.displayMetrics.density) / height
        val fftBottom = if (isLandscape) 0.0f else 0.50f
        fft.onSurfaceChanged(width, height, fftTop, fftBottom)

        waterfall.onSurfaceChanged(height, fftBottom)
        channel.onSurfaceChanged(width, height, fftBottom)

        updateFFT()

        isReady = true
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (gain.update()) {
            info.setGain(gain.value)
        }

        synchronized(fft) {
            fft.draw()
        }

        if (hasChannel) {
            synchronized(channel) {
                channel.draw()
            }
        }

        info.draw()

        synchronized(waterfall) {
            waterfall.draw()
        }
    }

    fun saveInstanceState(bundle: Bundle) {
        bundle.putLong("frequency", frequency)
        bundle.putLong("minFrequency", minFrequency)
        bundle.putLong("maxFrequency", maxFrequency)
        bundle.putInt("sampleRate", sampleRate)
        bundle.putBoolean("isFrequencyLocked", isFrequencyLocked)
        bundle.putBoolean("isSourceInput", isSourceInput)
        bundle.putDouble("viewFrequency", viewFrequency)
        bundle.putDouble("viewBandwidth", viewBandwidth)
        bundle.putDouble("channelFrequency", channelFrequency)
        bundle.putInt("channelBandwidth", channelBandwidth)

        fft.saveInstanceState(bundle)
        info.saveInstanceState(bundle)
    }

    fun restoreInstanceState(bundle: Bundle) {
        frequency = bundle.getLong("frequency")
        minFrequency = bundle.getLong("minFrequency")
        maxFrequency = bundle.getLong("maxFrequency")
        sampleRate = bundle.getInt("sampleRate")
        isFrequencyLocked = bundle.getBoolean("isFrequencyLocked")
        isSourceInput = bundle.getBoolean("isSourceInput")
        viewFrequency = bundle.getDouble("viewFrequency")
        viewBandwidth = bundle.getDouble("viewBandwidth")
        channelFrequency = bundle.getDouble("channelFrequency")
        channelBandwidth = bundle.getInt("channelBandwidth")

        fft.restoreInstanceState(bundle)
        info.restoreInstanceState(bundle)

        updateChannel()
    }

    fun start(channelBandwidth: Int) {
        Log.d(TAG, "Starting")

        this.channelFrequency = preferences.channelFrequency.toDouble()
        this.channelBandwidth = channelBandwidth

        isRunning = true

        info.start()

        if (isReady) {
            updateFFT()
        }

        updateChannel()
        updateInfoBar()
    }

    fun stop(restart: Boolean) {
        Log.d(TAG, "Stopping")

        isRunning = false

        info.stop(restart)

        if (!restart) {
            requestRender()
        }
    }

    fun setInputInfo(name: String, details: String, minimumFrequency: Long, maximumFrequency: Long, isSourceInput: Boolean) {
        minFrequency = minimumFrequency
        maxFrequency = maximumFrequency
        this.isSourceInput = isSourceInput
        info.setInputInfo(name, details)
        updateFFT()
    }

    private fun resetFrequencyScale() {
        viewFrequency = frequency.toDouble()
        viewBandwidth = sampleRate.toDouble()
    }

    fun updateFrequency(frequency: Long) {
        this.frequency = frequency
        info.setFrequency(frequency)
        updateFFT()
    }

    fun updateSampleRate(sampleRate: Int) {
        this.sampleRate = sampleRate
        info.setSampleRate(sampleRate)
        resetFrequencyScale()
        updateFFT()
    }

    fun updateRealSamples(realSamples: Boolean) {
        this.realSamples = realSamples
        resetFrequencyScale()
        updateFFT()
    }

    fun setDemodulatorText(text: String?) {
        info.setDemodulatorText(text)
    }

    fun updateFFT(magnitudes: FloatArray, count: Int, fftSize: Int) {
        if (!isReady) {
            Log.d(TAG, "Dropping FFT update, surface not ready")
            return
        }

        synchronized(fft) {
            fft.update(magnitudes, count)
        }

        synchronized(waterfall) {
            waterfall.update(magnitudes, count)
        }

        info.setFFTSize(fftSize)
        info.updateFPS()

        requestRender()
    }

    private fun updateFFT() {
        val sampleRate = if (realSamples) sampleRate / 2.0 else sampleRate.toDouble()
        val frequency = if (realSamples) sampleRate / 2.0 else frequency.toDouble()

        val minViewBandwidth = min(sampleRate, 5000.0)
        val maxViewBandwidth = sampleRate

        viewBandwidth = viewBandwidth.coerceIn(minViewBandwidth, maxViewBandwidth)

        val frequency0 = frequency - sampleRate / 2.0
        val frequency1 = frequency + sampleRate / 2.0
        val viewFrequency0 = frequency0 + viewBandwidth / 2.0
        val viewFrequency1 = frequency1 - viewBandwidth / 2.0

        viewFrequency = if (viewFrequency0 != viewFrequency1) {
            viewFrequency.coerceIn(viewFrequency0, viewFrequency1)
        } else {
            viewFrequency.coerceIn(minFrequency.toDouble(), maxFrequency.toDouble())
        }

        var steppedViewFrequency = if (viewFrequency0 != viewFrequency1) {
            viewFrequency
        } else {
            round(viewFrequency / preferences.frequencyStep) * preferences.frequencyStep
        }
        steppedViewFrequency = steppedViewFrequency.coerceIn(viewFrequency0, viewFrequency1)

        val scale = sampleRate / viewBandwidth
        val translate = (steppedViewFrequency - viewBandwidth / 2.0 - frequency0) / sampleRate * scale

        val frequencyStart = steppedViewFrequency - viewBandwidth / 2
        val frequencyEnd = steppedViewFrequency + viewBandwidth / 2

        viewDBRange = viewDBRange.coerceIn(minDBRange, maxDBRange)
        viewDBCenter = viewDBCenter.coerceIn(minDB + viewDBRange / 2, maxDB - viewDBRange / 2)

        updatePreferencesDB()

        synchronized(fft) {
            fft.updateX(scale.toFloat(), translate.toFloat(), frequency0, frequency1, frequencyStart, frequencyEnd)
            fft.updateY(viewDBCenter - viewDBRange / 2, viewDBCenter + viewDBRange / 2)
        }

        updateChannel()
        requestRender()
    }

    private fun updateChannel() {
        if (!hasChannel) {
            return
        }

        val steppedChannelFrequency = round((frequency + channelFrequency) / preferences.frequencyStep) * preferences.frequencyStep
        preferences.channelFrequency = (steppedChannelFrequency - frequency).toLong()
        preferences.save()

        synchronized(channel) {
            channel.setFrequency(steppedChannelFrequency, channelBandwidth)
            channel.setFrequencyRange(viewFrequency - viewBandwidth / 2, viewFrequency + viewBandwidth / 2)
        }
    }

    private fun highlightChannel(highlight: Boolean) {
        synchronized(channel) {
            channel.highlight(highlight)
        }

        if (!isRunning) {
            requestRender()
        }
    }

    private fun updateInfoBar() {
        info.setFrequency(frequency)
        info.setFrequencyLock(isFrequencyLocked)
        info.setSampleRate(sampleRate)
        info.setGain(gain.value)
        info.setFFTSize(fft.fftSize)
    }

    private fun updatePreferencesDB() {
        if (preferences.dbCenter != viewDBCenter || preferences.dbRange != viewDBRange) {
            preferences.dbCenter = viewDBCenter
            preferences.dbRange = viewDBRange
            preferences.save()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            highlightChannel(false)
        }

        val scaleGestureResult = event?.let { scaleGestureDetector.onTouchEvent(it) }
        val gestureResult = event?.let { gestureDetector.onTouchEvent(it) }

        return scaleGestureResult == true || gestureResult == true
    }

    fun onScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        val fftHeight = if (isLandscape) height else height / 2

        if (focusX < fft.grid.leftScaleSize * 1.5f && focusY < fftHeight) {
            // Scale the Y axis.
            viewDBRange /= scaleFactor

            val focusDB = viewDBCenter + (focusY / fftHeight - 0.5f)
            viewDBCenter = focusDB + (viewDBCenter - focusDB) / scaleFactor
        } else {
            // Scale the X axis.
            if (!isFrequencyLocked) {
                return
            }

            viewBandwidth /= scaleFactor

            val focusFrequency = viewFrequency + (focusX / width - 0.5)
            viewFrequency = focusFrequency + (viewFrequency - focusFrequency) / scaleFactor
        }

        updateFFT()
    }

    fun onDown(x: Float, y: Float) {
        val infoBarheight = Info.HEIGHT * resources.displayMetrics.density * 1.5f
        val fftHeight = if (isLandscape) height else height / 2

        val viewOffset = (frequency - viewFrequency) / viewBandwidth
        val channelCenter = 0.5 + channelFrequency / viewBandwidth + viewOffset
        var channelStart = width * (channelCenter - channelBandwidth / 2 / viewBandwidth)
        var channelEnd = width * (channelCenter + channelBandwidth / 2 / viewBandwidth)

        val minChannelWidth = 32 * resources.displayMetrics.density
        if (channelEnd - channelStart < minChannelWidth) {
            channelStart = width * channelCenter - minChannelWidth / 2
            channelEnd = width * channelCenter + minChannelWidth / 2
        }

        scrollTarget = if (y < infoBarheight) {
            ScrollTarget.None
        } else if (y < fftHeight && x < fft.grid.leftScaleSize * 1.5f) {
            ScrollTarget.Y
        } else if (y < fftHeight && x >= channelStart && x <= channelEnd) {
            highlightChannel(true)
            ScrollTarget.Channel
        } else {
            ScrollTarget.X
        }
    }

    fun onScroll(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        when (scrollTarget) {
            ScrollTarget.X -> onFrequencyScroll(deltaX)
            ScrollTarget.Y -> onDBScroll(deltaY)
            ScrollTarget.Channel -> onChannelScroll(deltaX)
            ScrollTarget.None -> return
        }

        updateFFT()
    }

    private fun onFrequencyScroll(deltaX: Float) {
        viewFrequency += viewBandwidth * deltaX / width

        if (!isRunning || isFrequencyLocked || preferences.isRecording || maxFrequency == 0L) {
            // If locked or recording, can't change frequency.
            return
        }

        var newFrequency = round(viewFrequency / preferences.frequencyStep).toLong() * preferences.frequencyStep
        newFrequency = newFrequency.coerceIn(minFrequency, maxFrequency)

        if (preferences.sourceSettings.frequency != newFrequency) {
            preferences.sourceSettings.frequency = newFrequency
            preferences.save()

            frequency = newFrequency
            info.setFrequency(frequency)
        }
    }

    private fun onDBScroll(deltaY: Float) {
        viewDBCenter -= viewDBRange * deltaY / if (isLandscape) height else height / 2
        viewDBCenter = viewDBCenter.coerceIn(minDB + viewDBRange / 2, maxDB - viewDBRange / 2)
    }

    private fun onChannelScroll(deltaX: Float) {
        if (!isRunning || !hasChannel) {
            return
        }

        channelFrequency -= viewBandwidth * deltaX / width
        channelFrequency = channelFrequency.coerceIn(-sampleRate / 2.0 + channelBandwidth / 2, sampleRate / 2.0 - channelBandwidth / 2)

        updateChannel()
    }

    fun onSingleTap(x: Float, y: Float) {
        val infoBarheight = Info.HEIGHT * resources.displayMetrics.density * 1.5f
        val fftHeight = if (isLandscape) height else height / 2

        if (y < infoBarheight) {
            // Info bar tapped, lock/unlock frequency.
            isFrequencyLocked = !isFrequencyLocked
            info.setFrequencyLock(isFrequencyLocked)
            if (!isFrequencyLocked) {
                // When unlocked, reset the X axis.
                resetFrequencyScale()
                updateFFT()
            } else {
                requestRender()
            }
        } else if (y < fftHeight && x > fft.grid.leftScaleSize * 1.5f) {
            if (isRunning && hasChannel) {
                channelFrequency = viewFrequency + (x / width - 0.5) * viewBandwidth - frequency
                updateChannel()
            }
        }
    }

    fun onDoubleTap(x: Float, y: Float) {
        val infoBarheight = Info.HEIGHT * resources.displayMetrics.density * 1.5f

        if (y < infoBarheight) {
            // Info bar double-tapped, open frequency popup.
            showSetFrequencyDialog()
        } else if (x < fft.grid.leftScaleSize * 1.5f && y < height / 2) {
            // Reset the Y axis.
            viewDBCenter = preferences.dbCenterDefault
            viewDBRange = preferences.dbRangeDefault
            updateFFT()
        } else {
            // Reset the X axis.
            resetFrequencyScale()
            updateFFT()
        }
    }

    fun showSetFrequencyDialog() {
        if (minFrequency == maxFrequency) {
            return
        }

        val layout = LayoutInflater.from(context).inflate(R.layout.set_frequency, null)

        layout.findViewById<EditText>(R.id.set_frequency)?.apply {
            setText(frequency.toString())
            requestFocus()
            selectAll()
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.set_frequency)
            .setView(layout)
            .create()

        layout.findViewById<Button>(R.id.apply_button)?.setOnClickListener { onFrequencyClick(dialog, DialogInterface.BUTTON_POSITIVE, layout) }
        layout.findViewById<Button>(R.id.cancel_button)?.setOnClickListener { onFrequencyClick(dialog, DialogInterface.BUTTON_NEGATIVE, layout) }

        dialog.window?.attributes = dialog.window?.attributes?.apply { gravity = android.view.Gravity.BOTTOM }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        dialog.show()
    }

    private fun onFrequencyClick(dialog: DialogInterface?, which: Int, layout: View) {
        if (which == DialogInterface.BUTTON_NEGATIVE) {
            dialog?.dismiss()
            return
        }

        val input = layout.findViewById<EditText>(R.id.set_frequency) ?: return
        var newFrequency = input.text.toString().toDouble()

        if (newFrequency < minFrequency) {
            // If it's too low, assume it's MHz.
            newFrequency *= 1000000.0
        }

        // TODO: should query the source here to make sure it's in range.
        if (newFrequency < minFrequency || newFrequency > maxFrequency) {
            layout.findViewById<TextView>(R.id.error)?.apply {
                text = context.getString(R.string.frequency_out_of_range, minFrequency, maxFrequency)
                visibility = View.VISIBLE
            }
            return
        }

        preferences.sourceSettings.frequency = newFrequency.toLong()
        preferences.save()

        if (isRunning) {
            frequency = newFrequency.toLong()
            viewFrequency = frequency.toDouble()

            info.setFrequency(frequency)

            updateFFT()
        }

        dialog?.dismiss()
    }
}
