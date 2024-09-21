package com.hypermagik.spectrum

import com.hypermagik.spectrum.demodulator.DemodulatorType
import com.hypermagik.spectrum.lib.data.SampleType
import com.hypermagik.spectrum.lib.dsp.Window
import com.hypermagik.spectrum.source.SourceType

class Constants {
    companion object {
        val sourceTypeToMenu = mapOf(
            SourceType.ToneGenerator to R.menu.source_tone_generator,
            SourceType.IQFile to R.menu.source_iq_file,
            SourceType.BladeRF to R.menu.source_bladerf,
            SourceType.RTLSDR to R.menu.source_rtlsdr,
        )

        val sourceTypeToMenuItem = mapOf(
            SourceType.ToneGenerator to R.id.menu_source_tone_generator,
            SourceType.IQFile to R.id.menu_source_iq_file,
            SourceType.BladeRF to R.id.menu_source_bladerf,
            SourceType.RTLSDR to R.id.menu_source_rtlsdr,
        )

        val sampleTypeToMenuItem = mapOf(
            SampleType.NONE to R.id.menu_sample_type_none,
            SampleType.S8 to R.id.menu_sample_type_s8,
            SampleType.U8 to R.id.menu_sample_type_u8,
            SampleType.S12P to R.id.menu_sample_type_s12p,
            SampleType.F32 to R.id.menu_sample_type_f32,
        )

        val sampleRateToMenuItem = mapOf(
            1000000 to R.id.menu_sr_1M,
            1024000 to R.id.menu_sr_1_024M,
            1536000 to R.id.menu_sr_1_536M,
            2000000 to R.id.menu_sr_2M,
            2048000 to R.id.menu_sr_2_048M,
            2400000 to R.id.menu_sr_2_40M,
            2880000 to R.id.menu_sr_2_88M,
            3200000 to R.id.menu_sr_3_20M,
            5000000 to R.id.menu_sr_5M,
            10000000 to R.id.menu_sr_10M,
            10240000 to R.id.menu_sr_10_24M,
            20000000 to R.id.menu_sr_20M,
            20480000 to R.id.menu_sr_20_48M,
            30000000 to R.id.menu_sr_30M,
            30720000 to R.id.menu_sr_30_72M,
            40000000 to R.id.menu_sr_40M,
            40960000 to R.id.menu_sr_40_96M,
            61440000 to R.id.menu_sr_61_44M,
        )

        val demodulatorTypeToMenuItem = mapOf(
            DemodulatorType.None to R.id.menu_demodulator_none,
            DemodulatorType.WFM to R.id.menu_demodulator_wfm,
        )

        val demodulatorTypeToMenu = mapOf(
            DemodulatorType.None to R.menu.demodulator_none,
            DemodulatorType.WFM to R.menu.demodulator_wfm,
        )

        val fftSizeToMenuItem = mapOf(
            256 to R.id.menu_fft_256,
            512 to R.id.menu_fft_512,
            1024 to R.id.menu_fft_1024,
            2048 to R.id.menu_fft_2048,
            4096 to R.id.menu_fft_4096,
            8192 to R.id.menu_fft_8192,
            16384 to R.id.menu_fft_16384,
        )

        val fftWindowToMenuItem = mapOf(
            Window.Type.FLAT_TOP to R.id.menu_fft_flat_top,
            Window.Type.BLACKMAN_HARRIS to R.id.menu_fft_blackman_harris,
            Window.Type.HAMMING to R.id.menu_fft_hamming,
        )

        val peakHoldDecayToMenuItem = mapOf(
            10 to R.id.menu_peak_hold_decay_10,
            20 to R.id.menu_peak_hold_decay_20,
            30 to R.id.menu_peak_hold_decay_30,
            40 to R.id.menu_peak_hold_decay_40,
            50 to R.id.menu_peak_hold_decay_50,
            60 to R.id.menu_peak_hold_decay_60,
            70 to R.id.menu_peak_hold_decay_70,
            80 to R.id.menu_peak_hold_decay_80,
            90 to R.id.menu_peak_hold_decay_90,
            100 to R.id.menu_peak_hold_decay_100,
        )

        val wfSpedToMenuItem = mapOf(
            1 to R.id.menu_wf_speed_1x,
            2 to R.id.menu_wf_speed_2x,
            4 to R.id.menu_wf_speed_4x,
        )

        val wfColormapToMenuItem = mapOf(
            0 to R.id.menu_wf_colormap_classic,
            1 to R.id.menu_wf_colormap_classic_green,
            2 to R.id.menu_wf_colormap_gqrx,
            3 to R.id.menu_wf_colormap_vivid,
        )

        val wfColormapToResource = mapOf(
            0 to R.raw.colormap_classic,
            1 to R.raw.colormap_classic_green,
            2 to R.raw.colormap_gqrx,
            3 to R.raw.colormap_vivid,
        )

        val frequencyStepToMenuItem = mapOf(
            1 to R.id.menu_fstep_1,
            10 to R.id.menu_fstep_10,
            100 to R.id.menu_fstep_100,
            1000 to R.id.menu_fstep_1000,
            10000 to R.id.menu_fstep_10000,
        )

        val fpsLimitToMenuItem = mapOf(
            0 to R.id.menu_fps_unlimited,
            10 to R.id.menu_fps_10,
            20 to R.id.menu_fps_20,
            30 to R.id.menu_fps_30,
            60 to R.id.menu_fps_60,
            120 to R.id.menu_fps_120,
        )
    }
}
