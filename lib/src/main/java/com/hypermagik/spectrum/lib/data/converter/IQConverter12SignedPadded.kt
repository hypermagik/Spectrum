package com.hypermagik.spectrum.lib.data.converter

import com.hypermagik.spectrum.lib.data.Complex32Array
import java.nio.ByteBuffer

class IQConverter12SignedPadded : IQConverter {
    private val lookupTable = FloatArray(4096)

    init {
        for (i in 0 until 4096) {
            lookupTable[i] = (i - 2048) / 2048.0f
        }
    }

    override fun getSampleSize(): Int {
        return 2 * Short.SIZE_BYTES
    }

    override fun convert(b: ByteBuffer, c: Complex32Array) {
        for (i in c.indices) {
            c[i].re = b.getShort() / 2048.0f
            c[i].im = b.getShort() / 2048.0f
        }
    }

    override fun convertWithLUT(b: ByteBuffer, c: Complex32Array) {
        for (i in c.indices) {
            c[i].re = lookupTable[(b.getShort() + 2048).coerceIn(0, 4095)]
            c[i].im = lookupTable[(b.getShort() + 2048).coerceIn(0, 4095)]
        }
    }
}
