package com.hypermagik.spectrum.lib.devices.bladerf

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log
import com.hypermagik.spectrum.lib.utils.Utils
import com.hypermagik.spectrum.lib.utils.Utils.getIntAt
import com.hypermagik.spectrum.lib.utils.Utils.getLongAt
import com.hypermagik.spectrum.lib.utils.Utils.getShortAt

@Suppress("Unused", "MemberVisibilityCanBePrivate")
class NIOS(
    private val connection: UsbDeviceConnection,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint,
    private val dumpMessages: Boolean
) {
    companion object {
        private const val TAG = "BladeRF-NIOS"
        private const val TIMEOUT = 1000
    }

    private fun access(buffer: ByteArray, timeout: Int = TIMEOUT, quiet: Boolean = false): Boolean {
        if (dumpMessages) {
            Log.d(TAG, "NIOS request sending " + buffer.joinToString("") { "%02x".format(it) })
        }

        var status = connection.bulkTransfer(endpointOut, buffer, buffer.size, timeout)
        if (status < 0) {
            if (!quiet) {
                Log.e(TAG, "USB peripheral bulk out transfer failure $status")
            }
            return false
        }

        status = connection.bulkTransfer(endpointIn, buffer, buffer.size, timeout)
        if (status < 0) {
            if (!quiet) {
                Log.e(TAG, "USB peripheral bulk in transfer failure $status")
            }
            return false
        }

        if (dumpMessages) {
            Log.d(TAG, "NIOS request returned " + buffer.joinToString("") { "%02x".format(it) })
        }

        return true
    }

    fun read8x16(target: Byte, command: Byte): Short? {
        val buffer = ByteArray(16)

        buffer[Constants.NIOS_PKT_8x16_IDX_MAGIC] = Constants.NIOS_PKT_8x16_MAGIC.toByte()
        buffer[Constants.NIOS_PKT_8x16_IDX_TARGET_ID] = target
        buffer[Constants.NIOS_PKT_8x16_IDX_FLAGS] = 0
        buffer[Constants.NIOS_PKT_8x16_IDX_ADDR] = command

        if (dumpMessages) {
            Log.d(
                TAG, "NIOS 8x16 read" +
                        ", target=" + target +
                        ", command=" + command +
                        ", data=" + buffer.joinToString("") { "%02x".format(it) }
            )
        }

        if (!access(buffer)) {
            return null
        }

        return buffer.getShortAt(Constants.NIOS_PKT_8x16_IDX_DATA)
    }

    fun write8x16(target: Byte, command: Byte, data: Short): Boolean {
        val buffer = ByteArray(16)

        buffer[Constants.NIOS_PKT_8x16_IDX_MAGIC] = Constants.NIOS_PKT_8x16_MAGIC.toByte()
        buffer[Constants.NIOS_PKT_8x16_IDX_TARGET_ID] = target
        buffer[Constants.NIOS_PKT_8x16_IDX_FLAGS] = 1
        buffer[Constants.NIOS_PKT_8x16_IDX_ADDR] = command
        buffer[Constants.NIOS_PKT_8x16_IDX_DATA + 0] = ((data.toInt() shr 0) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_8x16_IDX_DATA + 1] = ((data.toInt() shr 8) and 0xff).toByte()

        if (dumpMessages) {
            Log.d(
                TAG, "NIOS 8x16 write" +
                        ", target=" + target +
                        ", command=" + command +
                        ", data=" + buffer.joinToString("") { "%02x".format(it) }
            )
        }

        return access(buffer) && (buffer[Constants.NIOS_PKT_8x16_IDX_FLAGS].toInt() and 1) != 0
    }

    fun read8x32(target: Byte, command: Byte): Int? {
        val buffer = ByteArray(16)

        buffer[Constants.NIOS_PKT_8x32_IDX_MAGIC] = Constants.NIOS_PKT_8x32_MAGIC.toByte()
        buffer[Constants.NIOS_PKT_8x32_IDX_TARGET_ID] = target
        buffer[Constants.NIOS_PKT_8x32_IDX_FLAGS] = 0
        buffer[Constants.NIOS_PKT_8x32_IDX_ADDR] = command

        if (dumpMessages) {
            Log.d(
                TAG, "NIOS 8x32 read" +
                        ", target=" + target +
                        ", command=" + command +
                        ", data=" + buffer.joinToString("") { "%02x".format(it) }
            )
        }

        if (!access(buffer)) {
            return null
        }

        return buffer.getIntAt(Constants.NIOS_PKT_8x32_IDX_DATA)
    }

    fun write8x32(target: Byte, command: Byte, data: Int): Boolean {
        val buffer = ByteArray(16)

        buffer[Constants.NIOS_PKT_8x32_IDX_MAGIC] = Constants.NIOS_PKT_8x32_MAGIC.toByte()
        buffer[Constants.NIOS_PKT_8x32_IDX_TARGET_ID] = target
        buffer[Constants.NIOS_PKT_8x32_IDX_FLAGS] = 1
        buffer[Constants.NIOS_PKT_8x32_IDX_ADDR] = command
        buffer[Constants.NIOS_PKT_8x32_IDX_DATA + 0] = ((data shr 0) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_8x32_IDX_DATA + 1] = ((data shr 8) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_8x32_IDX_DATA + 2] = ((data shr 16) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_8x32_IDX_DATA + 3] = ((data shr 24) and 0xff).toByte()

        if (dumpMessages) {
            Log.d(
                TAG, "NIOS 8x32 write" +
                        ", target=" + target +
                        ", command=" + command +
                        ", data=" + buffer.joinToString("") { "%02x".format(it) }
            )
        }

        return access(buffer) && (buffer[Constants.NIOS_PKT_8x32_IDX_FLAGS].toInt() and 1) != 0
    }

    @JvmOverloads
    fun read16x64(target: Byte, command: Byte, channel: Byte, fastAndQuiet: Boolean = false): Long? {
        val buffer = ByteArray(16)

        buffer[Constants.NIOS_PKT_16x64_IDX_MAGIC] = Constants.NIOS_PKT_16x64_MAGIC.toByte()
        buffer[Constants.NIOS_PKT_16x64_IDX_TARGET_ID] = target
        buffer[Constants.NIOS_PKT_16x64_IDX_FLAGS] = 0
        buffer[Constants.NIOS_PKT_16x64_IDX_ADDR + 0] = command
        buffer[Constants.NIOS_PKT_16x64_IDX_ADDR + 1] = channel

        if (dumpMessages) {
            Log.d(
                TAG, "NIOS 16x64 read" +
                        ", target=" + target +
                        ", command=" + command +
                        ", channel=" + channel +
                        ", data=" + buffer.joinToString("") { "%02x".format(it) }
            )
        }

        if (!access(buffer, if (fastAndQuiet) TIMEOUT / 10 else TIMEOUT, fastAndQuiet)) {
            return null
        }

        return buffer.getLongAt(Constants.NIOS_PKT_16x64_IDX_DATA)
    }

    fun write16x64(target: Byte, command: Byte, channel: Byte, data: Long): Boolean {
        val buffer = ByteArray(16)

        buffer[Constants.NIOS_PKT_16x64_IDX_MAGIC] = Constants.NIOS_PKT_16x64_MAGIC.toByte()
        buffer[Constants.NIOS_PKT_16x64_IDX_TARGET_ID] = target
        buffer[Constants.NIOS_PKT_16x64_IDX_FLAGS] = 1
        buffer[Constants.NIOS_PKT_16x64_IDX_ADDR + 0] = command
        buffer[Constants.NIOS_PKT_16x64_IDX_ADDR + 1] = channel
        buffer[Constants.NIOS_PKT_16x64_IDX_DATA + 0] = ((data shr 0) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_16x64_IDX_DATA + 1] = ((data shr 8) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_16x64_IDX_DATA + 2] = ((data shr 16) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_16x64_IDX_DATA + 3] = ((data shr 24) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_16x64_IDX_DATA + 4] = ((data shr 32) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_16x64_IDX_DATA + 5] = ((data shr 40) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_16x64_IDX_DATA + 6] = ((data shr 48) and 0xff).toByte()
        buffer[Constants.NIOS_PKT_16x64_IDX_DATA + 7] = ((data shr 56) and 0xff).toByte()

        if (dumpMessages) {
            Log.d(
                TAG, "NIOS 16x64 write" +
                        ", target=" + target +
                        ", command=" + command +
                        ", channel=" + channel +
                        ", data=" + buffer.joinToString("") { "%02x".format(it) }
            )
        }

        return access(buffer) && (buffer[Constants.NIOS_PKT_16x64_IDX_FLAGS].toInt() and 1) != 0
    }

    fun getFPGAVersion(): String? {
        Log.i(TAG, "Reading FPGA version")

        val result = read8x32(Constants.NIOS_PKT_8x32_TARGET_VERSION, 0.toByte()) ?: return null

        val major = (result shr 0) and 0xff
        val minor = (result shr 8) and 0xff
        val patch = (result shr 16) and 0xffff
        return "$major.$minor.$patch"
    }

    fun getVCTCXOTrim(): Short? {
        Log.i(TAG, "Reading VCTCXO trim")
        return read8x16(Constants.NIOS_PKT_8x16_TARGET_AD56X1_DAC, 0.toByte())
    }

    fun setVCTCXOTrim(value: Short): Boolean {
        Log.i(TAG, "Setting VCTCXO trim to $value")
        return write8x16(Constants.NIOS_PKT_8x16_TARGET_AD56X1_DAC, 0.toByte(), value)
    }

    fun getGPIO(): Int? {
        Log.i(TAG, "Reading GPIO")
        return read8x32(Constants.NIOS_PKT_8x32_TARGET_CONTROL, 0.toByte())
    }

    fun getRFFECSR(): Int? {
        Log.i(TAG, "Reading RFFE CSR")
        return read8x32(Constants.NIOS_PKT_8x32_TARGET_RFFE_CSR, 0.toByte())
    }
}
