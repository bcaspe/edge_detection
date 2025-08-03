package com.sample.edgedetection.scan

import org.opencv.core.Size

object ScanConstants {
    // Keep these as fallback values for very large images to prevent memory issues
    private const val ABSOLUTE_MAX_HEIGHT = 8192.0 // Absolute maximum to prevent memory issues
    private const val ABSOLUTE_MAX_WIDTH = 4096.0  // Absolute maximum to prevent memory issues
    
    // Function to get dynamic max size based on image dimensions
    fun getMaxSizeForImage(imageSize: Size): Size {
        // Use the image's own dimensions as the maximum, but cap at absolute maximums
        val maxWidth = minOf(imageSize.width, ABSOLUTE_MAX_WIDTH)
        val maxHeight = minOf(imageSize.height, ABSOLUTE_MAX_HEIGHT)
        return Size(maxWidth, maxHeight)
    }
    
    // Legacy method for backward compatibility (if needed)
    val MAX_SIZE: Size = Size(ABSOLUTE_MAX_WIDTH, ABSOLUTE_MAX_HEIGHT)
}