package com.culinary.feature.scan.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * 相机权限查询。
 *
 * 只做状态读取与说明文案选择；请求本身由 Compose 的 ActivityResult 启动器发起，
 * 避免在模块内引入额外生命周期。
 */
internal object CameraPermission {
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCamera(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}
