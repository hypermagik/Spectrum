package com.hypermagik.spectrum.source

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbRequest
import android.util.Log
import com.hypermagik.spectrum.IQRecorder
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleType
import com.hypermagik.spectrum.lib.data.converter.IQConverterU8
import com.hypermagik.spectrum.lib.devices.rtlsdr.RTLSDR
import com.hypermagik.spectrum.utils.TAG
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RTLSDR(private val context: Context, private val recorder: IQRecorder) : Source {
    private val device = RTLSDR()
    private val converter = IQConverterU8()

    private var bufferSize = 0
    private var bufferCount = 4

    private val buffers = Array(bufferCount) { ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder()) }
    private var requests = Array(0) { UsbRequest() }

    override fun getName(): String = "RTL-SDR"
    override fun getType(): SourceType = SourceType.RTLSDR
    override fun getSampleType(): SampleType = SampleType.U8
    override fun getUsbDevice(): UsbDevice? = device.getDevice(context)

    override fun open(preferences: Preferences): String? {
        Log.d(TAG, "Opening ${getName()}")

        bufferSize = preferences.getSampleFifoBufferSize() * converter.getSampleSize()

        Log.d(TAG, "Buffer size: $bufferSize, buffer count: $bufferCount")

        for (i in buffers.indices) {
            buffers[i] = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        }

        val error = device.open(context)
        if (error != null) {
            return error
        }

        requests = device.initializeUSBRequests(buffers.size)

        if (requests.isEmpty()) {
            device.close()
            return "Failed to initialize USB requests"
        }

        device.setFrequency(preferences.sourceSettings.frequency, false)
        device.setSampleRate(preferences.sourceSettings.sampleRate)
        device.setAGC(preferences.sourceSettings.agc)
        device.setGain(preferences.sourceSettings.gain, false)

        return null
    }

    override fun close() {
        Log.d(TAG, "Closing")

        device.close()
    }

    override fun start() {
        Log.d(TAG, "Starting")

        device.enableRx()

        for (i in requests.indices) {
            buffers[i].rewind()
            device.queueUSBRequest(requests[i], buffers[i])
        }
    }

    override fun stop() {
        Log.d(TAG, "Stopping")

        device.disableRx()
        device.cancelUSBRequests(requests)
    }

    override fun read(output: Complex32Array): Boolean {
        val request = device.getSamples() ?: return false
        val buffer = request.clientData as ByteBuffer

        buffer.rewind()
        converter.convert(buffer, output)

        buffer.rewind()
        recorder.record(buffer)

        buffer.rewind()
        device.queueUSBRequest(request, buffer)

        return true
    }

    override fun setFrequency(frequency: Long) {
        device.setFrequency(frequency, true)
    }

    override fun getMinimumFrequency(): Long = device.getMinimumFrequency()
    override fun getMaximumFrequency(): Long = device.getMaximumFrequency()

    override fun setGain(gain: Int) {
        device.setGain(gain, true)
    }

    override fun getMinimumGain(): Int = device.getMinimumGain()
    override fun getMaximumGain(): Int = device.getMaximumGain()

    override fun setAGC(enable: Boolean) {
        device.setAGC(enable)
    }
}
