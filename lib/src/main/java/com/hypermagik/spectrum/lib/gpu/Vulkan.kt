package com.hypermagik.spectrum.lib.gpu

import android.content.Context
import android.content.res.AssetManager

class Vulkan {
    companion object {
        private var hasContext = false

        fun init(context: Context) {
            hasContext = createContext(VK_DEBUG, context.assets)
        }

        fun isAvailable(): Boolean {
            return hasContext
        }

        private external fun createContext(debug: Boolean, assetManager: AssetManager): Boolean
    }
}