package com.hypermagik.spectrum.source

import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleType

interface Source {
    fun getName(): String
    fun getType(): SourceType
    fun getSampleType(): SampleType

    fun open(preferences: Preferences): String?
    fun close()

    fun start()
    fun stop()

    fun read(buffer: Complex32Array)

    fun setFrequency(frequency: Long)
    fun getMinimumFrequency(): Long
    fun getMaximumFrequency(): Long

    fun setGain(gain: Int)
    fun getMinimumGain(): Int
    fun getMaximumGain(): Int

    fun setAGC(enable: Boolean)
}
