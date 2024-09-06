package com.hypermagik.spectrum

class PreferencesWrapper {
    class Frequency(private val preferences: Preferences) {
        var value = preferences.frequency

        fun update(): Boolean {
            val newValue = preferences.frequency
            if (value != newValue) {
                value = newValue
                return true
            }
            return false
        }
    }

    class SampleRate(private val preferences: Preferences) {
        var value = preferences.sampleRate

        fun update(): Boolean {
            val newValue = preferences.sampleRate
            if (value != newValue) {
                value = newValue
                return true
            }
            return false
        }
    }

    class Gain(private val preferences: Preferences) {
        var value = preferences.gain

        fun update(): Boolean {
            val newValue = preferences.gain
            if (value != newValue) {
                value = newValue
                return true
            }
            return false
        }
    }
}