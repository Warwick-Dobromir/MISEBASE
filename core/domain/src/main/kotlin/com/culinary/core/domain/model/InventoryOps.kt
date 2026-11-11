package com.culinary.core.domain.model

import java.time.Instant

/** 冻结动作（v9）。 */
enum class FreezeAction {
    FREEZE,
    UNFREEZE,
}

/** 冻结/解冻记录，追加式留痕。 */
data class FreezeRecord(
    val id: String,
    val batchId: String,
    val action: FreezeAction,
    val reason: String,
    val operator: String? = null,
    val occurredAt: Instant,
)

/** 库位转移记录：不新建批次，不改变总量。 */
data class LocationTransferRecord(
    val id: String,
    val batchId: String,
    val fromLocationName: String?,
    val toLocationName: String?,
    val quantity: Double,
    val note: String? = null,
    val occurredAt: Instant,
)

/** 库位树节点。 */
data class StorageLocationNode(
    val location: StorageLocation,
    val depth: Int = 1,
    val childCount: Int = 0,
    val batchCount: Int = 0,
) {
    val pathLabel: String get() = location.name
}

/** 临时库存状态：正常 / 提醒（≥15 天）/ 待处理（≥20 天）。 */
enum class TemporaryStatus {
    NORMAL,
    REMIND,
    PENDING,
}

/** 临时库存行；15 天提醒、20 天待处理，不自动删除。 */
data class TemporaryStockRow(
    val batch: InventoryBatchRecord,
    val days: Long,
    val status: TemporaryStatus,
)

/** 回收站行：剩余保留天数，30 天后由启动维护清理。 */
data class RecycleRow(
    val batch: InventoryBatchRecord,
    val daysInRecycle: Long,
    val daysLeft: Long,
)

/** 打印类型：入库单 / 出库单 / 批次标签。 */
enum class PrintKind {
    RECEIPT,
    ISSUE,
    LABEL,
}

/** 打印状态；失败可重打，不影响库存。 */
enum class PrintStatus {
    PENDING,
    PRINTED,
    FAILED,
}

/** 打印任务（v9）。 */
data class PrintJobRecord(
    val id: String,
    val kind: PrintKind,
    val refType: String,
    val refId: String,
    val status: PrintStatus,
    val attempts: Int = 0,
    val reprintCount: Int = 0,
    val lastError: String? = null,
    val paperWidthMm: Int = 80,
    val createdAt: Instant,
    val printedAt: Instant? = null,
) {
    val needsReprint: Boolean get() = status == PrintStatus.FAILED
}

/** 盘点范围。 */
enum class StocktakeScope {
    ALL,
    CATEGORY,
    LOCATION,
    ARCHIVE,
}

/** 盘点状态：草稿（报告）→ 已确认（调整生效）。 */
enum class StocktakeStatus {
    DRAFT,
    CONFIRMED,
}

/** 盘点行：账面、实盘、差异与冻结标记。 */
data class StocktakeLine(
    val id: String,
    val lineNo: Int,
    val batchId: String,
    val archiveId: String,
    val archiveName: String,
    val batchCode: String? = null,
    val locationName: String? = null,
    val bookQty: Double,
    val countedQty: Double,
    val unitSymbol: String,
    val frozen: Boolean = false,
) {
    val diff: Double get() = countedQty - bookQty
    val hasDiff: Boolean get() = kotlin.math.abs(diff) > 1e-9
}

/** 盘点单：报告 + 差异。 */
data class Stocktake(
    val id: String,
    val scope: StocktakeScope,
    val scopeLabel: String,
    val status: StocktakeStatus,
    val note: String? = null,
    val createdAt: Instant,
    val confirmedAt: Instant? = null,
    val lines: List<StocktakeLine> = emptyList(),
) {
    val diffCount: Int get() = lines.count { it.hasDiff }
}

/** 库存规则（v9）：FIFO 固定生效，其余为天数阈值，读取即影响业务。 */
data class InventoryRules(
    val fifo: Boolean = true,
    val temporaryRemindDays: Int = 15,
    val temporaryPendingDays: Int = 20,
    val recycleRetentionDays: Int = 30,
) {
    init {
        require(temporaryRemindDays in 1..3650) { "temporaryRemindDays out of range" }
        require(temporaryPendingDays >= temporaryRemindDays) { "pending days must be >= remind days" }
        require(recycleRetentionDays in 1..3650) { "recycleRetentionDays out of range" }
    }
}

/** 库存首页"最近记录"一行。 */
data class RecentInventoryActivity(
    val ledgerId: String,
    val occurredAt: Instant,
    val typeLabel: String,
    val archiveName: String,
    val delta: Double,
    val unitSymbol: String,
    val printFailed: Boolean = false,
)
