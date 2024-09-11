package com.hypermagik.spectrum.lib.devices.rtlsdr

import android.util.Log
import kotlin.math.min

class R820T(private val master: I2CMaster) : Tuner {
    companion object {
        private const val TAG = "RTLSDR-R820T"

        private const val MIN_FREQUENCY = 24000000L
        private const val MAX_FREQUENCY = 1766000000L

        private const val VERSION = 49

        private val resetRegisters = intArrayOf(
            0x83, 0x32, 0x75,       // 05 to 07
            0xc0, 0x40, 0xd6, 0x6c, // 08 to 0b
            0xf5, 0x63, 0x75, 0x68, // 0c to 0f
            0x6c, 0x83, 0x80, 0x00, // 10 to 13
            0x0f, 0x00, 0xc0, 0x30, // 14 to 17
            0x48, 0xcc, 0x60, 0x00, // 18 to 1b
            0x54, 0xae, 0x4a, 0xc0, // 1c to 1f
        )

        private val readReverseLUT = intArrayOf(0x0, 0x8, 0x4, 0xc, 0x2, 0xa, 0x6, 0xe, 0x1, 0x9, 0x5, 0xd, 0x3, 0xb, 0x7, 0xf)

        // Bandwidth contribution by low-pass filter.
        private val ifLowPassBandwidth = intArrayOf(1700000, 1600000, 1550000, 1450000, 1200000, 900000, 700000, 550000, 450000, 350000)

        private const val FILT_HP_BW1 = 350000
        private const val FILT_HP_BW2 = 380000

        data class FrequencyRange(val frequency: Long, val openDrain: Int, val rfMuxFilt: Int, val tfCorner: Int)

        private val frequencyRanges = arrayOf(
            FrequencyRange(0, 0x08, 0x02, 0xdf),
            FrequencyRange(50, 0x08, 0x02, 0xbe),
            FrequencyRange(55, 0x08, 0x02, 0x8b),
            FrequencyRange(60, 0x08, 0x02, 0x7b),
            FrequencyRange(65, 0x08, 0x02, 0x69),
            FrequencyRange(70, 0x08, 0x02, 0x58),
            FrequencyRange(75, 0x00, 0x02, 0x44),
            FrequencyRange(80, 0x00, 0x02, 0x44),
            FrequencyRange(90, 0x00, 0x02, 0x34),
            FrequencyRange(100, 0x00, 0x02, 0x34),
            FrequencyRange(110, 0x00, 0x02, 0x24),
            FrequencyRange(120, 0x00, 0x02, 0x24),
            FrequencyRange(140, 0x00, 0x02, 0x14),
            FrequencyRange(180, 0x00, 0x02, 0x13),
            FrequencyRange(220, 0x00, 0x02, 0x13),
            FrequencyRange(250, 0x00, 0x02, 0x11),
            FrequencyRange(280, 0x00, 0x02, 0x00),
            FrequencyRange(310, 0x00, 0x41, 0x00),
            FrequencyRange(450, 0x00, 0x41, 0x00),
            FrequencyRange(588, 0x00, 0x40, 0x00),
            FrequencyRange(650, 0x00, 0x40, 0x00),
        )

        private val lnaGainSteps = intArrayOf(0, 9, 13, 40, 38, 13, 31, 22, 26, 31, 26, 14, 19, 5, 35, 13)
        private val mixerGainSteps = intArrayOf(0, 5, 10, 10, 19, 9, 10, 25, 17, 10, 8, 16, 13, 6, 3, -8)
    }

    private val shadowRegisters = ByteArray(32)
    private val pllRegisters = ByteArray(7)
    private val syncBuffer = ByteArray(4)
    private val dataBuffer = ByteArray(16)
    private val scratchBuffer = ByteArray(16)

    private var xtalFrequency = Constants.DEF_RTL_XTAL_FREQ
    private var xtalFrequencyCorrectionPPM = 0L
    private var intermediateFrequency = Constants.R820T_IF_FREQ
    private var pllHasLock = false
    private var gain = 0
    private var agc = false

    private fun Byte.maskedSet(value: Int, mask: Int): Byte {
        return ((this.toInt() and mask.inv()) or (value and mask)).toByte()
    }

