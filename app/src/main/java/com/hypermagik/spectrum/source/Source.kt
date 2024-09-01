package com.hypermagik.spectrum.source

import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.Complex32Array

interface Source {
    fun getName(): String

    fun open(preferences: Preferences): Boolean
    fun close()

    fun start()
    fun stop()

    fun read(buffer: Complex32Array)

    fun getMinimumSampleRate(): Int
    fun getMaximumSampleRate(): Int

    fun setFrequency(frequency: Long)
    fun getMinimumFrequency(): Long
    fun getMaximumFrequency(): Long

    fun setGain(gain: Int)
    fun getMinimumGain(): Int
    fun getMaximumGain(): Int

    fun setAGC(enable: Boolean)
}
