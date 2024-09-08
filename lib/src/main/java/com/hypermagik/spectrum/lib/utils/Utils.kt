package com.hypermagik.spectrum.lib.utils

object Utils {
    fun ByteArray.getShortAt(offset: Int): Short {
        val result = (this[offset].toInt() and 0xff) or
                ((this[offset + 1].toInt() and 0xff) shl 8)
        return result.toShort()
    }

    fun ByteArray.getIntAt(offset: Int): Int {
        val result = (this[offset].toInt() and 0xff) or
                ((this[offset + 1].toInt() and 0xff) shl 8) or
                ((this[offset + 2].toInt() and 0xff) shl 16) or
                ((this[offset + 3].toInt() and 0xff) shl 24)
        return result
    }

    fun ByteArray.getLongAt(offset: Int): Long {
        val result = (this[offset].toLong() and 0xff) or
                ((this[offset + 1].toLong() and 0xff) shl 8) or
                ((this[offset + 2].toLong() and 0xff) shl 16) or
                ((this[offset + 3].toLong() and 0xff) shl 24) or
                ((this[offset + 4].toLong() and 0xff) shl 32) or
                ((this[offset + 5].toLong() and 0xff) shl 40) or
                ((this[offset + 6].toLong() and 0xff) shl 48) or
                ((this[offset + 7].toLong() and 0xff) shl 56)
        return result
    }
}
