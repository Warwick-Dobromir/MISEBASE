package com.culinary.feature.inventory.print

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.print.PrintJob
import android.print.PrintManager
import android.util.Log

/**
 * 库存单据打印入口：把排版计划交给系统打印框架。
 *
 * 打印是附加能力，不是库存的一部分：任何失败都只返回 [PrintResult.Unavailable]，绝不抛异常、
 * 也不触碰库存数据；调用方据此把打印任务标记为 FAILED（`PrintStatus.FAILED`，可重打）。
 *
 * 不申请任何权限，也不使用 INTERNET：打印完全通过系统 `PrintManager` 与已安装的打印服务插件完成。
 *
 * @param context 打印上下文；需能解析到 Activity（见 [print]）
 */
class InventoryPrintService(
    private val context: Context,
) {
    private val tag: String = "${context.packageName}.print"

    /**
     * 启动一次打印。
     *
     * 平台实现要求 `PrintManager.print` 由 Activity 上下文发起（否则抛 `IllegalStateException`），
     * 因此这里先解开 [ContextWrapper] 链找到 Activity；找不到就按"不可用"返回。
     *
     * @param jobName 打印任务名，显示在系统打印队列；为空时回退到固定名称
     * @param plan 排版计划（纸宽、纸高、逻辑行）
     * @return [PrintResult.Started] 系统已接受任务（后续仍可能失败并被标记 FAILED）；
     *   [PrintResult.Unavailable] 当前设备/上下文无法打印，调用方按失败处理
     */
    fun print(
        jobName: String,
        plan: SlipRenderPlan,
    ): PrintResult {
        val name = jobName.trim().ifEmpty { DEFAULT_JOB_NAME }
        if (plan.lines.isEmpty()) {
            return PrintResult.Unavailable("没有可打印的内容")
        }
        val activity =
            context.findActivity()
                ?: return PrintResult.Unavailable("打印需要页面上下文（Activity），当前上下文不可用")
        val printManager =
            activity.getSystemService(PrintManager::class.java)
                ?: return PrintResult.Unavailable("设备没有可用的打印服务")
        return try {
            val adapter = InventoryDocumentAdapter(activity, plan, name)
            // 声明为可空：平台在没有打印服务插件时会返回 null（源码标 @NonNull，但实际可能为空）。
            val job: PrintJob? = printManager.print(name, adapter, InventoryDocumentAdapter.attributesFor(plan))
            if (job == null) {
                PrintResult.Unavailable("系统未创建打印任务（可能缺少打印服务插件）")
            } else {
                PrintResult.Started(name)
            }
        } catch (error: RuntimeException) {
            Log.w(tag, "[$name] 启动打印失败", error)
            PrintResult.Unavailable(error.message ?: "启动打印失败")
        }
    }

    /** 沿 [ContextWrapper] 链找到 Activity；超过深度上限按不可用处理，避免异常链导致死循环。 */
    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        var depth = 0
        while (current != null && depth < MAX_WRAPPER_DEPTH) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
            depth++
        }
        return null
    }

    private companion object {
        private const val DEFAULT_JOB_NAME = "库存单据"
        private const val MAX_WRAPPER_DEPTH = 8
    }
}

/** 打印启动结果。 */
sealed interface PrintResult {
    /** 系统已接受任务；真实结果（已打印 / 失败）由打印服务异步给出。 */
    data class Started(
        val jobName: String,
    ) : PrintResult

    /** 当前设备/上下文无法打印；调用方把打印任务标记 FAILED，库存保持不变。 */
    data class Unavailable(
        val reason: String,
    ) : PrintResult
}
