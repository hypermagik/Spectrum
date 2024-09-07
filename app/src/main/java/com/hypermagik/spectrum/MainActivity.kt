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

    enum class RecordingState { Running, Stopping, Stopped }

    private var recorder = IQRecorder(this, preferences)
    private var recordingState = RecordingState.Stopped

    private val getIQFileContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> onIQFileSelected(uri) }
    private val getRecordLocationContent = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? -> onRecordLocationSelected(uri) }

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
        analyzerView.setFrequencyRange(source.getMinimumFrequency(), source.getMaximumFrequency())

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
            SourceType.ToneGenerator -> ToneGenerator(recorder)
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

        menu.findItem(R.id.action_playpause)?.apply {
            when (state) {
                State.Running -> {
                    isEnabled = true
                    setIcon(R.drawable.ic_pause)
                }

                State.Stopped -> {
                    isEnabled = true
                    setIcon(R.drawable.ic_play)
                }

                else -> {
                    isEnabled = false
                }
            }
        }

        menu.findItem(R.id.action_open)?.setVisible(source.getType() == SourceType.IQFile)

        menu.findItem(R.id.record)?.apply {
            isEnabled = state == State.Running
            isVisible = source.getType() != SourceType.IQFile

            if (preferences.isRecording) {
                setIcon(R.drawable.ic_record_on)
            } else {
                setIcon(R.drawable.ic_record_off)
            }
        }

        Constants.sourceTypeToMenuItem[source.getType()]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.sampleRateToMenuItem[preferences.sampleRate]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.sampleTypeToMenuItem[preferences.iqFileType]?.also {
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

        menu.findItem(R.id.menu_peak_hold)?.setChecked(preferences.peakHoldEnabled)
        menu.findItem(R.id.menu_peak_indicator)?.setChecked(preferences.peakIndicatorEnabled)

        Constants.peakHoldDecayToMenuItem[preferences.peakHoldDecay]?.also {
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
        } else if (item.itemId == R.id.record) {
            toggleRecord()
        } else if (item.groupId == R.id.source_type_group) {
            preferences.sourceType = Constants.sourceTypeToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.saveNow()
            invalidateOptionsMenu()
            createSource()
        } else if (item.itemId == R.id.set_frequency) {
            analyzerView.showSetFrequencyDialog()
        } else if (item.groupId == R.id.sample_rate_group) {
            val sampleRate = Constants.sampleRateToMenuItem.filterValues { it == item.itemId }.keys.first()
            restartIfRunning {
                preferences.sampleRate = sampleRate
                preferences.saveNow()
            }
            item.setChecked(true)
        } else if (item.groupId == R.id.sample_type_group) {
            val sampleType = Constants.sampleTypeToMenuItem.filterValues { it == item.itemId }.keys.first()
            restartIfRunning {
                preferences.iqFileType = sampleType
                preferences.saveNow()
            }
            item.setChecked(true)
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
        } else if (item.itemId == R.id.menu_peak_hold) {
            preferences.peakHoldEnabled = !preferences.peakHoldEnabled
            preferences.saveNow()
            item.setChecked(preferences.peakHoldEnabled)
            analyzerView.requestRender()
        } else if (item.groupId == R.id.peak_hold_decay_group) {
            val peakHoldDecay = Constants.peakHoldDecayToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.peakHoldDecay = peakHoldDecay
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.itemId == R.id.menu_peak_indicator) {
            preferences.peakIndicatorEnabled = !preferences.peakIndicatorEnabled
            preferences.saveNow()
            item.setChecked(preferences.peakIndicatorEnabled)
            analyzerView.requestRender()
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
        getIQFileContent.launch(arrayOf("*/*"))
    }

    private fun onIQFileSelected(uri: Uri?) {
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

    private fun openRecordLocation() {
        getRecordLocationContent.launch(null)
    }

    private fun onRecordLocationSelected(uri: Uri?) {
        if (uri == null) {
            return
        }

        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        preferences.recordLocation = uri.toString()
        preferences.save()

        if (recordingState == RecordingState.Stopped) {
            startRecorder()
        }
    }

    private fun toggleRecord() {
        if (recordingState == RecordingState.Running) {
            recordingState = RecordingState.Stopping
        } else if (recordingState == RecordingState.Stopped) {
            if (preferences.recordLocation == null) {
                openRecordLocation()
            } else {
                startRecorder()
            }
        }
    }

    private fun startRecorder() {
        var error: String?

        try {
            error = recorder.start(source.getSampleType())
            if (error == null) {
                recordingState = RecordingState.Running
                invalidateOptionsMenu()
            }
        } catch (e: Exception) {
            error = e.message
        }

        if (error != null) {
            preferences.recordLocation = null
            Log.e(TAG, "Error starting recorder: $error")
            Toast.makeText(this, "Error starting recorder:\n$error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkRecorderState() {
        when (recordingState) {
            RecordingState.Running -> {
                if (!recorder.isRecording()) {
                    recordingState = RecordingState.Stopped
                    invalidateOptionsMenu()
                }
            }

            RecordingState.Stopping -> {
                recorder.stop()
                recordingState = RecordingState.Stopped
                invalidateOptionsMenu()
            }

            RecordingState.Stopped -> return
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
        val error = try {
            source.open(preferences)
        } catch (e: Exception) {
            e.message
        }
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

        val frequency = PreferencesWrapper.Frequency(preferences)
        val gain = PreferencesWrapper.Gain(preferences)

        Log.i(TAG, "Starting source")
        source.start()

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

            if (frequency.update()) {
                source.setFrequency(frequency.value)
            }
            if (gain.update()) {
                source.setGain(gain.value)
            }

            checkRecorderState()
        }

        if (recordingState != RecordingState.Stopped) {
            recorder.stop()
            recordingState = RecordingState.Stopped
        }

        Log.i(TAG, "Closing source")
        source.stop()

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
