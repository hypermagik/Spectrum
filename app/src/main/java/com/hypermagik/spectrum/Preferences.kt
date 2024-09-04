package com.hypermagik.spectrum

import android.app.Activity
import android.util.Log
import com.hypermagik.spectrum.lib.dsp.FFT.WindowType
import com.hypermagik.spectrum.utils.TAG
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.math.max

class Preferences(private val activity: Activity?) {
    var frequency = 3000000000L
    var sampleRate = 1000000
    var gain = 0
    var agc = false

    var frequencyStep = 1000

    var fftSize = 256
    var fftWindowType = WindowType.FLAT_TOP

    var wfSpeed = 2
    var wfColorMap = 0

    var fpsLimit = 60

    private val timer = Timer()
    private var timerTask: TimerTask? = null

    fun load() {
        Log.d(TAG, "Loading preferences")

        if (activity == null) {
            return
        }

        activity.getPreferences(Activity.MODE_PRIVATE).run {
            frequency = getLong("frequency", frequency)
            sampleRate = getInt("sampleRate", sampleRate)
            gain = getInt("gain", gain)
            agc = getBoolean("agc", agc)
            fftSize = getInt("fftSize", fftSize)
            fftWindowType = WindowType.entries.toTypedArray()[getInt("fftWindowType", fftWindowType.ordinal)]
            wfSpeed = getInt("wfSpeed", wfSpeed)
            wfColorMap = getInt("wfColorMap", wfColorMap)
            frequencyStep = getInt("frequencyStep", frequencyStep)
            fpsLimit = getInt("fpsLimit", fpsLimit)
        }
    }

    private fun saveIt() {
        Log.d(TAG, "Saving preferences")

        if (activity == null) {
            return
        }

        activity.getPreferences(Activity.MODE_PRIVATE).edit().run {
            putLong("frequency", frequency)
            putInt("sampleRate", sampleRate)
            putInt("gain", gain)
            putBoolean("agc", agc)
            putInt("fftSize", fftSize)
            putInt("fftWindowType", fftWindowType.ordinal)
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
        return max(fftSize, 4096)
    }
}
