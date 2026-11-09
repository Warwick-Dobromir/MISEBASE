package com.culinary.cooking

import com.culinary.core.domain.model.KitchenStep
import com.culinary.core.common.KitchenTime

/**
 * 制作模式的一步：重新编号后的制作流程步骤。
 *
 * [originalStepNo] 是档案中的原始步骤序号（1 起），用于返回详情页定位；
 * [number] 是制作模式内部编号（1 起），进度、弹窗、结束状态共用这一套编号。
 */
data class CookingPlanStep(
    val step: KitchenStep,
    val originalStepNo: Int,
    val number: Int,
    val total: Int,
) {
    val hasTime: Boolean get() = step.timeSeconds > 0
}

/**
 * 制作流程构建（任务书第八阶段）。
 *
 * 规则：
 * 1. 从被点击的步骤开始，忽略它之前的步骤；
 * 2. 一直纳入到"最后一个有有效时间的步骤"，其后的无时间步骤不计入；
 * 3. 中间的无时间步骤必须纳入并参与进度计算；
 * 4. 重新建立 01..N 的连续编号，不使用原始档案编号。
 */
object CookingPlan {
    fun build(
        steps: List<KitchenStep>,
        startIndex: Int,
    ): List<CookingPlanStep> {
        if (steps.isEmpty()) return emptyList()
        val lastTimed = steps.indexOfLast { it.timeSeconds > 0 }
        if (lastTimed < 0) return emptyList()
        val start = startIndex.coerceAtLeast(0)
        if (start > lastTimed) return emptyList()
        val included = steps.subList(start, lastTimed + 1)
        val total = included.size
        return included.mapIndexed { offset, step ->
            CookingPlanStep(
                step = step,
                originalStepNo = start + offset + 1,
                number = offset + 1,
                total = total,
            )
        }
    }

    /** 进度：以整个制作流程为基准，中间无时间步骤同样计入。 */
    fun progress(number: Int, total: Int): Float {
        if (total <= 0) return 0f
        return (number.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** 预设时间的可读文案：`预设时间 · 30分钟`。 */
    fun presetLabel(seconds: Int): String =
        if (seconds > 0) "预设时间 · " + KitchenTime.describe(seconds) else "无预设时间"
}
