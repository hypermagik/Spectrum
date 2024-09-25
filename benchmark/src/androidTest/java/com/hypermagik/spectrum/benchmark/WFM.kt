package com.hypermagik.spectrum.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.demod.Quadrature
import com.hypermagik.spectrum.lib.dsp.AGC
import com.hypermagik.spectrum.lib.dsp.BinarySlicer
import com.hypermagik.spectrum.lib.dsp.Costas
import com.hypermagik.spectrum.lib.dsp.Deemphasis
import com.hypermagik.spectrum.lib.dsp.Delay
import com.hypermagik.spectrum.lib.dsp.DifferentialDecoder
import com.hypermagik.spectrum.lib.dsp.FIR
import com.hypermagik.spectrum.lib.dsp.FIRC
import com.hypermagik.spectrum.lib.clock.MM
import com.hypermagik.spectrum.lib.dsp.PLL
import com.hypermagik.spectrum.lib.dsp.Resampler
import com.hypermagik.spectrum.lib.dsp.Shifter
import com.hypermagik.spectrum.lib.dsp.Taps
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.SplittableRandom

@RunWith(AndroidJUnit4::class)
class WFM {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private var sampleRate = 1000000
    private val samples = Complex32Array(sampleRate) { Complex32() }
    private val random = SplittableRandom()

    private val quadratureRate = 250000
    private val quadratureDeviation = 75000

    init {
        for (i in samples.indices) {
            samples[i].set(random.nextDouble(), random.nextDouble())
        }
    }

    @Test
    fun wfmMono() {
        val shifter = Shifter(sampleRate, -200000.0f)
        val resampler = Resampler(sampleRate, quadratureRate)
        val quadrature = Quadrature(quadratureRate, quadratureDeviation)
        val lowPassFIR = FIR(Taps.halfBand(), 2, true)
        val deemphasis = Array(2) { Deemphasis(50e-6f) }
        val audioTaps = Taps.lowPass(125000f, 14000.0f, 5000.0f)
        val audioFIR = FIR(audioTaps, 4)

        benchmarkRule.measureRepeated {
            shifter.shift(samples, samples)

            var sampleCount = resampler.resample(samples, samples, samples.size)
            var sampleRate = resampler.outputSampleRate

            quadrature.demodulate(samples, samples, sampleCount)

            sampleCount = lowPassFIR.filter(samples, samples, sampleCount)
            sampleRate /= lowPassFIR.decimation

            sampleCount = audioFIR.filter(samples, samples, sampleCount)
            sampleRate /= audioFIR.decimation

            deemphasis[0].filter(samples, sampleRate, sampleCount)
        }
    }

    @Test
    fun wfmMonoRDS() {
        val shifter = Shifter(sampleRate, -200000.0f)
        val resampler = Resampler(sampleRate, quadratureRate)
        val quadrature = Quadrature(quadratureRate, quadratureDeviation)
        val lowPassFIR = FIR(Taps.halfBand(), 2, true)
        val deemphasis = Array(2) { Deemphasis(50e-6f) }
        val audioTaps = Taps.lowPass(125000f, 14000.0f, 5000.0f)
        val audioFIR = FIR(audioTaps, 4)

        val rdsShifter = Shifter(sampleRate, -(57000 + 2375 / 2.0f))
        val rdsResampler = Resampler(sampleRate, 2375)
        val rdsAGC = AGC(0.5f)
        val rdsCostas = Costas(0.1f)
        val rdsMM = MM(1187.5f, 2375.0f)
        val rdsDifferentialDecoder = DifferentialDecoder()
        var rdsSamples = Complex32Array(0) { Complex32() }
        val rdsSoftBits = ByteArray(1188)
        val rdsHardBits = ByteArray(1188)

        benchmarkRule.measureRepeated {
            shifter.shift(samples, samples)

            var sampleCount = resampler.resample(samples, samples, samples.size)
            var sampleRate = resampler.outputSampleRate

            quadrature.demodulate(samples, samples, sampleCount)

            if (rdsSamples.size < sampleCount) {
                rdsSamples = Complex32Array(sampleCount) { Complex32() }
            }

            rdsShifter.shift(samples, rdsSamples, sampleCount)

            val rdsSampleCount = rdsResampler.resample(rdsSamples, rdsSamples, sampleCount)

            rdsAGC.process(rdsSamples, rdsSamples, rdsSampleCount)
            rdsCostas.process(rdsSamples, rdsSamples, rdsSampleCount)

            val symbolCount = rdsMM.process(rdsSamples, rdsSamples, rdsSampleCount)

            BinarySlicer.slice(rdsSamples, rdsSoftBits, symbolCount)
            rdsDifferentialDecoder.decode(rdsSoftBits, rdsHardBits, symbolCount)

            sampleCount = lowPassFIR.filter(samples, samples, sampleCount)
            sampleRate /= lowPassFIR.decimation

            sampleCount = audioFIR.filter(samples, samples, sampleCount)
            sampleRate /= audioFIR.decimation

            deemphasis[0].filter(samples, sampleRate, sampleCount)
        }
    }

