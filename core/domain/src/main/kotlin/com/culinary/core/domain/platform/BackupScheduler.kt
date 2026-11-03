package com.culinary.core.domain.platform

import com.culinary.core.common.Outcome

/**
 * 自动备份调度端口（A03A §4/§6.4）。
 *
 * `core:platform` 用 WorkManager 实现；`core:domain` 不感知 Android 调度器。
 * 周期任务与立即任务使用不同的唯一工作名，避免互相覆盖。
 */
interface BackupScheduler {
    fun schedulePeriodicBackup()

    fun cancelPeriodicBackup()

    /** 入队一次立即备份，返回 WorkManager 的请求 ID。 */
    fun enqueueImmediateBackup(): Outcome<String>
}
