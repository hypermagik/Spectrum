package com.hypermagik.spectrum.lib.data.converter

class IQConverterFactory {
    enum class IQConverterType { IQ8Signed, IQ8Unsigned, IQ12SignedPadded, IQ32Float }

    companion object {
        fun create(type: IQConverterType): IQConverter {
            return when (type) {
                IQConverterType.IQ8Signed -> IQConverter8Signed()
                IQConverterType.IQ8Unsigned -> IQConverter8Unsigned()
                IQConverterType.IQ12SignedPadded -> IQConverter12SignedPadded()
                IQConverterType.IQ32Float -> IQConverter32Float()
            }
        }
    }
}
