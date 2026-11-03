package com.culinary.core.data.logging

import com.culinary.core.common.AppLogger
import timber.log.Timber

/**
 * `AppLogger` 的 Timber 实现（E03 §7）。
 *
 * 只转发事件名与结构化字段；调用方负责不把用户输入原文、姓名、完整路径、
 * 条码原文写进字段（E03 §7.3）。日志不离开设备。
 */
class TimberAppLogger : AppLogger {
    override fun debug(
        event: String,
        fields: Map<String, Any?>,
    ) {
        Timber.d(format(event, fields))
    }

    override fun info(
        event: String,
        fields: Map<String, Any?>,
    ) {
        Timber.i(format(event, fields))
    }

    override fun warn(
        event: String,
        error: Throwable?,
        fields: Map<String, Any?>,
    ) {
        Timber.w(error, format(event, fields))
    }

    override fun error(
        event: String,
        error: Throwable?,
        fields: Map<String, Any?>,
    ) {
        Timber.e(error, format(event, fields))
    }

    private fun format(
        event: String,
        fields: Map<String, Any?>,
    ): String =
        if (fields.isEmpty()) {
            event
        } else {
            event + " " + fields.entries.joinToString(separator = " ") { (key, value) -> "$key=$value" }
        }
}
