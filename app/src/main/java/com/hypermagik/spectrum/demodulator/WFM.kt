package com.hypermagik.spectrum.demodulator

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.demod.Quadrature
import com.hypermagik.spectrum.lib.dsp.Deemphasis
import com.hypermagik.spectrum.lib.dsp.Delay
import com.hypermagik.spectrum.lib.dsp.FIR
import com.hypermagik.spectrum.lib.dsp.FIRC
import com.hypermagik.spectrum.lib.loop.PLL
import com.hypermagik.spectrum.lib.dsp.Resampler
import com.hypermagik.spectrum.lib.dsp.Shifter
import com.hypermagik.spectrum.lib.dsp.Taps
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians

class WFM(private val audio: Boolean, private val stereo: Boolean, rds: Boolean) : Demodulator {
    private var sampleRate = 1000000

    private val quadratureRate = 250000
    private val quadratureDeviation = 75000

    private var shifter = Shifter(sampleRate, 0.0f)
    private var resampler = Resampler(sampleRate, quadratureRate)
    private var quadrature = Quadrature(quadratureRate, quadratureDeviation)
    private var lowPassFIR = FIR(Taps.halfBand(), 2, true)

    private var rdsDemodulator: RDS? = null

    private var stereoShift = true
    private var stereoShifter = Shifter(125000, -38000.0f)
    private var pilotFIR = FIRC(Taps.bandPassC(125000.0f, 18750.0f, 19250.0f, 4000.0f))
    private var pilotPLL = PLL(0.1f, (19000 / 125000.0f).toRadians(), (18750 / 125000.0f).toRadians(), (19250 / 125000.0f).toRadians())
    private val delay = Delay((pilotFIR.taps.size - 1) / 2 + 1)
    private var buffers = Array(3) { Complex32Array(0) { Complex32() } }

    // Typical time constant values:
    // USA: tau = 75 us
    // EU:  tau = 50 us
    private var deemphasis = Array(2) { Deemphasis(50e-6f) }

    private val audioTaps = Taps.lowPass(125000f, 14000.0f, 5000.0f)
    private var audioFIRs = arrayOf(FIR(audioTaps, 4), FIR(audioTaps, 4))

    private var audioSink: AudioSink? = null

    private val outputs = mapOf(
        1 to "LPF",
        2 to "Quadrature",
        3 to "Audio"
    )

    override fun getName(): String = "WFM"

    override fun getOutputCount(): Int = outputs.size
    override fun getOutputName(output: Int): String = outputs[output]!!

    init {
        if (audio) {
            audioSink = AudioSink(31250, 0.5f)
        }

        if (rds) {
            rdsDemodulator = RDS(quadratureRate)
        }
    }

    override fun getChannelBandwidth(): Int = quadratureRate

    override fun setFrequency(frequency: Long) {
        shifter.update(-frequency.toFloat())
    }

    override fun start() {
        audioSink?.start()
    }

    override fun stop() {
        audioSink?.stop()
    }

    override fun demodulate(buffer: SampleBuffer, output: Int, observe: (samples: SampleBuffer, preserveSamples: Boolean) -> Unit) {
        if (output == 0) {
            observe(buffer, true)
        }

        if (sampleRate != buffer.sampleRate) {
            sampleRate = buffer.sampleRate
            shifter = Shifter(sampleRate, shifter.frequency)
            resampler = Resampler(sampleRate, quadratureRate)
        }

        shifter.shift(buffer.samples, buffer.samples)
        buffer.frequency += -shifter.frequency.toLong()

        buffer.sampleCount = resampler.resample(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleRate = resampler.outputSampleRate

        if (output == 1) {
            observe(buffer, true)
        }

        quadrature.demodulate(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.realSamples = true

        rdsDemodulator?.demodulate(buffer)

        buffer.sampleCount = lowPassFIR.filter(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleRate /= lowPassFIR.decimation

        if (output == 2) {
            observe(buffer, true)
        }

        if (audio && stereo) {
            if (buffers[0].size < buffer.sampleCount) {
                buffers = arrayOf(
                    Complex32Array(buffer.sampleCount) { Complex32() },
                    Complex32Array(buffer.sampleCount) { Complex32() },
                    Complex32Array(buffer.sampleCount) { Complex32() },
                )
            }

            if (stereoShift) {
                stereoShifter.shift(buffer.samples, buffers[1], buffer.sampleCount)
            } else {
                pilotFIR.filter(buffer.samples, buffers[0], buffer.sampleCount)
                pilotPLL.process(buffers[0], buffers[0], buffer.sampleCount)

                delay.process(buffer.samples, buffer.samples, buffer.sampleCount)

                for (i in 0 until buffer.sampleCount) {
                    buffers[1][i].setmulconj(buffer.samples[i], buffers[0][i])
                    buffers[1][i].setmulconj(buffers[1][i], buffers[0][i])
                }
            }

            for (i in 0 until buffer.sampleCount) {
                buffers[2][i].setdif(buffer.samples[i], buffers[1][i])
                buffers[1][i].setsum(buffer.samples[i], buffers[1][i])
            }

            audioFIRs[0].filter(buffers[1], buffer.samples, buffer.sampleCount)
            buffer.sampleCount = audioFIRs[1].filter(buffers[2], buffers[2], buffer.sampleCount)
            buffer.sampleRate /= audioFIRs[0].decimation

            deemphasis[0].filter(buffer.samples, buffer.sampleRate, buffer.sampleCount)
            deemphasis[1].filter(buffers[2], buffer.sampleRate, buffer.sampleCount)

            audioSink?.play(buffer.samples, buffers[2], buffer.sampleCount)
        } else {
            buffer.sampleCount = audioFIRs[0].filter(buffer.samples, buffer.samples, buffer.sampleCount)
            buffer.sampleRate /= audioFIRs[0].decimation

            deemphasis[0].filter(buffer)

            audioSink?.play(buffer.samples, buffer.samples, buffer.sampleCount)
        }

        if (output == 3) {
            observe(buffer, false)
        }
    }

    override fun getText(): String? = rdsDemodulator?.getText()
}