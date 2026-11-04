package com.culinary.core.domain.rule.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.rule.unit.UnitConverter

/** 展开后的一条食材需求（仍保留原单位，汇总阶段再统一）。 */
data class ExplodedRequirement(
    val ingredientId: IngredientId,
    val quantity: Quantity,
    val isOptional: Boolean,
)

/**
 * 配方展开（A06 §4.7 / R01 阶段 8；Stage 4 补酱汁图）。
 *
 * - 以根版本产量为基准，按 [multiplier] 缩放；
 * - 子配方按"父级引用量 ÷ 子版本产量"换算后再递归；
 * - 子酱汁同样按"引用量 ÷ 酱汁产量"换算，并递归展开其食材、子菜谱与子酱汁；
 * - 可选标记沿引用路径传播：任一祖先为可选，则叶节点需求为可选；
 * - 默认排除可选行，[includeOptional] 可显式包含；
 * - 缺失版本返回 `CUL-NF-001`，配方/酱汁成环返回 `CUL-REP-001`，
 *   单位不可换算返回 `CUL-UNIT-001`；
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
            expandRecipe(
                versionId = rootVersionId,
                factor = multiplier,
                source = source,
                includeOptional = includeOptional,
                optionalPath = false,
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

    private fun expandRecipe(
        versionId: RecipeVersionId,
        factor: Double,
        source: RecipeGraphSource,
        includeOptional: Boolean,
        optionalPath: Boolean,
        requirements: MutableList<ExplodedRequirement>,
        visiting: LinkedHashSet<GraphNode>,
    ): CulinaryError? {
        val node = GraphNode.Recipe(versionId)
        if (!visiting.add(node)) {
            return cycleError(visiting + node)
        }
        val version =
            source.findVersion(versionId)
                ?: return CulinaryError.NotFound(entity = "recipe_version", id = versionId.value)

        for (component in version.components) {
            if (component.isOptional && !includeOptional) continue
            val componentOptional = optionalPath || component.isOptional
            val scaled = Quantity(component.quantity.amount * factor, component.quantity.unit)
            val error =
                when (val target = component.target) {
                    is ComponentTarget.Ingredient -> {
                        requirements +=
                            ExplodedRequirement(target.id, scaled, componentOptional)
                        null
                    }

                    is ComponentTarget.Recipe ->
                        expandChildRecipe(
                            target = target,
                            scaled = scaled,
                            source = source,
                            includeOptional = includeOptional,
                            optionalPath = componentOptional,
                            requirements = requirements,
                            visiting = visiting,
                        )

                    is ComponentTarget.Sauce ->
                        expandChildSauce(
                            target = target,
                            scaled = scaled,
                            source = source,
                            includeOptional = includeOptional,
                            optionalPath = componentOptional,
                            requirements = requirements,
                            visiting = visiting,
                        )
                }
            if (error != null) {
                visiting -= node
                return error
            }
        }

        visiting -= node
        return null
    }

    private fun expandSauce(
        versionId: SauceVersionId,
        factor: Double,
        source: RecipeGraphSource,
        includeOptional: Boolean,
        optionalPath: Boolean,
        requirements: MutableList<ExplodedRequirement>,
        visiting: LinkedHashSet<GraphNode>,
    ): CulinaryError? {
        val node = GraphNode.Sauce(versionId)
        if (!visiting.add(node)) {
            return cycleError(visiting + node)
        }
        val version =
            source.findSauceVersion(versionId)
                ?: return CulinaryError.NotFound(entity = "sauce_version", id = versionId.value)

        for (component in version.components) {
            if (component.isOptional && !includeOptional) continue
            val componentOptional = optionalPath || component.isOptional
            val scaled = Quantity(component.quantity.amount * factor, component.quantity.unit)
            val error =
                when (val target = component.target) {
                    is ComponentTarget.Ingredient -> {
                        requirements +=
                            ExplodedRequirement(target.id, scaled, componentOptional)
                        null
                    }

                    is ComponentTarget.Recipe ->
                        expandChildRecipe(
                            target = target,
                            scaled = scaled,
                            source = source,
                            includeOptional = includeOptional,
                            optionalPath = componentOptional,
                            requirements = requirements,
                            visiting = visiting,
                        )

                    is ComponentTarget.Sauce ->
                        expandChildSauce(
                            target = target,
                            scaled = scaled,
                            source = source,
                            includeOptional = includeOptional,
                            optionalPath = componentOptional,
                            requirements = requirements,
                            visiting = visiting,
                        )
                }
            if (error != null) {
                visiting -= node
                return error
            }
        }

        visiting -= node
        return null
    }

    private fun expandChildRecipe(
        target: ComponentTarget.Recipe,
        scaled: Quantity,
        source: RecipeGraphSource,
        includeOptional: Boolean,
        optionalPath: Boolean,
        requirements: MutableList<ExplodedRequirement>,
        visiting: LinkedHashSet<GraphNode>,
    ): CulinaryError? {
        val child =
            source.findVersion(target.versionId)
                ?: return CulinaryError.NotFound(entity = "recipe_version", id = target.versionId.value)
        return recurseWithYield(
            scaled = scaled,
            yield = child.yield,
            expand = { factor ->
                expandRecipe(
                    versionId = target.versionId,
                    factor = factor,
                    source = source,
                    includeOptional = includeOptional,
                    optionalPath = optionalPath,
                    requirements = requirements,
                    visiting = visiting,
                )
            },
        )
    }

    private fun expandChildSauce(
        target: ComponentTarget.Sauce,
        scaled: Quantity,
        source: RecipeGraphSource,
        includeOptional: Boolean,
        optionalPath: Boolean,
        requirements: MutableList<ExplodedRequirement>,
        visiting: LinkedHashSet<GraphNode>,
    ): CulinaryError? {
        val child =
            source.findSauceVersion(target.versionId)
                ?: return CulinaryError.NotFound(entity = "sauce_version", id = target.versionId.value)
        return recurseWithYield(
            scaled = scaled,
            yield = child.yield,
            expand = { factor ->
                expandSauce(
                    versionId = target.versionId,
                    factor = factor,
                    source = source,
                    includeOptional = includeOptional,
                    optionalPath = optionalPath,
                    requirements = requirements,
                    visiting = visiting,
                )
            },
        )
    }

    /**
     * 把父级引用量换算为子版本产量单位，再按 `引用量 ÷ 产量` 递归。
     * 单位不可换算时返回 `CUL-UNIT-001`，不猜密度。
     */
    private fun recurseWithYield(
        scaled: Quantity,
        yield: Quantity,
        expand: (Double) -> CulinaryError?,
    ): CulinaryError? {
        val converted =
            when (val result = unitConverter.convert(scaled, yield.unit)) {
                is Outcome.Failure -> return result.error
                is Outcome.Success -> result.value
            }
        if (yield.amount <= 0.0) {
            return CulinaryError.Validation(field = "yield", rule = "must be a finite positive number")
        }
        return expand(converted.amount / yield.amount)
    }

    private fun cycleError(visiting: Set<GraphNode>): CulinaryError =
        CulinaryError.Domain(
            detail = DomainDetail.RecipeCycle(visiting.map { it.displayId }),
            code = ErrorCode.REP_001,
        )

    /** 统一图节点：配方与酱汁使用独立命名空间，避免同 ID 误判为环。 */
    private sealed interface GraphNode {
        val displayId: String

        data class Recipe(
            val id: RecipeVersionId,
        ) : GraphNode {
            override val displayId: String get() = id.value
        }

        data class Sauce(
            val id: SauceVersionId,
        ) : GraphNode {
            override val displayId: String get() = "sauce:${id.value}"
        }
    }
}
