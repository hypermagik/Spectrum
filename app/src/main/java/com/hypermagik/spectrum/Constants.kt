package com.hypermagik.spectrum

import com.hypermagik.spectrum.lib.data.converter.IQConverterFactory
import com.hypermagik.spectrum.lib.dsp.FFT
import com.hypermagik.spectrum.source.SourceType

class Constants {
    companion object {
        val sampleRateToMenuItem = mapOf(
            1000000 to R.id.menu_sr_1M,
            2000000 to R.id.menu_sr_2M,
            5000000 to R.id.menu_sr_5M,
            10000000 to R.id.menu_sr_10M,
            20000000 to R.id.menu_sr_20M,
            30000000 to R.id.menu_sr_30M,
            40000000 to R.id.menu_sr_40M,
            61440000 to R.id.menu_sr_61_44M,
        )

        val fpsLimitToMenuItem = mapOf(
            0 to R.id.menu_fps_unlimited,
            10 to R.id.menu_fps_10,
            20 to R.id.menu_fps_20,
            30 to R.id.menu_fps_30,
            60 to R.id.menu_fps_60,
            120 to R.id.menu_fps_120,
        )

        val frequencyStepToMenuItem = mapOf(
            1 to R.id.menu_fstep_1,
            10 to R.id.menu_fstep_10,
            100 to R.id.menu_fstep_100,
            1000 to R.id.menu_fstep_1000,
            10000 to R.id.menu_fstep_10000,
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
            FFT.WindowType.FLAT_TOP to R.id.menu_fft_flat_top,
            FFT.WindowType.BLACKMAN_HARRIS to R.id.menu_fft_blackman_harris,
            FFT.WindowType.HAMMING to R.id.menu_fft_hamming,
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

        val sourceTypeToMenu = mapOf(
            SourceType.ToneGenerator to R.menu.source_tone_generator,
            SourceType.IQFile to R.menu.source_iq_file,
        )

        val sourceTypeToMenuItem = mapOf(
            SourceType.ToneGenerator to R.id.menu_source_tone_generator,
            SourceType.IQFile to R.id.menu_source_iq_file,
        )

        val sampleTypeToMenuItem = mapOf(
            IQConverterFactory.IQConverterType.IQ8Signed to R.id.menu_sample_type_8s,
            IQConverterFactory.IQConverterType.IQ8Unsigned to R.id.menu_sample_type_8u,
            IQConverterFactory.IQConverterType.IQ12SignedPadded to R.id.menu_sample_type_12sp,
            IQConverterFactory.IQConverterType.IQ32Float to R.id.menu_sample_type_32f,
        )
    }
}
