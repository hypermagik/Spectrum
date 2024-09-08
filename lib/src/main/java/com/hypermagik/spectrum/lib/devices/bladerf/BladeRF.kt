package com.hypermagik.spectrum.lib.devices.bladerf

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.util.Log
import com.hypermagik.spectrum.lib.utils.Utils.getIntAt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeoutException

@Suppress("Unused")
class BladeRF {
    companion object {
        private const val TAG = "BladeRF-Device"
    }

    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private lateinit var usbSampleEndpointIn: UsbEndpoint

    private var nios: NIOS? = null
    private var rfic: RFIC? = null

    fun getDevice(context: Context): UsbDevice? {
        val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: HashMap<String, UsbDevice> = usbManager.getDeviceList()

        Log.i(TAG, "Found " + deviceList.size + " USB devices")

        var usbDevice: UsbDevice? = null
        for (device in deviceList.values) {
            Log.i(TAG, device.toString())

            if (device.vendorId == 11504 && device.productId == 21072) {
                Log.i(TAG, "Found bladeRF at " + device.deviceName)
                usbDevice = device
                break
            }
        }

        return usbDevice
    }

    fun open(context: Context): String? {
        val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbDevice: UsbDevice = getDevice(context) ?: return "No bladeRF device found"

        if (!usbManager.hasPermission(usbDevice)) {
            return "USB permission is not granted"
        }

        val usbConnection: UsbDeviceConnection
        try {
            usbConnection = usbManager.openDevice(usbDevice)
        } catch (e: Exception) {
            return "Couldn't open USB device: " + e.message
        }

        val usbInterface: UsbInterface
        try {
            usbInterface = usbDevice.getInterface(Constants.INTERFACE_RF_LINK)
        } catch (e: Exception) {
            usbConnection.close()
            return "Couldn't get USB interface: " + e.message
        }

        if (!usbConnection.claimInterface(usbInterface, true)) {
            usbConnection.close()
            return "Couldn't claim USB interface"
        }

        if (!usbConnection.setInterface(usbInterface)) {
            usbConnection.close()
            return "Couldn't set USB interface"
        }

        val usbSampleEndpointIn: UsbEndpoint
        //val usbSampleEndpointOut: UsbEndpoint
        val usbPeripheralEndpointIn: UsbEndpoint
        val usbPeripheralEndpointOut: UsbEndpoint

        try {
            usbSampleEndpointIn = usbInterface.getEndpoint(0)
            //usbSampleEndpointOut = usbInterface.getEndpoint(1)
            usbPeripheralEndpointIn = usbInterface.getEndpoint(2)
            usbPeripheralEndpointOut = usbInterface.getEndpoint(3)
        } catch (e: Exception) {
            usbConnection.close()
            return "Couldn't get USB endpoints: " + e.message
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

        if (!isFirmwareReady()) {
            reset()
            close()
            return "Device firmware is not ready, resetting device"
        }
        Log.i(TAG, "Device firmware is ready")

        val firmwareVersion = getFirmwareVersion()
        if (firmwareVersion == null) {
            close()
            return "Could not get firmware version"
        }
        Log.i(TAG, "Firmware version is $firmwareVersion")

        if (!isFPGALoaded()) {
            close()
            return "FPGA is not loaded"
        }
        Log.i(TAG, "FPGA is loaded")

        val nios = NIOS(usbConnection, usbPeripheralEndpointIn, usbPeripheralEndpointOut, Constants.DUMP_MESSAGES)

        val fpgaVersion: String? = nios.getFPGAVersion()
        if (fpgaVersion == null) {
            Log.e(TAG, "Could not get FPGA version")
        }
        Log.i(TAG, "FPGA version is $fpgaVersion")

        val ina219 = INA219(nios)
        if (!ina219.initialize()) {
            close()
            return "Could not initialize INA219"
        }

        val rfic = RFIC(nios)

        if (!rfic.open()) {
            close()
            return "Could not initialize RFIC"
        }

        rfic.setTxMute()

        Log.i(TAG, "Sample rate is " + rfic.getSampleRate())
        Log.i(TAG, "Frequency is " + rfic.getFrequency())
        Log.i(TAG, "Bandwidth is " + rfic.getBandwidth())
        Log.i(TAG, "Gain mode is " + rfic.getGainMode())
        Log.i(TAG, "Gain is " + rfic.getGain())
        Log.i(TAG, "Rx filter is " + rfic.getRxFilter())
        Log.i(TAG, "VCTCXO trim is " + nios.getVCTCXOTrim())
        Log.i(TAG, String.format("GPIO is 0x%08x", nios.getGPIO()))
        Log.i(TAG, String.format("RFFE CSR is 0x%08x", nios.getRFFECSR()))

        if (!nios.setVCTCXOTrim(0x1f3f.toShort())) {
            Log.e(TAG, "Could not set VCTCXO trim")
        }

        val deviceSerialNumber: String = usbDevice.serialNumber ?: "unknown"
        Log.i(TAG, "Device with serial number $deviceSerialNumber is ready")

        this.nios = nios
        this.rfic = rfic

        return null
    }

    fun close() {
        rfic?.close()
        rfic = null
        nios = null
        usbConnection?.releaseInterface(usbInterface)
        usbConnection?.close()
        usbConnection = null
    }

    private fun controlTransfer(request: Int, buffer: ByteArray): Int {
        val usbConnection = usbConnection ?: return -1

        var len = buffer.size
        val endpoint: Int = UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR

        len = usbConnection.controlTransfer(endpoint, request, 0, 0, buffer, len, Constants.USB_TIMEOUT)
        if (len < 0) {
            Log.e(
                TAG, "USB control transfer failed"
                        + ", endpoint=" + endpoint
                        + ", request=" + request
                        + ", result=" + len
            )
        }

        return len
    }

    private fun isFirmwareReady(): Boolean {
        Log.i(TAG, "Reading firmware ready state")

        val result = ByteArray(4)
        val n = controlTransfer(Constants.USB_CMD_QUERY_DEVICE_READY, result)
        if (n != 4) {
            Log.e(TAG, "Response length mismatch")
            return false
        }

        return result.getIntAt(0) == 1
    }

    private fun getFirmwareVersion(): String? {
        Log.i(TAG, "Reading firmware version")

        val result = ByteArray(4)
        val n = controlTransfer(Constants.USB_CMD_QUERY_VERSION, result)
        if (n != 4) {
            Log.e(TAG, "Response length mismatch")
            return null
        }

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        val major = buffer.getShort().toInt()
        val minor = buffer.getShort().toInt()
        return "$major.$minor"
    }

    private fun isFPGALoaded(): Boolean {
        Log.i(TAG, "Reading FPGA ready state")

        val result = ByteArray(4)
        val n = controlTransfer(Constants.USB_CMD_QUERY_FPGA_STATUS, result)
        if (n != 4) {
            Log.e(TAG, "Response length mismatch")
            return false
        }

        return result.getIntAt(0) == 1
    }

    private fun controlTransferWithResult(request: Int, value: Int): Int {
        val usbConnection = usbConnection ?: return -1

        val result = ByteArray(4)
        val endpoint: Int = UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR

        val len: Int = usbConnection.controlTransfer(endpoint, request, value, 0, result, result.size, Constants.USB_TIMEOUT)
        if (len < 0 && request != Constants.USB_CMD_RESET) {
            Log.e(
                TAG, "USB control transfer failed"
                        + ", endpoint=" + endpoint
                        + ", request=" + request
                        + ", result=" + len
            )
            return -1
        }

        return result.getIntAt(0)
    }

    private fun reset() {
        controlTransferWithResult(Constants.USB_CMD_RESET, 1)
    }

    private fun toggleRx(state: Int): Boolean {
        return controlTransferWithResult(Constants.USB_CMD_RF_RX, state) == 0
    }

    fun getSampleRate(): Int {
        val rfic = rfic ?: return 0
        return rfic.getSampleRate().toInt()
    }

    fun setSampleRate(sampleRate: Long) {
        val rfic = rfic ?: return

        if (sampleRate < Constants.FIR_SAMPLE_RATE) {
            if (rfic.getSampleRate() > Constants.FIR_SAMPLE_RATE) {
                rfic.setSampleRate(Constants.FIR_SAMPLE_RATE)
            }
            if (!rfic.setRxFilter(Constants.RFIC_RXFIR_DEC4.toLong()) || !rfic.setTxFilter(Constants.RFIC_TXFIR_INT4.toLong())) {
                Log.e(TAG, "Failed to set FIR filters to 4x decimate/interpolate")
            }
        } else {
            if (rfic.getSampleRate() < Constants.FIR_SAMPLE_RATE) {
                rfic.setSampleRate(Constants.FIR_SAMPLE_RATE)
            }
            if (sampleRate <= 61440000 / 2) {
                if (!rfic.setRxFilter(Constants.RFIC_RXFIR_DEC2.toLong()) || !rfic.setTxFilter(Constants.RFIC_TXFIR_INT2.toLong())) {
                    Log.e(TAG, "Failed to set FIR filters to 2x decimate/interpolate")
                }
            } else if (!rfic.setRxFilter(Constants.RFIC_RXFIR_DEC1.toLong()) || !rfic.setTxFilter(Constants.RFIC_TXFIR_INT1.toLong())) {
                Log.e(TAG, "Failed to set FIR filters to 1x decimate/interpolate")
            }
        }

        rfic.setSampleRate(sampleRate)
        rfic.setBandwidth((sampleRate * 0.9f).toLong())
    }

    fun getFrequency(): Long {
        val rfic = rfic ?: return 0
        return rfic.getFrequency()
    }

    fun getMinimumFrequency(): Long = Constants.MIN_FREQUENCY
    fun getMaximumFrequency(): Long = Constants.MAX_FREQUENCY

    fun setFrequency(frequency: Long, silent: Boolean) {
        rfic?.setFrequency(frequency, silent)
    }

    fun getManualGain(): Boolean {
        val rfic = rfic ?: return false
        return rfic.getGainMode() == Constants.GAIN_MGC
    }

    fun setManualGain(enable: Boolean) {
        rfic?.setGainMode(if (enable) Constants.GAIN_MGC else Constants.GAIN_SLOWATTACK_AGC)
    }

    fun getMinimumGain(): Int = Constants.MIN_GAIN
    fun getMaximumGain(): Int = Constants.MAX_GAIN

    fun getGain(): Long {
        val rfic = rfic ?: return 0
        return rfic.getGain()
    }

    fun setGain(gain: Long) {
        rfic?.setGain(gain + 17)
    }

    fun enableRx() {
        val rfic = rfic ?: return

        if (toggleRx(1)) {
            Log.i(TAG, "Rx enabled")
        } else {
            Log.e(TAG, "Failed to enable Rx (firmware error)")
        }

        if (!rfic.enable(Constants.RFIC_STATE_ON)) {
            Log.e(TAG, "Failed to enable Rx (RFIC error)")
        }
    }

    fun disableRx() {
        val rfic = rfic ?: return

        rfic.enable(Constants.RFIC_STATE_OFF)

        toggleRx(0)
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
                val request: UsbRequest = usbConnection.requestWait(Constants.USB_TIMEOUT_FOR_SAMPLES.toLong())

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
