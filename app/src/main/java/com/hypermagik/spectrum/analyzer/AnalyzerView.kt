package com.hypermagik.spectrum.analyzer

import android.content.Context
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
    private var waterfall = Waterfall(context, preferences.fftSize)

    private var currentFrequency: Long = 0
    private var currentGain: Int = 0

    private var viewFrequency = preferences.frequency.toFloat()
    private var viewBandwidth = preferences.sampleRate.toFloat()

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

        GLES20.glClearColor(0f, 0f, 0f, 1.0f)

        grid.onSurfaceCreated(program)
        info.onSurfaceCreated(program)
        fft.onSurfaceCreated(program)
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
        waterfall.onSurfaceChanged(height)

        isReady = true
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (currentFrequency != preferences.frequency) {
            currentFrequency = preferences.frequency
            info.setFrequency(currentFrequency)
        }

        if (currentGain != preferences.gain) {
            currentGain = preferences.gain
            info.setGain(currentGain)
        }

        grid.drawBackground()
        info.draw()

        synchronized(fft) {
            fft.draw()
        }

        synchronized(waterfall) {
            waterfall.draw()
        }

        grid.drawLabels()
    }

    fun saveInstanceState(bundle: Bundle) {
        bundle.putFloat("viewFrequency", viewFrequency)
        bundle.putFloat("viewBandwidth", viewBandwidth)
        fft.saveInstanceState(bundle)
    }

    fun restoreInstanceState(bundle: Bundle) {
        viewFrequency = bundle.getFloat("viewFrequency")
        viewBandwidth = bundle.getFloat("viewBandwidth")
        fft.restoreInstanceState(bundle)
        updateFFT()
    }

    fun start() {
        Log.d(TAG, "Starting")

        isRunning = true

        info.start()
        waterfall.start()

        updateFFT()
    }

    fun stop(restart: Boolean) {
        Log.d(TAG, "Stopping")

        isRunning = false

        info.stop(restart)
        waterfall.stop()
    }

    fun updateFFT(magnitudes: FloatArray) {
        if (!isReady) {
            Log.d(TAG, "Dropping FFT update, surface not ready")
            return
        }

        synchronized(fft) {
            fft.update(magnitudes)
        }

        synchronized(waterfall) {
            waterfall.update(magnitudes)
        }

        requestRender()
    }

    private fun updateFFT() {
        val minViewBandwidth = preferences.sampleRate / (preferences.fftSize / 128.0f)
        val maxViewBandwidth = preferences.sampleRate / 1.0f

        viewBandwidth = viewBandwidth.coerceIn(minViewBandwidth, maxViewBandwidth)

        val frequency0 = preferences.frequency - preferences.sampleRate / 2f
        val frequency1 = preferences.frequency + preferences.sampleRate / 2f
        val viewFrequency0 = frequency0 + viewBandwidth / 2f
        val viewFrequency1 = frequency1 - viewBandwidth / 2f

        viewFrequency = viewFrequency.coerceIn(viewFrequency0, viewFrequency1)

        val scale = preferences.sampleRate / viewBandwidth
        val translate = (viewFrequency - viewBandwidth / 2f - frequency0) / preferences.sampleRate * scale

        fft.update(scale, translate)

        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val scaleGestureResult = event?.let { scaleGestureDetector.onTouchEvent(it) }
        val gestureResult = event?.let { gestureDetector.onTouchEvent(it) }
        return scaleGestureResult == true || gestureResult == true
    }

    fun onScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        viewBandwidth = (viewBandwidth / scaleFactor)

        val focusFrequency = viewFrequency + (focusX / width - 0.5f)
        viewFrequency = focusFrequency + (viewFrequency - focusFrequency) / scaleFactor

        updateFFT()
    }

    fun onScroll(delta: Float) {
        viewFrequency = (viewFrequency + viewBandwidth * delta / width)

        if (viewBandwidth.toInt() == preferences.sampleRate) {
            preferences.frequency = viewFrequency.toLong() / preferences.frequencyStep * preferences.frequencyStep
            preferences.save()
        }

        updateFFT()
    }
}
