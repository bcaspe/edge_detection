package com.sample.edgedetection.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.sample.edgedetection.processor.Corners
import com.sample.edgedetection.processor.TAG
import org.opencv.core.Point
import org.opencv.core.Size
import kotlin.math.abs
import kotlin.math.sqrt

class PaperRectangle(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    companion object {
        private const val DEFAULT_CIRCLE_RADIUS = 20F
        private const val DEFAULT_TOUCH_TARGET_SIZE = 40F
        private const val MIN_SIDE_LENGTH = 50
        private const val STROKE_WIDTH = 4F
    }

    private val rectPaint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = STROKE_WIDTH
        color = Color.argb(128, 173, 216, 230) // Light blue with transparency
        isAntiAlias = true
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(10f)
    }

    private val cornerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        color = Color.argb(255, 173, 216, 230)
        isAntiAlias = true
        isDither = true
    }

    private val path = Path()

    private var ratioX: Double = 1.0
    private var ratioY: Double = 1.0

    private var latestDownX = 0F
    private var latestDownY = 0F

    var tl = Point(100.0, 100.0)
    var tr = Point(500.0, 100.0)
    var br = Point(500.0, 500.0)
    var bl = Point(100.0, 500.0)

    private var activeCorner: Point? = null
    private var activeSide = -1

    var cropMode = false  // Start with crop mode disabled
    
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        // Draw quadrilateral
        path.reset()
        path.moveTo(tl.x.toFloat(), tl.y.toFloat())
        path.lineTo(tr.x.toFloat(), tr.y.toFloat())
        path.lineTo(br.x.toFloat(), br.y.toFloat())
        path.lineTo(bl.x.toFloat(), bl.y.toFloat())
        path.close()
        canvas?.drawPath(path, rectPaint)

        // Only draw corners and touch targets in crop mode
        if (cropMode) {
            // Draw corners
            drawCorner(tl, canvas)
            drawCorner(tr, canvas)
            drawCorner(br, canvas)
            drawCorner(bl, canvas)

            // Draw touch targets for sides
            drawTouchTargets(canvas)
        }
    }
    private fun drawCorner(point: Point, canvas: Canvas?) {
        canvas?.drawCircle(point.x.toFloat(), point.y.toFloat(), DEFAULT_CIRCLE_RADIUS, cornerPaint)
    }

    private fun drawTouchTargets(canvas: Canvas?) {
        val sides = listOf(
            Pair(tl, tr), // Top
            Pair(tr, br), // Right
            Pair(br, bl), // Bottom
            Pair(bl, tl)  // Left
        )
        sides.forEach { side ->
            val midX = (side.first.x + side.second.x) / 2
            val midY = (side.first.y + side.second.y) / 2

            val rect = RectF(
                (midX - DEFAULT_TOUCH_TARGET_SIZE).toFloat(),
                (midY - DEFAULT_TOUCH_TARGET_SIZE).toFloat(),
                (midX + DEFAULT_TOUCH_TARGET_SIZE).toFloat(),
                (midY + DEFAULT_TOUCH_TARGET_SIZE).toFloat()
            )
            canvas?.drawRect(rect, rectPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (!cropMode || event == null) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                latestDownX = event.x
                latestDownY = event.y
                activeCorner = detectTouchedCorner(event.x, event.y)
                if (activeCorner == null) {
                    activeSide = detectTouchedSide(event.x, event.y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCorner != null) {
                    moveCorner(activeCorner!!, event.x - latestDownX, event.y - latestDownY)
                } else if (activeSide != -1) {
                    moveSide(activeSide, event.x - latestDownX, event.y - latestDownY)
                }
                latestDownX = event.x
                latestDownY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                validateQuadrilateral()
                activeCorner = null
                activeSide = -1
            }
        }
        return true
    }

    private fun detectTouchedCorner(x: Float, y: Float): Point? {
        // Log touch coordinates for debugging
        Log.d(TAG, "Touch at x=$x, y=$y")
        
        val touchRadius = DEFAULT_CIRCLE_RADIUS * 3 // Increase touch area
        
        // Check each corner with euclidean distance for more accurate touch detection
        val corners = listOf(
            Pair(tl, "TL"),
            Pair(tr, "TR"),
            Pair(br, "BR"),
            Pair(bl, "BL")
        )
        
        return corners.firstOrNull { (corner, name) ->
            val dx = corner.x - x
            val dy = corner.y - y
            val distance = sqrt(dx * dx + dy * dy)
            
            // Log distance for debugging
            Log.d(TAG, "$name corner distance: $distance")
            
            distance < touchRadius
        }?.first
    }

    private fun detectTouchedSide(x: Float, y: Float): Int {
        val sides = listOf(
            Pair(tl, tr),
            Pair(tr, br),
            Pair(br, bl),
            Pair(bl, tl)
        )
        return sides.indexOfFirst { side ->
            val midX = (side.first.x + side.second.x) / 2
            val midY = (side.first.y + side.second.y) / 2
            abs(midX - x) < DEFAULT_TOUCH_TARGET_SIZE && abs(midY - y) < DEFAULT_TOUCH_TARGET_SIZE
        }
    }

    private fun moveCorner(corner: Point, dx: Float, dy: Float) {
        // Create a new Point instead of modifying the existing one
        when (corner) {
            tl -> tl = Point(tl.x + dx, tl.y + dy)
            tr -> tr = Point(tr.x + dx, tr.y + dy)
            br -> br = Point(br.x + dx, br.y + dy)
            bl -> bl = Point(bl.x + dx, bl.y + dy)
        }
        invalidate()
    }

    private fun moveSide(sideIndex: Int, dx: Float, dy: Float) {
        when (sideIndex) {
            0 -> { // Top side
                tl = Point(tl.x + dx, tl.y + dy)
                tr = Point(tr.x + dx, tr.y + dy)
            }
            1 -> { // Right side
                tr = Point(tr.x + dx, tr.y + dy)
                br = Point(br.x + dx, br.y + dy)
            }
            2 -> { // Bottom side
                br = Point(br.x + dx, br.y + dy)
                bl = Point(bl.x + dx, bl.y + dy)
            }
            3 -> { // Left side
                bl = Point(bl.x + dx, bl.y + dy)
                tl = Point(tl.x + dx, tl.y + dy)
            }
        }
    }

    private fun validateQuadrilateral() {
        val sides = listOf(Pair(tl, tr), Pair(tr, br), Pair(br, bl), Pair(bl, tl))
        
        // Check if any point is outside the view bounds
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val points = listOf(tl, tr, br, bl)
        
        val isOutOfBounds = points.any { point ->
            point.x < -100 || point.x > viewWidth + 100 ||
            point.y < -100 || point.y > viewHeight + 100
        }
        
        // Check if any side is too small (collapsed)
        val isTooSmall = sides.any { (p1, p2) ->
            val sideLength = sqrt((p2.x - p1.x) * (p2.x - p1.x) + 
                                (p2.y - p1.y) * (p2.y - p1.y))
            sideLength < MIN_SIDE_LENGTH
        }
        
        if (isOutOfBounds || isTooSmall) {
            // Reset to a reasonable default size based on view dimensions
            val margin = 50.0
            tl = Point(margin, margin)
            tr = Point(viewWidth - margin, margin)
            br = Point(viewWidth - margin, viewHeight - margin)
            bl = Point(margin, viewHeight - margin)
            invalidate()
        }
    }

    private fun resetQuadrilateral() {
        tl = Point(100.0, 100.0)
        tr = Point(500.0, 100.0)
        br = Point(500.0, 500.0)
        bl = Point(100.0, 500.0)
        invalidate()
    }

    fun getCorners2Crop(): List<Point> {  // Change return type from Array to List
        return listOf(tl, tr, br, bl)
    }

    fun onCorners2Crop(corners: Corners?, size: Size?, width: Int, height: Int) {
        if (corners == null || size == null) {
            resetQuadrilateral()
            return
        }
        
        corners.corners.let { cornerPoints ->
            tl = cornerPoints[0] ?: Point()
            tr = cornerPoints[1] ?: Point()
            br = cornerPoints[2] ?: Point()
            bl = cornerPoints[3] ?: Point()
        }
        
        ratioX = size.width / width.toDouble()
        ratioY = size.height / height.toDouble()
        
        resize()
        cropMode = true
        invalidate()
    }

    fun onCornersNotDetected() {
        resetQuadrilateral()
        invalidate()
    }

    fun onCornersDetected(corners: Corners) {
        ratioX = corners.size.width / measuredWidth
        ratioY = corners.size.height / measuredHeight

        for (i in 0..3) {
            for (j in i + 1..3) {
                if (corners.corners[i]?.equals(corners.corners[j]) == true) {
                    resetQuadrilateral()
                    return
                }
            }
        }

        tl = corners.corners[0] ?: Point()
        tr = corners.corners[1] ?: Point()
        br = corners.corners[2] ?: Point()
        bl = corners.corners[3] ?: Point()

        resize()
        invalidate()
    }

    private fun resize() {
        tl.x /= ratioX; tl.y /= ratioY
        tr.x /= ratioX; tr.y /= ratioY
        br.x /= ratioX; br.y /= ratioY
        bl.x /= ratioX; bl.y /= ratioY
    }

    fun getActualCorners(): List<Point> {
        return listOf(tl, tr, br, bl).map { Point(it.x * ratioX, it.y * ratioY) }
    }
}
