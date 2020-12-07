package com.example.dual_camera_platform_views

import android.content.Context
import android.graphics.PixelFormat
import android.view.SurfaceView
import android.view.View
import android.widget.TextView
import io.flutter.plugin.platform.PlatformView

class DualCameraView(context: Context) : PlatformView {
    val surfaceview: SurfaceView = SurfaceView(context);
    override fun getView(): View {
        //surfaceview.holder.setFixedSize(200,200);
        //surfaceview.holder.setFormat(PixelFormat.TRANSLUCENT)
        return MainActivity.surfaceView
    }
    override fun dispose() {}
}