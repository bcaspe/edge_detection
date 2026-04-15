package com.sample.edgedetection.scan

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.exifinterface.media.ExifInterface
import com.sample.edgedetection.ERROR_CODE
import com.sample.edgedetection.EdgeDetectionHandler
import com.sample.edgedetection.R
import com.sample.edgedetection.REQUEST_CODE
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.view.PaperRectangle
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import java.io.*

class ScanActivity : BaseActivity(), IScanView.Proxy {

    private lateinit var mPresenter: ScanPresenter
    private lateinit var initialBundle: Bundle
    private var fromGalleryMode: Boolean = false
    private val capturedPaths = arrayListOf<String>()

    override fun provideContentViewId(): Int = R.layout.activity_scan

    override fun initPresenter() {
        initialBundle = intent.getBundleExtra(EdgeDetectionHandler.INITIAL_BUNDLE) ?: Bundle()
        Log.d("EdgeDetection", "ScanActivity bundle: ${initialBundle.keySet().joinToString()}")
        fromGalleryMode = initialBundle.getBoolean(EdgeDetectionHandler.FROM_GALLERY, false)

        mPresenter = ScanPresenter(this, this, initialBundle) {
            createNextSavePath()
        }
    }

    override fun prepare() {
        if (!OpenCVLoader.initDebug()) {
            Log.i(TAG, "loading opencv error, exit")
            finish()
        }
        else {
            Log.i("OpenCV", "OpenCV loaded Successfully!");
        }

        

        findViewById<View>(R.id.shut).setOnClickListener {
            if (mPresenter.canShut) {
                mPresenter.shut()
            }
        }

        // to hide the flashLight button from  SDK versions which we do not handle the permission for!
        findViewById<View>(R.id.flash).visibility = if
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU && baseContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
            View.VISIBLE else
                View.GONE

        findViewById<View>(R.id.flash).setOnClickListener {
            mPresenter.toggleFlash()
        }

        if(!fromGalleryMode){
            this.title = initialBundle.getString(EdgeDetectionHandler.SCAN_TITLE, "") as String
        }

        findViewById<View>(R.id.gallery).visibility =
                if (initialBundle.getBoolean(EdgeDetectionHandler.CAN_USE_GALLERY, true))
                    View.VISIBLE
                else View.GONE

        findViewById<View>(R.id.gallery).setOnClickListener {
            pickupFromGallery()
        }

        findViewById<Button>(R.id.finish_session).setOnClickListener {
            finishWithCapturedPaths()
        }
        updateCapturedPreviewUi()

        if (fromGalleryMode) {
            pickupFromGallery()
        }
    }

    private fun pickupFromGallery() {
        mPresenter.stop()
        val gallery = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply{type="image/*"}
        ActivityCompat.startActivityForResult(this, gallery, 1, null)
    }

    override fun onStart() {
        super.onStart()
        mPresenter.start()
    }

    override fun onStop() {
        super.onStop()
        mPresenter.stop()
    }

    override fun exit() {
        finish()
    }

