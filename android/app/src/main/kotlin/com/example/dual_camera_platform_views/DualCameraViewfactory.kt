package com.example.dual_camera_platform_views

import android.content.Context
import android.view.SurfaceView
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class DualCameraViewfactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        /*val androidTextView = AndroidTextView(context)
        androidTextView.contentView.id = viewId
        val params = args?.let { args as Map<*, *> }
        val text = params?.get("text") as CharSequence?
        text?.let {
            androidTextView.contentView.text = it
        }
        return androidTextView*/
        val params = args?.let { args as Map<*, *> }
        val camera = params?.get("camera") as Int
        MainActivity.cameraID = camera
        return DualCameraView(context)
    }
}