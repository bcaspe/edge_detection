package com.sample.edgedetection

import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core

object OpenCvBootstrap {
    private const val TAG = "OpenCvBootstrap"
    @Volatile
    private var loaded = false

    fun ensureLoaded(): Boolean {
        if (loaded) {
            configure()
            return true
        }
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initDebug failed")
            return false
        }
        loaded = true
        configure()
        Log.i(TAG, "OpenCV loaded and configured (single-thread, no SIMD opts)")
        return true
    }

    /** Avoid TBB parallel workers and SIMD paths that can SIGILL on some ARM64 devices. */
    fun configure() {
        try {
            Core.setNumThreads(1)
            Core.setUseOptimized(false)
        } catch (e: Exception) {
            Log.w(TAG, "OpenCV configure failed", e)
        }
    }
}
