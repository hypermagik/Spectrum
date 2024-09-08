package com.hypermagik.spectrum

class PreferencesWrapper {
    class Frequency(private val preferences: Preferences) {
        var value = preferences.sourceSettings.frequency

        fun update(): Boolean {
            val newValue = preferences.sourceSettings.frequency
            if (value != newValue) {
                value = newValue
                return true
            }
            return false
        }
    }

    class SampleRate(private val preferences: Preferences) {
        var value = preferences.sourceSettings.sampleRate

        fun update(): Boolean {
            val newValue = preferences.sourceSettings.sampleRate
            if (value != newValue) {
                value = newValue
                return true
            }
            return false
        }
    }

    class Gain(private val preferences: Preferences) {
        var value = preferences.sourceSettings.gain

        fun update(): Boolean {
            val newValue = preferences.sourceSettings.gain
            if (value != newValue) {
                value = newValue
                return true
            }
            return false
        }
    }

    class AGC(private val preferences: Preferences) {
        var value = preferences.sourceSettings.agc

        fun update(): Boolean {
            val newValue = preferences.sourceSettings.agc
            if (value != newValue) {
                value = newValue
                return true
            }
            return false
        }
    }
}