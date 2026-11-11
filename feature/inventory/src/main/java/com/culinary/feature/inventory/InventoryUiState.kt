package com.culinary.feature.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.InventoryBatchSummary
import com.culinary.core.domain.model.InventoryFormOptions
import com.culinary.core.domain.model.InventoryLedgerRow
import com.culinary.core.domain.model.InventoryOverviewRow
import com.culinary.core.domain.model.InventoryProductDetail
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.RequirementPlan
import com.culinary.core.domain.usecase.inventory.StockIssueReason
import com.culinary.core.domain.usecase.recipe.RecipeOption
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 总览：搜索与"仅看缺货"在已加载行上过滤。 */
data class InventoryOverviewUiState(
    val rows: List<InventoryOverviewRow> = emptyList(),
    val query: String = "",
    val onlyLowStock: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val visibleRows: List<InventoryOverviewRow>
        get() =
            rows.filter { row ->
                val matchesQuery =
                    query.isBlank() ||
                        row.productName.contains(query, ignoreCase = true) ||
                        row.ingredientName.contains(query, ignoreCase = true) ||
                        row.locationNames.any { it.contains(query, ignoreCase = true) }
                matchesQuery && (!onlyLowStock || row.available <= 0.0)
            }
}

/** 商品详情：批次 + 流水 + 关联菜谱。 */
data class InventoryDetailUiState(
    val detail: InventoryProductDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

enum class StockMovementMode {
    RECEIPT,
    ISSUE,
    ADJUST,
}

enum class StockSource {
    PRODUCT,
    INGREDIENT,
}

/** 入库/出库/调整共用的表单状态。 */
data class StockMovementUiState(
    val mode: StockMovementMode = StockMovementMode.RECEIPT,
    val options: InventoryFormOptions = InventoryFormOptions(emptyList(), emptyList(), emptyList()),
    val source: StockSource = StockSource.PRODUCT,
    val selectedProductId: ProductId? = null,
    val selectedIngredientId: String? = null,
    val selectedBatchId: String? = null,
    val quantity: String = "",
    val unitCode: String = "g",
    val reason: StockIssueReason = StockIssueReason.CONSUMED,
    val note: String = "",
    val batchCode: String = "",
    val expiryDate: String = "",
    val locationId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val selectedBatch: InventoryBatchSummary?
        get() = options.batches.firstOrNull { it.item.batchId.value == selectedBatchId }

    val selectedProductName: String?
        get() = options.products.firstOrNull { it.id == selectedProductId }?.name

    val selectedBatchLabel: String?
        get() {
            val batch = selectedBatch ?: return null
            val product = options.products.firstOrNull { it.id == batch.item.productId }?.name ?: "库存批次"
            val code =
                batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.item.batchId.value
                    .take(8)
            return "$product · $code · ${formatAmount(batch.item.available)} ${batch.item.stockUnit.symbol}"
        }
}

enum class LedgerTypeFilter {
    ALL,
    RECEIPT,
    ISSUE,
    ADJUSTMENT,
}

/** 流水：时间、类型、变化、前后余额与来源。 */
data class InventoryLedgerUiState(
    val rows: List<InventoryLedgerRow> = emptyList(),
    val query: String = "",
    val typeFilter: LedgerTypeFilter = LedgerTypeFilter.ALL,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val visibleRows: List<InventoryLedgerRow>
        get() =
            rows.filter { row ->
                val typeMatches =
                    when (typeFilter) {
                        LedgerTypeFilter.ALL -> true
                        LedgerTypeFilter.RECEIPT -> row.entry.type == LedgerEntryType.PURCHASE_RECEIPT
                        LedgerTypeFilter.ISSUE ->
                            row.entry.type == LedgerEntryType.PRODUCTION_ISSUE ||
                                row.entry.type == LedgerEntryType.WASTE
                        LedgerTypeFilter.ADJUSTMENT ->
                            row.entry.type == LedgerEntryType.ADJUSTMENT_IN ||
                                row.entry.type == LedgerEntryType.ADJUSTMENT_OUT
                    }
                val queryMatches =
                    query.isBlank() ||
                        row.productName.contains(query, ignoreCase = true) ||
                        row.ingredientName.contains(query, ignoreCase = true) ||
                        row.locationName.contains(query, ignoreCase = true) ||
                        row.entry.referenceId
                            .orEmpty()
                            .contains(query, ignoreCase = true)
                typeMatches && queryMatches
            }
}

/** 需求计划、采购清单与计算记录回放。 */
data class RequirementPlanUiState(
    val recipes: List<RecipeOption> = emptyList(),
    val selectedRecipeId: String? = null,
    val selectedVersionId: RecipeVersionId? = null,
    val servings: String = "",
    val plan: RequirementPlan? = null,
    val records: List<CalculationRecord> = emptyList(),
    val ingredientNames: Map<String, String> = emptyMap(),
    val replayedRecord: CalculationRecord? = null,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val csv: String? = null,
) {
    val selectedRecipeName: String?
        get() = recipes.firstOrNull { it.recipe.id.value == selectedRecipeId }?.recipe?.name
}

object InventoryErrorMessages {
    fun describe(error: CulinaryError): String = "${friendly(error)}（${error.code.value}）"

    @Suppress("CyclomaticComplexMethod") // 错误类型与字段组合是稳定的用户文案映射，穷举比表驱动更可读。
    private fun friendly(error: CulinaryError): String =
        when (error) {
            is CulinaryError.Validation ->
                when {
                    error.field == "quantity" || error.field == "countedQuantity" -> "数量必须是有效的非负数字。"
                    error.field == "servings" -> "目标份数必须是大于 0 的数字。"
                    error.field == "productId" -> "请选择商品或食材。"
                    error.rule.contains("reserved") -> "实盘数量不能低于已预留数量。"
                    else -> "输入不合法，请检查后重试。"
                }

            is CulinaryError.Domain ->
                when (val detail = error.detail) {
                    is DomainDetail.InsufficientStock ->
                        "库存不足：需要 ${formatAmount(detail.requested)}，可用 ${formatAmount(detail.available)}。"
                    is DomainDetail.UnitIncompatible -> "单位不可换算，请补充食材换算或改用库存单位。"
                    is DomainDetail.RecipeCycle -> "配方存在循环引用，无法计算。"
                    is DomainDetail.InvalidState -> "当前状态不允许该操作。"
                }

            is CulinaryError.NotFound -> "记录不存在或已被删除。"
            is CulinaryError.Conflict -> "数据已变化，请刷新后重试。"
            else -> "操作失败，请重试。"
        }
}

fun formatAmount(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

fun formatInstant(value: Instant): String =
    DateTimeFormatter
        .ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(value)

fun formatDate(value: LocalDate?): String = value?.toString().orEmpty()

fun parseDate(value: String): LocalDate? =
    value
        .trim()
        .takeIf {
            it.isNotEmpty()
        }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun ledgerTypeLabel(type: LedgerEntryType): String =
    when (type) {
        LedgerEntryType.PURCHASE_RECEIPT -> "入库"
        LedgerEntryType.PRODUCTION_ISSUE -> "领用"
        LedgerEntryType.RESERVE -> "预留"
        LedgerEntryType.RELEASE -> "释放"
        LedgerEntryType.ADJUSTMENT_IN -> "盘盈"
        LedgerEntryType.ADJUSTMENT_OUT -> "盘亏"
        LedgerEntryType.WASTE -> "损耗"
        LedgerEntryType.TRANSFER_OUT -> "调出"
        LedgerEntryType.TRANSFER_IN -> "调入"
        LedgerEntryType.RETURN -> "退货"
        LedgerEntryType.VOID_RECEIPT -> "冲销"
        LedgerEntryType.FREEZE -> "冻结"
        LedgerEntryType.UNFREEZE -> "解冻"
        LedgerEntryType.LOCATION_TRANSFER -> "库位转移"
    }

fun issueReasonLabel(reason: StockIssueReason): String =
    when (reason) {
        StockIssueReason.CONSUMED -> "领用"
        StockIssueReason.WASTE -> "损耗"
        StockIssueReason.OTHER -> "其他"
    }