    private fun read(data: ByteArray, length: Int): Boolean {
        if (!master.readI2C(Constants.R820T_I2C_ADDR, data, length)) {
            return false
        }
        for (i in 0 until length) {
            data[i] = ((readReverseLUT[data[i].toInt() and 0xf] shl 4) or (readReverseLUT[data[i].toInt() shr 4 and 0xf])).toByte()
        }
        return true
    }

    private fun write(register: Int, data: ByteArray, length: Int): Boolean {
        var result = true
        for (i in 0 until length) {
            if (shadowRegisters[register + i] != data[i]) {
                result = false
                break
            }
        }

        if (result) {
            return true
        }

        result = true

        for (i in 0 until length step 8) {
            val n = min(8, length - i)
            dataBuffer[0] = (register + i).toByte()

            for (j in 0 until n) {
                dataBuffer[1 + j] = data[i + j]
            }

            var success = master.writeI2C(Constants.R820T_I2C_ADDR, dataBuffer, n + 1)
            if (!success) {
                // Read and try again.
                read(syncBuffer, syncBuffer.size)
                success = master.writeI2C(Constants.R820T_I2C_ADDR, dataBuffer, n + 1)
            }

            if (success) {
                for (j in i until i + n) {
                    shadowRegisters[register + j] = data[j]
                }
            }

            result = result and success
        }

        return result
    }

    private fun writeRegister(register: Int, value: Int): Boolean {
        if (shadowRegisters[register] == value.toByte()) {
            return true
        }

        shadowRegisters[register] = value.toByte()

        dataBuffer[0] = register.toByte()
        dataBuffer[1] = value.toByte()
        var result = master.writeI2C(Constants.R820T_I2C_ADDR, dataBuffer, 2)
        if (!result) {
            // Read and try again.
            read(syncBuffer, syncBuffer.size)

            dataBuffer[0] = register.toByte()
            dataBuffer[1] = value.toByte()
            result = master.writeI2C(Constants.R820T_I2C_ADDR, dataBuffer, 2)
        }
        if (!result) {
            Log.e(TAG, "Failed to write register $register, value $value, result $result")
        }
        return result
    }

    private fun writeRegisterMask(register: Int, value: Int, mask: Int): Boolean {
        val v = (shadowRegisters[register].toInt() and 0xff and mask.inv()) or (value and mask)
        return writeRegister(register, v)
    }

    override fun open(): Boolean {
        Log.i(TAG, "Initializing R820T")

        xtalFrequency = Constants.DEF_RTL_XTAL_FREQ
        xtalFrequencyCorrectionPPM = 0L
        intermediateFrequency = Constants.R820T_IF_FREQ
        pllHasLock = false

        // Initialize registers
        val registers = ByteArray(resetRegisters.size)
        for (i in resetRegisters.indices) {
            registers[i] = resetRegisters[i].toByte()
        }

        if (!write(5, registers, registers.size)) return false

        for (i in resetRegisters.indices) {
            shadowRegisters[5 + i] = resetRegisters[i].toByte()
        }

        if (!setStandardDTV()) return false
        if (!setDeliverySystemDVBT()) return false

        return true
    }

