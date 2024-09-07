package com.hypermagik.spectrum.lib.data.converter

import com.hypermagik.spectrum.lib.data.SampleType

class IQConverterFactory {
    companion object {
        fun create(type: SampleType): IQConverter {
            return when (type) {
                SampleType.S8 -> IQConverterS8()
                SampleType.U8 -> IQConverterU8()
                SampleType.S12P -> IQConverterS12P()
                SampleType.F32 -> IQConverterF32()
                else -> throw IllegalArgumentException("No converter available for sample type: $type")
            }
        }
    }
}
