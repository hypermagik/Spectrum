package com.hypermagik.spectrum.lib.devices.rtlsdr

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException

class RTLSDR {
    companion object {
        private const val TAG = "RTLSDR-Device"
    }

    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private lateinit var usbSampleEndpointIn: UsbEndpoint

    private var rtl2832u: RTL2832U? = null

    fun getDevice(context: Context): UsbDevice? {
        val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: HashMap<String, UsbDevice> = usbManager.getDeviceList()

        Log.i(TAG, "Found " + deviceList.size + " USB devices")

        var usbDevice: UsbDevice? = null
        for (device in deviceList.values) {
            Log.i(TAG, device.toString())

            if (device.vendorId == 0x0bda) {
                if (device.productId == 0x2832) {
                    Log.i(TAG, "Found RTL2832 at " + device.deviceName)
                    usbDevice = device
                    break
                } else if (device.productId == 0x2838) {
                    Log.i(TAG, "Found RTL2838 at " + device.deviceName)
                    usbDevice = device
                    break
                }
            }
        }

        return usbDevice
    }

    fun open(context: Context): String? {
        val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbDevice: UsbDevice = getDevice(context) ?: return "No RTL-SDR device found"

        if (!usbManager.hasPermission(usbDevice)) {
            return "USB permission is not granted"
        }

        val usbConnection: UsbDeviceConnection
        try {
            usbConnection = usbManager.openDevice(usbDevice)
        } catch (e: Exception) {
            return "Couldn't open USB device: " + e.message
        }

        var usbInterface: UsbInterface? = null
        var usbSampleEndpointIn: UsbEndpoint? = null

        try {
            for (i in 0 until usbDevice.interfaceCount) {
                val iface = usbDevice.getInterface(i)
                if (usbConnection.claimInterface(iface, true)) {
                    for (j in 0 until iface.endpointCount) {
                        val endpoint = iface.getEndpoint(j)
                        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_IN) {
                            usbInterface = iface
                            usbSampleEndpointIn = endpoint
                            break
                        }
                    }
                    if (usbInterface != null) {
                        break
                    }
                    usbConnection.releaseInterface(iface)
                }
            }
        } catch (_: Exception) {
        }

        if (usbInterface == null || usbSampleEndpointIn == null) {
            usbConnection.close()
            return "Couldn't get USB endpoint"
        }

        Log.i(
            TAG, "Rx endpoint address: " + usbSampleEndpointIn.address
                    + ", attributes: " + usbSampleEndpointIn.attributes
                    + ", direction: " + usbSampleEndpointIn.direction
                    + ", max packet size: " + usbSampleEndpointIn.maxPacketSize
        )

        this.usbConnection = usbConnection
        this.usbInterface = usbInterface
        this.usbSampleEndpointIn = usbSampleEndpointIn

        rtl2832u = RTL2832U(usbConnection)
        return rtl2832u!!.open()
    }

    fun close() {
        rtl2832u?.close()
        rtl2832u = null

        usbConnection?.releaseInterface(usbInterface)
        usbConnection?.close()
        usbConnection = null
    }

    fun setSampleRate(sampleRate: Int) {
        rtl2832u?.setSampleRate(sampleRate)
    }

    fun getMinimumFrequency(): Long = rtl2832u?.getMinimumFrequency() ?: 0
    fun getMaximumFrequency(): Long = rtl2832u?.getMaximumFrequency() ?: 0

    fun setFrequency(frequency: Long, silent: Boolean) {
        rtl2832u?.setFrequency(frequency, silent)
    }

    fun getMinimumGain(): Int = 0
    fun getMaximumGain(): Int = 50

    fun setGain(gain: Int, silent: Boolean) {
        rtl2832u?.setGain(gain, silent)
    }

    fun setAGC(enable: Boolean) {
        rtl2832u?.setAGC(enable)
    }

    fun enableRx() {
        rtl2832u?.enableRx()
    }

    fun disableRx() {
        rtl2832u?.disableRx()
    }

    fun initializeUSBRequests(count: Int): Array<UsbRequest> {
        val requests = Array(count) { UsbRequest() }

        var error = false

        for (request in requests) {
            if (!request.initialize(usbConnection, usbSampleEndpointIn)) {
                Log.e(TAG, "Couldn't initialize USB request")
                error = true
                break
            }
        }

        if (error) {
            cancelUSBRequests(requests)
            return Array(0) { UsbRequest() }
        }

        return requests
    }

    fun cancelUSBRequests(requests: Array<UsbRequest>) {
        for (request in requests) {
            request.cancel()
        }
    }

    fun getSamples(): UsbRequest? {
        val usbConnection = usbConnection ?: return null

        while (true) {
            try {
                val request: UsbRequest = usbConnection.requestWait(Constants.USB_TIMEOUT_FOR_SAMPLES)

                if (request.endpoint !== usbSampleEndpointIn) {
                    Log.w(TAG, "Received USB request for wrong endpoint")
                    continue
                }

                return request
            } catch (e: TimeoutException) {
                Log.w(TAG, "USB request timeout")
                return null
            }
        }
    }

    fun queueUSBRequest(request: UsbRequest, buffer: ByteBuffer): Boolean {
        request.clientData = buffer

        if (!request.queue(buffer)) {
            Log.e(TAG, "Couldn't queue USB request")
            return false
        }

        return true
    }
}