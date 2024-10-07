package com.hypermagik.spectrum.lib.data.converter

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import java.nio.ByteBuffer

class IQConverterS8 : IQConverter {
    private val lookupTable = FloatArray(256)
    private val array = ByteArray(Complex32.MAX_ARRAY_SIZE * 2 * Byte.SIZE_BYTES)

    init {
        for (i in 0 until 256) {
            lookupTable[i] = i / 128.0f
        }
    }

    override fun getSampleSize(): Int {
        return 2 * Byte.SIZE_BYTES
    }

    override fun convert(input: ByteBuffer, output: Complex32Array) {
        if (input.isDirect) {
            input.get(array, 0, output.size * 2)

            for (i in output.indices) {
                output[i].re = array[2 * i + 0] / 128.0f
                output[i].im = array[2 * i + 1] / 128.0f
            }
        } else {
            for (i in output.indices) {
                output[i].re = input.get() / 128.0f
                output[i].im = input.get() / 128.0f
            }
        }
    }

    override fun convertWithLUT(input: ByteBuffer, output: Complex32Array) {
        if (input.isDirect) {
            input.get(array, 0, output.size * 2)

            for (i in output.indices) {
                output[i].re = lookupTable[array[2 * i + 0] + 128]
                output[i].im = lookupTable[array[2 * i + 1] + 128]
            }
        } else {
            for (i in output.indices) {
                output[i].re = lookupTable[input.get() + 128]
                output[i].im = lookupTable[input.get() + 128]
            }
        }
    }
}
