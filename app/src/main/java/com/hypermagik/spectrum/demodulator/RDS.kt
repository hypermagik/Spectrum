package com.hypermagik.spectrum.demodulator

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.dsp.AGC
import com.hypermagik.spectrum.lib.dsp.BinarySlicer
import com.hypermagik.spectrum.lib.dsp.Costas
import com.hypermagik.spectrum.lib.dsp.DifferentialDecoder
import com.hypermagik.spectrum.lib.clock.MM
import com.hypermagik.spectrum.lib.dsp.Resampler
import com.hypermagik.spectrum.lib.dsp.Shifter
import java.util.Locale

class RDS(sampleRate: Int) {
    private var frequency = 0L

    private val shifter = Shifter(sampleRate, -(57000 + 2375 / 2.0f))
    private val resampler = Resampler(sampleRate, 2375)
    private val agc = AGC(0.5f)
    private val costas = Costas(0.1f)
    private val mm = MM(1187.5f, 2375.0f)
    private val differentialDecoder = DifferentialDecoder()

    private var samples = Complex32Array(0) { Complex32() }
    private val softBits = ByteArray(1188)
    private val hardBits = ByteArray(1188)

    fun demodulate(buffer: SampleBuffer) {
        if (frequency != buffer.frequency) {
            frequency = buffer.frequency
            reset()
        }

        if (samples.size < buffer.sampleCount) {
            samples = Complex32Array(buffer.sampleCount) { Complex32() }
        }

        shifter.shift(buffer.samples, samples, buffer.sampleCount)

        val sampleCount = resampler.resample(samples, samples, buffer.sampleCount)

        agc.process(samples, samples, sampleCount)
        costas.process(samples, samples, sampleCount)

        val symbolCount = mm.process(samples, samples, sampleCount)

        BinarySlicer.slice(samples, softBits, symbolCount)
        differentialDecoder.decode(softBits, hardBits, symbolCount)

        decode(symbolCount)
    }

    companion object {
        private const val BLOCK_LENGTH = 26
        private const val DATA_LENGTH = 16
        private const val POLYNOMIAL_LENGTH = 10
        private const val LFSR_POLYNOMIAL = 0b0110111001
        private const val INPUT_POLYNOMIAL = 0b1100011011
    }

    private val syndromes = mapOf(
        0b1111011000 to BlockType.A,
        0b1111010100 to BlockType.B,
        0b1001011100 to BlockType.C,
        0b1111001100 to BlockType.CP,
        0b1001011000 to BlockType.D,
    )

    private val offsets = intArrayOf(
        0b0011111100,
        0b0110011000,
        0b0101101000,
        0b1101010000,
        0b0110110100,
    )

    private var shiftRegister = 0

    private var symbolsPending = BLOCK_LENGTH
    private var knownSyndromes = 0

    enum class BlockType { A, B, C, CP, D }

    private var lastBlockType = BlockType.A

    private var blocks = IntArray(BlockType.entries.size)
    private var blockAvailable = BooleanArray(BlockType.entries.size)

    private var numBlocks = 0
    private var badBlocks = 0
    private var blerTimestamp = System.nanoTime()
    private var bler = 0.0

    private var groupType = 0
    private var groupLength = 0
    private var groupVersion = 0

    private val programServiceName = CharArray(8) { ' ' }
    private val radioText = CharArray(64) { ' ' }
    private var radioTextAB = 0
    private var textChanged = false

