package com.hypermagik.spectrum.lib.digital

class BitUnpacker {
    companion object {
        fun unpack2B(input: ByteArray, output: ByteArray, length: Int = input.size): Int {
            for (i in 0 until length) {
                output[2 * i + 0] = (input[i].toInt() shr 1 and 0b01).toByte()
                output[2 * i + 1] = (input[i].toInt() and 0b01).toByte()
            }

            return length * 2
        }
    }
}