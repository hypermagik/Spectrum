package com.hypermagik.spectrum.lib.devices.rtlsdr

interface Tuner {
    fun open(): Boolean
    fun close()
    fun getMinimumFrequency(): Long
    fun getMaximumFrequency(): Long
    fun setFrequency(frequency: Long)
    fun setFrequencyCorrection(ppm: Long)
    fun setBandwidth(bandwidth: Int): Long
    fun setGain(gain: Int)
    fun setAGC(enable: Boolean)
}