    @Test
    fun wfmStereoPilot() {
        val shifter = Shifter(sampleRate, -200000.0f)
        val resampler = Resampler(sampleRate, quadratureRate)
        val quadrature = Quadrature(quadratureRate, quadratureDeviation)
        val lowPassFIR = FIR(Taps.halfBand(), 2, true)
        val pilotFIR = FIRC(Taps.bandPassC(125000.0f, 18750.0f, 19250.0f, 4000.0f))
        val pilotPLL = PLL(0.1f, (19000 / 125000.0f).toRadians(), (18750 / 125000.0f).toRadians(), (19250 / 125000.0f).toRadians())
        val delay = Delay((pilotFIR.taps.size - 1) / 2 + 1)
        var buffers = Array(3) { Complex32Array(0) { Complex32() } }
        val deemphasis = Array(2) { Deemphasis(50e-6f) }
        val audioTaps = Taps.lowPass(125000f, 14000.0f, 5000.0f)
        val audioFIRs = arrayOf(FIR(audioTaps, 4), FIR(audioTaps, 4))

        benchmarkRule.measureRepeated {
            shifter.shift(samples, samples)

            var sampleCount = resampler.resample(samples, samples, samples.size)
            var sampleRate = resampler.outputSampleRate

            quadrature.demodulate(samples, samples, sampleCount)

            sampleCount = lowPassFIR.filter(samples, samples, sampleCount)
            sampleRate /= lowPassFIR.decimation

            if (buffers[0].size < sampleCount) {
                buffers = arrayOf(
                    Complex32Array(sampleCount) { Complex32() },
                    Complex32Array(sampleCount) { Complex32() },
                    Complex32Array(sampleCount) { Complex32() },
                )
            }

            pilotFIR.filter(samples, buffers[0], sampleCount)
            pilotPLL.process(buffers[0], buffers[0], sampleCount)

            delay.process(samples, samples, sampleCount)

            for (i in 0 until sampleCount) {
                buffers[1][i].setmulconj(samples[i], buffers[0][i])
                buffers[1][i].setmulconj(buffers[1][i], buffers[0][i])
            }

            for (i in 0 until sampleCount) {
                buffers[2][i].setdif(samples[i], buffers[1][i])
                buffers[1][i].setsum(samples[i], buffers[1][i])
            }

            audioFIRs[0].filter(buffers[1], buffers[1], sampleCount)
            sampleCount = audioFIRs[1].filter(buffers[2], buffers[2], sampleCount)
            sampleRate /= audioFIRs[0].decimation

            deemphasis[0].filter(buffers[1], sampleRate, sampleCount)
            deemphasis[1].filter(buffers[2], sampleRate, sampleCount)
        }
    }

