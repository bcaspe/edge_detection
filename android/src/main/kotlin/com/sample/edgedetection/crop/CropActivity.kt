package com.sample.edgedetection.crop

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageView
import com.sample.edgedetection.EdgeDetectionHandler
import com.sample.edgedetection.R
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.view.PaperRectangle

class CropActivity : BaseActivity(), ICropView.Proxy {

    private var showMenuItems = false

    private lateinit var mPresenter: CropPresenter

    private lateinit var initialBundle: Bundle

    override fun prepare() {
        this.initialBundle = intent.getBundleExtra(EdgeDetectionHandler.INITIAL_BUNDLE) as Bundle
        this.title = initialBundle.getString(EdgeDetectionHandler.CROP_TITLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.paper).post {
            // we have to initialize everything in post when the view has been drawn and we have the actual height and width of the whole view
            mPresenter.onViewsReady(findViewById<View>(R.id.paper).width, findViewById<View>(R.id.paper).height)
        }
    }

    override fun provideContentViewId(): Int = R.layout.activity_crop


    override fun initPresenter() {
        val initialBundle = intent.getBundleExtra(EdgeDetectionHandler.INITIAL_BUNDLE) as Bundle
        mPresenter = CropPresenter(this, initialBundle)
        findViewById<ImageView>(R.id.crop).setOnClickListener {
            Log.e(TAG, "Crop touched!")
            mPresenter.crop()
            changeMenuVisibility(true)
        }

        findViewById<ImageView>(R.id.black_white).setOnClickListener {
            Log.e(TAG, "Black and White button clicked!")
            mPresenter.enhance() // Black and white logic
        }

        findViewById<ImageView>(R.id.rotate).setOnClickListener {
            Log.e(TAG, "Rotate button clicked!")
            mPresenter.rotate() // Rotate logic
        }

        findViewById<ImageView>(R.id.reset).setOnClickListener {
            Log.e(TAG, "Reset button clicked!")
            mPresenter.reset() // Reset logic
        }

        findViewById<ImageView>(R.id.crop).setOnClickListener {
            Log.e(TAG, "Crop button clicked!")
            mPresenter.crop()
            changeMenuVisibility(true)
        }
    }

    override fun getPaper(): ImageView = findViewById(R.id.paper)

    override fun getPaperRect() = findViewById<PaperRectangle>(R.id.paper_rect)

    override fun getCroppedPaper() = findViewById<ImageView>(R.id.picture_cropped)

    // override fun onCreateOptionsMenu(menu: Menu): Boolean {
    //     // Create menu items programmatically
    // menu.add(Menu.NONE, R.id.action_label, Menu.NONE, "").apply {
    //     setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    //     isVisible = showMenuItems
    // }

    // // Create enhance group
    // val enhanceGroup = menu.addSubMenu(R.id.enhance_group, Menu.NONE, Menu.NONE, "")
    
    // enhanceGroup.add(Menu.NONE, R.id.rotation_image, Menu.NONE, "").apply {
    //     setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    //     isVisible = showMenuItems
    // }
    
    // enhanceGroup.add(Menu.NONE, R.id.gray, Menu.NONE, 
    //     initialBundle.getString(EdgeDetectionHandler.CROP_BLACK_WHITE_TITLE)).apply {
    //     setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    // }
    
    // enhanceGroup.add(Menu.NONE, R.id.reset, Menu.NONE,
    //     initialBundle.getString(EdgeDetectionHandler.CROP_RESET_TITLE)).apply {
    //     setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    // }

    // menu.setGroupVisible(R.id.enhance_group, showMenuItems)

    // // Update crop button visibility
    // findViewById<ImageView>(R.id.crop).visibility = 
    //     if (showMenuItems) View.GONE else View.VISIBLE

    // return true
    // }


    private fun changeMenuVisibility(showMenuItems: Boolean) {
        val buttonRow = findViewById<LinearLayout>(R.id.button_row)
        val doneButton = findViewById<ImageView>(R.id.done)
        val cropButton = findViewById<ImageView>(R.id.crop)

        buttonRow.visibility = if (showMenuItems) View.VISIBLE else View.GONE
        doneButton.visibility = if (showMenuItems) View.VISIBLE else View.GONE
        cropButton.visibility = if (showMenuItems) View.GONE else View.VISIBLE
    }

    override fun onBackPressed() {
        if (showMenuItems) {
            // If menu items are visible (indicating that the image is cropped), revert to pre-cropped state
            mPresenter.reset() // Call the reset method in the presenter to revert changes
            changeMenuVisibility(false) // Hide the menu items
        } else {
            // If menu items are not visible, proceed with default back button behavior
            super.onBackPressed()
        }
    }

    // // handle button activities
    // override fun onOptionsItemSelected(item: MenuItem): Boolean {
    //     when (item.itemId) {
    //         android.R.id.home -> {
    //             onBackPressed()
    //             return true
    //         }
    //         R.id.action_label -> {
    //             Log.e(TAG, "Saved touched!")
    //             item.isEnabled = false
    //             mPresenter.save()
    //             setResult(Activity.RESULT_OK)
    //             System.gc()
    //             finish()
    //             return true
    //         }
    //         R.id.rotation_image -> {
    //             Log.e(TAG, "Rotate touched!")
    //             mPresenter.rotate()
    //             return true
    //         }
    //         R.id.gray -> {
    //             Log.e(TAG, "Black White touched!")
    //             mPresenter.enhance()
    //             return true
    //         }
    //         R.id.reset -> {
    //             Log.e(TAG, "Reset touched!")
    //             mPresenter.reset()
    //             return true
    //         }
    //         else -> return super.onOptionsItemSelected(item)
    //     }
    // }
}
