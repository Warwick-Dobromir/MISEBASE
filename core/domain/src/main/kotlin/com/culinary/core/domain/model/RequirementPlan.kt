package com.culinary.core.domain.model

import java.time.Instant

/** 需求预览的一行：需求、可用、缺口与建议商品。 */
data class RequirementPreviewLine(
    val ingredientId: IngredientId,
    val ingredientName: String,
    val required: Quantity,
    val available: Quantity,
    val shortfall: Quantity,
    val isOptional: Boolean,
    val productId: ProductId? = null,
    val productName: String? = null,
) {
    init {
        require(required.unit.id == available.unit.id) { "required and available must share a unit" }
        require(shortfall.unit.id == required.unit.id) { "shortfall must use the requirement unit" }
    }

    val isCovered: Boolean get() = shortfall.amount <= 0.0
}

/**
 * 菜谱需求计划（Stage 4）：展开/汇总/缺口/采购清单的计算结果。
 *
 * [record] 是带稳定 payload hash 的确认前快照；用户确认扣减或生成清单时
 * 才由用例写入 `calculation_record`。
 */
data class RequirementPlan(
    val recipeVersionId: RecipeVersionId,
    val recipeName: String,
    val baseServings: Int,
    val targetServings: Double,
    val requiredLines: List<RequirementPreviewLine>,
    val optionalLines: List<RequirementPreviewLine>,
    val purchaseList: PurchaseList,
    val record: CalculationRecord,
    val generatedAt: Instant,
) {
    val hasRequiredShortfall: Boolean get() = requiredLines.any { !it.isCovered }
    val hasOptionalShortfall: Boolean get() = optionalLines.any { !it.isCovered }

    /** 计算记录中的需求行（含可选标记，按食材 + 单位唯一）。 */
    val recordLines: List<RequirementLine> get() = record.lines
}

/** 生成采购清单的结果：清单 + 已确认记录。 */
data class PurchaseListResult(
    val plan: RequirementPlan,
    val record: CalculationRecord,
)

/** 确认扣减的结果：计划、确认记录与实际写入的流水数。 */
data class RequirementDeductionResult(
    val plan: RequirementPlan,
    val record: CalculationRecord,
    val appliedEntryCount: Int,
)
