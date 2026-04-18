package com.sample.edgedetection.scan

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.ClipData
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.FrameLayout
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
    private lateinit var baseSavePath: String
    private var fromGalleryMode: Boolean = false
    private var canUseFlash: Boolean = false
    private val capturedPaths = arrayListOf<String>()
    private val pendingGalleryUris = ArrayDeque<Uri>()

    override fun provideContentViewId(): Int = R.layout.activity_scan

    override fun initPresenter() {
        initialBundle = intent.getBundleExtra(EdgeDetectionHandler.INITIAL_BUNDLE) ?: Bundle()
        baseSavePath = initialBundle.getString(EdgeDetectionHandler.SAVE_TO).orEmpty()
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

        canUseFlash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU &&
            baseContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        invalidateOptionsMenu()

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
        findViewById<View>(R.id.finish_session).setOnClickListener {
            finishWithCapturedPaths()
        }

        updateCapturedPreviewUi()

        if (fromGalleryMode) {
            pickupFromGallery()
        }
    }

    private fun pickupFromGallery() {
        mPresenter.stop()
        val gallery = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
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

                if (pendingGalleryUris.isNotEmpty()) {
                    processNextPendingGalleryUri()
                } else if (fromGalleryMode) {
                    pickupFromGallery()
                } else {
                    mPresenter.start()
                }
            } else {
                if (pendingGalleryUris.isNotEmpty()) {
                    processNextPendingGalleryUri()
                } else if (fromGalleryMode && capturedPaths.isEmpty())
                    finish()
                else if (fromGalleryMode)
                    pickupFromGallery()
                else
                    mPresenter.start()
            }
        }

        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                enqueueSelectedUris(data)
                processNextPendingGalleryUri()
            }else if(resultCode == Activity.RESULT_CANCELED){
                if (fromGalleryMode && capturedPaths.isEmpty()) {
                    finish()
                } else if (fromGalleryMode) {
                    // In gallery session mode, cancellation means user is done selecting.
                    finishWithCapturedPaths()
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
        R.id.action_toggle_flash -> {
            mPresenter.toggleFlash()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.scan_activity_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val flashItem = menu.findItem(R.id.action_toggle_flash)
        flashItem?.isVisible = canUseFlash
        flashItem?.isEnabled = canUseFlash
        return super.onPrepareOptionsMenu(menu)
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

    private fun enqueueSelectedUris(data: Intent?) {
        if (data == null) return
        val clipData: ClipData? = data.clipData
        if (clipData != null && clipData.itemCount > 0) {
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index)?.uri?.let { pendingGalleryUris.add(it) }
            }
            return
        }
        data.data?.let { pendingGalleryUris.add(it) }
    }

    private fun processNextPendingGalleryUri() {
        if (pendingGalleryUris.isEmpty()) {
            return
        }
        val nextUri = pendingGalleryUris.removeFirst()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            onImageSelected(nextUri)
        } else {
            // Existing gallery flow already only supports API 28+ decode logic.
            pendingGalleryUris.clear()
            if (fromGalleryMode && capturedPaths.isEmpty()) {
                finish()
            }
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
        val basePath = baseSavePath
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
        val thumbsScroll = findViewById<HorizontalScrollView>(R.id.thumbnail_scroll)
        val finishButton = findViewById<ImageView>(R.id.finish_session)

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

        val container = FrameLayout(this).apply {
            addView(
                preview,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val deleteButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundResource(R.drawable.round_button)
            setPadding(20, 20, 20, 20)
        }
        val deleteParams = FrameLayout.LayoutParams(
            resources.displayMetrics.density.times(52).toInt(),
            resources.displayMetrics.density.times(52).toInt()
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            val margin = resources.displayMetrics.density.times(12).toInt()
            setMargins(margin, margin, margin, margin)
        }
        container.addView(deleteButton, deleteParams)

        val previewDialog = AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_photo_title)
                .setMessage(R.string.delete_photo_message)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    deleteCapturedPhoto(path)
                    previewDialog.dismiss()
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }

        previewDialog.show()
    }

    private fun deleteCapturedPhoto(path: String) {
        capturedPaths.remove(path)
        runCatching {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        updateCapturedPreviewUi()
    }

    private fun finishWithCapturedPaths() {
        val output = Intent().apply {
            putStringArrayListExtra(EdgeDetectionHandler.RESULT_PATHS, capturedPaths)
        }
        setResult(Activity.RESULT_OK, output)
        finish()
    }
}
