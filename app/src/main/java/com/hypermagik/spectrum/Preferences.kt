package com.hypermagik.spectrum

import android.app.Activity
import android.util.Log
import com.hypermagik.spectrum.demodulator.DemodulatorType
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.SampleType
import com.hypermagik.spectrum.lib.dsp.Window
import com.hypermagik.spectrum.lib.gpu.GPUAPI
import com.hypermagik.spectrum.source.SourceType
import com.hypermagik.spectrum.utils.TAG
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule

class Preferences(private val activity: Activity?) {

    data class SourceSettings(var frequency: Long, var sampleRate: Int, var gain: Int, var agc: Boolean = false)

    private var allSourceSettings = mapOf(
        SourceType.ToneGenerator to SourceSettings(3000000000L, 1000000, 0),
        SourceType.IQFile to SourceSettings(0, 0, 0),
        SourceType.BladeRF to SourceSettings(100000000L, 1000000, 0),
        SourceType.RTLSDR to SourceSettings(100000000L, 1024000, 25),
    )

    var sourceType = SourceType.ToneGenerator
        set(value) {
            field = value
            sourceSettings = allSourceSettings[value]!!
        }
    var sourceSettings = allSourceSettings[sourceType]!!

    var iqFile: String? = null
    var iqFileType: SampleType = SampleType.NONE

    var recordLocation: String? = null
    var recordLimit = 32 * 1024 * 1024L

    // Internal use only, not serialized.
    var isRecording = false

    var demodulatorType = DemodulatorType.None

    var demodulatorGPUAPI = GPUAPI.None
    var demodulatorAudio = false
    var demodulatorStereo = false
    var demodulatorRDS = false

    var channelFrequency = 0L

    var fftSize = 256
    var fftWindowType = Window.Type.FLAT_TOP

    var dbCenterDefault = -55.0f
    var dbRangeDefault = 110.0f
    var dbCenter = dbCenterDefault
    var dbRange = dbRangeDefault

    var peakHoldEnabled = true
    var peakHoldDecay = 30
    var peakIndicatorEnabled = true

    var wfSpeed = 2
    var wfColorMap = 0

    var frequencyStep = 1000
    var fpsLimit = 60

    private val timer = Timer()
    private var timerTask: TimerTask? = null

    fun load() {
        Log.d(TAG, "Loading preferences")

        if (activity == null) {
            return
        }

        activity.getPreferences(Activity.MODE_PRIVATE).run {
            sourceType = SourceType.entries.toTypedArray()[getInt("sourceType", sourceType.ordinal)]
            for (key in allSourceSettings.keys) {
                val settings = allSourceSettings[key]!!
                settings.frequency = getLong("$key-frequency", settings.frequency)
                settings.sampleRate = getInt("$key-sampleRate", settings.sampleRate)
                settings.gain = getInt("$key-gain", settings.gain)
                settings.agc = getBoolean("$key-agc", settings.agc)
            }
            iqFile = getString("iqFile", iqFile)
            iqFileType = SampleType.entries.toTypedArray()[getInt("iqFileType", iqFileType.ordinal)]
            recordLocation = getString("recordLocation", recordLocation)
            recordLimit = getLong("recordLimit", recordLimit)
            demodulatorType = DemodulatorType.entries.toTypedArray()[getInt("demodulatorType", demodulatorType.ordinal)]
            demodulatorGPUAPI = GPUAPI.entries.toTypedArray()[getInt("demodulatorGPUAPI", demodulatorGPUAPI.ordinal)]
            demodulatorAudio = getBoolean("demodulatorAudio", demodulatorAudio)
            demodulatorStereo = getBoolean("demodulatorStereo", demodulatorStereo)
            demodulatorRDS = getBoolean("demodulatorRDS", demodulatorRDS)
            channelFrequency = getLong("channelFrequency", channelFrequency)
            fftSize = getInt("fftSize", fftSize)
            fftWindowType = Window.Type.entries.toTypedArray()[getInt("fftWindowType", fftWindowType.ordinal)]
            dbCenter = getFloat("dbCenter", dbCenter)
            dbRange = getFloat("dbRange", dbRange)
            peakHoldEnabled = getBoolean("peakHoldEnabled", peakHoldEnabled)
            peakHoldDecay = getInt("peakHoldDecay", peakHoldDecay)
            peakIndicatorEnabled = getBoolean("peakIndicatorEnabled", peakIndicatorEnabled)
            wfSpeed = getInt("wfSpeed", wfSpeed)
            wfColorMap = getInt("wfColorMap", wfColorMap)
            frequencyStep = getInt("frequencyStep", frequencyStep)
            fpsLimit = getInt("fpsLimit", fpsLimit)
        }

        sourceSettings = allSourceSettings[sourceType]!!
    }

    private fun saveIt() {
        Log.d(TAG, "Saving preferences")

        if (activity == null) {
            return
        }

        activity.getPreferences(Activity.MODE_PRIVATE).edit().run {
            putInt("sourceType", sourceType.ordinal)
            for (key in allSourceSettings.keys) {
                val settings = allSourceSettings[key]!!
                putLong("$key-frequency", settings.frequency)
                putInt("$key-sampleRate", settings.sampleRate)
                putInt("$key-gain", settings.gain)
                putBoolean("$key-agc", settings.agc)
            }
            putString("iqFile", iqFile)
            putInt("iqFileType", iqFileType.ordinal)
            putString("recordLocation", recordLocation)
            putLong("recordLimit", recordLimit)
            putInt("demodulatorType", demodulatorType.ordinal)
            putInt("demodulatorGPUAPI", demodulatorGPUAPI.ordinal)
            putBoolean("demodulatorAudio", demodulatorAudio)
            putBoolean("demodulatorStereo", demodulatorStereo)
            putBoolean("demodulatorRDS", demodulatorRDS)
            putLong("channelFrequency", channelFrequency)
            putInt("fftSize", fftSize)
            putInt("fftWindowType", fftWindowType.ordinal)
            putFloat("dbCenter", dbCenter)
            putFloat("dbRange", dbRange)
            putBoolean("peakHoldEnabled", peakHoldEnabled)
            putInt("peakHoldDecay", peakHoldDecay)
            putBoolean("peakIndicatorEnabled", peakIndicatorEnabled)
            putInt("wfSpeed", wfSpeed)
            putInt("wfColorMap", wfColorMap)
            putInt("frequencyStep", frequencyStep)
            putInt("fpsLimit", fpsLimit)
            apply()
        }
    }

    fun save() {
        timerTask?.cancel()
        timerTask = timer.schedule(250) {
            saveIt()
        }
    }

    fun saveNow() {
        timerTask?.cancel()
        saveIt()
    }

    fun getSampleFifoBufferSize(): Int {
        var size = Complex32.MAX_ARRAY_SIZE
        // At least 120 buffers per second.
        while (sourceSettings.sampleRate / size < 120) {
            size /= 2
        }
        return size
    }

    fun getPeakHoldDecayFactor(): Float {
        return (peakHoldDecay * 0.001f * if (fpsLimit == 0) 1.0f else 120.0f / fpsLimit).coerceIn(0.01f, 0.1f)
    }
}
