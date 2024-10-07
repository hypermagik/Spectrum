package com.hypermagik.spectrum.lib.data.converter

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import java.nio.ByteBuffer

class IQConverterS12P : IQConverter {
    private val lookupTable = FloatArray(4096)
    private val array = ShortArray(Complex32.MAX_ARRAY_SIZE * 2 * Short.SIZE_BYTES)

    init {
        for (i in 0 until 4096) {
            lookupTable[i] = (i - 2048) / 2048.0f
        }
    }

    override fun getSampleSize(): Int {
        return 2 * Short.SIZE_BYTES
    }

    override fun convert(input: ByteBuffer, output: Complex32Array) {
        if (input.isDirect) {
            input.asShortBuffer().get(array, 0, output.size * 2)

            for (i in output.indices) {
                output[i].re = array[2 * i + 0] / 2048.0f
                output[i].im = array[2 * i + 1] / 2048.0f
            }
        } else {
            for (i in output.indices) {
                output[i].re = input.getShort() / 2048.0f
                output[i].im = input.getShort() / 2048.0f
            }
        }
    }

    override fun convertWithLUT(input: ByteBuffer, output: Complex32Array) {
        if (input.isDirect) {
            input.asShortBuffer().get(array, 0, output.size * 2)

            for (i in output.indices) {
                output[i].re = lookupTable[(array[2 * i + 0] + 2048).coerceIn(0, 4095)]
                output[i].im = lookupTable[(array[2 * i + 1] + 2048).coerceIn(0, 4095)]
            }
        } else {
            for (i in output.indices) {
                output[i].re = lookupTable[(input.getShort() + 2048).coerceIn(0, 4095)]
                output[i].im = lookupTable[(input.getShort() + 2048).coerceIn(0, 4095)]
            }
        }
    }
}
