package com.hypermagik.spectrum.lib.devices.rtlsdr

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import com.hypermagik.spectrum.lib.utils.Utils.getShortAt
import kotlin.math.pow

@Suppress("Unused", "SameParameterValue")
class RTL2832U(private val usbConnection: UsbDeviceConnection) : I2CMaster {
    companion object {
        private const val TAG = "RTLSDR-RTL2832U"
    }

    private val dataBuffer = ByteArray(32)

    private var xtalFrequency = Constants.DEF_RTL_XTAL_FREQ
    private var xtalFrequencyCorrectionPPM = 0L
    private var intermediateFrequency = Constants.R820T_IF_FREQ
    private var frequency = 0L
    private var bandwidth = 0
    private var sampleRate = 0

    private var tuner: Tuner? = null

    fun open(): String? {
        xtalFrequency = Constants.DEF_RTL_XTAL_FREQ
        xtalFrequencyCorrectionPPM = 0L
        intermediateFrequency = Constants.R820T_IF_FREQ
        frequency = 0L
        bandwidth = 0
        sampleRate = 0

        initializeBaseband()

        if (!initializeTuner()) {
            close()
            return "Failed to initialize tuner"
        }

        return null
    }

    fun close() {
        tuner?.close()
        tuner = null

        deinitializeBaseband()
    }

    private fun readArray(block: Int, address: Int, data: ByteArray, length: Int = data.size): Boolean {
        val endpoint: Int = UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR

        if (Constants.DUMP_MESSAGES) {
            Log.i(
                TAG, "Read"
                        + ", block=0x" + "%02x".format(block)
                        + ", address=0x" + "%02x".format(address)
            )
        }

        val len = usbConnection.controlTransfer(endpoint, 0, address, block shl 8, data, length, Constants.USB_TIMEOUT)
        if (len < 0) {
            Log.e(
                TAG, "Read failed"
                        + ", block=0x" + "%02x".format(block)
                        + ", address=0x" + "%02x".format(address)
                        + ", result=" + len
            )
            return false
        }

        if (Constants.DUMP_MESSAGES) {
            Log.i(
                TAG, "Read returned"
                        + ", block=0x" + "%02x".format(block)
                        + ", address=0x" + "%02x".format(address)
                        + ", data=" + data.slice(0..<length).joinToString("") { "%02x".format(it) }
            )
        }

        return true
    }

    private fun writeArray(block: Int, address: Int, data: ByteArray, length: Int, silent: Boolean = false): Boolean {
        val endpoint: Int = UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR

        if (Constants.DUMP_MESSAGES) {
            Log.i(
                TAG, "Write"
                        + ", block=0x" + "%02x".format(block)
                        + ", address=0x" + "%02x".format(address)
                        + ", value=" + data.slice(0..<length).joinToString("") { "%02x".format(it) }
            )
        }

        val len = usbConnection.controlTransfer(endpoint, 0, address, block shl 8 or 0x10, data, length, Constants.USB_TIMEOUT)
        if (len < 0 && !silent) {
            Log.e(
                TAG, "Write failed"
                        + ", block=0x" + "%02x".format(block)
                        + ", address=0x" + "%02x".format(address)
                        + ", value=" + data.slice(0..<length).joinToString("") { "%02x".format(it) }
                        + ", result=" + len
            )
            return false
        }

        return len == length
    }

    private fun readRegister(block: Int, address: Int, length: Int): Int {
        if (!readArray(block, address, dataBuffer, length)) {
            return -1
        }
        return if (length == 1) dataBuffer[0].toInt() else dataBuffer.getShortAt(0).toInt()
    }

    private fun writeRegister(block: Int, address: Int, value: Int, length: Int): Boolean {
        if (length == 1) {
            dataBuffer[0] = value.toByte()
        } else {
            dataBuffer[0] = (value shr 8).toByte()
            dataBuffer[1] = (value and 0xff).toByte()
        }
        return writeArray(block, address, dataBuffer, length)
    }

