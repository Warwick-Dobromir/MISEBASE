package com.culinary.settings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 本地后台提醒事件日志。只记录调度与执行元数据，不记录商品名称或库存明细。
 * 保留最近 200 条，避免无限增长；导出时按时间顺序输出。
 */
object ExpiryReminderDiagnostics {
    private const val PREFS_NAME = "inventory_expiry_reminder_runtime"
    private const val KEY_EVENTS = "reminder_event_history"
    private const val MAX_EVENTS = 200
    private val lock = Any()
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun record(context: Context, event: String, detail: String = "") {
        synchronized(lock) {
            val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val events = runCatching { JSONArray(preferences.getString(KEY_EVENTS, "[]") ?: "[]") }
                .getOrDefault(JSONArray())
            val entry = JSONObject()
                .put("at", System.currentTimeMillis())
                .put("event", event)
                .put("detail", detail.take(1200))
            events.put(entry)
            val trimmed = JSONArray()
            val start = (events.length() - MAX_EVENTS).coerceAtLeast(0)
            for (index in start until events.length()) trimmed.put(events.getJSONObject(index))
            preferences.edit().putString(KEY_EVENTS, trimmed.toString()).apply()
        }
    }

    fun export(context: Context): String {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val testPrefs = app.getSharedPreferences(ExpiryTestNotificationWorker.TEST_STATE_PREFS, Context.MODE_PRIVATE)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || runCatching {
            alarmManager.canScheduleExactAlarms()
        }.getOrDefault(false)
        val notificationPermission =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val nextAlarm = prefs.getLong(ExpiryReminderAlarmReceiver.KEY_NEXT_ALARM_AT, 0L)
        val events = runCatching { JSONArray(prefs.getString(KEY_EVENTS, "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        val zone = ZoneId.systemDefault()
        return buildString {
            appendLine("MISEBASE 后台提醒诊断")
            appendLine("导出时间: ${formatter.format(Instant.now().atZone(zone))}")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("时区: ${zone.id}")
            appendLine("精确闹钟权限: ${if (exactAllowed) "已允许" else "未允许"}")
            appendLine("通知运行时权限: ${if (notificationPermission) "已允许" else "未允许"}")
            appendLine("系统通知总开关: ${notificationManager.areNotificationsEnabled()}")
            appendLine("电池优化豁免: ${powerManager.isIgnoringBatteryOptimizations(app.packageName)}")
            val nextAlarmText =
                if (nextAlarm > 0L) formatter.format(Instant.ofEpochMilli(nextAlarm).atZone(zone)) else "未登记"
            appendLine("下次提醒时间: $nextAlarmText")
            appendLine("闹钟登记模式: ${prefs.getString(ExpiryReminderAlarmReceiver.KEY_ALARM_SCHEDULE_MODE, "未知")}")
            appendLine("最近提醒阶段: ${prefs.getString(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE, "无记录")}")
            val stageAt = prefs.getLong(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE_AT, 0L)
            appendLine("最近阶段时间: ${formatMillis(stageAt, zone)}")
            val lastAlarmAt = prefs.getLong(ExpiryReminderAlarmReceiver.KEY_ALARM_LAST_TRIGGERED_AT, 0L)
            appendLine("最近闹钟触发时间: ${formatMillis(lastAlarmAt, zone)}")
            appendLine("最近扫描时间: ${formatMillis(prefs.getLong(ExpiryReminderWorker.KEY_LAST_SCAN_AT, 0L), zone)}")
            appendLine("最近扫描状态: ${prefs.getString(ExpiryReminderWorker.KEY_LAST_SCAN_STATUS, "无记录")}")
            appendLine("最近扫描错误: ${prefs.getString(ExpiryReminderWorker.KEY_LAST_SCAN_ERROR, "无")}")
            appendLine("测试通知状态: ${testPrefs.getString(ExpiryTestNotificationWorker.TEST_STATE_KEY, "无记录")}")
            appendLine()
            appendLine("===== 后台提醒事件历史（最多最近 ${MAX_EVENTS} 条）=====")
            if (events.length() == 0) appendLine("暂无事件记录")
            for (index in 0 until events.length()) {
                val item = events.optJSONObject(index) ?: continue
                val at = formatMillis(item.optLong("at"), zone)
                appendLine("$at | ${item.optString("event")} | ${item.optString("detail")}")
            }
        }
    }

    private fun formatMillis(value: Long, zone: ZoneId): String =
        if (value <= 0L) "无记录" else formatter.format(Instant.ofEpochMilli(value).atZone(zone))
}
