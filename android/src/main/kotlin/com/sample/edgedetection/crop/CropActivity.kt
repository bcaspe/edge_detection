package com.sample.edgedetection.crop

import android.app.Activity
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import com.sample.edgedetection.EdgeDetectionHandler
import com.sample.edgedetection.R
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.view.PaperRectangle

class CropActivity : BaseActivity(), ICropView.Proxy {

    private var showMenuItems = false
    private var isBlackWhiteActive = true

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
        changeMenuVisibility(false)
        // Replace the SeekBar setup with this
        findViewById<ImageView>(R.id.threshold_up).setOnClickListener {
            val currentValue = findViewById<TextView>(R.id.threshold_value).text.toString().toInt()
            if (currentValue < 30) {  // Max threshold
                val newValue = currentValue + 1
                findViewById<TextView>(R.id.threshold_value).text = newValue.toString()
                mPresenter.reset()
                mPresenter.enhance(newValue)
            }
        }

        findViewById<ImageView>(R.id.threshold_down).setOnClickListener {
            val currentValue = findViewById<TextView>(R.id.threshold_value).text.toString().toInt()
            if (currentValue > 1) {  // Min threshold
                val newValue = currentValue - 1
                findViewById<TextView>(R.id.threshold_value).text = newValue.toString()
                mPresenter.reset()
                mPresenter.enhance(newValue)
            }
        }

        // Show threshold controls when black/white is activated
        findViewById<ImageView>(R.id.black_white).setOnClickListener { button ->
            if (!isBlackWhiteActive) {
                activateBlackWhite()
            } else {
                findViewById<LinearLayout>(R.id.threshold_controls).visibility = View.GONE
                (button as ImageView).clearColorFilter()
                mPresenter.reset()
            }
            isBlackWhiteActive = !isBlackWhiteActive
        }
    }

    override fun provideContentViewId(): Int = R.layout.activity_crop

    fun activateBlackWhite() {
        findViewById<LinearLayout>(R.id.threshold_controls).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.black_white).setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN)
        mPresenter.enhance()
    }

    override fun initPresenter() {
        val initialBundle = intent.getBundleExtra(EdgeDetectionHandler.INITIAL_BUNDLE) as Bundle
        mPresenter = CropPresenter(this, initialBundle)

        findViewById<ImageView>(R.id.crop).setOnClickListener {
            Log.e(TAG, "Crop touched!")
            mPresenter.crop()
            changeMenuVisibility(true)
            activateBlackWhite()
        }

        findViewById<ImageView>(R.id.rotate).setOnClickListener {
            Log.e(TAG, "Rotate button clicked!")
            mPresenter.rotate() // Rotate logic
        }

        findViewById<ImageView>(R.id.done).setOnClickListener {
            Log.e(TAG, "Saved touched!")
            mPresenter.save()
            setResult(Activity.RESULT_OK)
            System.gc()
            finish()// Save logic
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
        this.showMenuItems = showMenuItems
        val buttonRow = findViewById<LinearLayout>(R.id.button_row)
        val cropButton = findViewById<ImageView>(R.id.crop)

        buttonRow.visibility = if (showMenuItems) View.VISIBLE else View.GONE
        cropButton.visibility = if (showMenuItems) View.GONE else View.VISIBLE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {  // This is the ID for the back button in the title bar
                onBackPressed()     // Reuse your existing back button logic
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (showMenuItems) {
            Log.e(TAG, "Back button clicked! resetting")
            // If menu items are visible (indicating that the image is cropped), revert to pre-cropped state
            if (!mPresenter.handleBackButton()) {
                super.onBackPressed() // This will finish the activity and return to scan
            } else {
                changeMenuVisibility(false) // Hide the menu items
            }
        } else {
            Log.e(TAG, "Back button clicked! going back")
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