    @Test
    fun wfmStereoPilot10MHz() {
        var sampleRate = 10000000
        val shifter = Shifter(sampleRate, -200000.0f)
        val resampler = Resampler(sampleRate, quadratureRate)
        val quadrature = Quadrature(quadratureRate, quadratureDeviation)
        val lowPassFIR = FIR(Taps.halfBand(), 2, true)
        val pilotFIR = FIRC(Taps.bandPassC(125000.0f, 18750.0f, 19250.0f, 4000.0f))
        val pilotPLL = PLL(0.1f, (19000 / 125000.0f).toRadians(), (18750 / 125000.0f).toRadians(), (19250 / 125000.0f).toRadians())
        val delay = Delay((pilotFIR.taps.size - 1) / 2 + 1)
        var buffers = Array(3) { Complex32Array(0) { Complex32() } }
        val deemphasis = Array(2) { Deemphasis(50e-6f) }
        val audioTaps = Taps.lowPass(125000f, 14000.0f, 5000.0f)
        val audioFIRs = arrayOf(FIR(audioTaps, 4), FIR(audioTaps, 4))

        benchmarkRule.measureRepeated {
            for (k in 0 until 10) {
                shifter.shift(samples, samples)

                var sampleCount = resampler.resample(samples, samples, samples.size)
                sampleRate = resampler.outputSampleRate

                quadrature.demodulate(samples, samples, sampleCount)

                sampleCount = lowPassFIR.filter(samples, samples, sampleCount)
                sampleRate /= lowPassFIR.decimation

                if (buffers[0].size < sampleCount) {
                    buffers = arrayOf(
                        Complex32Array(sampleCount) { Complex32() },
                        Complex32Array(sampleCount) { Complex32() },
                        Complex32Array(sampleCount) { Complex32() },
                    )
                }

                pilotFIR.filter(samples, buffers[0], sampleCount)
                pilotPLL.process(buffers[0], buffers[0], sampleCount)

                delay.process(samples, samples, sampleCount)

                for (i in 0 until sampleCount) {
                    buffers[1][i].setmulconj(samples[i], buffers[0][i])
                    buffers[1][i].setmulconj(buffers[1][i], buffers[0][i])
                }

                for (i in 0 until sampleCount) {
                    buffers[2][i].setdif(samples[i], buffers[1][i])
                    buffers[1][i].setsum(samples[i], buffers[1][i])
                }

                audioFIRs[0].filter(buffers[1], buffers[1], sampleCount)
                sampleCount = audioFIRs[1].filter(buffers[2], buffers[2], sampleCount)
                sampleRate /= audioFIRs[0].decimation

                deemphasis[0].filter(buffers[1], sampleRate, sampleCount)
                deemphasis[1].filter(buffers[2], sampleRate, sampleCount)
            }
        }
    }

    @Test
    fun wfmStereoShift() {
        val shifter = Shifter(sampleRate, -200000.0f)
        val resampler = Resampler(sampleRate, quadratureRate)
        val quadrature = Quadrature(quadratureRate, quadratureDeviation)
        val lowPassFIR = FIR(Taps.halfBand(), 2, true)
        val stereoShifter = Shifter(125000, -38000.0f)
        var buffers = Array(3) { Complex32Array(0) { Complex32() } }
        val deemphasis = Array(2) { Deemphasis(50e-6f) }
        val audioTaps = Taps.lowPass(125000f, 14000.0f, 5000.0f)
        val audioFIRs = arrayOf(FIR(audioTaps, 4), FIR(audioTaps, 4))

        benchmarkRule.measureRepeated {
            shifter.shift(samples, samples)

            var sampleCount = resampler.resample(samples, samples, samples.size)
            var sampleRate = resampler.outputSampleRate

            quadrature.demodulate(samples, samples, sampleCount)

            sampleCount = lowPassFIR.filter(samples, samples, sampleCount)
            sampleRate /= lowPassFIR.decimation

            if (buffers[0].size < sampleCount) {
                buffers = arrayOf(
                    Complex32Array(sampleCount) { Complex32() },
                    Complex32Array(sampleCount) { Complex32() },
                    Complex32Array(sampleCount) { Complex32() },
                )
            }

            stereoShifter.shift(samples, buffers[0], sampleCount)

            for (i in 0 until sampleCount) {
                buffers[2][i].setdif(samples[i], buffers[0][i])
                buffers[1][i].setsum(samples[i], buffers[0][i])
            }

            audioFIRs[0].filter(buffers[1], buffers[1], sampleCount)
            sampleCount = audioFIRs[1].filter(buffers[2], buffers[2], sampleCount)
            sampleRate /= audioFIRs[0].decimation

            deemphasis[0].filter(buffers[1], sampleRate, sampleCount)
            deemphasis[1].filter(buffers[2], sampleRate, sampleCount)
        }
    }
}