    private fun setStandardDTV(): Boolean {
        Log.d(TAG, "Setting standard to Digital TV")

        intermediateFrequency = Constants.R820T_IF_FREQ

        // BW < 6 MHz
        val lo = 56000              // 52000->56000
        val gain = 0x10             // +3db, 6mhz on
        val image = 0x00            // image negative
        val q = 0x10                // r10[4]:low q(1'b1)
        val hpCorner = 0x6b         // 1.7m disable, +2cap, 1.0mhz
        val extEnable = 0x60        // r30[6]=1 ext enable r30[5]:1 ext at lna max-1
        val loopThrough = 0x01      // r5[7], lt off
        val loopThroughAtt = 0x00   // r31[7], lt att enable
        val extWide = 0x00          // r15[7]: flt_ext_wide off
        val polyfil = 0x60          // r25[6:5]:min

        var result = 0

        // Init Flag & Xtal_check Result (inits VGA gain, needed?)
        writeRegisterMask(0x0c, 0x00, 0x0f)
        // Version
        writeRegisterMask(0x13, VERSION, 0x3f)
        // For LT Gain test
        writeRegisterMask(0x1d, 0x00, 0x38)

        // Filter calibration
        for (i in 0 until 2) {
            // Set filt_cap
            writeRegisterMask(0x0b, hpCorner, 0x60)
            // Set cali clk = on
            writeRegisterMask(0x0f, 0x04, 0x04)
            // X'tal cap 0pF for PLL
            writeRegisterMask(0x10, 0x00, 0x03)

            if (!setPLL(lo * 1000L) || !pllHasLock) {
                return false
            }

            // Start Trigger
            writeRegisterMask(0x0b, 0x10, 0x10)
            // Stop Trigger
            writeRegisterMask(0x0b, 0x00, 0x10)
            // Set cali clk = off
            writeRegisterMask(0x0f, 0x00, 0x04)

            // Check if calibration worked
            read(scratchBuffer, scratchBuffer.size)

            result = scratchBuffer[4].toInt() and 0x0f

            if (result != 0 && result != 0x0f) {
                break
            }
        }

        // Narrowest
        if (result == 0x0f) {
            result = 0
        }
        writeRegisterMask(0x0a, q or result, 0x1f)

        // Set BW, Filter_gain, & HP corner
        writeRegisterMask(0x0b, hpCorner, 0xef)
        // Set Img_R
        writeRegisterMask(0x07, image, 0x80)
        // Set filt_3dB, V6MHz
        writeRegisterMask(0x06, gain, 0x30)
        // Channel filter extension
        writeRegisterMask(0x1e, extEnable, 0x60)
        // Loop through
        writeRegisterMask(0x05, loopThrough, 0x80)
        // Loop through attenuation
        writeRegisterMask(0x1f, loopThroughAtt, 0x80)
        // Filter extension widest
        writeRegisterMask(0x0f, extWide, 0x80)
        // RF poly filter current
        writeRegisterMask(0x19, polyfil, 0x60)

        return true
    }

    private fun setDeliverySystemDVBT(): Boolean {
        Log.d(TAG, "Setting delivery system to DVB-T")

        val mixerTop = 0x24     // mixer top:13 , top-1, low-discharge
        val lnaTop = 0xe5       // detect bw 3, lna top:4, predet top:2
        val cpCur = 0x38        // 111, auto
        val divBufCur = 0x30    // 11, 150u
        val lnaVthL = 0x53      // lna vth 0.84	,  vtl 0.64
        val mixerVthL = 0x75    // mixer vth 1.04, vtl 0.84
        val airCable1In = 0x00
        val cable2In = 0x00
        val lnaDischarge = 14
        val filterCur = 0x40    // 10, low

        writeRegisterMask(0x1d, lnaTop, 0xc7)
        writeRegisterMask(0x1c, mixerTop, 0xf8)
        writeRegister(0x0d, lnaVthL)
        writeRegister(0x0e, mixerVthL)
        writeRegisterMask(0x05, airCable1In, 0x60)
        writeRegisterMask(0x06, cable2In, 0x08)
        writeRegisterMask(0x11, cpCur, 0x38)
        writeRegisterMask(0x17, divBufCur, 0x30)
        writeRegisterMask(0x0a, filterCur, 0x60)

        // LNA TOP: lowest
        writeRegisterMask(0x1d, 0, 0x38)
        // 0: normal mode
        writeRegisterMask(0x1c, 0, 0x04)
        // 0: PRE_DECT off
        writeRegisterMask(0x06, 0, 0x40)
        // AGC clk 250hz
        writeRegisterMask(0x1a, 0x30, 0x30)
        // Write LNA TOP = 3
        writeRegisterMask(0x1d, 0x18, 0x38)
        // Write discharge mode
        writeRegisterMask(0x1c, mixerTop, 0x04)
        // LNA discharge current
        writeRegisterMask(0x1e, lnaDischarge, 0x1f)
        // AGC clk 60hz
        writeRegisterMask(0x1a, 0x20, 0x30)

        return true
    }

