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

private const val TAG = "PaperRectangle"

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
    private var point2Move = Point()
    private var userIsEditing = false
    

    var tl = Point(100.0, 100.0)
    var tr = Point(500.0, 100.0)
    var br = Point(500.0, 500.0)
    var bl = Point(100.0, 500.0)

    private var activeCorner: Point? = null
    private var activeSide = -1
    var cropMode = false

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
        Log.d(TAG, "Touch DOWN at x: ${event.x}, y: ${event.y}")
            
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                latestDownX = event.x
                latestDownY = event.y
                activeCorner = detectTouchedCorner(event.x, event.y)
                if (activeCorner != null) {
                // Log which corner was touched
                val cornerName = when(activeCorner) {
                    tl -> "TOP_LEFT"
                    tr -> "TOP_RIGHT"
                    br -> "BOTTOM_RIGHT"
                    bl -> "BOTTOM_LEFT"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Corner touched: $cornerName")
            } else {
                activeSide = detectTouchedSide(event.x, event.y)
                if (activeSide != -1) {
                    // Log which side was touched
                    val sideName = when(activeSide) {
                        0 -> "TOP"
                        1 -> "RIGHT"
                        2 -> "BOTTOM"
                        3 -> "LEFT"
                        else -> "UNKNOWN"
                    }
                    Log.d(TAG, "Side touched: $sideName")
                } else {
                    Log.d(TAG, "No corner or side touched")
                }
            }
            
            if (activeCorner != null || activeSide != -1) {
                userIsEditing = true
                Log.d(TAG, "User started editing")
            }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCorner != null) {
                    Log.v(TAG, "Moving corner to x: ${event.x}, y: ${event.y}")
                    moveCorner(activeCorner!!, event.x - latestDownX, event.y - latestDownY)
                } else if (activeSide != -1) {
                    Log.v(TAG, "Moving side to x: ${event.x}, y: ${event.y}")
                    moveSide(activeSide, event.x - latestDownX, event.y - latestDownY)
                }
                latestDownX = event.x
                latestDownY = event.y
                movePoints()
            }
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "Touch UP at x: ${event.x}, y: ${event.y}")
                if (activeCorner != null) {
                    Log.d(TAG, "Released corner")
                } else if (activeSide != -1) {
                    Log.d(TAG, "Released side")
                }
                activeCorner = null
                activeSide = -1
            }
        }
        return true
    }

    private fun detectTouchedCorner(x: Float, y: Float): Point? {
        val touchRadius = DEFAULT_CIRCLE_RADIUS * 3
        val corners = listOf(
            Pair(tl, "TL"),
            Pair(tr, "TR"),
            Pair(br, "BR"),
            Pair(bl, "BL")
        )
        corners.forEach { (corner, name) ->
            val dx = corner.x - x
            val dy = corner.y - y
            val distance = sqrt(dx * dx + dy * dy)
            Log.v(TAG, "Distance to corner $name: $distance (radius: $touchRadius)")
        }
        
        return corners.firstOrNull { (corner, name) ->
            val dx = corner.x - x
            val dy = corner.y - y
            val distance = sqrt(dx * dx + dy * dy)
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
        val newPoint = when (corner) {
            tl -> Point(tl.x + dx, tl.y + dy)
            tr -> Point(tr.x + dx, tr.y + dy)
            br -> Point(br.x + dx, br.y + dy)
            bl -> Point(bl.x + dx, bl.y + dy)
            else -> {
                Log.d(TAG, "Invalid move 1")
                return
            }
        }
        
        // Only update if the new position maintains minimum side lengths
        if (isValidQuadrilateral(corner, newPoint)) {
            when (corner) {
                tl -> tl = newPoint
                tr -> tr = newPoint
                br -> br = newPoint
                bl -> bl = newPoint
            
            }   
        } else {
            Log.d(TAG, "Invalid move 2")
        }
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

    private fun movePoints() {
        path.reset()
        path.moveTo(tl.x.toFloat(), tl.y.toFloat())
        path.lineTo(tr.x.toFloat(), tr.y.toFloat())
        path.lineTo(br.x.toFloat(), br.y.toFloat())
        path.lineTo(bl.x.toFloat(), bl.y.toFloat())
        path.close()
        invalidate()
    }

    private fun isValidQuadrilateral(corner: Point, newPoint: Point): Boolean {
        // Check minimum side lengths
        val minLength = MIN_SIDE_LENGTH
        
        // Create temporary points for validation
        val testPoints = when(corner) {
            tl -> listOf(newPoint, tr, br, bl)
            tr -> listOf(tl, newPoint, br, bl) 
            br -> listOf(tl, tr, newPoint, bl)
            bl -> listOf(tl, tr, br, newPoint)
            else -> return false
        }
    
    // Check all sides maintain minimum length
    return testPoints.zipWithNext().all { (p1, p2) ->
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
            sqrt(dx * dx + dy * dy) >= minLength
        }
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
        movePoints()
    }

    fun onCornersNotDetected() {
        if (!userIsEditing) {
            resetQuadrilateral()
            invalidate()
        }
    }

    fun onCorners2Crop(corners: Corners?, size: Size?, paperWidth: Int, paperHeight: Int) {
        if (size == null) {
            return
        }

        cropMode = true
        tl = corners?.corners?.get(0) ?: Point(size.width * 0.1, size.height * 0.1)
        tr = corners?.corners?.get(1) ?: Point(size.width * 0.9, size.height * 0.1)
        br = corners?.corners?.get(2) ?: Point(size.width * 0.9, size.height * 0.9)
        bl = corners?.corners?.get(3) ?: Point(size.width * 0.1, size.height * 0.9)
        ratioX = size.width / paperWidth
        ratioY = size.height / paperHeight
        resize()
        movePoints()
    }

    fun getCorners2Crop(): List<Point> {
        reverseSize()
        return listOf(tl, tr, br, bl)
    }

    private fun resetQuadrilateral() {
        tl = Point(100.0, 100.0)
        tr = Point(500.0, 100.0)
        br = Point(500.0, 500.0)
        bl = Point(100.0, 500.0)
        movePoints()
    }

    private fun resize() {
        tl.x = tl.x / ratioX
        tl.y = tl.y / ratioY
        tr.x = tr.x / ratioX
        tr.y = tr.y / ratioY
        br.x = br.x / ratioX
        br.y = br.y / ratioY
        bl.x = bl.x / ratioX
        bl.y = bl.y / ratioY
    }

    private fun reverseSize() {
        tl.x = tl.x * ratioX
        tl.y = tl.y * ratioY
        tr.x = tr.x * ratioX
        tr.y = tr.y * ratioY
        br.x = br.x * ratioX
        br.y = br.y * ratioY
        bl.x = bl.x * ratioX
        bl.y = bl.y * ratioY
    }
}
