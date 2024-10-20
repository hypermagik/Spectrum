package com.hypermagik.spectrum.demodulator

import android.util.Log
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.clock.FD
import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.digital.BitUnpacker
import com.hypermagik.spectrum.lib.digital.DQPSK
import com.hypermagik.spectrum.lib.digital.Tetra
import com.hypermagik.spectrum.lib.dsp.FIR
import com.hypermagik.spectrum.lib.dsp.FastAGC
import com.hypermagik.spectrum.lib.dsp.Resampler
import com.hypermagik.spectrum.lib.dsp.RootRaisedCosine
import com.hypermagik.spectrum.lib.loop.Costas
import com.hypermagik.spectrum.lib.loop.FLL
import com.hypermagik.spectrum.utils.TAG
import java.util.Locale
import kotlin.math.PI

class Tetra(private val preferences: Preferences) : Demodulator {
    private var sampleRate = 1000000
    private var channelSampleRate = 36000
    private var shiftFrequency = 0.0f

    private var symbolRate = 18000
    private val samplesPerSymbol = channelSampleRate / symbolRate

    private var resampler = Resampler(sampleRate, channelSampleRate, preferences.demodulatorGPUAPI)
    private val agc = FastAGC(2.0f, 1e6f, 0.02f)
    private val fll = FLL(samplesPerSymbol, 0.006f, -PI.toFloat() / 2, PI.toFloat() / 2, 65, 0.35f)
    private val rrc = FIR(RootRaisedCosine.make(samplesPerSymbol, 65, 0.35f))
    private val fd = FD(0.00628f, samplesPerSymbol)
    private val costas = Costas(0.01f, -PI.toFloat() / 8, PI.toFloat() / 8)
    private val dqpsk = DQPSK(symbolRate)

    private val symbols = ByteArray(symbolRate)
    private val softBits = ByteArray(symbolRate * 2)

    private val stack = Tetra()

    private val tetraSymbolMap = mapOf(
        0b00.toByte() to 0b00.toByte(),
        0b01.toByte() to 0b01.toByte(),
        0b10.toByte() to 0b11.toByte(),
        0b11.toByte() to 0b10.toByte(),
    )

    private val timeslotContent = arrayOf('O', '1', '2', 'S', 'V')
    private var demodEVM = 0.0f
    private var demodPE = 0.0f
    private var textChanged = true

    override fun getName(): String = "Tetra"

    private val outputs = mapOf(
        1 to "Channel",
        2 to "FLL",
    )

    override fun getOutputCount(): Int = outputs.size
    override fun getOutputName(output: Int): String = outputs[output]!!

    override fun getChannelBandwidth(): Int = channelSampleRate

    override fun setFrequency(frequency: Long) {
        shiftFrequency = frequency.toFloat()
        resampler.setShiftFrequency(-shiftFrequency)
    }

    override fun start() {
        stack.start()
    }

    override fun stop() {
        stack.stop()
        resampler.close()
    }

    override fun demodulate(buffer: SampleBuffer, output: Int, observe: (samples: SampleBuffer, preserveSamples: Boolean) -> Unit) {
        if (output == 0) {
            observe(buffer, true)
        }

        if (sampleRate != buffer.sampleRate) {
            Log.d(TAG, "Sample rate changed from $sampleRate to ${buffer.sampleRate}")
            sampleRate = buffer.sampleRate

            resampler.close()
            resampler = Resampler(sampleRate, channelSampleRate, preferences.demodulatorGPUAPI)
            resampler.setShiftFrequency(-shiftFrequency)
        }

        buffer.sampleCount = resampler.resample(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleRate = resampler.outputSampleRate
        buffer.frequency -= shiftFrequency.toLong()

        if (output == 1) {
            observe(buffer, true)
        }

        agc.process(buffer.samples, buffer.samples, buffer.sampleCount)
        fll.process(buffer.samples, buffer.samples, buffer.sampleCount)

        if (output == 2) {
            observe(buffer, true)
        }

        rrc.filter(buffer.samples, buffer.samples, buffer.sampleCount)

        val symbolCount = fd.process(buffer.samples, buffer.samples, buffer.sampleCount)

        costas.processPI4(buffer.samples, buffer.samples, symbolCount)

        dqpsk.process(buffer.samples, symbols, symbolCount)

        for (i in 0 until symbolCount) {
            symbols[i] = tetraSymbolMap[symbols[i]]!!
        }

        val softBitCount = BitUnpacker.unpack2B(symbols, softBits, symbolCount)

        stack.process(softBits, softBitCount)

        if (demodEVM != dqpsk.evm || demodPE != dqpsk.phaseError) {
            demodEVM = dqpsk.evm
            demodPE = dqpsk.phaseError
            textChanged = true
        }
    }

    override fun getText(): String? {
        if (textChanged) {
            textChanged = false

            if (!stack.isLocked()) {
                return String.format(Locale.getDefault(), "EVM: %.2f%% PE: %.2f%%", demodEVM * 100, demodPE * 100)
            }

            val tsc = stack.getTimeslotContent()
            val slot0 = tsc and 0xff
            val slot1 = tsc shr 8 and 0xff
            val slot2 = tsc shr 16 and 0xff
            val slot3 = tsc shr 24 and 0xff

            return String.format(
                Locale.getDefault(),
                "%d/%d/%d %f/%f [%c|%c|%c|%c] EVM: %.2f%% PE: %.2f%%",
                stack.getMCC(), stack.getMNC(), stack.getCC(), stack.getULFrequency() / 1e6f, stack.getDLFrequency() / 1e6f,
                timeslotContent[slot0], timeslotContent[slot1], timeslotContent[slot2], timeslotContent[slot3],
                demodEVM * 100, demodPE * 100
            )
        }
        return null
    }
}