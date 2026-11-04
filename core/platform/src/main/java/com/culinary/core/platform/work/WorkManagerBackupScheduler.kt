package com.culinary.core.platform.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.platform.BackupScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * WorkManager 自动备份调度（E06 / R01 阶段 5）。
 *
 * 周期任务 24 小时、电量不低时执行；使用唯一工作名，重复调度不会叠加。
 * 立即备份使用独立的唯一工作名，避免覆盖周期任务。
 */
class WorkManagerBackupScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BackupScheduler {
        override fun schedulePeriodicBackup() {
            val request =
                PeriodicWorkRequestBuilder<AutoBackupWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    ).build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        override fun cancelPeriodicBackup() {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        override fun enqueueImmediateBackup(
            backupId: String?,
            targetDirectory: String?,
        ): Outcome<String> =
            try {
                val input = Data.Builder()
                if (backupId != null) input.putString(AutoBackupWorker.KEY_BACKUP_ID, backupId)
                if (targetDirectory != null) input.putString(AutoBackupWorker.KEY_TARGET_DIRECTORY, targetDirectory)
                val request =
                    OneTimeWorkRequestBuilder<AutoBackupWorker>()
                        .setInputData(input.build())
                        .build()
                WorkManager.getInstance(context).enqueue(request)
                Outcome.success(request.id.toString())
            } catch (e: IllegalStateException) {
                Outcome.failure(
                    CulinaryError.Unexpected(
                        message = "cannot enqueue backup work",
                        cause = e,
                    ),
                )
            }

        companion object {
            const val PERIODIC_WORK_NAME: String = "culinary-auto-backup"
            const val PERIODIC_INTERVAL_HOURS: Long = 24
        }
    }
