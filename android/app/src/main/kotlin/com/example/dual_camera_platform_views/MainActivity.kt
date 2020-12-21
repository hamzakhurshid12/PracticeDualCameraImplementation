package com.example.dual_camera_platform_views

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugins.GeneratedPluginRegistrant
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : FlutterActivity() {
    companion object {
        lateinit var surfaceview : SurfaceView
        lateinit var surfaceview2 : SurfaceView
        lateinit var linearlayout: LinearLayout
        var cameraID : Int = 0
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine)
        val registry = flutterEngine.platformViewsController.registry
        surfaceview = SurfaceView(context)
        surfaceview2 = SurfaceView(context)
        linearlayout = LinearLayout(context)
        linearlayout.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT,MATCH_PARENT)
        linearlayout.orientation = LinearLayout.VERTICAL
        val captureButton: ImageButton = ImageButton(context)
        captureButton.setImageDrawable(getDrawable(R.drawable.ic_camera))
        captureButton.setOnClickListener(View.OnClickListener {
            cameraDevice1.createCaptureSession(
                    mutableListOf(imageReader1.surface),
                    captureCallback1,
                    Handler { true })
            cameraDevice2.createCaptureSession(
                    mutableListOf(imageReader2.surface),
                    captureCallback2,
                    Handler { true })
        })
        surfaceview.holder.addCallback(surfaceReadyCallback)
        surfaceview2.holder.addCallback(surfaceReadyCallback2)
        linearlayout.addView(captureButton, MATCH_PARENT, 120)
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

    lateinit var cameraDevice1: CameraDevice
    lateinit var imageReader1: ImageReader
    lateinit var captureCallback1: CameraCaptureSession.StateCallback
    lateinit var cameraDevice2: CameraDevice
    lateinit var imageReader2: ImageReader
    lateinit var captureCallback2: CameraCaptureSession.StateCallback

    lateinit var camera1bmp: Bitmap
    lateinit var camera2bmp: Bitmap

    lateinit var camera1PreviewSize: Size
    lateinit var camera2PreviewSize: Size

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startCameraSession() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (cameraManager.cameraIdList.isEmpty()) {
            // no cameras
            return
        }
        val firstCamera = cameraManager.cameraIdList[0]
        if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            val toast = Toast(this)
            toast.setText("No Permission Granted!")
            toast.show()
            return
        }
        cameraManager.openCamera(firstCamera, object : CameraDevice.StateCallback() {
            override fun onDisconnected(p0: CameraDevice) {}
            override fun onError(p0: CameraDevice, p1: Int) {}

            override fun onOpened(cameraDevice: CameraDevice) {
                // use the camera
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)

                cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.let { streamConfigurationMap ->
                    streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)
                            ?.let { yuvSizes ->
                                var previewSize = yuvSizes.last()
                                camera1PreviewSize = previewSize
                                cameraDevice1 = cameraDevice

                                val displayRotation = windowManager.defaultDisplay.rotation
                                val swappedDimensions =
                                        areDimensionsSwapped(displayRotation, cameraCharacteristics)
                                // swap width and height if needed
                                val rotatedPreviewWidth =
                                        if (swappedDimensions) previewSize.height else previewSize.width
                                val rotatedPreviewHeight =
                                        if (swappedDimensions) previewSize.width else previewSize.height

                                surfaceview.holder.setFixedSize(
                                        rotatedPreviewWidth,
                                        rotatedPreviewHeight
                                )

                                val matchedResolution = getMatchingResolution()

                                // Configure Image Reader
                                imageReader1 = ImageReader.newInstance(
                                        matchedResolution.width, matchedResolution.height,
                                        ImageFormat.JPEG, 1
                                )

                                imageReader1.setOnImageAvailableListener({
                                    Log.d("camera", "setOnImageAvailableListener")
                                    imageReader1.acquireLatestImage()?.let { image ->
                                        Log.d("camera", "acquireLatestImage")
                                        val buffer = image.planes[0].buffer
                                        var bytes = ByteArray(buffer.remaining()).apply {
                                            buffer.get(this)
                                        }
                                        camera1bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        combineAndSaveImage()
                                        image.close()
                                        imageReader1.close()
                                    }
                                }, Handler { true })

                                val previewSurface = surfaceview.holder.surface
                                val recordingSurface = imageReader1.surface

                                val captureCallbackNormal =
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                // session configured
                                                val previewRequestBuilder =
                                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                                                .apply {
                                                                    addTarget(previewSurface)
                                                                }
                                                session.setRepeatingRequest(
                                                        previewRequestBuilder.build(),
                                                        object : CameraCaptureSession.CaptureCallback() {},
                                                        Handler { true }
                                                )
                                            }
                                        }
                                cameraDevice.createCaptureSession(
                                        mutableListOf(previewSurface),
                                        captureCallbackNormal,
                                        Handler { true })


                                val captureCallbackImage =
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                // session configured
                                                val previewRequestBuilder =
                                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                                                .apply {
                                                                    addTarget(recordingSurface)
                                                                }
                                                session.setRepeatingRequest(
                                                        previewRequestBuilder.build(),
                                                        object : CameraCaptureSession.CaptureCallback() {},
                                                        Handler { true }
                                                )
                                            }
                                        }
                                captureCallback1 = captureCallbackImage

                            }
                }
            }
        }, Handler { true })
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startCameraSession2() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (cameraManager.cameraIdList.isEmpty()) {
            // no cameras
            return
        }
        val firstCamera = cameraManager.cameraIdList[1]
        if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            val toast = Toast(this)
            toast.setText("No Permission Granted!")
            toast.show()
            return
        }
        cameraManager.openCamera(firstCamera, object : CameraDevice.StateCallback() {
            override fun onDisconnected(p0: CameraDevice) {}
            override fun onError(p0: CameraDevice, p1: Int) {}

            override fun onOpened(cameraDevice: CameraDevice) {
                // use the camera
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)

                cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.let { streamConfigurationMap ->
                    streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)
                            ?.let { yuvSizes ->
                                var previewSize = yuvSizes.last()
                                camera2PreviewSize = previewSize

                                val displayRotation = windowManager.defaultDisplay.rotation
                                val swappedDimensions =
                                        areDimensionsSwapped(displayRotation, cameraCharacteristics)
                                // swap width and height if needed
                                val rotatedPreviewWidth =
                                        if (swappedDimensions) previewSize.height else previewSize.width
                                val rotatedPreviewHeight =
                                        if (swappedDimensions) previewSize.width else previewSize.height

                                /*surfaceView2.holder.setFixedSize(
                                    rotatedPreviewWidth,
                                    rotatedPreviewHeight
                                )*/

                                val matchedResolution = getMatchingResolution()

                                // Configure Image Reader
                                imageReader2 = ImageReader.newInstance(
                                        matchedResolution.width, matchedResolution.height,
                                        ImageFormat.JPEG, 1
                                )

                                imageReader2.setOnImageAvailableListener({
                                    Log.d("camera", "setOnImageAvailableListener")
                                    imageReader2.acquireLatestImage()?.let { image ->
                                        Log.d("camera", "acquireLatestImage")
                                        val buffer = image.planes[0].buffer
                                        var bytes = ByteArray(buffer.remaining()).apply {
                                            buffer.get(this)
                                        }
                                        camera2bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        combineAndSaveImage()
                                        image.close()
                                        imageReader2.close()
                                    }
                                }, Handler { true })

                                val previewSurface = surfaceview2.holder.surface
                                val recordingSurface = imageReader2.surface

                                val captureCallbackNormal =
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                // session configured
                                                val previewRequestBuilder =
                                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                                                .apply {
                                                                    addTarget(previewSurface)
                                                                }
                                                session.setRepeatingRequest(
                                                        previewRequestBuilder.build(),
                                                        object : CameraCaptureSession.CaptureCallback() {},
                                                        Handler { true }
                                                )
                                            }
                                        }
                                cameraDevice.createCaptureSession(
                                        mutableListOf(previewSurface),
                                        captureCallbackNormal,
                                        Handler { true })


                                val captureCallbackImage =
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                // session configured
                                                val previewRequestBuilder =
                                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                                                .apply {
                                                                    addTarget(recordingSurface)
                                                                }
                                                previewRequestBuilder.set(
                                                        CaptureRequest.JPEG_ORIENTATION,
                                                        cameraCharacteristics.get(
                                                                CameraCharacteristics.SENSOR_ORIENTATION
                                                        )
                                                )
                                                session.setRepeatingRequest(
                                                        previewRequestBuilder.build(),
                                                        object : CameraCaptureSession.CaptureCallback() {},
                                                        Handler { true }
                                                )
                                            }
                                        }

                                cameraDevice2 = cameraDevice
                                captureCallback2 = captureCallbackImage


                            }
                }
            }
        }, Handler { true })
    }

    val surfaceReadyCallback = object : SurfaceHolder.Callback {
        override fun surfaceChanged(
                p0: SurfaceHolder,
                p1: Int,
                p2: Int,
                p3: Int
        ) {

        }

        override fun surfaceDestroyed(p0: SurfaceHolder) {}

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun surfaceCreated(p0: SurfaceHolder) {
            startCameraSession()
        }
    }

    val surfaceReadyCallback2 = object : SurfaceHolder.Callback {
        override fun surfaceChanged(
                p0: SurfaceHolder,
                p1: Int,
                p2: Int,
                p3: Int
        ) {
        }

        override fun surfaceDestroyed(p0: SurfaceHolder) {}

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun surfaceCreated(p0: SurfaceHolder) {
            startCameraSession2()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun areDimensionsSwapped(
            displayRotation: Int,
            cameraCharacteristics: CameraCharacteristics
    ): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 90 || cameraCharacteristics.get(
                                CameraCharacteristics.SENSOR_ORIENTATION
                        ) == 270
                ) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 0 || cameraCharacteristics.get(
                                CameraCharacteristics.SENSOR_ORIENTATION
                        ) == 180
                ) {
                    swappedDimensions = true
                }
            }
            else -> {
                // invalid display rotation
            }
        }
        return swappedDimensions
    }

    /**
     * Create a [File] named a using formatted timestamp with the current date and time.
     *
     * @return [File] created.
     */
    private fun createFile(context: Context, extension: String): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        val workingDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(workingDir, "IMG_${sdf.format(Date())}.$extension")
    }

    private fun combineImages(
            c: Bitmap,
            s: Bitmap
    ): Bitmap? { // can add a 3rd parameter 'String loc' if you want to save the new image - left some code to do that at the bottom
        var cs: Bitmap? = null
        val width: Int
        var height = 0
        if (c.height > s.height) {
            height = c.height + s.height
            width = c.width
        } else {
            height = s.height + s.height
            width = c.width
        }
        cs = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val comboImage = Canvas(cs)
        comboImage.drawBitmap(c, 0f, 0f, null)
        comboImage.drawBitmap(s, 0f, c.height.toFloat(), null)
        return cs
    }

    private fun combineAndSaveImage(){
        if(!this::camera1bmp.isInitialized || !this::camera2bmp.isInitialized) {
            Log.d("combineAndSaveImage", "One image ready, waiting for the other!")
        } else {
            camera1bmp = camera1bmp.rotate(90f)
            camera2bmp = camera2bmp.rotate(-90f)
            val joined = combineImages(camera1bmp, camera2bmp)
            try {
                val output = createFile(applicationContext, "jpg")
                val stream = ByteArrayOutputStream()
                if (joined != null) {
                    joined.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val bytes = stream.toByteArray()
                    FileOutputStream(output).use { it.write(bytes) }
                }
            } catch (exc: IOException) {
                Log.e("Error", "Unable to write JPEG image to file", exc)
            }
        }
    }

    fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    //Matches the available resolutions of twi camera devices and finds the
    // image resolution present for both cameras.
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getMatchingResolution(): Size {
        val size: Size = Size(640,480)
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(
                "0"
        )
        val characteristics2: CameraCharacteristics = cameraManager.getCameraCharacteristics(
                "1"
        )
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val map2 = characteristics2.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if(map==null || map2==null)
            return size

        var x1 = map.getOutputSizes(ImageFormat.JPEG)
        var x2 = map2.getOutputSizes(ImageFormat.JPEG)
        for(item in x1.reversed()){
            for (item2 in x2){
                if(item.equals(item2))
                    return item
            }
        }

        return size
    }
}
