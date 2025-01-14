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
import kotlin.math.pow

private const val TAG = "PaperRectangle"

sealed class Corner {
    object TopLeft : Corner()
    object TopRight : Corner()
    object BottomRight : Corner()
    object BottomLeft : Corner()
}

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
    private var centerMoveMode = false
    

    var tl = Point(100.0, 100.0)
    var tr = Point(500.0, 100.0)
    var br = Point(500.0, 500.0)
    var bl = Point(100.0, 500.0)

    private var activeCorner: Corner? = null
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
            val (start, end) = side

            // Calculate the angle of the side
            val angle = Math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())

            // Calculate the midpoint of the side
            val midX = (start.x + end.x) / 2
            val midY = (start.y + end.y) / 2

            // Calculate the length of the side
            val length = Math.sqrt(((end.x - start.x).toDouble().pow(2) + (end.y - start.y).toDouble().pow(2)))

            // Create a rotated rectangle for the touch target
            val path = Path()
            val halfWidth = DEFAULT_TOUCH_TARGET_SIZE / 2
            val touchTargetLength = DEFAULT_TOUCH_TARGET_SIZE * 1.5f  // Adjust this multiplier as needed
            val halfLength = touchTargetLength / 2
            
            path.moveTo((-halfLength).toFloat(), (-halfWidth).toFloat())
            path.lineTo(halfLength.toFloat(), (-halfWidth).toFloat())
            path.lineTo(halfLength.toFloat(), halfWidth.toFloat())
            path.lineTo((-halfLength).toFloat(), halfWidth.toFloat())
            path.close()

            // Apply rotation and translation
            val matrix = Matrix()
            matrix.postRotate(Math.toDegrees(angle).toFloat())
            matrix.postTranslate(midX.toFloat(), midY.toFloat())
            path.transform(matrix)

            // Draw the rotated touch target
            canvas?.drawPath(path, rectPaint)
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
                    Log.d(TAG, "Corner touched: ${activeCorner!!::class.simpleName}")
                    userIsEditing = true
                    return true
                } 

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
                    userIsEditing = true
                    return true
                } else {
                    Log.d(TAG, "No corner or side touched")
                }

                // Finally check if it's a center touch
                if (detectTouchedCenter(event.x, event.y)) {
                    centerMoveMode = true
                    userIsEditing = true
                    Log.d(TAG, "Center area touched")
                    return true
                }
                
                
                
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCorner != null) {
                    Log.v(TAG, "Moving corner to x: ${event.x}, y: ${event.y}")
                    moveCorner(activeCorner!!, event.x - latestDownX, event.y - latestDownY)
                } else if (activeSide != -1) {
                    Log.v(TAG, "Moving side to x: ${event.x}, y: ${event.y}")
                    moveSide(activeSide, event.x - latestDownX, event.y - latestDownY)
                } else if (centerMoveMode) {
                    Log.v(TAG, "Moving entire shape dx: ${event.x - latestDownX}, dy: ${event.y - latestDownY}")
                    moveEntireShape(event.x - latestDownX, event.y - latestDownY)
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
                centerMoveMode = false
            }
        }
        return true
    }

    private fun detectTouchedCorner(x: Float, y: Float): Corner? {
        val touchRadius = DEFAULT_CIRCLE_RADIUS * 3
        val corners = listOf(
            Triple(tl, "TL", Corner.TopLeft),
            Triple(tr, "TR", Corner.TopRight),
            Triple(br, "BR", Corner.BottomRight),
            Triple(bl, "BL", Corner.BottomLeft)
        )
        
        corners.forEach { (point, name, _) ->
            val dx = point.x - x
            val dy = point.y - y
            val distance = sqrt(dx * dx + dy * dy)
            Log.d(TAG, "Distance to corner $name: $distance (radius: $touchRadius)")
        }
        
        return corners.firstOrNull { (point, _, _) ->
            val dx = point.x - x
            val dy = point.y - y
            val distance = sqrt(dx * dx + dy * dy)
            distance < touchRadius
        }?.third
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

    private fun detectTouchedCenter(x: Float, y: Float): Boolean {
    // Function to check if point is on the left side of a line
    fun isLeftOfLine(lineStart: Point, lineEnd: Point, point: Point): Boolean {
        return ((lineEnd.x - lineStart.x) * (point.y - lineStart.y) - 
                (lineEnd.y - lineStart.y) * (point.x - lineStart.x)) > 0
    }

    // Create test point from touch coordinates
    val testPoint = Point(x.toDouble(), y.toDouble())
    
    // Check if point is inside with margin
    val margin = DEFAULT_TOUCH_TARGET_SIZE
    
    // Shrink the quadrilateral by the margin for the test
    val center = Point(
        (tl.x + tr.x + br.x + bl.x) / 4,
        (tl.y + tr.y + br.y + bl.y) / 4
    )

    // Calculate vectors from center to corners
    val vectors = listOf(tl, tr, br, bl).map { corner ->
        Point(corner.x - center.x, corner.y - center.y)
    }

    // Create shrunk corners by moving towards center by margin
    val shrunkCorners = vectors.map { vec ->
        val length = sqrt(vec.x * vec.x + vec.y * vec.y)
        val scale = (length - margin) / length
        Point(
            center.x + vec.x * scale,
            center.y + vec.y * scale
        )
    }

    // Log the test point and shrunk corners
    Log.d(TAG, "Testing point ($x, $y) against shrunk quadrilateral")
    shrunkCorners.forEachIndexed { index, point ->
        Log.d(TAG, "Shrunk corner $index: (${point.x}, ${point.y})")
    }

    // Check if point is inside the shrunk quadrilateral
    val isInside = shrunkCorners.indices.all { i ->
        val start = shrunkCorners[i]
        val end = shrunkCorners[(i + 1) % 4]
        isLeftOfLine(start, end, testPoint)
    }

    Log.d(TAG, "Point is ${if (isInside) "inside" else "outside"} shrunk quadrilateral")
    return isInside
}

    private fun moveCorner(corner: Corner, dx: Float, dy: Float) {
        val newPoint = when (corner) {
            is Corner.TopLeft -> Point(tl.x + dx, tl.y + dy)
            is Corner.TopRight -> Point(tr.x + dx, tr.y + dy)
            is Corner.BottomRight -> Point(br.x + dx, br.y + dy)
            is Corner.BottomLeft -> Point(bl.x + dx, bl.y + dy)
        }
        
        Log.d(TAG, "Moving ${corner::class.simpleName} to (${newPoint.x}, ${newPoint.y})")
        
        if (isValidQuadrilateral(corner, newPoint)) {
            when (corner) {
                is Corner.TopLeft -> tl = newPoint
                is Corner.TopRight -> tr = newPoint
                is Corner.BottomRight -> br = newPoint
                is Corner.BottomLeft -> bl = newPoint
            }
            movePoints()
        } else {
            Log.d(TAG, "Invalid move - would create invalid quadrilateral")
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

    // Add function to move entire shape
    private fun moveEntireShape(dx: Float, dy: Float) {
        // Move all points by the delta
        tl = Point(tl.x + dx, tl.y + dy)
        tr = Point(tr.x + dx, tr.y + dy)
        br = Point(br.x + dx, br.y + dy)
        bl = Point(bl.x + dx, bl.y + dy)
        
        Log.v(TAG, "Shape moved by dx: $dx, dy: $dy")
        Log.v(TAG, "New positions - TL:(${tl.x}, ${tl.y}), TR:(${tr.x}, ${tr.y}), BR:(${br.x}, ${br.y}), BL:(${bl.x}, ${bl.y})")
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

    private fun isValidQuadrilateral(corner: Corner, newPoint: Point): Boolean {
        val testPoints = when (corner) {
            is Corner.TopLeft -> listOf(newPoint, tr, br, bl)
            is Corner.TopRight -> listOf(tl, newPoint, br, bl)
            is Corner.BottomRight -> listOf(tl, tr, newPoint, bl)
            is Corner.BottomLeft -> listOf(tl, tr, br, newPoint)
        }
        
        return testPoints.zipWithNext().all { (p1, p2) ->
            val dx = p1.x - p2.x
            val dy = p1.y - p2.y
            sqrt(dx * dx + dy * dy) >= MIN_SIDE_LENGTH
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
