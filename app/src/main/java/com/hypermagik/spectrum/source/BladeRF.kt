package com.hypermagik.spectrum.source

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbRequest
import android.util.Log
import com.hypermagik.spectrum.IQRecorder
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleType
import com.hypermagik.spectrum.lib.data.converter.IQConverterS12P
import com.hypermagik.spectrum.lib.devices.bladerf.BladeRF
import com.hypermagik.spectrum.utils.TAG
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BladeRF(private val context: Context, private val recorder: IQRecorder) : Source {
    private val device = BladeRF()
    private val converter = IQConverterS12P()

    private var bufferSize = 0
    private var bufferCount = 16

    private val buffers = Array(bufferCount) { ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder()) }
    private var requests = Array(0) { UsbRequest() }

    override fun getName(): String = "bladeRF 2.0"
    override fun getType(): SourceType = SourceType.BladeRF
    override fun getSampleType(): SampleType = SampleType.S12P
    override fun getUsbDevice(): UsbDevice? = device.getDevice(context)

    override fun open(preferences: Preferences): String? {
        Log.d(TAG, "Opening ${getName()}")

        bufferSize = preferences.getSampleFifoBufferSize() * converter.getSampleSize()

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
        device.setSampleRate(preferences.sourceSettings.sampleRate.toLong())
        setAGC(preferences.sourceSettings.agc)
        setGain(preferences.sourceSettings.gain)

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

    override fun read(output: Complex32Array) {
        val request = device.getSamples()
        if (request == null) {
            for (c in output) {
                c.re = 0.0f
                c.im = 0.0f
            }
            return
        }

        val buffer = request.clientData as ByteBuffer

        buffer.rewind()
        converter.convert(buffer, output)

        buffer.rewind()
        recorder.record(buffer)

        buffer.rewind()
        device.queueUSBRequest(request, buffer)
    }

    override fun setFrequency(frequency: Long) {
        device.setFrequency(frequency, true)
    }

    override fun getMinimumFrequency(): Long = device.getMinimumFrequency()
    override fun getMaximumFrequency(): Long = device.getMaximumFrequency()

    override fun setGain(gain: Int) {
        device.setGain(gain + 17L)
    }

    override fun getMinimumGain(): Int = device.getMinimumGain()
    override fun getMaximumGain(): Int = device.getMaximumGain()

    override fun setAGC(enable: Boolean) {
        device.setManualGain(!enable)
    }
}
