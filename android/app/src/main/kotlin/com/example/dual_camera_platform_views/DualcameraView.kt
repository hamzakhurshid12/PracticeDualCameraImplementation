package com.example.dual_camera_platform_views

import android.content.Context
import android.graphics.PixelFormat
import android.view.SurfaceView
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.flutter.plugin.platform.PlatformView

class DualCameraView(context: Context) : PlatformView {
    //val linearLayout: LinearLayout = LinearLayout(context);
    override fun getView(): View {
        return MainActivity.linearlayout;
    }
    override fun dispose() {}
}