package com.culinary.core.platform.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 自动备份 Worker 骨架。
 *
 * 当前只验证调度链路（入队/取消/状态），真正的备份编排（写屏障 + checkpoint +
 * 归档 + E05 校验）在阶段 9 接入；这里不会伪造"备份成功"的业务结果。
 */
class AutoBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        // TODO(G2-S9, R01 §9): 接入 E05 自动备份编排后再返回真实结果
        return Result.success()
    }
}