    private fun decode(symbolCount: Int) {
        for (i in 0 until symbolCount) {
            shiftRegister = (shiftRegister shl 1) and 0x3ffffff or hardBits[i].toInt()

            if (--symbolsPending > 0) {
                continue
            }

            numBlocks++

            val syndrome = getSyndrome(shiftRegister)
            if (!syndromes.containsKey(syndrome)) {
                badBlocks++
            }

            val now = System.nanoTime()
            if (now - blerTimestamp > 1000000000) {
                bler = badBlocks * 100.0 / numBlocks
                blerTimestamp = now
                numBlocks = 0
                badBlocks = 0
                textChanged = true
            }

            knownSyndromes += if (syndromes.containsKey(syndrome)) 1 else -1
            knownSyndromes = knownSyndromes.coerceIn(0, 4)

            if (knownSyndromes == 0) {
                continue
            }

            val blockType = syndromes[syndrome] ?: BlockType.entries[(lastBlockType.ordinal + 1) % BlockType.entries.size]

            blocks[blockType.ordinal] = correctErrors(blockType)

            if (syndromes.containsKey(syndrome) && !blockAvailable[blockType.ordinal]) {
                badBlocks++
            }

            if (blockType == BlockType.A) {
                decodeBlockA()
            } else if (blockType == BlockType.B) {
                groupLength = 1
            } else if (blockType == BlockType.C || blockType == BlockType.CP) {
                if (lastBlockType == BlockType.B) {
                    groupLength++
                }
            } else if (blockType == BlockType.D) {
                if (lastBlockType == BlockType.C || lastBlockType == BlockType.CP) {
                    groupLength++
                }
            } else {
                if (groupLength == 1) {
                    decodeBlockB()
                }
                groupLength = 0
            }

            if (groupLength >= 3) {
                groupLength = 0
                decodeGroup()
            }

            lastBlockType = blockType
            symbolsPending = BLOCK_LENGTH
        }
    }

    private fun getSyndrome(block: Int): Int {
        var syndrome = 0

        for (i in BLOCK_LENGTH - 1 downTo 0) {
            val bit = syndrome shr (POLYNOMIAL_LENGTH - 1) and 1
            syndrome = syndrome shl 1 and 0b1111111111
            syndrome = syndrome xor (LFSR_POLYNOMIAL * bit)
            syndrome = syndrome xor (INPUT_POLYNOMIAL * (block shr i and 1))
        }

        return syndrome
    }

    private fun correctErrors(blockType: BlockType): Int {
        val input = shiftRegister xor offsets[blockType.ordinal]
        var output = input
        var error = 0

        var syndrome = getSyndrome(input)
        if (syndrome != 0) {
            for (i in DATA_LENGTH - 1 downTo 0) {
                error = error or if (syndrome and 0b11111 == 0) 1 else 0

                val outBit = syndrome shr (POLYNOMIAL_LENGTH - 1) and 1
                output = output xor ((error and outBit) shl (i + POLYNOMIAL_LENGTH))

                syndrome = syndrome shl 1 and 0b1111111111
                syndrome = syndrome xor LFSR_POLYNOMIAL * outBit * if (error == 0) 1 else 0
            }
        }

        blockAvailable[blockType.ordinal] = syndrome and 0b11111 == 0

        return output
    }

    private fun decodeBlockA() {
        if (!blockAvailable[BlockType.A.ordinal]) {
            return
        }

        val block = blocks[BlockType.A.ordinal]
        val piCode = block shr POLYNOMIAL_LENGTH and 0xffff
        val countryCode = piCode shr 12 and 0xf
        val programCoverage = piCode shr 8 and 0xf
        val programRefNumber = piCode and 0xff

        // Log.d("RDS", "PI code: $piCode, Country code: $countryCode, program coverage: $programCoverage, program reference number: $programRefNumber")
    }

    private fun decodeBlockB() {
        if (!blockAvailable[BlockType.B.ordinal]) {
            return
        }

        val block = blocks[BlockType.B.ordinal]
        val data = block shr POLYNOMIAL_LENGTH and 0xffff

        groupType = data shr 12 and 0xf
        groupVersion = data shr 11 and 1

        val trafficProgram = data shr 10 and 1
        val programType = data shr 5 and 0x1f

        // Log.d("RDS", "Group type: $groupType, group version: $groupVersion, traffic program: $trafficProgram, program type: $programType")
    }

    private fun decodeGroup() {
        if (!blockAvailable[BlockType.B.ordinal]) {
            return
        }

        decodeBlockB()

        when (groupType) {
            0 -> decodeGroup0()
            2 -> decodeGroup2()
        }
    }

