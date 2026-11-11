package com.culinary.core.domain.model

import java.time.Instant
import java.time.LocalDate

/** 新建批次（v9）：余额恒为 0，数量只由随后的入库流水增加。 */
data class NewArchiveBatch(
    val archiveId: String,
    val productId: String? = null,
    val locationId: LocationId,
    val batchCode: String? = null,
    val receivedAt: Instant,
    val productionDate: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val stockUnit: Unit,
    val isTemporary: Boolean = false,
)

/** 出库单明细输入（逐批次）。 */
data class NewIssueSlipLine(
    val lineNo: Int,
    val archiveId: String,
    val archiveName: String,
    val batchId: String,
    val batchCode: String? = null,
    val locationId: LocationId,
    val locationName: String? = null,
    val quantity: Double,
    val unitId: String,
    val unitSymbol: String,
    val role: IssueRole = IssueRole.INGREDIENT,
)

/** 出库单输入；`idempotencyKey` 由单据号或动作 ID 决定，重复提交命中重放。 */
data class NewIssueSlip(
    val slipNo: String,
    val type: IssueType,
    val occurredAt: Instant,
    val idempotencyKey: String,
    val recipeVersionId: String? = null,
    val recipeName: String? = null,
    val note: String? = null,
    val operator: String? = null,
    val lines: List<NewIssueSlipLine>,
)

/** 盘点建单输入：快照当前在库批次作为账面数。 */
data class NewStocktake(
    val scope: StocktakeScope,
    val scopeRef: String? = null,
    val scopeLabel: String,
    val note: String? = null,
    val createdAt: Instant,
    val lines: List<NewStocktakeLine>,
)

/** 盘点行输入。 */
data class NewStocktakeLine(
    val lineNo: Int,
    val batchId: String,
    val archiveId: String,
    val archiveName: String,
    val batchCode: String? = null,
    val locationName: String? = null,
    val bookQty: Double,
    val unitId: String,
    val unitSymbol: String,
    val frozen: Boolean = false,
)
