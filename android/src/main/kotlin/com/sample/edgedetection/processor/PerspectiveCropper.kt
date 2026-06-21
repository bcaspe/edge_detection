package com.sample.edgedetection.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import org.opencv.core.Point
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

private const val MAX_OUTPUT_DIMENSION = 4096

object PerspectiveCropper {

    fun crop(source: Bitmap, pts: List<Point>): Bitmap {
        require(pts.size == 4) { "Expected 4 corner points, got ${pts.size}" }

        val tl = pts[0]
        val tr = pts[1]
        val br = pts[2]
        val bl = pts[3]

        val widthA = sqrt((br.x - bl.x).pow(2.0) + (br.y - bl.y).pow(2.0))
        val widthB = sqrt((tr.x - tl.x).pow(2.0) + (tr.y - tl.y).pow(2.0))
        val heightA = sqrt((tr.x - br.x).pow(2.0) + (tr.y - br.y).pow(2.0))
        val heightB = sqrt((tl.x - bl.x).pow(2.0) + (tl.y - bl.y).pow(2.0))

        var outWidth = max(widthA, widthB).toInt().coerceAtLeast(1)
        var outHeight = max(heightA, heightB).toInt().coerceAtLeast(1)

        val longest = max(outWidth, outHeight)
        if (longest > MAX_OUTPUT_DIMENSION) {
            val scale = MAX_OUTPUT_DIMENSION.toDouble() / longest
            outWidth = (outWidth * scale).toInt().coerceAtLeast(1)
            outHeight = (outHeight * scale).toInt().coerceAtLeast(1)
            Log.i(TAG, "Downscaled crop output to ${outWidth}x$outHeight")
        }

        val src = floatArrayOf(
            tl.x.toFloat(), tl.y.toFloat(),
            tr.x.toFloat(), tr.y.toFloat(),
            br.x.toFloat(), br.y.toFloat(),
            bl.x.toFloat(), bl.y.toFloat(),
        )
        val dst = floatArrayOf(
            0f, 0f,
            outWidth.toFloat(), 0f,
            outWidth.toFloat(), outHeight.toFloat(),
            0f, outHeight.toFloat(),
        )

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
            throw IllegalStateException("setPolyToPoly failed for perspective crop")
        }

        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }
}
