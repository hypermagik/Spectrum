package com.hypermagik.spectrum.lib.devices.rtlsdr

interface I2CMaster {
    fun readI2C(address: Int, data: ByteArray, length: Int): Boolean
    fun writeI2C(address: Int, data: ByteArray, length: Int): Boolean
}