package com.hypermagik.spectrum.lib.data.converter

import com.hypermagik.spectrum.lib.data.Complex32Array
import java.nio.ByteBuffer

class IQConverterF32 : IQConverter {
    override fun getSampleSize(): Int {
        return 2 * Float.SIZE_BYTES
    }

    override fun convert(b: ByteBuffer, c: Complex32Array) {
        for (i in c.indices) {
            c[i].re = b.getFloat()
            c[i].im = b.getFloat()
        }
    }

    override fun convertWithLUT(b: ByteBuffer, c: Complex32Array) {
        throw NotImplementedError()
    }
}
