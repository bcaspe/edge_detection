package com.sample.edgedetection.crop

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.sample.edgedetection.SourceManager
import com.sample.edgedetection.EdgeDetectionHandler
import com.sample.edgedetection.processor.Corners
import com.sample.edgedetection.processor.TAG
import com.sample.edgedetection.processor.cropPicture
import com.sample.edgedetection.processor.enhancePicture
import com.sample.edgedetection.processor.processPicture
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream

class CropPresenter(
    private val iCropView: ICropView.Proxy,
    private val initialBundle: Bundle
) {
    private var picture: Mat? = SourceManager.pic
    private var isCropped = false
    private var corners: Corners? = SourceManager.corners
    private var croppedPicture: Mat? = null
    private var enhancedPicture: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var rotateBitmap: Bitmap? = null
    private var rotateBitmapDegree: Int = -90
    private var currentThreshold = 15

    fun onViewsReady() {
        val pic = picture ?: return
        val bitmap = Bitmap.createBitmap(pic.width(), pic.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(pic, bitmap, true)
        val paper = iCropView.getPaper()
        paper.setImageBitmap(bitmap)
        // ImageView's fitCenter matrix is ready after the next layout pass
        paper.post { syncCropOverlay() }
    }

    /** Align PaperRectangle to the bitmap's displayed (letterboxed) rect inside the ImageView. */
    private fun syncCropOverlay() {
        val pic = picture ?: return
        val paper = iCropView.getPaper()
        val displayRect = paper.bitmapDisplayRect()
        if (displayRect.width() <= 0f || displayRect.height() <= 0f) {
            Log.w(TAG, "bitmapDisplayRect empty; skipping overlay sync")
            return
        }
        Log.i(
            TAG,
            "Crop overlay displayRect=$displayRect image=${pic.width()}x${pic.height()} view=${paper.width}x${paper.height}"
        )
        iCropView.getPaperRect().onCorners2Crop(corners, pic.size(), displayRect)
    }

    fun crop(onComplete: (() -> Unit)? = null, onError: (() -> Unit)? = null) {
        val sourcePicture: Mat = picture ?: run {
            Log.i(TAG, "picture null?")
            onError?.invoke()
            return
        }

        if (croppedBitmap != null) {
            Log.i(TAG, "already cropped")
            onComplete?.invoke()
            return
        }

        Observable.create<Mat> { emitter ->
            try {
                emitter.onNext(
                    cropPicture(sourcePicture, iCropView.getPaperRect().getCorners2Crop())
                )
                emitter.onComplete()
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { pc ->
                    Log.i(TAG, "cropped picture: ${pc.width()}x${pc.height()}")
                    croppedPicture?.release()
                    croppedPicture = pc
                    croppedBitmap = Bitmap.createBitmap(
                        pc.width(),
                        pc.height(),
                        Bitmap.Config.ARGB_8888
                    )
                    Utils.matToBitmap(pc, croppedBitmap)
                    iCropView.getCroppedPaper().setImageBitmap(croppedBitmap)
                    iCropView.getPaper().visibility = View.GONE
                    iCropView.getPaperRect().visibility = View.GONE
                    isCropped = true
                    onComplete?.invoke()
                },
                { error ->
                    Log.e(TAG, "crop failed", error)
                    onError?.invoke()
                }
            )
    }

    fun handleBackButton(): Boolean {
        if (isCropped) {
            isCropped = false
            croppedBitmap = null
            croppedPicture?.release()
            croppedPicture = null
            enhancedPicture = null
            rotateBitmap = null
            
            iCropView.getPaper().visibility = View.VISIBLE
            iCropView.getPaperRect().visibility = View.VISIBLE
            iCropView.getCroppedPaper().setImageBitmap(null)
            
            // Reset the paper rectangle to original corners
            syncCropOverlay()
        
            return true
        }
        return false
    }

    fun enhance(threshold: Int = currentThreshold) {
        if (croppedBitmap == null) {
            Log.i(TAG, "picture null?")
            return
        }

        Observable.create<Bitmap> { emitter ->
            try {
                val enhanced = enhancePicture(croppedBitmap, threshold * 2 + 1, threshold.toDouble())
                emitter.onNext(enhanced)
                emitter.onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Error enhancing image", e)
                emitter.onError(e)
            }
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            { enhancedBitmap ->
                currentThreshold = threshold
                enhancedPicture = enhancedBitmap
                rotateBitmap = enhancedPicture
                iCropView.getCroppedPaper().setImageBitmap(enhancedBitmap)
            },
            { error ->
                Log.e(TAG, "Error in enhance subscription", error)
            }
        )
    }

    fun reset() {
        if (croppedBitmap == null) {
            Log.i(TAG, "picture null?")
            return
        }
        rotateBitmap = croppedBitmap
        enhancedPicture = croppedBitmap

        iCropView.getCroppedPaper().setImageBitmap(croppedBitmap)
    }

    fun rotate() {
        if (!isCropped) {
            rotatePreCrop()
            return
        }

        if (enhancedPicture != null && rotateBitmap == null) {
            Log.i(TAG, "enhancedPicture ***** TRUE")
            rotateBitmap = enhancedPicture
        }

        if (rotateBitmap == null) {
            Log.i(TAG, "rotateBitmap ***** TRUE")
            rotateBitmap = croppedBitmap
        }

        Log.i(TAG, "ROTATE BITMAP DEGREE --> $rotateBitmapDegree")

        rotateBitmap = rotateBitmap?.rotateInt(rotateBitmapDegree)

        iCropView.getCroppedPaper().setImageBitmap(rotateBitmap)

        enhancedPicture = rotateBitmap
        croppedBitmap = croppedBitmap?.rotateInt(rotateBitmapDegree)
    }

    private fun rotatePreCrop() {
        val originalPicture = picture ?: run {
            Log.i(TAG, "picture null?")
            return
        }
        com.sample.edgedetection.OpenCvBootstrap.configure()
        val rotatedPicture = Mat()
        Core.rotate(originalPicture, rotatedPicture, Core.ROTATE_90_CLOCKWISE)
        picture = rotatedPicture
        SourceManager.pic = rotatedPicture
        corners = processPicture(rotatedPicture)
        SourceManager.corners = corners

        val bitmap = Bitmap.createBitmap(rotatedPicture.width(), rotatedPicture.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rotatedPicture, bitmap, true)
        val paper = iCropView.getPaper()
        paper.setImageBitmap(bitmap)
        paper.post { syncCropOverlay() }
    }

    fun save() {
        val file = File(initialBundle.getString(EdgeDetectionHandler.SAVE_TO) as String)

        val rotatePic = rotateBitmap
        if (null != rotatePic) {
            val outStream = FileOutputStream(file)
            rotatePic.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
            outStream.flush()
            outStream.close()
            Log.i(TAG, "RotateBitmap Saved")
        } else {
            val pic = enhancedPicture
            if (null != pic) {
                val outStream = FileOutputStream(file)
                pic.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                outStream.flush()
                outStream.close()
                Log.i(TAG, "EnhancedPicture Saved")
            } else {
                val cropPic = croppedBitmap
                if (null != cropPic) {
                    val outStream = FileOutputStream(file)
                    cropPic.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                    outStream.flush()
                    outStream.close()
                    Log.i(TAG, "CroppedBitmap Saved")
                }
            }
        }
    }

    private fun Bitmap.rotateInt(degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val scaledBitmap = Bitmap.createScaledBitmap(
            this,
            width,
            height,
            true
        )
        return Bitmap.createBitmap(
            scaledBitmap,
            0,
            0,
            scaledBitmap.width,
            scaledBitmap.height,
            matrix,
            true
        )
    }
}

/** Rect of the drawable as drawn by fitCenter (includes padding), in ImageView coordinates. */
private fun ImageView.bitmapDisplayRect(): RectF {
    val d = drawable ?: return RectF()
    val dwidth = d.intrinsicWidth.toFloat().coerceAtLeast(1f)
    val dheight = d.intrinsicHeight.toFloat().coerceAtLeast(1f)

    val mapped = RectF(0f, 0f, dwidth, dheight)
    val matrix = Matrix(imageMatrix)
    matrix.postTranslate(paddingLeft.toFloat(), paddingTop.toFloat())
    matrix.mapRect(mapped)
    if (mapped.width() > 1f && mapped.height() > 1f) {
        return mapped
    }

    // Matrix not configured yet — compute fitCenter manually from view size
    val vwidth = (width - paddingLeft - paddingRight).toFloat()
    val vheight = (height - paddingTop - paddingBottom).toFloat()
    if (vwidth <= 0f || vheight <= 0f) return RectF()
    val scale = minOf(vwidth / dwidth, vheight / dheight)
    val dx = paddingLeft + (vwidth - dwidth * scale) * 0.5f
    val dy = paddingTop + (vheight - dheight * scale) * 0.5f
    return RectF(dx, dy, dx + dwidth * scale, dy + dheight * scale)
}
