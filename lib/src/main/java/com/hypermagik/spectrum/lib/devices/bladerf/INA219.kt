package com.hypermagik.spectrum.lib.devices.bladerf

import android.util.Log

@Suppress("SameParameterValue")
class INA219 internal constructor(private val nios: NIOS) {
    companion object {
        private const val TAG = "BladeRF-INA219"
        const val SHUNT: Float = 0.001f
    }

    private fun read(command: Byte): Short? {
        return nios.read8x16(Constants.NIOS_PKT_8x16_TARGET_INA219, command)
    }

    private fun write(command: Byte, value: Short): Boolean {
        return nios.write8x16(Constants.NIOS_PKT_8x16_TARGET_INA219, command, value)
    }

    fun initialize(): Boolean {
        Log.i(TAG, "Resetting INA219")

        var value = 0x8000
        if (!write(Constants.INA219_REG_CONFIGURATION, value.toShort())) {
            Log.e(TAG, "INA219 soft reset error")
            return false
        }

        // Poll until we're out of reset
        while ((value and 0x8000) != 0) {
            val result = read(Constants.INA219_REG_CONFIGURATION)
            if (result == null) {
                Log.e(TAG, "INA219 soft reset poll error")
                return false
            }
            value = result.toInt()
        }

        // Write configuration register
        // BRNG   (13) = 0 for 16V FSR
        // PG  (12-11) = 00 for 40mV
        // BADC (10-7) = 0011 for 12-bit / 532uS
        // SADC  (6-3) = 0011 for 12-bit / 532uS
        // MODE  (2-0) = 111 for continuous shunt & bus
        value = 0x019f
        if (!write(Constants.INA219_REG_CONFIGURATION, value.toShort())) {
            Log.e(TAG, "INA219 configuration error")
            return false
        }

        Log.i(TAG, String.format("Configuration register: 0x%04x", value))

        // Write calibration register
        // Current_LSB = 0.001 A / LSB
        // Calibration = 0.04096 / (Current_LSB * r_shunt)
        value = ((0.04096 / (0.001 * SHUNT)) + 0.5).toInt()
        if (!write(Constants.INA219_REG_CALIBRATION, value.toShort())) {
            Log.e(TAG, "INA219 calibration error")
            return false
        }

        Log.i(TAG, String.format("Calibration register: 0x%04x", value))

        return true
    }
}
