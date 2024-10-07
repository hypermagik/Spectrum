package com.hypermagik.spectrum.lib.data.converter

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.utils.fromArray
import java.nio.ByteBuffer

class IQConverterF32 : IQConverter {
    private val array = FloatArray(Complex32.MAX_ARRAY_SIZE * 2 * Float.SIZE_BYTES)

    override fun getSampleSize(): Int {
        return 2 * Float.SIZE_BYTES
    }

    override fun convert(input: ByteBuffer, output: Complex32Array) {
        if (input.isDirect) {
            input.asFloatBuffer().get(array, 0, output.size * 2)
            output.fromArray(array, 0, output.size)
        } else for (i in output.indices) {
            output[i].re = input.getFloat()
            output[i].im = input.getFloat()
        }
    }

    override fun convertWithLUT(input: ByteBuffer, output: Complex32Array) {
        throw NotImplementedError()
    }
}
