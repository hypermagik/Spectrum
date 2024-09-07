package com.hypermagik.spectrum.lib.data.converter

import com.hypermagik.spectrum.lib.data.Complex32Array
import java.nio.ByteBuffer

class IQConverterU8 : IQConverter {
    private val lookupTable = FloatArray(256)

    init {
        for (i in 0 until 256) {
            lookupTable[i] = (i - 127.5f) / 128.0f
        }
    }

    override fun getSampleSize(): Int {
        return 2 * Byte.SIZE_BYTES
    }

    override fun convert(b: ByteBuffer, c: Complex32Array) {
        for (i in c.indices) {
            c[i].re = b.get().toUByte().toInt() - 127.5f / 128.0f
            c[i].im = b.get().toUByte().toInt() - 127.5f / 128.0f
        }
    }

    override fun convertWithLUT(b: ByteBuffer, c: Complex32Array) {
        for (i in c.indices) {
            c[i].re = lookupTable[b.get().toUByte().toInt()]
            c[i].im = lookupTable[b.get().toUByte().toInt()]
        }
    }
}