    override fun close() {
        writeRegister(0x06, 0xb1)
        writeRegister(0x05, 0xa0)
        writeRegister(0x07, 0x3a)
        writeRegister(0x08, 0x40)
        writeRegister(0x09, 0xc0)
        writeRegister(0x0a, 0x36)
        writeRegister(0x0c, 0x35)
        writeRegister(0x0f, 0x68)
        writeRegister(0x11, 0x03)
        writeRegister(0x17, 0xf4)
        writeRegister(0x19, 0x0c)
    }

    override fun setBandwidth(bandwidth: Int): Long {
        val r0a: Int
        var r0b: Int

        if (bandwidth > ifLowPassBandwidth[0] + FILT_HP_BW1 + FILT_HP_BW2) {
            r0a = 0x10
            r0b = 0x6b
            intermediateFrequency = 3570000
        } else {
            r0a = 0x00
            r0b = 0x80
            intermediateFrequency = 2300000

            var bw = bandwidth
            var realBW = 0

            if (bw > ifLowPassBandwidth[0] + FILT_HP_BW1) {
                bw -= FILT_HP_BW2
                intermediateFrequency += FILT_HP_BW2
                realBW += FILT_HP_BW2
            } else {
                r0b = r0b or 0x20
            }

            if (bw > ifLowPassBandwidth[0]) {
                bw -= FILT_HP_BW1
                intermediateFrequency += FILT_HP_BW1
                realBW += FILT_HP_BW1
            } else {
                r0b = r0b or 0x40
            }

            // Find low-pass filter.
            var index = ifLowPassBandwidth.size
            for (i in ifLowPassBandwidth.indices) {
                if (bw > ifLowPassBandwidth[i]) {
                    index = i
                    break
                }
            }

            index -= 1
            r0b = r0b or (15 - index)
            realBW += ifLowPassBandwidth[index]

            intermediateFrequency -= realBW / 2
        }

        writeRegisterMask(0x0a, r0a, 0x10)
        writeRegisterMask(0x0b, r0b, 0xef)

        return intermediateFrequency
    }

    private fun getXtalFrequency(): Long {
        return (xtalFrequency * (1.0 + xtalFrequencyCorrectionPPM / 1e6)).toLong()
    }

    private fun setMux(frequency: Long) {
        val fMHz = frequency / 1000000

        var frequencyRange: FrequencyRange = frequencyRanges[frequencyRanges.size - 1]
        for (i in 0 until frequencyRanges.size - 1) {
            if (fMHz < frequencyRanges[i + 1].frequency) {
                frequencyRange = frequencyRanges[i]
                break
            }
        }

        // Open Drain
        writeRegisterMask(0x17, frequencyRange.openDrain, 0x08)
        // RF_MUX, RF filter band
        writeRegisterMask(0x1a, frequencyRange.rfMuxFilt, 0xc3)
        // TF BAND
        writeRegister(0x1b, frequencyRange.tfCorner)
        // REFDIV, CAPX
        writeRegisterMask(0x10, 0x00, 0x0b)
        // PW0_AMP, IMR_G
        writeRegisterMask(0x08, 0x00, 0x3f)
        // PW1_IFFILT, IMR_P
        writeRegisterMask(0x09, 0x00, 0x3f)
    }

