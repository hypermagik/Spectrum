package com.hypermagik.spectrum

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PerformanceHintManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.hypermagik.spectrum.analyzer.Analyzer
import com.hypermagik.spectrum.databinding.ActivityMainBinding
import com.hypermagik.spectrum.demodulator.AM
import com.hypermagik.spectrum.demodulator.Demodulator
import com.hypermagik.spectrum.demodulator.DemodulatorType
import com.hypermagik.spectrum.demodulator.FM
import com.hypermagik.spectrum.demodulator.Tetra
import com.hypermagik.spectrum.demodulator.WFM
import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.data.SampleFIFO
import com.hypermagik.spectrum.lib.gpu.GLES
import com.hypermagik.spectrum.lib.gpu.Vulkan
import com.hypermagik.spectrum.source.BladeRF
import com.hypermagik.spectrum.source.IQFile
import com.hypermagik.spectrum.source.RTLSDR
import com.hypermagik.spectrum.source.Source
import com.hypermagik.spectrum.source.SourceType
import com.hypermagik.spectrum.source.ToneGenerator
import com.hypermagik.spectrum.utils.TAG
import java.text.DecimalFormat
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity(), MenuItem.OnActionExpandListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var toast: Toast
    private lateinit var analyzerFrame: FrameLayout
    private lateinit var gainSlider: Slider

    private var preferences = Preferences(this)

    private lateinit var source: Source

    private val sampleFifoSize = 8
    private lateinit var sampleFifo: SampleFIFO

    private lateinit var analyzer: Analyzer
    private var demodulator: Demodulator? = null

    private var analyzerInput = 0
    private var glesOffloadAvailable = false
    private var vulkanOffloadAvailable = false

    private var sourceThread: Thread? = null
    private var workerThread: Thread? = null

    enum class State { Stopped, Stopping, Restarting, Running }

    private var state = State.Stopped
    private var startSourceOnResume = false

    enum class RecordingState { Running, Stopping, Stopped }

    private var recorder = IQRecorder(this, preferences)
    private var recordingState = RecordingState.Stopped
    private var startRecordingOnResume = false

    private val getIQFileContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> onIQFileSelected(uri) }
    private val getRecordLocationContent = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? -> onRecordLocationSelected(uri) }

    init {
        System.loadLibrary("spectrum")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        toast = Toast(this)

        preferences.load()

        gainSlider = binding.appBarMain.gainSlider.apply {
            isEnabled = false
            addOnChangeListener { _, value, _ ->
                preferences.sourceSettings.gain = value.toInt()
                preferences.save()
            }
        }

        createSource(true)

        sampleFifo = SampleFIFO(sampleFifoSize, preferences.getSampleFifoBufferSize())

        analyzer = Analyzer(this, preferences)
        analyzer.setSourceInput(source.getMinimumFrequency(), source.getMaximumFrequency())

        analyzerFrame = binding.appBarMain.analyzerFrame
        analyzerFrame.setBackgroundColor(resources.getColor(R.color.black, null))
        analyzerFrame.addView(analyzer.view)

        if (savedInstanceState != null) {
            startSourceOnResume = savedInstanceState.getBoolean("startSourceOnResume", false)
            analyzerInput = savedInstanceState.getInt("analyzerInput", analyzerInput)
        }

        if (intent.action == Intent.ACTION_VIEW) {
            onIQFileSelected(intent.data)
            startSourceOnResume = true
        }

        thread { glesOffloadAvailable = GLES.INSTANCE.isAvailable() }.join()

        Vulkan.init(this)
        vulkanOffloadAvailable = Vulkan.isAvailable()
    }

    private fun createSource(force: Boolean = false) {
        if (!force && source.getType() == preferences.sourceType) {
            return
        }

        stop(false)

        source = when (preferences.sourceType) {
            SourceType.ToneGenerator -> ToneGenerator(preferences, recorder)
            SourceType.IQFile -> IQFile(this)
            SourceType.BladeRF -> BladeRF(this, recorder)
            SourceType.RTLSDR -> RTLSDR(this, recorder)
        }

        updateActionBarSubtitle()
        updateGainSlider()
    }

    private fun createDemodulator() {
        demodulator = when (preferences.demodulatorType) {
            DemodulatorType.None -> null
            DemodulatorType.AM -> AM(preferences)
            DemodulatorType.FM -> FM(preferences)
            DemodulatorType.WFM -> WFM(preferences)
            DemodulatorType.Tetra -> Tetra(preferences)
        }
    }

    override fun onResume() {
        super.onResume()

        if (startSourceOnResume) {
            startSourceOnResume = false
            start()
        }

        if (startRecordingOnResume) {
            startRecordingOnResume = false
            startRecorder()
        }
    }

    override fun onPause() {
        if (state == State.Running) {
            startSourceOnResume = true
            stop(false)
        }

        super.onPause()
    }

    override fun onDestroy() {
        if (state == State.Running) {
            startSourceOnResume = true
            stop(false)
        }

        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("startSourceOnResume", startSourceOnResume)
        outState.putInt("analyzerInput", analyzerInput)
        analyzer.saveInstanceState(outState)
        preferences.saveNow()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        analyzer.restoreInstanceState(savedInstanceState)
    }

    private fun toast(message: String) {
        toast.cancel()
        if (message.isNotBlank()) {
            toast = Toast.makeText(this, message, Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    private fun updateActionBarSubtitle() {
        if (source.getType() == SourceType.IQFile) {
            supportActionBar!!.subtitle = source.getName()
        } else {
            val sampleRate = DecimalFormat("0.###").format(preferences.sourceSettings.sampleRate / 1e6f)
            supportActionBar!!.subtitle = String.format("${source.getName()} @ %s Msps", sampleRate)
        }
    }

    private fun updateGainSlider() {
        gainSlider.isEnabled = state == State.Running && !preferences.sourceSettings.agc

        gainSlider.valueFrom = source.getMinimumGain().toFloat()
        gainSlider.valueTo = source.getMaximumGain().toFloat()
        gainSlider.value = preferences.sourceSettings.gain.toFloat()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.setGroupDividerEnabled(true)
        menuInflater.inflate(R.menu.main, menu)
        Constants.sourceTypeToMenu[source.getType()]?.also { menuInflater.inflate(it, menu.findItem(R.id.menu_source).subMenu) }
        Constants.demodulatorTypeToMenu[preferences.demodulatorType]?.also { menuInflater.inflate(it, menu.findItem(R.id.menu_demodulator).subMenu) }
        updateOptionsMenu()
        return true
    }

    private fun updateOptionsMenu() {
        val menu = binding.appBarMain.toolbar.menu

        menu.findItem(R.id.menu_playpause)?.apply {
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

        menu.findItem(R.id.menu_open)?.setVisible(source.getType() == SourceType.IQFile)

        menu.findItem(R.id.menu_record)?.apply {
            isEnabled = state == State.Running
            isVisible = source.getType() != SourceType.IQFile

            if (preferences.isRecording) {
                setIcon(R.drawable.ic_record_on)
            } else {
                setIcon(R.drawable.ic_record_off)
            }
        }

        menu.findItem(R.id.menu_toggle_analyzer_input)?.setVisible(preferences.demodulatorType != DemodulatorType.None)

        Constants.sourceTypeToMenuItem[source.getType()]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.sampleRateToMenuItem[preferences.sourceSettings.sampleRate]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.sampleTypeToMenuItem[preferences.iqFileType]?.also {
            menu.findItem(it)?.setChecked(true)
        }

        menu.findItem(R.id.menu_toggle_agc)?.setChecked(preferences.sourceSettings.agc)

        Constants.demodulatorTypeToMenuItem[preferences.demodulatorType]?.also {
            menu.findItem(it)?.setChecked(true)
        }

        Constants.gpuAPIToMenuItem[preferences.demodulatorGPUAPI]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        menu.findItem(R.id.menu_demodulator_gpu_offload_gles)?.apply {
            isEnabled = glesOffloadAvailable
        }
        menu.findItem(R.id.menu_demodulator_gpu_offload_vulkan)?.apply {
            isEnabled = vulkanOffloadAvailable
        }

        menu.findItem(R.id.menu_demodulator_audio_output)?.setChecked(preferences.demodulatorAudio)
        menu.findItem(R.id.menu_demodulator_stereo)?.setChecked(preferences.demodulatorStereo)
        menu.findItem(R.id.menu_demodulator_rds)?.setChecked(preferences.demodulatorRDS)

        Constants.fftSizeToMenuItem[preferences.fftSize]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.fftWindowToMenuItem[preferences.fftWindowType]?.also {
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
        Constants.frequencyStepToMenuItem[preferences.frequencyStep]?.also {
            menu.findItem(it)?.setChecked(true)
        }
        Constants.fpsLimitToMenuItem[preferences.fpsLimit]?.also {
            menu.findItem(it)?.setChecked(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_playpause) {
            if (state == State.Stopped) {
                start()
                item.tooltipText = getString(R.string.action_pause)
            } else {
                stop(false)
                item.tooltipText = getString(R.string.action_play)
            }
        } else if (item.itemId == R.id.menu_open) {
            openIQFile()
        } else if (item.itemId == R.id.menu_record) {
            toggleRecord()
        } else if (item.groupId == R.id.menu_source_type_group) {
            preferences.sourceType = Constants.sourceTypeToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.saveNow()
            invalidateOptionsMenu()
            createSource()
        } else if (item.itemId == R.id.menu_toggle_analyzer_input) {
            demodulator?.run {
                analyzerInput = (analyzerInput + 1) % (1 + getOutputCount())
            }
        } else if (item.itemId == R.id.menu_set_frequency) {
            analyzer.showSetFrequencyDialog()
        } else if (item.groupId == R.id.menu_sample_rate_group) {
            val sampleRate = Constants.sampleRateToMenuItem.filterValues { it == item.itemId }.keys.first()
            if (preferences.sourceSettings.sampleRate != sampleRate) {
                restartIfRunning {
                    preferences.sourceSettings.sampleRate = sampleRate
                    preferences.saveNow()
                }
            }
            item.setChecked(true)
            updateActionBarSubtitle()
        } else if (item.groupId == R.id.menu_sample_type_group) {
            val sampleType = Constants.sampleTypeToMenuItem.filterValues { it == item.itemId }.keys.first()
            if (preferences.iqFileType != sampleType) {
                restartIfRunning {
                    preferences.iqFileType = sampleType
                    preferences.saveNow()
                }
            }
            item.setChecked(true)
        } else if (item.itemId == R.id.menu_toggle_agc) {
            preferences.sourceSettings.agc = !preferences.sourceSettings.agc
            preferences.saveNow()
            item.setChecked(preferences.sourceSettings.agc)
            updateGainSlider()
        } else if (item.groupId == R.id.menu_demodulator_group) {
            val demodulatorType = Constants.demodulatorTypeToMenuItem.filterValues { it == item.itemId }.keys.first()
            if (preferences.demodulatorType != demodulatorType) {
                restartIfRunning {
                    preferences.demodulatorType = demodulatorType
                    preferences.saveNow()
                    analyzerInput = 0
                }
            }
            item.setChecked(true)
            invalidateOptionsMenu()
        } else if (item.groupId == R.id.menu_demodulator_gpu_offload_group) {
            restartIfRunning {
                val gpuAPI = Constants.gpuAPIToMenuItem.filterValues { it == item.itemId }.keys.first()
                preferences.demodulatorGPUAPI = gpuAPI
                preferences.saveNow()
            }
            item.setChecked(true)
        } else if (item.itemId == R.id.menu_demodulator_audio_output) {
            restartIfRunning {
                preferences.demodulatorAudio = !preferences.demodulatorAudio
                preferences.saveNow()
            }
            item.setChecked(preferences.demodulatorAudio)
        } else if (item.itemId == R.id.menu_demodulator_stereo) {
            restartIfRunning {
                preferences.demodulatorStereo = !preferences.demodulatorStereo
                preferences.saveNow()
            }
            item.setChecked(preferences.demodulatorStereo)
        } else if (item.itemId == R.id.menu_demodulator_rds) {
            restartIfRunning {
                preferences.demodulatorRDS = !preferences.demodulatorRDS
                preferences.saveNow()
            }
            item.setChecked(preferences.demodulatorRDS)
        } else if (item.groupId == R.id.menu_fft_size_group) {
            val fftSize = Constants.fftSizeToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.fftSize = fftSize
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.groupId == R.id.menu_fft_window_group) {
            val fftWindow = Constants.fftWindowToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.fftWindowType = fftWindow
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.itemId == R.id.menu_peak_hold) {
            preferences.peakHoldEnabled = !preferences.peakHoldEnabled
            preferences.saveNow()
            item.setChecked(preferences.peakHoldEnabled)
            analyzer.view.requestRender()
        } else if (item.groupId == R.id.menu_peak_hold_decay_group) {
            val peakHoldDecay = Constants.peakHoldDecayToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.peakHoldDecay = peakHoldDecay
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.itemId == R.id.menu_peak_indicator) {
            preferences.peakIndicatorEnabled = !preferences.peakIndicatorEnabled
            preferences.saveNow()
            item.setChecked(preferences.peakIndicatorEnabled)
            analyzer.view.requestRender()
        } else if (item.groupId == R.id.menu_wf_speed_group) {
            val wfSpeed = Constants.wfSpedToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.wfSpeed = wfSpeed
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.groupId == R.id.menu_wf_colormap_group) {
            val colorMap = Constants.wfColormapToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.wfColorMap = colorMap
            preferences.saveNow()
            item.setChecked(true)
            analyzer.view.requestRender()
        } else if (item.groupId == R.id.menu_frequency_step_group) {
            val frequencyStep = Constants.frequencyStepToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.frequencyStep = frequencyStep
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.groupId == R.id.menu_fps_limit_group) {
            val fpsLimit = Constants.fpsLimitToMenuItem.filterValues { it == item.itemId }.keys.first()
            preferences.fpsLimit = fpsLimit
            preferences.saveNow()
            item.setChecked(true)
        } else if (item.itemId == R.id.menu_recorder_settings) {
            showRecorderSettings()
        }

        if (item.groupId == R.id.menu_demodulator_settings_group ||
            item.groupId == R.id.menu_fft_size_group ||
            item.groupId == R.id.menu_fft_window_group ||
            item.groupId == R.id.menu_peak_hold_group ||
            item.groupId == R.id.menu_peak_hold_decay_group ||
            item.groupId == R.id.menu_wf_speed_group ||
            item.groupId == R.id.menu_wf_colormap_group
        ) {
            return keepMenuOpen(item)
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onMenuItemActionExpand(p0: MenuItem): Boolean = false
    override fun onMenuItemActionCollapse(p0: MenuItem): Boolean = false

    private fun keepMenuOpen(item: MenuItem): Boolean {
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        item.setActionView(analyzer.view)
        item.setOnActionExpandListener(this)
        return false
    }

    private fun openIQFile() {
        getIQFileContent.launch(arrayOf("*/*"))
    }

    private fun onIQFileSelected(uri: Uri?) {
        if (uri == null) {
            return
        }

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            Log.w(TAG, "Could not take persistable URI permission for $uri")
        }

        restartIfRunning {
            preferences.iqFile = uri.toString()
            preferences.saveNow()
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
    }

    private fun showRecorderSettings() {
        val layout = LayoutInflater.from(this).inflate(R.layout.recorder_settings, null)

        val sizeLimit = layout.findViewById<TextView>(R.id.size_limit)?.also {
            it.text = getString(R.string.megabytes, preferences.recordLimit / 1024 / 1024)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.recorder_settings)
            .setView(layout)
            .create()

        layout.findViewById<Slider>(R.id.size_limit_slider)?.apply {
            value = preferences.recordLimit / 1024 / 1024.0f
            addOnChangeListener { _, value, _ ->
                sizeLimit?.text = getString(R.string.megabytes, value.toLong())
                preferences.recordLimit = value.toLong() * 1024 * 1024
                preferences.save()
            }
        }

        layout.findViewById<Button>(R.id.choose_location_button)?.setOnClickListener { openRecordLocation() }
        layout.findViewById<Button>(R.id.close_button)?.setOnClickListener { dialog.dismiss() }

        dialog.window?.attributes = dialog.window?.attributes?.apply { gravity = android.view.Gravity.BOTTOM }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        dialog.show()
    }

    private fun toggleRecord() {
        if (recordingState == RecordingState.Running) {
            recordingState = RecordingState.Stopping
        } else if (recordingState == RecordingState.Stopped) {
            if (preferences.recordLocation == null) {
                startRecordingOnResume = true
                openRecordLocation()
            } else {
                startRecorder()
            }
        }
    }

    private fun startRecorder() {
        if (preferences.recordLocation == null) {
            return
        }

        var error: String?

        try {
            error = recorder.start(source.getSampleType())
            if (error == null) {
                recordingState = RecordingState.Running
                invalidateOptionsMenu()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception thrown while starting recorder", e)
            error = "Exception thrown"
        }

        if (error != null) {
            preferences.recordLocation = null
            recordingState = RecordingState.Stopped
            Log.e(TAG, "Error starting recorder: $error")
            toast(error)
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

        updateGainSlider()
        updateOptionsMenu()
    }

    private fun start() {
        if (state != State.Stopped) {
            stop(true)
        }

        val usbDevice = source.getUsbDevice()
        if (usbDevice != null) {
            val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            if (!usbManager.hasPermission(usbDevice)) {
                Permissions.request(this, usbManager, usbDevice) { error ->
                    if (error != null) {
                        Log.e(TAG, "Error requesting USB permission: $error")
                        toast(error)
                    } else {
                        start()
                    }
                }
                return
            }
        }

        Log.i(TAG, "Opening source")
        var error: String?
        try {
            error = source.open(preferences)
        } catch (e: Exception) {
            Log.e(TAG, "Exception thrown while opening source", e)
            error = "Exception thrown"
        }

        if (error != null) {
            Log.e(TAG, "Error opening source: $error")
            toast(error)
            return
        }

        updateActionBarSubtitle()

        if (sampleFifo.bufferSize != preferences.getSampleFifoBufferSize()) {
            sampleFifo = SampleFIFO(sampleFifoSize, preferences.getSampleFifoBufferSize())
        }

        analyzerInput = 0

        Runtime.getRuntime().gc()

        setState(State.Running)

        workerThread = thread { workerThreadFn(source) }
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

        workerThread?.interrupt()
        workerThread?.join()

        demodulator = null

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

        var buffer: SampleBuffer? = null
        val scratch = SampleBuffer(sampleFifo.bufferSize)

        val frequency = PreferencesWrapper.Frequency(preferences)
        val gain = PreferencesWrapper.Gain(preferences)
        val agc = PreferencesWrapper.AGC(preferences)

        Log.i(TAG, "Starting source")
        source.start()

        while (state == State.Running) {
            if (buffer == null || buffer === scratch) {
                buffer = sampleFifo.getPushBuffer()
            }
            if (buffer == null) {
                buffer = scratch
            }
            try {
                if (source.read(buffer.samples)) {
                    if (scratch !== buffer) {
                        buffer.sampleCount = buffer.samples.size
                        // TODO: should probably come from source
                        buffer.frequency = preferences.sourceSettings.frequency
                        buffer.sampleRate = preferences.sourceSettings.sampleRate
                        buffer.realSamples = false
                        sampleFifo.push()
                        buffer = null
                    }
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Exception thrown while reading from source", e)
                runOnUiThread {
                    toast("Error while reading from source")
                    stop(false)
                }
                break
            }

            if (frequency.update()) {
                source.setFrequency(frequency.value)
            }
            if (gain.update()) {
                source.setGain(gain.value)
            }
            if (agc.update()) {
                source.setAGC(agc.value)
            }

            checkRecorderState()
        }

        if (recordingState != RecordingState.Stopped) {
            recorder.stop()
            recordingState = RecordingState.Stopped
        }

        Log.i(TAG, "Closing source")
        source.stop()
        source.close()

        Log.d(TAG, "Stopping source thread")
    }

    private fun workerThreadFn(source: Source) {
        Log.d(TAG, "Starting worker thread")

        var analyzerInput = -1

        if (GLES.INSTANCE.isAvailable()) {
            GLES.INSTANCE.makeCurrent(this)
        }

        createDemodulator()
        demodulator?.start()

        val channelBandwidth = demodulator?.getChannelBandwidth() ?: 0

        if (preferences.channelFrequency < -preferences.sourceSettings.sampleRate / 2 + channelBandwidth) {
            preferences.channelFrequency = 0
        }
        if (preferences.channelFrequency > preferences.sourceSettings.sampleRate / 2 - channelBandwidth) {
            preferences.channelFrequency = 0
        }

        var channelFrequency = preferences.channelFrequency

        demodulator?.setFrequency(channelFrequency)

        analyzer.start(demodulator?.getName(), channelBandwidth)

        val samplesInterval = 1_000_000_000.0 * preferences.getSampleFifoBufferSize() / preferences.sourceSettings.sampleRate
        val demodulatorTime = LongArray(preferences.sourceSettings.sampleRate / preferences.getSampleFifoBufferSize())
        var timeIndex = 0

        var hintSession: PerformanceHintManager.Session? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @SuppressLint("WrongConstant")
            val performanceHintManager = this.getSystemService(Context.PERFORMANCE_HINT_SERVICE) as PerformanceHintManager
            hintSession = performanceHintManager.createHintSession(intArrayOf(android.os.Process.myTid()), samplesInterval.toLong())
        }

        while (state == State.Running) {
            var samples: SampleBuffer?

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
                val t1 = System.nanoTime()

                if (analyzerInput != this.analyzerInput) {
                    analyzerInput = this.analyzerInput
                    if (analyzerInput == 0) {
                        analyzer.setSourceInput(source.getMinimumFrequency(), source.getMaximumFrequency())
                    } else {
                        analyzer.setDemodulatorInput(demodulator!!.getOutputName(analyzerInput))
                    }
                }

                if (channelFrequency != preferences.channelFrequency) {
                    channelFrequency = preferences.channelFrequency
                    demodulator?.setFrequency(channelFrequency)
                }

                try {
                    val analyzerNeedsSamples = analyzer.needsSamples()

                    if (demodulator == null) {
                        if (analyzerNeedsSamples) {
                            analyzer.analyze(samples, false)
                        }
                    } else {
                        demodulator!!.demodulate(samples, analyzerInput) { buffer, preserveSamples ->
                            if (analyzerNeedsSamples) {
                                analyzer.analyze(buffer, preserveSamples)
                            }
                        }
                        analyzer.setDemodulatorText(demodulator!!.getText())
                    }
                } catch (_: InterruptedException) {
                    break
                }

                sampleFifo.pop()

                val duration = System.nanoTime() - t1
                demodulatorTime[timeIndex] = duration

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    hintSession?.reportActualWorkDuration(duration)
                }

                if (++timeIndex == demodulatorTime.size) {
                    timeIndex = 0
                    analyzer.setWorkerUsage(demodulatorTime.average() / samplesInterval, demodulatorTime.max() / samplesInterval)
                }
            }
        }

        demodulator?.stop()
        analyzer.stop(state == State.Restarting)

        Log.d(TAG, "Stopping analyzer thread")
    }
}
