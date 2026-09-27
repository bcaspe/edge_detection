package com.sample.edgedetection

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.sample.edgedetection.scan.ScanActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class EdgeDetectionPlugin : FlutterPlugin, ActivityAware {
    private var handler: EdgeDetectionHandler? = null

    override fun onAttachedToEngine(binding: FlutterPluginBinding) {
        handler = EdgeDetectionHandler()
        val channel = MethodChannel(
            binding.binaryMessenger, "edge_detection"
        )
        channel.setMethodCallHandler(handler)
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        handler?.detachFromActivity()
        handler = null
    }

    override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
        handler?.setActivityPluginBinding(activityPluginBinding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        handler?.detachFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        handler?.setActivityPluginBinding(binding)
    }

    override fun onDetachedFromActivity() {
        handler?.detachFromActivity()
    }
}

class EdgeDetectionHandler : MethodCallHandler, PluginRegistry.ActivityResultListener {
    private var activityPluginBinding: ActivityPluginBinding? = null
    private var result: Result? = null
    private var methodCall: MethodCall? = null
    private var listeningForActivityResult = false

    companion object {
        private const val TAG = "EdgeDetectionHandler"
        const val INITIAL_BUNDLE = "initial_bundle"
        const val FROM_GALLERY = "from_gallery"
        const val SAVE_TO = "save_to"
        const val RESULT_PATHS = "result_paths"
        const val CAN_USE_GALLERY = "can_use_gallery"
        const val SCAN_TITLE = "scan_title"
        const val CROP_TITLE = "crop_title"
        const val CROP_BLACK_WHITE_TITLE = "crop_black_white_title"
        const val CROP_RESET_TITLE = "crop_reset_title"
        /** 1-based index of the image being cropped in a multi-select gallery batch. */
        const val GALLERY_CROP_INDEX = "gallery_crop_index"
        /** Total images in the current gallery batch. */
        const val GALLERY_CROP_TOTAL = "gallery_crop_total"
    }

    fun setActivityPluginBinding(activityPluginBinding: ActivityPluginBinding) {
        // Avoid registering the same listener multiple times — Flutter notifies
        // every listener, and a second onActivityResult would re-use a completed Result.
        if (listeningForActivityResult && this.activityPluginBinding === activityPluginBinding) {
            return
        }
        detachFromActivity()
        activityPluginBinding.addActivityResultListener(this)
        this.activityPluginBinding = activityPluginBinding
        listeningForActivityResult = true
    }

    fun detachFromActivity() {
        activityPluginBinding?.removeActivityResultListener(this)
        activityPluginBinding = null
        listeningForActivityResult = false
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when {
            getActivity() == null -> {
                result.error(
                    "no_activity",
                    "edge_detection plugin requires a foreground activity.",
                    null
                )
                return
            }
            call.method.equals("edge_detect") -> {
                openCameraActivity(call, result)
            }
            call.method.equals("edge_detect_gallery") -> {
                openGalleryActivity(call, result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun getActivity(): Activity? {
        return activityPluginBinding?.activity
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) {
            return false
        }
        // No pending Flutter call — ignore stray / duplicate results (e.g. after
        // a prior reply, or request-code collisions with other plugins).
        if (this.result == null) {
            return false
        }
        when (resultCode) {
            Activity.RESULT_OK -> {
                val resultPaths = data?.getStringArrayListExtra(RESULT_PATHS) ?: arrayListOf()
                finishWithSuccess(resultPaths)
            }
            Activity.RESULT_CANCELED -> {
                finishWithSuccess(arrayListOf())
            }
            ERROR_CODE -> {
                finishWithError(ERROR_CODE.toString(), data?.getStringExtra("RESULT") ?: "ERROR")
            }
            else -> {
                finishWithSuccess(arrayListOf())
            }
        }
        return true
    }

    private fun openCameraActivity(call: MethodCall, result: Result) {
        if (!setPendingMethodCallAndResult(call, result)) {
            finishWithAlreadyActiveError(result)
            return
        }

        val initialIntent = Intent(getActivity()?.applicationContext, ScanActivity::class.java)

        val bundle = Bundle()
        bundle.putString(SAVE_TO, call.argument<String>(SAVE_TO) as String)
        bundle.putString(SCAN_TITLE, call.argument<String>(SCAN_TITLE) as String)
        bundle.putString(CROP_TITLE, call.argument<String>(CROP_TITLE) as String)
        bundle.putString(CROP_BLACK_WHITE_TITLE, call.argument<String>(CROP_BLACK_WHITE_TITLE) as String)
        bundle.putString(CROP_RESET_TITLE, call.argument<String>(CROP_RESET_TITLE) as String)
        bundle.putBoolean(CAN_USE_GALLERY, call.argument<Boolean>(CAN_USE_GALLERY) as Boolean)

        initialIntent.putExtra(INITIAL_BUNDLE, bundle)

        try {
            getActivity()?.startActivityForResult(initialIntent, REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScanActivity", e)
            finishWithError("start_failed", e.message ?: "Failed to start edge detection")
        }
    }

    private fun openGalleryActivity(call: MethodCall, result: Result) {
        if (!setPendingMethodCallAndResult(call, result)) {
            finishWithAlreadyActiveError(result)
            return
        }
        val initialIntent = Intent(getActivity()?.applicationContext, ScanActivity::class.java)

        val bundle = Bundle()
        bundle.putString(SAVE_TO, call.argument<String>(SAVE_TO) as String)
        bundle.putString(CROP_TITLE, call.argument<String>(CROP_TITLE) as String)
        bundle.putString(CROP_BLACK_WHITE_TITLE, call.argument<String>(CROP_BLACK_WHITE_TITLE) as String)
        bundle.putString(CROP_RESET_TITLE, call.argument<String>(CROP_RESET_TITLE) as String)
        bundle.putBoolean(FROM_GALLERY, call.argument<Boolean>(FROM_GALLERY) as Boolean)

        initialIntent.putExtra(INITIAL_BUNDLE, bundle)

        try {
            getActivity()?.startActivityForResult(initialIntent, REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScanActivity (gallery)", e)
            finishWithError("start_failed", e.message ?: "Failed to start edge detection")
        }
    }

    private fun setPendingMethodCallAndResult(
        methodCall: MethodCall,
        result: Result
    ): Boolean {
        if (this.result != null) {
            return false
        }
        this.methodCall = methodCall
        this.result = result
        return true
    }

    private fun finishWithAlreadyActiveError(incoming: Result) {
        // Reply to the *new* call — the old pending result still belongs to the
        // in-flight ScanActivity and must not be completed here.
        try {
            incoming.error("already_active", "Edge detection is already active", null)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "already_active reply ignored (already submitted)", e)
        }
    }

    private fun finishWithError(errorCode: String, errorMessage: String) {
        val pending = takePendingResult() ?: return
        try {
            pending.error(errorCode, errorMessage, null)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "error reply ignored (already submitted)", e)
        }
    }

    private fun finishWithSuccess(res: ArrayList<String>) {
        val pending = takePendingResult() ?: return
        try {
            pending.success(res)
        } catch (e: IllegalStateException) {
            // Duplicate onActivityResult / double listener registration.
            Log.w(TAG, "success reply ignored (already submitted)", e)
        }
    }

    /** Clears the pending Result before replying so a second callback is a no-op. */
    private fun takePendingResult(): Result? {
        val pending = result
        clearMethodCallAndResult()
        return pending
    }

    private fun clearMethodCallAndResult() {
        methodCall = null
        result = null
    }
}
