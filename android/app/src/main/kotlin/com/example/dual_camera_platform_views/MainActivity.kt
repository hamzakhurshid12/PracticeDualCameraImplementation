package com.example.dual_camera_platform_views

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugins.GeneratedPluginRegistrant

class MainActivity : FlutterActivity() {
    companion object {
        lateinit var surfaceview : SurfaceView
        lateinit var surfaceview2 : SurfaceView
        lateinit var linearlayout: LinearLayout
        var cameraID : Int = 0
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine)
        val registry = flutterEngine.platformViewsController.registry
        surfaceview = SurfaceView(context)
        surfaceview2 = SurfaceView(context)
        linearlayout = LinearLayout(context)
        linearlayout.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT,MATCH_PARENT)
        linearlayout.orientation = LinearLayout.VERTICAL

        surfaceview.holder.addCallback(surfaceReadyCallback)
        surfaceview2.holder.addCallback(surfaceReadyCallback2)
        linearlayout.addView(surfaceview)
        linearlayout.addView(surfaceview2)
        registry.registerViewFactory("platform_text_view", DualCameraViewfactory())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
        recreate()
    }

    /** Helper to ask camera permission.  */
    object CameraPermissionHelper {
        private const val CAMERA_PERMISSION_CODE = 0
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA

        /** Check to see we have the necessary permissions for this app.  */
        fun hasCameraPermission(activity: Activity): Boolean {
            return ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }

        /** Check to see we have the necessary permissions for this app, and ask for them if we don't.  */
        fun requestCameraPermission(activity: Activity) {
            ActivityCompat.requestPermissions(
                    activity, arrayOf(CAMERA_PERMISSION), CAMERA_PERMISSION_CODE)
        }

        /** Check to see if we need to show the rationale for this permission.  */
        fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION)
        }

        /** Launch Application Setting to grant permission.  */
        fun launchPermissionSettings(activity: Activity) {
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.fromParts("package", activity.packageName, null)
            activity.startActivity(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startCameraSession(surfaceView: SurfaceView) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (cameraManager.cameraIdList.isEmpty()) {
            // no cameras
            return
        }
        val firstCamera = cameraManager.cameraIdList[0]
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            val toast = Toast(this)
            //toast.setText("No Permission Granted!")
            //toast.show()
            return
        }
        cameraManager.openCamera(firstCamera, object: CameraDevice.StateCallback() {
            override fun onDisconnected(p0: CameraDevice) { }
            override fun onError(p0: CameraDevice, p1: Int) { }

            override fun onOpened(cameraDevice: CameraDevice) {
                // use the camera
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)

                cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.let { streamConfigurationMap ->
                    streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888)?.let { yuvSizes ->
                        val previewSize = yuvSizes.last()
                        // cont.
                        val displayRotation = windowManager.defaultDisplay.rotation
                        val swappedDimensions = areDimensionsSwapped(displayRotation, cameraCharacteristics)
                        // swap width and height if needed
                        val rotatedPreviewWidth = if (swappedDimensions) previewSize.height else previewSize.width
                        val rotatedPreviewHeight = if (swappedDimensions) previewSize.width else previewSize.height

                        surfaceView.holder.setFixedSize(surfaceView.width, surfaceView.height/2) //Take up half of available space

                        // Configure Image Reader
                        val imageReader = ImageReader.newInstance(rotatedPreviewWidth, rotatedPreviewHeight,
                                ImageFormat.YUV_420_888, 2)
                        imageReader.setOnImageAvailableListener({
                            val previewSurface = surfaceView.holder.surface
                            val recordingSurface = imageReader.surface
                            val previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(previewSurface)
                                addTarget(recordingSurface)
                            }
                            imageReader.setOnImageAvailableListener({
                                Log.d("camera","setOnImageAvailableListener")
                                imageReader.acquireLatestImage()?.let { image ->
                                    Log.d("camera","acquireLatestImage")
                                }
                            }, Handler { true })
                            previewRequestBuilder.build()

                        }, Handler { true })

                        val previewSurface = surfaceView.holder.surface

                        val captureCallback = object : CameraCaptureSession.StateCallback()
                        {
                            override fun onConfigureFailed(session: CameraCaptureSession) {}

                            override fun onConfigured(session: CameraCaptureSession) {
                                // session configured
                                val previewRequestBuilder =   cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                        .apply {
                                            addTarget(previewSurface)
                                        }
                                session.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        object: CameraCaptureSession.CaptureCallback() {},
                                        Handler { true }
                                )
                            }
                        }
                        cameraDevice.createCaptureSession(mutableListOf(previewSurface), captureCallback, Handler { true })
                    }

                }
            }
        }, Handler { true })
    }

    val surfaceReadyCallback = object: SurfaceHolder.Callback {
        override fun surfaceChanged(
                p0: SurfaceHolder,
                p1: Int,
                p2: Int,
                p3: Int
        ) { }
        override fun surfaceDestroyed(p0: SurfaceHolder) { }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun surfaceCreated(p0: SurfaceHolder) {
            startCameraSession(surfaceview)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startCameraSession2() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (cameraManager.cameraIdList.isEmpty()) {
            // no cameras
            return
        }
        val firstCamera = cameraManager.cameraIdList[1]
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            val toast = Toast(this)
            toast.setText("No Permission Granted!")
            toast.show()
            return
        }
        cameraManager.openCamera(firstCamera, @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        object: CameraDevice.StateCallback() {
            override fun onDisconnected(p0: CameraDevice) { }
            override fun onError(p0: CameraDevice, p1: Int) { }

            override fun onOpened(cameraDevice: CameraDevice) {
                // use the camera
                val cameraCharacteristics =    cameraManager.getCameraCharacteristics(cameraDevice.id)

                cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.let { streamConfigurationMap ->
                    streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888)?.let { yuvSizes ->
                        val previewSize = yuvSizes.last()
                        // cont.
                        val displayRotation = windowManager.defaultDisplay.rotation
                        val swappedDimensions = areDimensionsSwapped(displayRotation, cameraCharacteristics)
                        // swap width and height if needed
                        val rotatedPreviewWidth = if (swappedDimensions) previewSize.height else previewSize.width
                        val rotatedPreviewHeight = if (swappedDimensions) previewSize.width else previewSize.height

                        //surfaceview2.holder.setFixedSize(rotatedPreviewWidth, surfaceview2.height)

                        val previewSurface = surfaceview2.holder.surface

                        val captureCallback = object : CameraCaptureSession.StateCallback()
                        {
                            override fun onConfigureFailed(session: CameraCaptureSession) {}

                            override fun onConfigured(session: CameraCaptureSession) {
                                // session configured
                                val previewRequestBuilder =   cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                        .apply {
                                            addTarget(previewSurface)
                                        }
                                session.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        object: CameraCaptureSession.CaptureCallback() {
                                        },
                                        Handler { true }
                                )
                            }
                        }
                        cameraDevice.createCaptureSession(mutableListOf(previewSurface), captureCallback, Handler { true })
                    }

                }
            }
        }, Handler { true })
    }

    val surfaceReadyCallback2 = object: SurfaceHolder.Callback {
        override fun surfaceChanged(
                p0: SurfaceHolder,
                p1: Int,
                p2: Int,
                p3: Int
        ) { }
        override fun surfaceDestroyed(p0: SurfaceHolder) { }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun surfaceCreated(p0: SurfaceHolder) {
            startCameraSession2()
        }
    }
    

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun areDimensionsSwapped(displayRotation: Int, cameraCharacteristics: CameraCharacteristics): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 90 || cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 0 || cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                // invalid display rotation
            }
        }
        return swappedDimensions
    }
}
