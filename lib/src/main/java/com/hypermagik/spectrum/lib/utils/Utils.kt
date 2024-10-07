package com.hypermagik.spectrum.lib.utils

import com.hypermagik.spectrum.lib.data.Complex32Array

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

fun Complex32Array.toArray(array: FloatArray, offset: Int = 0, length: Int = size) {
    for (i in 0 until length) {
        array[2 * (offset + i) + 0] = this[i].re
        array[2 * (offset + i) + 1] = this[i].im
    }
}

fun Complex32Array.fromArray(array: FloatArray, offset: Int = 0, length: Int = array.size) {
    for (i in 0 until length) {
        this[i].re = array[2 * (offset + i) + 0]
        this[i].im = array[2 * (offset + i) + 1]
    }
}