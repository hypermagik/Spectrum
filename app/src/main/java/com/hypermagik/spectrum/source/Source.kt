package com.hypermagik.spectrum.source

import android.hardware.usb.UsbDevice
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleType

interface Source {
    fun getName(): String
    fun getShortName(): String

    fun getType(): SourceType
    fun getSampleType(): SampleType

    fun getUsbDevice(): UsbDevice?

    fun open(preferences: Preferences): String?
    fun close()

    fun start()
    fun stop()

    fun read(output: Complex32Array): Boolean

    fun setFrequency(frequency: Long)
    fun getMinimumFrequency(): Long
    fun getMaximumFrequency(): Long

    fun setGain(gain: Int)
    fun getMinimumGain(): Int
    fun getMaximumGain(): Int

    fun setAGC(enable: Boolean)
}
