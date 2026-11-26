package com.culinary.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ExpiryTestNotificationWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val productCount = inputData.getInt(KEY_PRODUCT_COUNT, 0)
        val batchCount = inputData.getInt(KEY_BATCH_COUNT, 0)
        val body = if (productCount > 0 && batchCount > 0) {
            "库存过期警戒：检测到 $productCount 种商品、$batchCount 个批次存在过期库存，请及时处理。"
        } else {
            "库存效期扫描测试完成：当前未发现过期库存。"
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "库存效期警戒", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                applicationContext,
                4107,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.culinary.R.mipmap.ic_launcher)
            .setContentTitle("库存效期警戒")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (pendingIntent != null) builder.setContentIntent(pendingIntent)
        val notification = builder.build()
        manager.notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "inventory-expiry-scan-test-notification"
        const val KEY_PRODUCT_COUNT = "product_count"
        const val KEY_BATCH_COUNT = "batch_count"
        private const val CHANNEL_ID = "inventory_expiry_test"
        private const val NOTIFICATION_ID = 4107
    }
}
