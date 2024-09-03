package com.hypermagik.spectrum

import com.hypermagik.spectrum.lib.dsp.FFT

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
    }
}