    private fun setPLL(frequency: Long): Boolean {
        for (i in pllRegisters.indices) {
            pllRegisters[i] = shadowRegisters[0x10 + i]
        }

        // Set pll autotune = 128kHz
        writeRegisterMask(0x1a, 0x00, 0x0c)

        // Set refdiv2 = 0
        pllRegisters[0] = pllRegisters[0].maskedSet(0x00, 0x10)
        // Set VCO current = 100
        pllRegisters[2] = pllRegisters[2].maskedSet(0x80, 0xe0)

        // Calculate divider
        val fKHz = (frequency + 500) / 1000

        if (Constants.DUMP_MESSAGES) {
            Log.d(TAG, "Setting PLL to $fKHz kHz")
        }

        val vcoMin = 1770000 // kHz
        val vcoMax = vcoMin * 2 // kHz
        var divNum = 0
        var mixDiv = 2
        while (mixDiv <= 64) {
            if ((fKHz * mixDiv) >= vcoMin && (fKHz * mixDiv) < vcoMax) {
                var divBuf = mixDiv
                while (divBuf > 2) {
                    divBuf /= 2
                    divNum += 1
                }
                break
            }
            mixDiv *= 2
        }

        read(scratchBuffer, scratchBuffer.size)

        val vcoPowerRef = 2 // 1 if R828D
        val vcoFineTune = (scratchBuffer[4].toInt() and 0x30) shr 4
        if (vcoFineTune > vcoPowerRef) {
            divNum -= 1
        } else if (vcoFineTune < vcoPowerRef) {
            divNum += 1
        }

        pllRegisters[0] = pllRegisters[0].maskedSet(divNum shl 5, 0xe0)

        // We want to approximate:
        //  vco_freq / (2 * pll_ref)
        // in the form:
        //  nint + sdm/65536
        // where nint,sdm are integers and 0 < nint, 0 <= sdm < 65536
        //
        // Scaling to fixed point and rounding:
        //  vco_div = 65536*(nint + sdm/65536) = int( 0.5 + 65536 * vco_freq / (2 * pll_ref) )
        //  vco_div = 65536*nint + sdm         = int( (pll_ref + 65536 * vco_freq) / (2 * pll_ref) )

        val pllReference = getXtalFrequency()
        val vcoFrequency = frequency * mixDiv
        val vcoDivider = (pllReference + 65536 * vcoFrequency) / (2 * pllReference)
        val nint = vcoDivider / 65536
        val sdm = vcoDivider % 65536

        if (nint > 128 / vcoPowerRef - 1) {
            Log.e(TAG, "No valid PLL values for $frequency Hz!")
            return false
        }

        val ni = (nint - 13) / 4
        val si = nint - 4 * ni - 13

        pllRegisters[4] = (ni + (si shl 6)).toByte()
        pllRegisters[2] = pllRegisters[2].maskedSet(if (sdm == 0L) 0x08 else 0x00, 0x08)
        pllRegisters[5] = (sdm and 0xff).toByte()
        pllRegisters[6] = (sdm shr 8).toByte()

        if (!write(0x10, pllRegisters, pllRegisters.size)) {
            Log.e(TAG, "Couldn't write PLL registers")
            return false
        }

        for (i in 0 until 2) {
            // Check if PLL has locked
            read(scratchBuffer, 3)

            if (scratchBuffer[2].toInt() and 0x40 != 0) {
                break
            }

            if (i == 0) {
                // Didn't lock. Increase VCO current
                writeRegisterMask(0x12, 0x60, 0xe0)
            }
        }

        if (scratchBuffer[2].toInt() and 0x40 == 0) {
            Log.e(TAG, "PLL not locked at $frequency Hz!")
            pllHasLock = false
            return false
        }

        pllHasLock = true

        // Set pll autotune = 8kHz
        return writeRegisterMask(0x1a, 0x08, 0x08)
    }

    override fun getMinimumFrequency(): Long = MIN_FREQUENCY
    override fun getMaximumFrequency(): Long = MAX_FREQUENCY

    override fun setFrequency(frequency: Long) {
        val lo = intermediateFrequency + frequency

        setMux(lo)
        setPLL(lo)
    }

    override fun setFrequencyCorrection(ppm: Long) {
        xtalFrequencyCorrectionPPM = ppm
    }

    override fun setGain(gain: Int) {
        this.gain = gain

        if (!agc) {
            var lnaIndex = 0
            var mixIndex = 0

            var totalGain = 0
            for (i in 0 until 15) {
                if (totalGain >= gain) break
                lnaIndex++
                totalGain += lnaGainSteps[lnaIndex]
                if (totalGain >= gain) break
                mixIndex++
                totalGain += mixerGainSteps[mixIndex]
            }

            // Set LNA gain
            writeRegisterMask(0x05, lnaIndex or 0x10, 0x1f)
            // Set Mixer gain
            writeRegisterMask(0x07, mixIndex, 0x1f)
        }
    }

    override fun setAGC(enable: Boolean) {
        agc = enable

        // Set fixed VGA gain for now (16.3 dB)
        writeRegisterMask(0x0c, 0x07, 0x9f)

        if (enable) {
            // LNA auto on
            writeRegisterMask(0x05, 0x00, 0x10)
            // Mixer auto on
            writeRegisterMask(0x07, 0x10, 0x10)
        } else {
            // Restore gain.
            setGain(gain)
        }
    }
}