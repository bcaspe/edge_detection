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

    var cropMode = true // Enables or disables crop editing

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

        // Draw corners
        drawCorner(tl, canvas)
        drawCorner(tr, canvas)
        drawCorner(br, canvas)
        drawCorner(bl, canvas)

        // Draw touch targets for sides
        if (cropMode) drawTouchTargets(canvas)
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
        val corners = listOf(tl, tr, br, bl)
        return corners.firstOrNull {
            abs(it.x - x) < DEFAULT_CIRCLE_RADIUS * 2 && abs(it.y - y) < DEFAULT_CIRCLE_RADIUS * 2
        }
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
        corner.x += dx
        corner.y += dy
    }

    private fun moveSide(sideIndex: Int, dx: Float, dy: Float) {
        when (sideIndex) {
            0 -> { tl.y += dy; tr.y += dy }
            1 -> { tr.x += dx; br.x += dx }
            2 -> { br.y += dy; bl.y += dy }
            3 -> { bl.x += dx; tl.x += dx }
        }
    }

    private fun validateQuadrilateral() {
        val sides = listOf(Pair(tl, tr), Pair(tr, br), Pair(br, bl), Pair(bl, tl))
        val isValid = sides.all { (p1, p2) ->
            // Replace .pow(2) with * for squaring
            sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)) > MIN_SIDE_LENGTH
        }
        if (!isValid) resetQuadrilateral()
    }

    private fun resetQuadrilateral() {
        tl = Point(100.0, 100.0)
        tr = Point(500.0, 100.0)
        br = Point(500.0, 500.0)
        bl = Point(100.0, 500.0)
        invalidate()
    }

    fun onCorners2Crop() {
        cropMode = true
        invalidate()
    }

    fun onCorners2Crop(topLeft: Point, topRight: Point, bottomRight: Point, bottomLeft: Point) {
        tl = topLeft
        tr = topRight
        br = bottomRight
        bl = bottomLeft
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
