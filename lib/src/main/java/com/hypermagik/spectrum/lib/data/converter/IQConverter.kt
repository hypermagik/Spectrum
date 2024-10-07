package com.hypermagik.spectrum.lib.data.converter

import com.hypermagik.spectrum.lib.data.Complex32Array
import java.nio.ByteBuffer

interface IQConverter {
    fun getSampleSize(): Int

    fun convert(input: ByteBuffer, output: Complex32Array)
    fun convertWithLUT(input: ByteBuffer, output: Complex32Array)
}