    private fun readDemodulatorRegister(page: Int, address: Int, length: Int): Int {
        val endpoint: Int = UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR

        if (Constants.DUMP_MESSAGES) {
            Log.i(
                TAG, "Demodulator register read"
                        + ", page=0x" + "%02x".format(page)
                        + ", address=0x" + "%02x".format(address)
            )
        }

        val len = usbConnection.controlTransfer(endpoint, 0, address shl 8 or 0x20, page, dataBuffer, length, Constants.USB_TIMEOUT)
        if (len < 0) {
            Log.e(
                TAG, "USB control transfer failed"
                        + ", page=0x" + "%02x".format(page)
                        + ", address=0x" + "%02x".format(address)
                        + ", result=" + len
            )
            return -1
        }

        if (Constants.DUMP_MESSAGES) {
            Log.i(
                TAG, "Demodulator register read"
                        + ", page=0x" + "%02x".format(page)
                        + ", address=0x" + "%02x".format(address)
                        + ", data=" + dataBuffer.slice(0..<length).joinToString("") { "%02x".format(it) }
            )
        }

        return if (length == 1) dataBuffer[0].toInt() else dataBuffer.getShortAt(0).toInt()
    }

    private fun writeDemodulatorRegister(page: Int, address: Int, value: Int, length: Int): Boolean {
        if (length == 1) {
            dataBuffer[0] = value.toByte()
        } else {
            dataBuffer[0] = (value shr 8).toByte()
            dataBuffer[1] = (value and 0xff).toByte()
        }
        val endpoint: Int = UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR

        if (Constants.DUMP_MESSAGES) {
            Log.i(
                TAG, "Demodulator register write"
                        + ", page=0x" + "%02x".format(page)
                        + ", address=0x" + "%02x".format(address)
                        + ", value=" + dataBuffer.slice(0..<length).joinToString("") { "%02x".format(it) }
            )
        }

        val len = usbConnection.controlTransfer(endpoint, 0, address shl 8 or 0x20, page or 0x10, dataBuffer, length, Constants.USB_TIMEOUT)
        if (len < 0) {
            Log.e(
                TAG, "Demodulator register write failed"
                        + ", page=0x" + "%02x".format(page)
                        + ", address=0x" + "%02x".format(address)
                        + ", value=" + dataBuffer.slice(0..<length).joinToString("") { "%02x".format(it) }
                        + ", result=" + len
            )
            return false
        }

        return len == length
    }

    override fun readI2C(address: Int, data: ByteArray, length: Int): Boolean {
        return readArray(Constants.IICB, address, data, length)
    }

    override fun writeI2C(address: Int, data: ByteArray, length: Int): Boolean {
        return writeArray(Constants.IICB, address, data, length, true)
    }

    private fun setGPIOOutput(gpio: Int) {
        var reg = readRegister(Constants.SYSB, Constants.GPD, 1)
        if (reg >= 0) {
            reg = reg and (1 shl gpio).inv()
            writeRegister(Constants.SYSB, Constants.GPD, reg, 1)
        }
        reg = readRegister(Constants.SYSB, Constants.GPOE, 1)
        if (reg >= 0) {
            reg = reg or (1 shl gpio)
            writeRegister(Constants.SYSB, Constants.GPOE, reg, 1)
        }
    }

    private fun setGPIOBit(gpio: Int, value: Boolean) {
        var reg = readRegister(Constants.SYSB, Constants.GPO, 1)
        if (reg >= 0) {
            reg = if (value) (reg or (1 shl gpio)) else (reg and (1 shl gpio).inv())
            writeRegister(Constants.SYSB, Constants.GPO, reg, 1)
        }
    }

    private fun setFIRCoeficients() {
        Log.d(TAG, "Setting FIR coefficients")

        // FIR coefficients.
        //
        // The filter is running at XTal frequency. It is symmetric filter with 32
        // coefficients. Only first 16 coefficients are specified, the other 16
        // use the same values but in reversed order. The first coefficient in
        // the array is the outer one, the last, the last is the inner one.
        // First 8 coefficients are 8 bit signed integers, the next 8 coefficients
        // are 12 bit signed integers. All coefficients have the same weight.
        //
        // Default FIR coefficients used for DAB/FM by the Windows driver,
        // the DVB driver uses different ones:
        val firCoeffs = intArrayOf(
            -54, -36, -41, -40, -32, -14, 14, 53,   //  8 bit signed
            101, 156, 215, 273, 327, 372, 404, 421  // 12 bit signed
        )

        val data = ByteArray(20)

        for (i in 0 until 8) {
            data[i] = firCoeffs[i].toByte()
        }

        for (i in 0 until 8 step 2) {
            val v0 = firCoeffs[8 + i + 0]
            val v1 = firCoeffs[8 + i + 1]

            data[8 + 3 * i / 2 + 0] = (v0 shr 4 and 0xff).toByte()
            data[8 + 3 * i / 2 + 1] = ((v0 shl 4 and 0xf0) or (v1 shr 8 and 0x0f)).toByte()
            data[8 + 3 * i / 2 + 2] = (v1 and 0xff).toByte()
        }

        // Set FIR coefficients.
        for (i in data.indices) {
            writeDemodulatorRegister(1, 0x1c + i, data[i].toInt(), 1)
        }
    }

