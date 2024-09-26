package com.hypermagik.spectrum.lib.digital

class DifferentialDecoder(private val modulus: Int = 2) {
    private var lastValue: Byte = 0

    fun decode(input: ByteArray, output: ByteArray, length: Int = input.size) {
        for (i in 0 until length) {
            output[i] = ((input[i] - lastValue + modulus) % modulus).toByte()
            lastValue = input[i]
        }
    }
}