    override fun getCurrentDisplay(): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.display
        } else {
            this.windowManager.defaultDisplay
        }
    }

    override fun getSurfaceView() = findViewById<SurfaceView>(R.id.surface)

    override fun getPaperRect() = findViewById<PaperRectangle>(R.id.paper_rect)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val savedPath = data?.getStringExtra(EdgeDetectionHandler.SAVE_TO)
                    ?: initialBundle.getString(EdgeDetectionHandler.SAVE_TO)

                if (!savedPath.isNullOrEmpty()) {
                    capturedPaths.add(savedPath)
                    updateCapturedPreviewUi()
                }

                if (fromGalleryMode) {
                    finishWithCapturedPaths()
                } else {
                    mPresenter.start()
                }
            } else {
                if (fromGalleryMode && capturedPaths.isEmpty())
                    finish()
                else
                    mPresenter.start()
            }
        }

        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                val uri: Uri = data!!.data!!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    onImageSelected(uri)
                }
            }else if(resultCode == Activity.RESULT_CANCELED){
                if (fromGalleryMode && capturedPaths.isEmpty()) {
                    finish()
                } else {
                    mPresenter.start()
                }
            }
            else {
                if (fromGalleryMode && capturedPaths.isEmpty())
                    finish()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun onImageSelected(imageUri: Uri) {
        try {
            val iStream: InputStream = contentResolver.openInputStream(imageUri)!!

            val exif = ExifInterface(iStream)
            var rotation = -1
            val orientation: Int = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotation = Core.ROTATE_90_CLOCKWISE
                ExifInterface.ORIENTATION_ROTATE_180 -> rotation = Core.ROTATE_180
                ExifInterface.ORIENTATION_ROTATE_270 -> rotation = Core.ROTATE_90_COUNTERCLOCKWISE
            }
            val mimeType = contentResolver.getType(imageUri)
            var imageWidth: Double
            var imageHeight: Double

            if (mimeType?.startsWith("image/png") == true) {
                val source = ImageDecoder.createSource(contentResolver, imageUri)
                val drawable = ImageDecoder.decodeDrawable(source)

                imageWidth = drawable.intrinsicWidth.toDouble()
                imageHeight = drawable.intrinsicHeight.toDouble()

                if (rotation == Core.ROTATE_90_CLOCKWISE || rotation == Core.ROTATE_90_COUNTERCLOCKWISE) {
                    imageWidth = drawable.intrinsicHeight.toDouble()
                    imageHeight = drawable.intrinsicWidth.toDouble()
                }
            } else {
                imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).toDouble()
                imageHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).toDouble()
                if (rotation == Core.ROTATE_90_CLOCKWISE || rotation == Core.ROTATE_90_COUNTERCLOCKWISE) {
                    imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).toDouble()
                    imageHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).toDouble()
                }
            }

            val inputData: ByteArray? = getBytes(contentResolver.openInputStream(imageUri)!!)
            if (inputData == null || inputData.isEmpty()) {
                throw Exception("Failed to read image data: inputData is null or empty")
            }
            val mat = MatOfByte(*inputData)
            val pic = Imgcodecs.imdecode(mat, Imgcodecs.IMREAD_UNCHANGED)
            if (pic.empty()) {
                mat.release()
                throw Exception("Failed to decode image: decoded image is empty")
            }
            if (rotation > -1) Core.rotate(pic, pic, rotation)
            mat.release()

            mPresenter.detectEdge(pic)
        } catch (error: Exception) {
            val intent = Intent()
            intent.putExtra("RESULT", error.toString())
            setResult(ERROR_CODE, intent)
            finish()
        }

    }

    @Throws(IOException::class)
    fun getBytes(inputStream: InputStream): ByteArray? {
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }

    private fun createNextSavePath(): String {
        val basePath = initialBundle.getString(EdgeDetectionHandler.SAVE_TO).orEmpty()
        if (basePath.isEmpty()) {
            return basePath
        }

        if (capturedPaths.isEmpty()) {
            return basePath
        }

        val file = File(basePath)
        val parent = file.parent.orEmpty()
        val fileName = file.name
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""
        return File(parent, "${baseName}_${capturedPaths.size + 1}$extension").absolutePath
    }

    private fun updateCapturedPreviewUi() {
        val thumbsContainer = findViewById<LinearLayout>(R.id.thumbnail_container)
        val finishButton = findViewById<Button>(R.id.finish_session)
        val thumbsScroll = findViewById<HorizontalScrollView>(R.id.thumbnail_scroll)

        thumbsContainer.removeAllViews()
        if (capturedPaths.isEmpty()) {
            thumbsScroll.visibility = View.GONE
            finishButton.visibility = View.GONE
            return
        }

        thumbsScroll.visibility = View.VISIBLE
        finishButton.visibility = View.VISIBLE

        capturedPaths.forEach { path ->
            val thumb = ImageView(this)
            val size = resources.displayMetrics.density.times(56).toInt()
            val margins = resources.displayMetrics.density.times(4).toInt()
            val params = LinearLayout.LayoutParams(size, size).apply {
                setMargins(margins, margins, margins, margins)
            }
            thumb.layoutParams = params
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.background = getDrawable(R.drawable.round_button)
            thumb.clipToOutline = true
            thumb.setPadding(2, 2, 2, 2)

            val bitmap = BitmapFactory.decodeFile(path)
            thumb.setImageBitmap(bitmap)
            thumb.setOnClickListener {
                showPreviewDialog(path)
            }
            thumbsContainer.addView(thumb)
        }
    }

    private fun showPreviewDialog(path: String) {
        val preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(BitmapFactory.decodeFile(path))
            setPadding(24, 24, 24, 24)
        }

        AlertDialog.Builder(this)
            .setView(preview)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun finishWithCapturedPaths() {
        val output = Intent().apply {
            putStringArrayListExtra(EdgeDetectionHandler.RESULT_PATHS, capturedPaths)
        }
        setResult(Activity.RESULT_OK, output)
        finish()
    }
}
