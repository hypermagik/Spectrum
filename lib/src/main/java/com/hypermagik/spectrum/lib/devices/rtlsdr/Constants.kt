package com.hypermagik.spectrum.lib.devices.rtlsdr

@Suppress("Unused")
object Constants {
    const val DUMP_MESSAGES = false

    const val USB_TIMEOUT = 1000
    const val USB_TIMEOUT_FOR_SAMPLES = 100L

    // XTAL
    const val DEF_RTL_XTAL_FREQ = 28800000L
    const val MIN_RTL_XTAL_FREQ = (DEF_RTL_XTAL_FREQ - 1000)
    const val MAX_RTL_XTAL_FREQ = (DEF_RTL_XTAL_FREQ + 1000)

    // USB registers
    const val USB_SYSCTL = 0x2000
    const val USB_CTRL = 0x2010
    const val USB_STAT = 0x2014
    const val USB_EPA_CFG = 0x2144
    const val USB_EPA_CTL = 0x2148
    const val USB_EPA_MAXPKT = 0x2158
    const val USB_EPA_MAXPKT_2 = 0x215a
    const val USB_EPA_FIFO_CFG = 0x2160

    // System registers
    const val DEMOD_CTL = 0x3000
    const val GPO = 0x3001
    const val GPI = 0x3002
    const val GPOE = 0x3003
    const val GPD = 0x3004
    const val SYSINTE = 0x3005
    const val SYSINTS = 0x3006
    const val GP_CFG0 = 0x3007
    const val GP_CFG1 = 0x3008
    const val SYSINTE_1 = 0x3009
    const val SYSINTS_1 = 0x300a
    const val DEMOD_CTL_1 = 0x300b
    const val IR_SUSPEND = 0x300c

    // Blocks
    const val DEMODB = 0
    const val USBB = 1
    const val SYSB = 2
    const val TUNB = 3
    const val ROMB = 4
    const val IRB = 5
    const val IICB = 6

    // R820T
    const val R820T_I2C_ADDR = 0x34
    const val R820T_CHECK_ADDR = 0x00
    const val R820T_CHECK_VAL = 0x69

    const val R820T_IF_FREQ = 3570000L
}