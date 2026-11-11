package com.culinary.core.domain.model

import java.time.Instant
import java.time.LocalDate

/** 出库来源（v9）。 */
enum class IssueType {
    MANUAL,
    RECIPE,
    SAUCE,
}

/** 出库明细角色：菜谱直接食材 / 酱汁现货 / 酱汁缺口展开的基础食材。 */
enum class IssueRole {
    INGREDIENT,
    SAUCE,
    SAUCE_GAP,
}

/** 单批次分配：FIFO 或手动指定，均带批次与库位便于打印与追溯。 */
data class BatchAllocation(
    val batchId: String,
    val batchCode: String? = null,
    val locationId: LocationId,
    val locationName: String? = null,
    val quantity: Double,
    val expiresOn: LocalDate? = null,
)

/** 出库计划行：一个档案一行，附批次分配；不足时 `shortfall > 0`。 */
data class IssuePlanLine(
    val lineNo: Int,
    val archiveId: String,
    val archiveName: String,
    val role: IssueRole,
    val unit: Unit,
    val required: Double,
    val allocations: List<BatchAllocation> = emptyList(),
    val available: Double = allocations.sumOf { it.quantity },
) {
    val allocated: Double get() = allocations.sumOf { it.quantity }
    val shortfall: Double get() = (required - allocated).coerceAtLeast(0.0)
    val isCovered: Boolean get() = shortfall <= EPSILON

    companion object {
        const val EPSILON = 1e-9
    }
}

/** 酱汁缺口：现货不足时按最小制作容量取整后的制作量与展开出的原料。 */
data class SauceGap(
    val sauceName: String,
    val required: Double,
    val spot: Double,
    val gap: Double,
    val productionQty: Double,
    val minimumBatch: Double?,
    val unit: Unit,
) {
    val roundedUp: Boolean get() = productionQty > gap + 1e-9
}

/** 出库计划：屏幕只展示前两项，但这里保存全部物料与批次分配。 */
data class IssuePlan(
    val type: IssueType,
    val lines: List<IssuePlanLine>,
    val sauceGaps: List<SauceGap> = emptyList(),
    val recipeVersionId: String? = null,
    val recipeName: String? = null,
) {
    val shortfalls: List<IssuePlanLine> get() = lines.filter { !it.isCovered }
    val isCovered: Boolean get() = shortfalls.isEmpty()
    val totalLines: Int get() = lines.size
}

/** 出库单（落库后）。 */
data class IssueSlip(
    val id: String,
    val slipNo: String,
    val type: IssueType,
    val recipeName: String? = null,
    val occurredAt: Instant,
    val note: String? = null,
    val lines: List<IssueSlipLine> = emptyList(),
    val printJob: PrintJobRecord? = null,
) {
    val totalLines: Int get() = lines.size
}

/** 出库单明细（逐批次）。 */
data class IssueSlipLine(
    val lineNo: Int,
    val archiveName: String,
    val batchCode: String? = null,
    val locationName: String? = null,
    val quantity: Double,
    val unitSymbol: String,
    val role: IssueRole = IssueRole.INGREDIENT,
)

/** 确认出库结果：单据、写入的流水数与打印任务。 */
data class IssueResult(
    val slip: IssueSlip,
    val ledgerEntries: Int,
    val printJob: PrintJobRecord? = null,
    val idempotentReplay: Boolean = false,
)