    private fun decodeGroup0() {
        val blockB = blocks[BlockType.B.ordinal]
        val blockC = blocks[BlockType.C.ordinal]
        val blockD = blocks[BlockType.D.ordinal]

        val trafficAnnouncement = blockB shr 14 and 1
        val music = blockB shr 13 and 1
        val diBit = blockB shr 12 and 1
        val offset = blockB shr 10 and 3
        val diOffset = 3 - offset
        val psOffset = offset * 2

        if (blockAvailable[BlockType.D.ordinal]) {
            val c1 = (blockD shr 18 and 0xff).toChar()
            val c2 = (blockD shr 10 and 0xff).toChar()
            if (c1 != programServiceName[psOffset + 0]) {
                programServiceName[psOffset + 0] = c1
                textChanged = true
            }
            if (c2 != programServiceName[psOffset + 1]) {
                programServiceName[psOffset + 1] = c2
                textChanged = true
            }
        }
    }

    private fun decodeGroup2() {
        val blockB = blocks[BlockType.B.ordinal]
        val blockC = blocks[BlockType.C.ordinal]
        val blockD = blocks[BlockType.D.ordinal]

        val ab = blockB shr 14 and 1
        val offset = blockB shr 10 and 0xf

        if (radioTextAB != ab) {
            radioTextAB = ab
            radioText.fill(' ')
        }

        if (groupVersion == 0) {
            val textOffset = offset * 4
            if (blockAvailable[BlockType.C.ordinal]) {
                val c1 = (blockC shr 18 and 0xff).toChar()
                val c2 = (blockC shr 10 and 0xff).toChar()
                if (c1 != radioText[textOffset + 0]) {
                    radioText[textOffset + 0] = c1
                    textChanged = true
                }
                if (c2 != radioText[textOffset + 1]) {
                    radioText[textOffset + 1] = c2
                    textChanged = true
                }
            }
            if (blockAvailable[BlockType.D.ordinal]) {
                val c1 = (blockD shr 18 and 0xff).toChar()
                val c2 = (blockD shr 10 and 0xff).toChar()
                if (c1 != radioText[textOffset + 2]) {
                    radioText[textOffset + 2] = c1
                    textChanged = true
                }
                if (c2 != radioText[textOffset + 3]) {
                    radioText[textOffset + 3] = c2
                    textChanged = true
                }
            }
        } else {
            val textOffset = offset * 2
            if (blockAvailable[BlockType.D.ordinal]) {
                val c1 = (blockD shr 18 and 0xff).toChar()
                val c2 = (blockD shr 10 and 0xff).toChar()
                if (c1 != radioText[textOffset + 0]) {
                    radioText[textOffset + 0] = c1
                    textChanged = true
                }
                if (c2 != radioText[textOffset + 1]) {
                    radioText[textOffset + 1] = c2
                    textChanged = true
                }
            }
        }

        // Log.d("RDS", "Radio text: ${getRadioText()}")
    }

    private fun reset() {
        symbolsPending = BLOCK_LENGTH
        knownSyndromes = 0
        blockAvailable.fill(false)
        groupLength = 0
        programServiceName.fill(' ')
        radioText.fill(' ')
        radioTextAB = 0
        numBlocks = 0
        badBlocks = 0
        bler = 0.0
        blerTimestamp = System.nanoTime()
        textChanged = true
    }

    private fun getRadioText(): String? {
        val hasProgramServiceName = programServiceName.any { it != ' ' }
        val hasRadioText = radioText.any { it != ' ' }

        var result: String =
            if (hasProgramServiceName && hasRadioText) {
                String(programServiceName).trim() + " - " + String(radioText).trim()
            } else if (hasProgramServiceName) {
                String(programServiceName).trim()
            } else if (hasRadioText) {
                String(radioText).trim()
            } else {
                ""
            }

        result += String.format(Locale.getDefault(), " (BLER: %.2f%%)", bler)

        return result
    }

    fun getText(): String? {
        if (textChanged) {
            textChanged = false
            return getRadioText()
        }
        return null
    }
}