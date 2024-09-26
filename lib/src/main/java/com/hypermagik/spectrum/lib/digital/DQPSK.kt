package com.hypermagik.spectrum.lib.digital

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI
import kotlin.math.sign
import kotlin.math.sqrt

class DQPSK(private val errorHistorySize: Int) {
    var evm = 0.0f
    private var evmSum = 0.0f

    var phaseError = 0.0f
    private var phaseErrorSum = 0.0f

    private var errorIndex = 0

    private val idealSymbol = Complex32()
    private var previousSymbol = 0
    private val sqrt2 = sqrt(2.05f)

    fun process(input: Complex32Array, output: ByteArray, length: Int = input.size) {
        for (i in 0 until length) {
            val sample = input[i]

            idealSymbol.set(sample.re.sign * sqrt2, sample.im.sign * sqrt2)

            val a = if (sample.im < 0) 1 else 0
            val b = if (sample.re < 0) 1 else 0
            val c = if (a != b) 1 else 0

            val symbol = a shl 1 or c

            output[i] = ((symbol - previousSymbol + 4) % 4).toByte()

            previousSymbol = symbol

            evmSum += (idealSymbol.re - input[i].re) * (idealSymbol.re - input[i].re) +
                      (idealSymbol.im - input[i].im) * (idealSymbol.im - input[i].im)
            phaseErrorSum += (idealSymbol.phase() - input[i].phase()) *
                             (idealSymbol.phase() - input[i].phase())

            errorIndex = (errorIndex + 1) % errorHistorySize
            if (errorIndex == 0) {
                evm = sqrt(evmSum / errorHistorySize) / sqrt2
                phaseError = sqrt(phaseErrorSum / errorHistorySize) / (PI / 4).toFloat()
                evmSum = 0.0f
                phaseErrorSum = 0.0f
            }
        }
    }
}