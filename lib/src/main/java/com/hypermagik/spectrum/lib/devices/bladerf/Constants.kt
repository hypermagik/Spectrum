package com.hypermagik.spectrum.lib.devices.bladerf

@Suppress("Unused", "ConstPropertyName")
object Constants {
    const val DUMP_MESSAGES = false

    const val USB_TIMEOUT = 1000
    const val USB_TIMEOUT_FOR_SAMPLES = 100

    const val MIN_SAMPLE_RATE = 520834
    const val MAX_SAMPLE_RATE = 61440000
    const val FIR_SAMPLE_RATE = 2083334L

    const val MIN_FREQUENCY = 70000000L
    const val MAX_FREQUENCY = 6000000000L

    const val MIN_GAIN = -17
    const val MAX_GAIN = 60

    const val INTERFACE_NULL: Int = 0
    const val INTERFACE_RF_LINK: Int = 1

    const val USB_CMD_QUERY_VERSION: Int = 0
    const val USB_CMD_QUERY_FPGA_STATUS: Int = 1
    const val USB_CMD_RF_RX: Int = 4
    const val USB_CMD_RF_TX: Int = 5
    const val USB_CMD_QUERY_DEVICE_READY: Int = 6
    const val USB_CMD_RESET: Int = 105

    const val NIOS_PKT_8x16_MAGIC: Int = 'B'.code
    const val NIOS_PKT_8x16_IDX_MAGIC: Int = 0
    const val NIOS_PKT_8x16_IDX_TARGET_ID: Int = 1
    const val NIOS_PKT_8x16_IDX_FLAGS: Int = 2
    const val NIOS_PKT_8x16_IDX_RESV1: Int = 3
    const val NIOS_PKT_8x16_IDX_ADDR: Int = 4
    const val NIOS_PKT_8x16_IDX_DATA: Int = 5
    const val NIOS_PKT_8x16_IDX_RESV2: Int = 7

    const val NIOS_PKT_8x16_TARGET_VCTCXO_DAC: Byte = 0
    const val NIOS_PKT_8x16_TARGET_IQ_CORR: Byte = 1
    const val NIOS_PKT_8x16_TARGET_AGC_CORR: Byte = 2
    const val NIOS_PKT_8x16_TARGET_AD56X1_DAC: Byte = 3
    const val NIOS_PKT_8x16_TARGET_INA219: Byte = 4

    const val NIOS_PKT_8x32_MAGIC: Int = 'C'.code
    const val NIOS_PKT_8x32_IDX_MAGIC: Int = 0
    const val NIOS_PKT_8x32_IDX_TARGET_ID: Int = 1
    const val NIOS_PKT_8x32_IDX_FLAGS: Int = 2
    const val NIOS_PKT_8x32_IDX_RESV1: Int = 3
    const val NIOS_PKT_8x32_IDX_ADDR: Int = 4
    const val NIOS_PKT_8x32_IDX_DATA: Int = 5
    const val NIOS_PKT_8x32_IDX_RESV2: Int = 9

    const val NIOS_PKT_8x32_TARGET_VERSION: Byte = 0
    const val NIOS_PKT_8x32_TARGET_CONTROL: Byte = 1
    const val NIOS_PKT_8x32_TARGET_ADF4351: Byte = 2
    const val NIOS_PKT_8x32_TARGET_RFFE_CSR: Byte = 3
    const val NIOS_PKT_8x32_TARGET_ADF400X: Byte = 4
    const val NIOS_PKT_8x32_TARGET_FASTLOCK: Byte = 5

    const val NIOS_PKT_16x64_MAGIC: Int = 'E'.code
    const val NIOS_PKT_16x64_IDX_MAGIC: Int = 0
    const val NIOS_PKT_16x64_IDX_TARGET_ID: Int = 1
    const val NIOS_PKT_16x64_IDX_FLAGS: Int = 2
    const val NIOS_PKT_16x64_IDX_RESV1: Int = 3
    const val NIOS_PKT_16x64_IDX_ADDR: Int = 4
    const val NIOS_PKT_16x64_IDX_DATA: Int = 6
    const val NIOS_PKT_16x64_IDX_RESV2: Int = 14

    const val NIOS_PKT_16x64_TARGET_AD9361: Byte = 0
    const val NIOS_PKT_16x64_TARGET_RFIC: Byte = 1

    const val INA219_REG_CONFIGURATION: Byte = 0
    const val INA219_REG_SHUNT_VOLTAGE: Byte = 1
    const val INA219_REG_BUS_VOLTAGE: Byte = 2
    const val INA219_REG_POWER: Byte = 3
    const val INA219_REG_CURRENT: Byte = 4
    const val INA219_REG_CALIBRATION: Byte = 5

    const val PLL_VCTCXO_FREQUENCY: Long = 38400000
    const val PLL_REFIN_DEFAULT: Long = 10000000
    const val PLL_RESET_FREQUENCY: Long = 70000000

    const val CHANNEL_RX0: Byte = 0
    const val CHANNEL_TX0: Byte = 1
    const val CHANNEL_RX1: Byte = 2
    const val CHANNEL_TX1: Byte = 3
    const val CHANNEL_INVALID: Byte = -1

    const val RFIC_CMD_STATUS: Byte = 0
    const val RFIC_CMD_INIT: Byte = 1
    const val RFIC_CMD_ENABLE: Byte = 2
    const val RFIC_CMD_SAMPLERATE: Byte = 3
    const val RFIC_CMD_FREQUENCY: Byte = 4
    const val RFIC_CMD_BANDWIDTH: Byte = 5
    const val RFIC_CMD_GAINMODE: Byte = 6
    const val RFIC_CMD_GAIN: Byte = 7
    const val RFIC_CMD_RSSI: Byte = 8
    const val RFIC_CMD_FILTER: Byte = 9
    const val RFIC_CMD_TXMUTE: Byte = 10

    const val RFIC_STATE_OFF: Int = 0
    const val RFIC_STATE_ON: Int = 1
    const val RFIC_STATE_STANDBY: Int = 2

    const val RFIC_RXFIR_BYPASS: Int = 0
    const val RFIC_RXFIR_CUSTOM: Int = 1
    const val RFIC_RXFIR_DEC1: Int = 2
    const val RFIC_RXFIR_DEC2: Int = 3
    const val RFIC_RXFIR_DEC4: Int = 4

    const val RFIC_TXFIR_BYPASS: Int = 0
    const val RFIC_TXFIR_CUSTOM: Int = 1
    const val RFIC_TXFIR_INT1: Int = 2
    const val RFIC_TXFIR_INT2: Int = 3
    const val RFIC_TXFIR_INT4: Int = 4

    const val GAIN_DEFAULT: Long = 0
    const val GAIN_MGC: Long = 1
    const val GAIN_FASTATTACK_AGC: Long = 2
    const val GAIN_SLOWATTACK_AGC: Long = 3
    const val GAIN_HYBRID_AGC: Long = 4
}
