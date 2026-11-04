package com.culinary.core.domain.rule.inventory

import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.PurchaseList
import com.culinary.core.domain.model.PurchaseListLine
import com.culinary.core.domain.model.ShortfallLine
import java.math.BigDecimal
import java.time.Instant

/**
 * 采购清单生成（MB-D-016）。
 *
 * - 只取"有缺口且非可选"的行：`shortfall > 0`、`isOptional = false`；
 * - **不落** `purchase_order*` 表，不引用任何采购单实体；
 * - 输出按食材 ID、单位稳定排序，便于展示与导出比对。
 */
class PurchaseListBuilder {
    fun build(
        shortfalls: List<ShortfallLine>,
        generatedAt: Instant,
        productFor: (IngredientId) -> ProductId? = { null },
    ): PurchaseList {
        val lines =
            shortfalls
                .asSequence()
                .filter { !it.isOptional && it.shortfall.amount > EPSILON }
                .map { shortfall ->
                    PurchaseListLine(
                        ingredientId = shortfall.ingredientId,
                        required = shortfall.required,
                        available = shortfall.available,
                        toPurchase = shortfall.shortfall,
                        productId = productFor(shortfall.ingredientId),
                    )
                }.sortedWith(compareBy({ it.ingredientId.value }, { it.required.unit.id.value }))
                .toList()
        return PurchaseList(generatedAt = generatedAt, lines = lines)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}

/** 采购清单导出契约：由 Inventory 提供实现，不依赖 Android。 */
fun interface PurchaseListExporter {
    fun export(list: PurchaseList): String
}

/**
 * CSV 导出（UTF-8，`\n` 换行）。
 *
 * 列固定为 `ingredient_id,product_id,required,available,to_purchase,unit`；
 * 数值使用十进制字符串，避免科学计数法与区域设置差异。
 */
class CsvPurchaseListExporter : PurchaseListExporter {
    override fun export(list: PurchaseList): String =
        buildString {
            append(HEADER).append('\n')
            list.lines.forEach { line ->
                append(line.ingredientId.value).append(',')
                append(line.productId?.value.orEmpty()).append(',')
                append(decimal(line.required.amount)).append(',')
                append(decimal(line.available.amount)).append(',')
                append(decimal(line.toPurchase.amount)).append(',')
                append(line.required.unit.code).append('\n')
            }
        }

    private fun decimal(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private companion object {
        const val HEADER = "ingredient_id,product_id,required,available,to_purchase,unit"
    }
}
