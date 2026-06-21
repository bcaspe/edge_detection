package com.sample.edgedetection.crop

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import com.sample.edgedetection.EdgeDetectionHandler
import com.sample.edgedetection.OpenCvBootstrap
import com.sample.edgedetection.R
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.view.PaperRectangle

class CropActivity : BaseActivity(), ICropView.Proxy {

    private lateinit var mPresenter: CropPresenter

    private lateinit var initialBundle: Bundle

    override fun prepare() {
        initialBundle = intent.getBundleExtra(EdgeDetectionHandler.INITIAL_BUNDLE) ?: Bundle()
        this.title = initialBundle.getString(EdgeDetectionHandler.CROP_TITLE) ?: "Crop script"
        val total = initialBundle.getInt(EdgeDetectionHandler.GALLERY_CROP_TOTAL, 0)
        val index = initialBundle.getInt(EdgeDetectionHandler.GALLERY_CROP_INDEX, 0)
        if (total >= 1 && index > 0 && index <= total) {
            supportActionBar?.subtitle = getString(R.string.crop_photo_progress, index, total)
        } else {
            supportActionBar?.subtitle = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!OpenCvBootstrap.ensureLoaded()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.paper).post {
            // we have to initialize everything in post when the view has been drawn and we have the actual height and width of the whole view
            mPresenter.onViewsReady(findViewById<View>(R.id.paper).width, findViewById<View>(R.id.paper).height)
        }
    }

    override fun provideContentViewId(): Int = R.layout.activity_crop

    override fun initPresenter() {
        initialBundle = intent.getBundleExtra(EdgeDetectionHandler.INITIAL_BUNDLE) ?: Bundle()
        mPresenter = CropPresenter(this, initialBundle)

        findViewById<ImageView>(R.id.crop).setOnClickListener { cropButton ->
            Log.e(TAG, "Crop touched!")
            cropButton.isEnabled = false
            mPresenter.crop(
                onComplete = {
                    mPresenter.save()
                    val output = Intent().apply {
                        putExtra(
                            EdgeDetectionHandler.SAVE_TO,
                            initialBundle.getString(EdgeDetectionHandler.SAVE_TO)
                        )
                    }
                    setResult(Activity.RESULT_OK, output)
                    System.gc()
                    finish()
                },
                onError = {
                    cropButton.isEnabled = true
                }
            )
        }
        findViewById<ImageView>(R.id.rotate_pre).setOnClickListener {
            Log.e(TAG, "Rotate (pre-crop) button clicked!")
            mPresenter.rotate()
        }
    }

    override fun getPaper(): ImageView = findViewById(R.id.paper)

    override fun getPaperRect() = findViewById<PaperRectangle>(R.id.paper_rect)

    override fun getCroppedPaper() = findViewById<ImageView>(R.id.picture_cropped)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
