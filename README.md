# Spectrum

An Android-based application designed to measure and analyze RF signals in real-time.
It displays the RF spectrum, allowing you to visualize the signal strength.

## Features

- RTL-SDR and bladeRF 2.0 live input.
- Raw IQ file playback and recording.
- GPU accelerated UI with FFT and waterfall views capable of rendering at 120 FPS.
- Adjustable FFT size and window function, peak detection and hold, waterfall speed and color, etc.
- AM, FM and wideband FM demodulators with audio output and RDS.

## Screenshots

<img src="https://github.com/hypermagik/Spectrum/blob/master/doc/screenshots/gsm.png?raw=true" width="32%"/> <img src="https://github.com/hypermagik/Spectrum/blob/master/doc/screenshots/fm.png?raw=true" width="32%"/> <img src="https://github.com/hypermagik/Spectrum/blob/master/doc/screenshots/demod.png?raw=true" width="32%"/>

## Requirements

Android 9 (API 28) or higher is required.

## Installation

Clone this repository, open the project in Android Studio then build and run the app on your Android device. Make sure to build and install the release variant, otherwise your battery will suffer.

## License
This project is licensed under the GNU GPLv3 License - see the LICENSE file for details.
