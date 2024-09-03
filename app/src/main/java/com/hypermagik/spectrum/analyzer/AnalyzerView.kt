package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.R
import com.hypermagik.spectrum.utils.TAG
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class AnalyzerView(context: Context, private val preferences: Preferences) :
    GLSurfaceView(context), GLSurfaceView.Renderer {

    constructor(context: Context) : this(context, Preferences(null))

    private var program: Int = 0

    private var grid = Grid(context)
    private var info = Info(context)
    private var fft = FFT(context, preferences.fftSize)
    private var peaks = Peaks(context)
    private var waterfall = Waterfall(context, preferences.fftSize)

    private var minFrequency: Long = 0
    private var maxFrequency: Long = 1000000
    private var isFrequencyLocked = false

    private var minDB = -135.0f
    private var maxDB = 15.0f
    private var minDBRange = 10.0f
    private var maxDBRange = maxDB - minDB
    private var defaultDBCenter = -55.0f
    private var defaultDBRange = 120.0f

    private var previousSampleRate = 0
    private var previousGain = 0

    private var viewFrequency = preferences.frequency.toDouble()
    private var viewBandwidth = preferences.sampleRate.toDouble()
    private var viewDBCenter = defaultDBCenter
    private var viewDBRange = defaultDBRange

    private var isReady = false
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

        info.setFrequency(preferences.frequency)
        info.setFrequencyLock(isFrequencyLocked)
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

        grid.onSurfaceCreated(program)
        info.onSurfaceCreated(program)
        fft.onSurfaceCreated(program)
        peaks.onSurfaceCreated(program)
        waterfall.onSurfaceCreated(program)
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

        grid.onSurfaceChanged(width, height)
        info.onSurfaceChanged(width, height)
        fft.onSurfaceChanged(height)
        peaks.onSurfaceChanged(width, height)
        waterfall.onSurfaceChanged(height)

        updateFFTandGrid()

        isReady = true
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (previousGain != preferences.gain) {
            previousGain = preferences.gain
            info.setGain(previousGain)
        }

        grid.drawBackground()

        synchronized(fft) {
            fft.draw()
        }

        synchronized(peaks) {
            peaks.draw()
        }

        grid.drawLabels()
        info.draw()

        synchronized(waterfall) {
            waterfall.draw()
        }
    }

    fun saveInstanceState(bundle: Bundle) {
        bundle.putBoolean("isFrequencyLocked", isFrequencyLocked)
        bundle.putDouble("viewFrequency", viewFrequency)
        bundle.putDouble("viewBandwidth", viewBandwidth)
        bundle.putFloat("viewDBCenter", viewDBCenter)
        bundle.putFloat("viewDBRange", viewDBRange)

        fft.saveInstanceState(bundle)
        peaks.saveInstanceState(bundle)
    }

    fun restoreInstanceState(bundle: Bundle) {
        isFrequencyLocked = bundle.getBoolean("isFrequencyLocked")
        viewFrequency = bundle.getDouble("viewFrequency")
        viewBandwidth = bundle.getDouble("viewBandwidth")
        viewDBCenter = bundle.getFloat("viewDBCenter")
        viewDBRange = bundle.getFloat("viewDBRange")

        fft.restoreInstanceState(bundle)
        peaks.restoreInstanceState(bundle)

        updateInfoBar()
    }

    fun start() {
        Log.d(TAG, "Starting")

        isRunning = true

        info.start()
        waterfall.start()

        if (previousSampleRate != preferences.sampleRate) {
            previousSampleRate = preferences.sampleRate
            viewFrequency = preferences.frequency.toDouble()
            viewBandwidth = preferences.sampleRate.toDouble()
            viewDBCenter = defaultDBCenter
            viewDBRange = defaultDBRange
        }

        if (isReady) {
            updateFFTandGrid()
        }

        updateInfoBar()
    }

    fun stop(restart: Boolean) {
        Log.d(TAG, "Stopping")

        isRunning = false

        info.stop(restart)
        waterfall.stop()
    }

    fun setFrequencyRange(minimumFrequency: Long, maximumFrequency: Long) {
        minFrequency = minimumFrequency
        maxFrequency = maximumFrequency
    }

    fun updateFFT(magnitudes: FloatArray) {
        if (!isReady) {
            Log.d(TAG, "Dropping FFT update, surface not ready")
            return
        }

        synchronized(fft) {
            fft.update(magnitudes)
        }

        synchronized(peaks) {
            peaks.update(magnitudes)
        }

        synchronized(waterfall) {
            waterfall.update(magnitudes)
        }

        info.updateFPS()

        requestRender()
    }

    private fun updateFFTandGrid() {
        val minViewBandwidth = preferences.sampleRate / (preferences.fftSize / 128.0)
        val maxViewBandwidth = preferences.sampleRate / 1.0

        viewBandwidth = viewBandwidth.coerceIn(minViewBandwidth, maxViewBandwidth)

        val frequency0 = preferences.frequency - preferences.sampleRate / 2.0
        val frequency1 = preferences.frequency + preferences.sampleRate / 2.0
        val viewFrequency0 = frequency0 + viewBandwidth / 2.0
        val viewFrequency1 = frequency1 - viewBandwidth / 2.0

        viewFrequency = viewFrequency.coerceIn(viewFrequency0, viewFrequency1)

        val scale = preferences.sampleRate / viewBandwidth
        val translate = (viewFrequency - viewBandwidth / 2.0 - frequency0) / preferences.sampleRate * scale

        fft.updateX(scale.toFloat(), translate.toFloat())

        viewDBRange = viewDBRange.coerceIn(minDBRange, maxDBRange)
        viewDBCenter = viewDBCenter.coerceIn(minDB + viewDBRange / 2, maxDB - viewDBRange / 2)

        fft.updateY(viewDBCenter - viewDBRange / 2, viewDBCenter + viewDBRange / 2)

        val frequencyStart = viewFrequency - viewBandwidth / 2
        val frequencyEnd = viewFrequency + viewBandwidth / 2

        grid.setFrequencyRange(frequencyStart, frequencyEnd)
        peaks.setFrequencyRange(frequencyStart, frequencyEnd, scale.toFloat())

        val dbStart = viewDBCenter - viewDBRange / 2
        val dbEnd = viewDBCenter + viewDBRange / 2

        grid.setDBRange(dbStart, dbEnd)
        peaks.setDBRange(dbStart, dbEnd)

        requestRender()
    }

    private fun updateInfoBar() {
        info.setFrequency(preferences.frequency)
        info.setFrequencyLock(isFrequencyLocked)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val scaleGestureResult = event?.let { scaleGestureDetector.onTouchEvent(it) }
        val gestureResult = event?.let { gestureDetector.onTouchEvent(it) }
        return scaleGestureResult == true || gestureResult == true
    }

    fun onScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        val fftHeight = if (context.resources.configuration.orientation == ORIENTATION_LANDSCAPE) height else height / 2

        if (focusX < grid.leftScaleSize * 1.5f && focusY < fftHeight) {
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

        updateFFTandGrid()
    }

    fun onScroll(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        val fftHeight = if (context.resources.configuration.orientation == ORIENTATION_LANDSCAPE) height else height / 2

        if (x < grid.leftScaleSize * 1.5f && y < fftHeight) {
            // Scroll the Y axis.
            viewDBCenter -= viewDBRange * deltaY / fftHeight
            viewDBCenter = viewDBCenter.coerceIn(minDB + viewDBRange / 2, maxDB - viewDBRange / 2)
        } else {
            // Scroll the X axis.
            viewFrequency += viewBandwidth * deltaX / width

            // If running and not locked, update source frequency.
            if (isRunning && !isFrequencyLocked) {
                var frequency = viewFrequency.toLong() / preferences.frequencyStep * preferences.frequencyStep
                frequency = frequency.coerceIn(minFrequency, maxFrequency)

                preferences.frequency = frequency
                preferences.save()

                updateInfoBar()
            }
        }

        updateFFTandGrid()
    }

    fun onSingleTap(x: Float, y: Float) {
        val infoArea = Info.HEIGHT * resources.displayMetrics.density * 1.5f
        if (y < infoArea) {
            // Info bar tapped, lock/unlock frequency.
            isFrequencyLocked = !isFrequencyLocked
            if (!isFrequencyLocked) {
                // When unlocked, reset the X axis.
                viewFrequency = preferences.frequency.toDouble()
                viewBandwidth = preferences.sampleRate.toDouble()
                updateFFTandGrid()
            } else {
                requestRender()
            }
            updateInfoBar()
        }
    }

    fun onDoubleTap(x: Float, y: Float) {
        val infoArea = Info.HEIGHT * resources.displayMetrics.density * 1.5f
        if (y < infoArea) {
            // Info bar double-tapped, open frequency popup.
            // TODO: open frequency popup
        } else if (x < grid.leftScaleSize * 1.5f && y < height / 2) {
            // Reset the Y axis.
            viewDBCenter = defaultDBCenter
            viewDBRange = defaultDBRange
            updateFFTandGrid()
        } else {
            // Reset the X axis.
            viewFrequency = preferences.frequency.toDouble()
            viewBandwidth = preferences.sampleRate.toDouble()
            updateFFTandGrid()
        }
    }
}