    private fun initializeBaseband() {
        Log.d(TAG, "Initializing baseband")

        // Initialize USB.
        writeRegister(Constants.USBB, Constants.USB_SYSCTL, 0x09, 1)
        writeRegister(Constants.USBB, Constants.USB_EPA_MAXPKT, 0x0002, 2)
        writeRegister(Constants.USBB, Constants.USB_EPA_CTL, 0x1002, 2)

        // Power up demodulator.
        writeRegister(Constants.SYSB, Constants.DEMOD_CTL, 0xe8, 1)
        writeRegister(Constants.SYSB, Constants.DEMOD_CTL_1, 0x22, 1)

        // Reset demodulator (bit 3, soft_rst).
        writeDemodulatorRegister(1, 0x01, 0x14, 1)
        writeDemodulatorRegister(1, 0x01, 0x18, 1)

        // Disable spectrum inversion and adjacent channel rejection.
        writeDemodulatorRegister(1, 0x15, 0x00, 1)

        // Clear both DDC shift and IF frequency registers.
        for (i in 0 until 6) {
            writeDemodulatorRegister(1, 0x16 + i, 0x00, 1)
        }

        // Set FIR filter coefficients.
        setFIRCoeficients()

        // Enable SDR mode, disable DAGC (bit 5).
        writeDemodulatorRegister(0, 0x19, 0x05, 1)

        // Init FSM state-holding register.
        writeDemodulatorRegister(1, 0x93, 0xf0, 1)
        writeDemodulatorRegister(1, 0x94, 0x0f, 1)

        // Disable AGC (en_dagc, bit 0) (this seems to have no effect).
        writeDemodulatorRegister(1, 0x11, 0x00, 1)

        // Disable RF and IF AGC loop.
        writeDemodulatorRegister(1, 0x04, 0x00, 1)

        // Disable PID filter (enable_PID = 0).
        writeDemodulatorRegister(0, 0x61, 0x60, 1)

        // opt_adc_iq = 0, default ADC_I/ADC_Q datapath.
        writeDemodulatorRegister(0, 0x06, 0x80, 1)

        // Enable Zero-IF mode (en_bbin bit), DC cancellation (en_dc_est), IQ estimation/compensation (en_iq_comp, en_iq_est).
        writeDemodulatorRegister(1, 0xb1, 0x1b, 1)

        // Disable 4.096 MHz clock output on pin TP_CK0.
        writeDemodulatorRegister(0, 0x0d, 0x83, 1)
    }

    private fun deinitializeBaseband() {
        // Power off demodulator and ADCs.
        writeRegister(Constants.SYSB, Constants.DEMOD_CTL, 0x20, 1)
    }

    private fun initializeTuner(): Boolean {
        Log.d(TAG, "Initializing tuner")

        // Initialise GPIOs.
        setGPIOOutput(4)

        // Reset tuner.
        setGPIOBit(4, true)
        setGPIOBit(4, false)

        val data = ByteArray(1) { Constants.R820T_CHECK_ADDR.toByte() }
        if (!writeI2C(Constants.R820T_I2C_ADDR, data, 1) ||
            !readI2C(Constants.R820T_I2C_ADDR, data, 1)) {
            return false
        }

        if (data[0] == Constants.R820T_CHECK_VAL.toByte()) {
            // Disable Zero-IF mode.
            writeDemodulatorRegister(1, 0xb1, 0x1a, 1)

            // Only enable in-phase ADC input.
            writeDemodulatorRegister(0, 0x08, 0x4d, 1)

            // The R820T uses 3.57 MHz IF for the DVB-T 6 MHz mode, and 4.57 MHz for the 8 MHz mode.
            setIF(Constants.R820T_IF_FREQ)

            // Enable spectrum inversion.
            writeDemodulatorRegister(1, 0x15, 0x01, 1)

            tuner = R820T(this)
        }

        if (tuner == null) {
            Log.d(TAG, "Tuner not supported")
        }

        tuner?.apply {
            if (!open()) {
                Log.e(TAG, "Tuner initialization failed")
                tuner = null
            }
        }

        return tuner != null
    }

