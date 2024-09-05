package com.hypermagik.spectrum

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.hypermagik.spectrum.analyzer.AnalyzerView
import com.hypermagik.spectrum.databinding.ActivityMainBinding
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleFIFO
import com.hypermagik.spectrum.lib.dsp.FFT
import com.hypermagik.spectrum.source.IQFile
import com.hypermagik.spectrum.source.Source
import com.hypermagik.spectrum.source.SourceType
import com.hypermagik.spectrum.source.ToneGenerator
import com.hypermagik.spectrum.utils.TAG
import com.hypermagik.spectrum.utils.Throttle
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var analyzerFrame: FrameLayout
    private lateinit var gainSlider: Slider

    private var preferences = Preferences(this)

    private lateinit var source: Source
    private var sourceThread: Thread? = null

    private lateinit var fft: FFT

    private val sampleFifoSize = 32
    private lateinit var sampleFifo: SampleFIFO

    private var analyzerThread: Thread? = null
    private val analyzerThrottle = Throttle()
    private lateinit var analyzerMagnitudes: FloatArray

    private lateinit var analyzerView: AnalyzerView

    enum class State { Stopped, Stopping, Restarting, Running }

    private var state = State.Stopped
    private var startOnResume = false

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> onFileSelected(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        preferences.load()

        createSource(true)

        fft = FFT(preferences.fftSize, preferences.fftWindowType)
        sampleFifo = SampleFIFO(sampleFifoSize, preferences.getSampleFifoBufferSize())

        analyzerThrottle.setFPS(preferences.fpsLimit)
        analyzerMagnitudes = FloatArray(preferences.fftSize) { 0.0f }

        analyzerView = AnalyzerView(this, preferences)

        analyzerFrame = binding.appBarMain.analyzerFrame
        analyzerFrame.setBackgroundColor(resources.getColor(R.color.black, null))
        analyzerFrame.addView(analyzerView)

        gainSlider = binding.appBarMain.gainSlider.apply {
            isEnabled = false
            valueFrom = source.getMinimumGain().toFloat()
            valueTo = source.getMaximumGain().toFloat()
            value = preferences.gain.toFloat()

            addOnChangeListener { _, value, _ ->
                preferences.gain = value.toInt()
                preferences.save()
            }
        }

        if (savedInstanceState != null) {
            startOnResume = savedInstanceState.getBoolean("startOnResume", false)
        }
    }

    private fun createSource(force: Boolean = false) {
        if (!force && source.getType() == preferences.sourceType) {
            return
        }

        stop(false)

        source = when (preferences.sourceType) {
            SourceType.ToneGenerator -> ToneGenerator()
            SourceType.IQFile -> IQFile(this)
        }

        supportActionBar!!.subtitle = source.getName()
    }

    override fun onResume() {
        super.onResume()

        if (startOnResume) {
            startOnResume = false
            start()
        }
    }

    override fun onPause() {
        if (state == State.Running) {
            startOnResume = true
            stop(false)
        }

        super.onPause()
    }

    override fun onDestroy() {
        if (state == State.Running) {
            startOnResume = true
            stop(false)
        }

        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("startOnResume", startOnResume)
        analyzerView.saveInstanceState(outState)
        preferences.saveNow()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        analyzerView.restoreInstanceState(savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.setGroupDividerEnabled(true)
        menuInflater.inflate(R.menu.main, menu)
        menuInflater.inflate(R.menu.fft, menu)
        menuInflater.inflate(R.menu.waterfall, menu)
        Constants.sourceTypeToMenu[source.getType()]?.also { menuInflater.inflate(it, menu) }
        updateOptionsMenu()
        return true
    }

    private fun updateOptionsMenu() {
        val menu = binding.appBarMain.toolbar.menu
        val item = menu.findItem(R.id.action_playpause)
        if (item != null) {
            when (state) {
                State.Running -> {
                    item.setEnabled(true)
                    item.setIcon(R.drawable.ic_pause)
                }

                State.Stopped -> {
                    item.setEnabled(true)
                    item.setIcon(R.drawable.ic_play)
                }

                else -> {
                    item.setEnabled(false)
                }
            }
        }

        menu.findItem(R.id.action_open)?.setVisible(source.getType() == SourceType.IQFile)

        Constants.sourceTypeToMenuItem[source.getType()]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.sampleRateToMenuItem[preferences.sampleRate]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.fpsLimitToMenuItem[preferences.fpsLimit]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.frequencyStepToMenuItem[preferences.frequencyStep]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.fftSizeToMenuItem[fft.size]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.fftWindowToMenuItem[fft.windowType]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.wfSpedToMenuItem[preferences.wfSpeed]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.wfColormapToMenuItem[preferences.wfColorMap]?.also {
            menu.findItem(it)?.setChecked(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_playpause) {
            if (state == State.Stopped) {
                start()
                item.tooltipText = getString(R.string.action_pause)
            } else {
                stop(false)
                item.tooltipText = getString(R.string.action_play)
            }
        } else if (item.itemId == R.id.action_open) {
            openIQFile()
        } else if (item.groupId == R.id.source_type_group) {
            preferences.sourceType = Constants.sourceTypeToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.saveNow()
            invalidateOptionsMenu()
            createSource()
        } else if (item.groupId == R.id.sample_rate_group) {
            val sampleRate = Constants.sampleRateToMenuItem.filterValues { it == item.itemId }.keys.first()
            restartIfRunning {
                preferences.sampleRate = sampleRate
                preferences.saveNow()
            }
        } else if (item.groupId == R.id.fps_limit_group) {
            val fpsLimit = Constants.fpsLimitToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.fpsLimit = fpsLimit
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.groupId == R.id.frequency_step_group) {
            val frequencyStep = Constants.frequencyStepToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.frequencyStep = frequencyStep
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.groupId == R.id.fft_size_group) {
            val fftSize = Constants.fftSizeToMenuItem.filterValues { it == item.itemId }.keys.first()
            if (fftSize != fft.size) {
                restartIfRunning {
                    preferences.fftSize = fftSize
                    preferences.saveNow()
                }
            }
            item.setChecked(true)
        } else if (item.groupId == R.id.fft_window_group) {
            val fftWindow = Constants.fftWindowToMenuItem.filterValues { it == item.itemId }.keys.first()
            if (fftWindow != fft.windowType) {
                restartIfRunning {
                    preferences.fftWindowType = fftWindow
                    preferences.saveNow()
                }
            }
            item.setChecked(true)
        } else if (item.groupId == R.id.wf_speed_group) {
            val wfSpeed = Constants.wfSpedToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.wfSpeed = wfSpeed
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.groupId == R.id.wf_colormap_group) {
            val colorMap = Constants.wfColormapToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.wfColorMap = colorMap
            preferences.saveNow()
            item.setChecked(true)
            analyzerView.requestRender()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openIQFile() {
        getContent.launch(arrayOf("*/*"))
    }

    private fun onFileSelected(uri: Uri?) {
        if (uri == null) {
            return
        }

        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        preferences.iqFile = uri.toString()
        preferences.save()

        if (state == State.Running) {
            stop(false)
            start()
        }
    }

    private fun setState(state: State) {
        Log.d(TAG, "Setting state to $state")

        this.state = state

        updateOptionsMenu()

        gainSlider.isEnabled = state == State.Running
    }

    private fun start() {
        if (state != State.Stopped) {
            stop(true)
        }

        Log.i(TAG, "Opening source")
        val error = source.open(preferences)
        if (error != null) {
            Log.e(TAG, "Error opening source: $error")
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            return
        }

        supportActionBar!!.subtitle = source.getName()

        if (fft.size != preferences.fftSize || fft.windowType != preferences.fftWindowType) {
            fft = FFT(preferences.fftSize, preferences.fftWindowType)
        }

        if (sampleFifo.bufferSize != preferences.getSampleFifoBufferSize()) {
            sampleFifo = SampleFIFO(sampleFifoSize, preferences.getSampleFifoBufferSize())
        }

        if (fft.size != analyzerMagnitudes.size) {
            Log.d(TAG, "Resizing magnitude buffer")
            analyzerMagnitudes = FloatArray(fft.size) { 0.0f }
        }

        analyzerThrottle.setFPS(preferences.fpsLimit)

        Log.i(TAG, "Starting source")
        source.start()

        setState(State.Running)

        analyzerThread = thread { analyzerThreadFn(source) }
        sourceThread = thread { sourceThreadFn() }
    }

    private fun stop(restart: Boolean) {
        if (state != State.Running) {
            return
        }

        Log.i(TAG, if (restart) "Restarting" else "Stopping")
        setState(if (restart) State.Restarting else State.Stopping)

        sourceThread?.interrupt()
        sourceThread?.join()

        analyzerThread?.interrupt()
        analyzerThread?.join()

        Log.i(TAG, "Closing source")
        source.stop()
        source.close()

        sampleFifo.clear()

        Log.i(TAG, "Stopped")
        setState(State.Stopped)
    }

    private fun restartIfRunning(block: () -> Unit) {
        val wasRunning = state == State.Running
        if (wasRunning) {
            stop(true)
        }

        block()

        if (wasRunning) {
            start()
        }
    }

    private fun sourceThreadFn() {
        Log.d(TAG, "Starting source thread")

        val scratch = Complex32Array(sampleFifo.bufferSize) { Complex32() }

        var previousFrequency = preferences.frequency
        var previousGain = preferences.gain

        while (state == State.Running) {
            try {
                val buffer = sampleFifo.getPushBuffer()
                if (buffer != null) {
                    source.read(buffer)
                    sampleFifo.push()
                } else {
                    source.read(scratch)
                }
            } catch (_: InterruptedException) {
                break
            }

            val newGain = preferences.gain
            if (previousGain != newGain) {
                previousGain = newGain
                source.setGain(newGain)
            }
            val newFrequency = preferences.frequency
            if (previousFrequency != newFrequency) {
                previousFrequency = newFrequency
                source.setFrequency(newFrequency)
            }
        }

        Log.d(TAG, "Stopping source thread")
    }

    private fun analyzerThreadFn(source: Source) {
        Log.d(TAG, "Starting analyzer thread")

        analyzerView.start()
        analyzerView.setFrequencyRange(source.getMinimumFrequency(), source.getMaximumFrequency())

        var previousFPSLimit = preferences.fpsLimit

        while (state == State.Running) {
            var samples: Complex32Array?

            while (true) {
                samples = sampleFifo.getPopBuffer()

                if (samples != null || state != State.Running) {
                    break
                }

                try {
                    Thread.sleep(1)
                } catch (_: InterruptedException) {
                    break
                }
            }

            if (samples != null) {
                try {
                    analyze(samples)
                } catch (_: InterruptedException) {
                    break
                }

                sampleFifo.pop()
            }

            val newFPSLimit = preferences.fpsLimit
            if (previousFPSLimit != newFPSLimit) {
                previousFPSLimit = newFPSLimit
                analyzerThrottle.setFPS(newFPSLimit)
            }
        }

        analyzerView.stop(state == State.Restarting)

        Log.d(TAG, "Stopping analyzer thread")
    }

    private fun analyze(samples: Complex32Array) {
        if (!analyzerThrottle.isSynced()) {
            return
        }

        fft.applyWindow(samples)
        fft.fft(samples)
        fft.magnitudes(samples, analyzerMagnitudes)

        analyzerView.updateFFT(analyzerMagnitudes)
    }
}
