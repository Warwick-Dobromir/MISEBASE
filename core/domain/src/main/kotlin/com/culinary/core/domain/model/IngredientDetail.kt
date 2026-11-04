package com.culinary.core.domain.model

/**
 * 食材详情投影：档案 + 默认单位 + 已确认的食材级换算。
 *
 * UI 只读这个投影；单位对象在用例内解析，页面不直接访问 Repository。
 */
data class IngredientDetail(
    val ingredient: Ingredient,
    val defaultUnit: Unit?,
    val conversions: List<IngredientConversionView>,
)

/** 一条换算依据；缺少任一单位时不猜数，保留 id 供诊断。 */
data class IngredientConversionView(
    val fromUnitId: UnitId,
    val toUnitId: UnitId,
    val fromUnit: Unit?,
    val toUnit: Unit?,
    val factor: Double,
    val note: String?,
)
