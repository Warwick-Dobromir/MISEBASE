package com.culinary.core.common

/**
 * 日志门面（E03 §7.1）。
 *
 * 业务代码只依赖本接口；实现基于 Timber，放在 Android 层（阶段 6）。
 * 不记录用户输入原文、姓名、完整路径、条码原文与备份密钥（E03 §7.3）。
 */
interface AppLogger {
    fun debug(
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    )

    fun info(
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    )

    fun warn(
        event: String,
        error: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    )

    fun error(
        event: String,
        error: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    )
}