    private fun getXtalFrequency(): Long {
        return (xtalFrequency * (1.0 + xtalFrequencyCorrectionPPM / 1e6)).toLong()
    }

    private fun setIF(frequency: Long) {
        Log.d(TAG, "Setting IF to $frequency")

        intermediateFrequency = frequency

        val iff = -frequency * 2.0.pow(22) / getXtalFrequency()

        var reg = iff.toLong() shr 16 and 0x3f
        writeDemodulatorRegister(1, 0x19, reg.toInt(), 1)

        reg = iff.toLong() shr 8 and 0xff
        writeDemodulatorRegister(1, 0x1a, reg.toInt(), 1)

        reg = iff.toLong() and 0xff
        writeDemodulatorRegister(1, 0x1b, reg.toInt(), 1)
    }

    private fun setSampleFrequencyCorrection(ppm: Long) {
        Log.d(TAG, "Setting sample frequency correction to $ppm")

        val offset = -ppm * 2.0.pow(24) / 1e6

        var reg = offset.toInt() and 0xff
        writeDemodulatorRegister(1, 0x3f, reg, 1)

        reg = offset.toInt() shr 8 and 0x3f
        writeDemodulatorRegister(1, 0x3e, reg, 1)
    }

    fun setFrequencyCorrection(ppm: Long) {
        Log.d(TAG, "Setting frequency correction to $ppm")

        xtalFrequencyCorrectionPPM = ppm

        setSampleFrequencyCorrection(ppm)
        tuner?.setFrequencyCorrection(ppm)

        if (frequency > 0) {
            setFrequency(frequency, false)
        }
    }

    private fun setTunerBandwidth(bandwidth: Int) {
        Log.d(TAG, "Setting tuner bandwidth to $bandwidth ($sampleRate)")

        this.bandwidth = bandwidth

        tuner?.also {
            val iff = it.setBandwidth(if (bandwidth > 0) bandwidth else sampleRate)
            if (iff > 0) {
                setIF(iff)
                setFrequency(frequency, false)
            }
        }
    }

    fun setSampleRate(sampleRate: Int) {
        Log.d(TAG, "Setting sample rate to $sampleRate")

        if (sampleRate <= 225000 || sampleRate > 3200000) {
            return
        }

        if (sampleRate in 300001..900000) {
            return
        }

        val ratio = (getXtalFrequency() * 2.0.pow(22) / sampleRate).toInt() and 0x0ffffffc
        val realRatio = ratio or ((ratio and 0x08000000) shl 1)
        val realSampleRate = (getXtalFrequency() * 2.0.pow(22) / realRatio).toInt()

        if (sampleRate != realSampleRate) {
            Log.w(TAG, "Requested sample rate was $sampleRate, exact sample rate is $realSampleRate")
        }

        this.sampleRate = realSampleRate

        setTunerBandwidth(bandwidth)

        writeDemodulatorRegister(1, 0x9f, ratio shr 16 and 0xffff, 2)
        writeDemodulatorRegister(1, 0xa1, ratio and 0xffff, 2)

        setSampleFrequencyCorrection(xtalFrequencyCorrectionPPM)

        // Reset demodulator (bit 3, soft_rst).
        writeDemodulatorRegister(1, 0x01, 0x14, 1)
        writeDemodulatorRegister(1, 0x01, 0x18, 1)
    }

    fun getMinimumFrequency(): Long = tuner?.getMinimumFrequency() ?: 0
    fun getMaximumFrequency(): Long = tuner?.getMaximumFrequency() ?: 0

    fun setFrequency(frequency: Long, silent: Boolean) {
        if (!silent) {
            Log.d(TAG, "Setting frequency to $frequency")
        }

        this.frequency = frequency
        tuner?.setFrequency(frequency)
    }

    fun setAGC(enable: Boolean) {
        Log.d(TAG, "Setting AGC to $enable")

        tuner?.setAGC(enable)
    }

    fun setGain(gain: Int, silent: Boolean) {
        if (!silent) {
            Log.d(TAG, "Setting gain to $gain")
        }

        tuner?.setGain(gain * 10)
    }

    fun enableRx() {
        Log.d(TAG, "Enabling RX")

        writeRegister(Constants.USBB, Constants.USB_EPA_CTL, 0x1002, 2)
        writeRegister(Constants.USBB, Constants.USB_EPA_CTL, 0x0000, 2)
    }

    fun disableRx() {
        Log.d(TAG, "Disabling RX")
    }
}