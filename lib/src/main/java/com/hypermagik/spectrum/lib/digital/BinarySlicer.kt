package com.hypermagik.spectrum.lib.digital

import com.hypermagik.spectrum.lib.data.Complex32Array

class BinarySlicer {
    companion object {
        fun slice(input: Complex32Array, output: ByteArray, length: Int = input.size) {
            for (i in 0 until length) {
                output[i] = if (input[i].re > 0) 1 else 0
            }
        }
    }
}