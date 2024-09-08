package com.hypermagik.spectrum.lib.devices.bladerf

import android.util.Log

@Suppress("SameParameterValue")
class RFIC internal constructor(private val nios: NIOS) {
    companion object {
        private const val TAG = "BladeRF-RFIC"
    }

    private fun read(command: Byte, channel: Byte): Long? {
        return nios.read16x64(Constants.NIOS_PKT_16x64_TARGET_RFIC, command, channel)
    }

    private fun getWriteQueueLength(): Int {
        val result = nios.read16x64(Constants.NIOS_PKT_16x64_TARGET_RFIC, Constants.RFIC_CMD_STATUS, Constants.CHANNEL_INVALID, true) ?: return -1
        val initialized = (result and 1L) == 1L
        if (!initialized) {
            return 0xff
        }
        return ((result shr 8) and 0xff).toInt()
    }

    private fun write(command: Byte, channel: Byte, value: Long): Boolean {
        val result = nios.write16x64(Constants.NIOS_PKT_16x64_TARGET_RFIC, command, channel, value)

        var tries = 50
        val delay = 100000

        while (tries-- > 0 && getWriteQueueLength() != 0) {
            try {
                Thread.sleep(0, delay)
            } catch (_: InterruptedException) {
                break
            }
        }

        return result
    }

    fun open(): Boolean {
        Log.i(TAG, "Opening RFIC")
        return write(Constants.RFIC_CMD_INIT, Constants.CHANNEL_INVALID, Constants.RFIC_STATE_ON.toLong())
    }

    fun close(): Boolean {
        Log.i(TAG, "Closing RFIC")
        return write(Constants.RFIC_CMD_INIT, Constants.CHANNEL_INVALID, Constants.RFIC_STATE_OFF.toLong())
    }

    fun enable(state: Int): Boolean {
        Log.i(TAG, if (state == 1) "Enabling RFIC Rx" else "Disabling RFIC Rx")
        return write(Constants.RFIC_CMD_ENABLE, Constants.CHANNEL_RX0, state.toLong())
    }

    fun getSampleRate(): Long {
        Log.i(TAG, "Reading RFIC sample rate")
        return read(Constants.RFIC_CMD_SAMPLERATE, Constants.CHANNEL_RX0) ?: -1
    }

    fun setSampleRate(sampleRate: Long): Boolean {
        Log.i(TAG, "Setting RFIC sample rate to $sampleRate")
        if (write(Constants.RFIC_CMD_SAMPLERATE, Constants.CHANNEL_RX0, sampleRate)) {
            Log.i(TAG, "RFIC sample rate set to $sampleRate")
            return true
        }
        return false
    }

    fun getBandwidth(): Long {
        Log.i(TAG, "Reading RFIC bandwidth")
        return read(Constants.RFIC_CMD_BANDWIDTH, Constants.CHANNEL_RX0) ?: -1
    }

    fun setBandwidth(bandwidth: Long): Boolean {
        Log.i(TAG, "Setting RFIC bandwidth to $bandwidth")
        return write(Constants.RFIC_CMD_BANDWIDTH, Constants.CHANNEL_RX0, bandwidth)
    }

    fun getGainMode(): Long {
        Log.i(TAG, "Reading RFIC gain mode")
        return read(Constants.RFIC_CMD_GAINMODE, Constants.CHANNEL_RX0) ?: -1
    }

    fun setGainMode(mode: Long): Boolean {
        Log.i(TAG, "Setting RFIC gain mode to $mode")
        return write(Constants.RFIC_CMD_GAINMODE, Constants.CHANNEL_RX0, mode)
    }

    fun getGain(): Long {
        Log.i(TAG, "Reading RFIC gain")
        return read(Constants.RFIC_CMD_GAIN, Constants.CHANNEL_RX0) ?: -1
    }

    fun setGain(gain: Long): Boolean {
        Log.i(TAG, "Setting RFIC gain to $gain")
        return write(Constants.RFIC_CMD_GAIN, Constants.CHANNEL_RX0, gain)
    }

    fun getFrequency(): Long {
        Log.i(TAG, "Reading RFIC frequency")
        return read(Constants.RFIC_CMD_FREQUENCY, Constants.CHANNEL_RX0) ?: -1
    }

    fun setFrequency(frequency: Long, quiet: Boolean): Boolean {
        if (!quiet) {
            Log.i(TAG, "Setting RFIC frequency to $frequency")
        }
        return write(Constants.RFIC_CMD_FREQUENCY, Constants.CHANNEL_RX0, frequency)
    }

    fun getRxFilter(): Long {
        Log.i(TAG, "Reading RFIC Rx filter")
        return read(Constants.RFIC_CMD_FILTER, Constants.CHANNEL_RX0) ?: -1
    }

    fun setRxFilter(filter: Long): Boolean {
        Log.i(TAG, "Setting RFIC Rx filter to $filter")
        return write(Constants.RFIC_CMD_FILTER, Constants.CHANNEL_RX0, filter)
    }

    fun setTxFilter(filter: Long): Boolean {
        Log.i(TAG, "Setting RFIC Tx filter to $filter")
        return write(Constants.RFIC_CMD_FILTER, Constants.CHANNEL_TX0, filter)
    }

    fun setTxMute() {
        Log.i(TAG, "Setting RFIC Tx mute")
        write(Constants.RFIC_CMD_TXMUTE, Constants.CHANNEL_TX0, 1)
        write(Constants.RFIC_CMD_TXMUTE, Constants.CHANNEL_TX1, 1)
    }
}
