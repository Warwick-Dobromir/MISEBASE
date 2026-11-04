package com.culinary.core.domain.rule.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.rule.unit.UnitConverter

/** 展开后的一条食材需求（仍保留原单位，汇总阶段再统一）。 */
data class ExplodedRequirement(
    val ingredientId: IngredientId,
    val quantity: Quantity,
    val isOptional: Boolean,
)

/**
 * 配方展开（A06 §4.7 / R01 阶段 8）。
 *
 * - 以根版本产量为基准，按 [multiplier] 缩放；
 * - 子配方按"父级引用量 ÷ 子版本产量"换算后再递归；
 * - 默认排除可选行，[includeOptional] 可显式包含；
 * - 缺失版本返回 `CUL-NF-001`，成环返回 `CUL-REP-001`，单位不可换算返回 `CUL-UNIT-001`；
 * - 不在本类做单位归一或合并，交由需求汇总处理。
 */
class RecipeExploder(
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    fun explode(
        rootVersionId: RecipeVersionId,
        source: RecipeGraphSource,
        multiplier: Double = 1.0,
        includeOptional: Boolean = false,
    ): Outcome<List<ExplodedRequirement>> {
        if (!multiplier.isFinite() || multiplier <= 0.0) {
            return Outcome.failure(
                CulinaryError.Validation(
                    field = "multiplier",
                    rule = "must be a finite positive number",
                ),
            )
        }

        val requirements = mutableListOf<ExplodedRequirement>()
        val error =
            expand(
                versionId = rootVersionId,
                factor = multiplier,
                source = source,
                includeOptional = includeOptional,
                requirements = requirements,
                visiting = LinkedHashSet(),
            )
        return if (error == null) Outcome.success(requirements) else Outcome.failure(error)
    }

    /**
     * 按"份数"展开：以根版本标注的 `servings` 为基准，倍数 = 目标份数 / 基准份数。
     *
     * 根版本缺失时返回 `CUL-NF-001`；份数必须为正（[Servings] 已保证）。
     */
    fun explodeForServings(
        rootVersionId: RecipeVersionId,
        servings: Servings,
        source: RecipeGraphSource,
        includeOptional: Boolean = false,
    ): Outcome<List<ExplodedRequirement>> {
        val root =
            source.findVersion(rootVersionId)
                ?: return Outcome.failure(
                    CulinaryError.NotFound(entity = "recipe_version", id = rootVersionId.value),
                )
        return explode(
            rootVersionId = rootVersionId,
            source = source,
            multiplier = servings.scaleFactor(Servings(root.servings.toDouble())),
            includeOptional = includeOptional,
        )
    }

    private fun expand(
        versionId: RecipeVersionId,
        factor: Double,
        source: RecipeGraphSource,
        includeOptional: Boolean,
        requirements: MutableList<ExplodedRequirement>,
        visiting: LinkedHashSet<RecipeVersionId>,
    ): CulinaryError? {
        if (!visiting.add(versionId)) {
            return cycleError(visiting + versionId)
        }
        val version =
            source.findVersion(versionId)
                ?: return CulinaryError.NotFound(entity = "recipe_version", id = versionId.value)

        for (component in version.components) {
            if (component.isOptional && !includeOptional) continue
            val scaled = Quantity(component.quantity.amount * factor, component.quantity.unit)
            val error =
                when (val target = component.target) {
                    is ComponentTarget.Ingredient -> {
                        requirements += ExplodedRequirement(target.id, scaled, component.isOptional)
                        null
                    }
                    is ComponentTarget.Recipe ->
                        expandChild(
                            target = target,
                            scaled = scaled,
                            source = source,
                            includeOptional = includeOptional,
                            requirements = requirements,
                            visiting = visiting,
                        )
                }
            if (error != null) {
                visiting -= versionId
                return error
            }
        }

        visiting -= versionId
        return null
    }

    private fun expandChild(
        target: ComponentTarget.Recipe,
        scaled: Quantity,
        source: RecipeGraphSource,
        includeOptional: Boolean,
        requirements: MutableList<ExplodedRequirement>,
        visiting: LinkedHashSet<RecipeVersionId>,
    ): CulinaryError? {
        val child =
            source.findVersion(target.versionId)
                ?: return CulinaryError.NotFound(entity = "recipe_version", id = target.versionId.value)

        val converted =
            when (val result = unitConverter.convert(scaled, child.yield.unit)) {
                is Outcome.Failure -> return result.error
                is Outcome.Success -> result.value
            }
        val childFactor = converted.amount / child.yield.amount
        return expand(
            versionId = target.versionId,
            factor = childFactor,
            source = source,
            includeOptional = includeOptional,
            requirements = requirements,
            visiting = visiting,
        )
    }

    private fun cycleError(visiting: Set<RecipeVersionId>): CulinaryError =
        CulinaryError.Domain(
            detail = DomainDetail.RecipeCycle(visiting.map { it.value }),
            code = ErrorCode.REP_001,
        )
}
