package com.hypermagik.spectrum.lib.digital

import java.nio.ByteBuffer

class Tetra {
    external fun start()
    external fun stop()
    external fun process(bits: ByteBuffer, length: Int)

    external fun isLocked(): Boolean
    external fun getCC(): Int
    external fun getMCC(): Int
    external fun getMNC(): Int
    external fun getDLFrequency(): Int
    external fun getULFrequency(): Int
    external fun getTimeslotContent(): Int
    external fun getServiceDetails(): Int

    private val buffer = ByteBuffer.allocateDirect(18000)

    fun process(bits: ByteArray, length: Int) {
        buffer.rewind()
        buffer.put(bits, 0, length)
        process(buffer, length)
    }
}
