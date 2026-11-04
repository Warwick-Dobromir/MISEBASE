package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.RequirementPlan
import com.culinary.core.domain.model.RequirementPreviewLine
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.model.ShortfallLine
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.rule.inventory.PurchaseListBuilder
import com.culinary.core.domain.rule.recipe.ExplodedRequirement
import com.culinary.core.domain.rule.requirement.InventoryAvailability
import com.culinary.core.domain.rule.requirement.RequirementAggregator
import com.culinary.core.domain.rule.requirement.RequirementGroup
import com.culinary.core.domain.rule.requirement.ShortfallCalculator
import com.culinary.core.domain.rule.unit.UnitConverter
import com.culinary.core.domain.snapshot.CalculationRecordFactory
import com.culinary.core.domain.usecase.recipe.ExpandRecipeUseCase
import kotlinx.coroutines.flow.first

/**
 * 构建菜谱需求计划（MB-D-014 / MB-D-015 / MB-D-016）。
 *
 * 流程：按份数展开（含酱汁图）→ 汇总 → 换算可用库存 → 缺口 → 采购清单 →
 * 生成带稳定 payload hash 的计算记录预览。整个过程只读，不写任何流水/记录。
 */
@Suppress("LongParameterList")
class BuildRequirementPlanUseCase(
    private val expandRecipe: ExpandRecipeUseCase,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val inventoryRepository: InventoryRepository,
    private val unitRepository: UnitRepository,
    private val unitConverter: UnitConverter = UnitConverter(),
    private val aggregator: RequirementAggregator = RequirementAggregator(),
    private val shortfallCalculator: ShortfallCalculator = ShortfallCalculator(),
    private val purchaseListBuilder: PurchaseListBuilder = PurchaseListBuilder(),
    private val recordFactory: CalculationRecordFactory = CalculationRecordFactory(),
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount")
    suspend operator fun invoke(
        recipeVersionId: RecipeVersionId,
        targetServings: Double,
    ): Outcome<RequirementPlan> {
        if (!targetServings.isFinite() || targetServings <= 0.0) {
            return invalid("servings", "must be a finite positive number")
        }
        val expanded =
            when (val result = expandRecipe(recipeVersionId, targetServings)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val root =
            recipeRepository.getVersion(recipeVersionId)
                ?: return Outcome.failure(
                    CulinaryError.NotFound("recipe_version", recipeVersionId.value),
                )
        val recipe =
            when (val result = recipeRepository.get(root.recipeId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val groups =
            when (
                val aggregated =
                    aggregator.aggregate(
                        expanded.lines.mapNotNull { line ->
                            line.ingredientId?.let { id ->
                                ExplodedRequirement(id, line.quantity, line.isOptional)
                            }
                        },
                    )
            ) {
                is Outcome.Failure -> return aggregated
                is Outcome.Success -> aggregated.value
            }
        val items =
            when (val result = inventoryRepository.listItems()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val products =
            when (val result = inventoryRepository.listProducts()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val productsByIngredient = products.groupBy { it.ingredientId }
        val productByProductId = products.associateBy { it.id }
        val names =
            runCatching { ingredientRepository.observeActive().first() }
                .getOrDefault(emptyList())
                .associate { it.id to it.name }
        val now = clock.now()
        val conversions = mutableMapOf<IngredientId, List<com.culinary.core.domain.model.IngredientUnitConversion>>()
        val requiredLines = mutableListOf<RequirementPreviewLine>()
        val optionalLines = mutableListOf<RequirementPreviewLine>()
        val shortfalls = mutableListOf<ShortfallLine>()

        for (group in groups) {
            for (total in group.totals) {
                val available = availableFor(group, total.unit, items, productByProductId, conversions)
                val requirement =
                    RequirementLine(
                        ingredientId = group.ingredientId,
                        quantity = total,
                        isOptional = group.isOptional,
                    )
                val report =
                    when (
                        val result =
                            shortfallCalculator.calculate(
                                requirements = listOf(requirement),
                                stock =
                                    listOf(
                                        InventoryAvailability(
                                            group.ingredientId,
                                            com.culinary.core.domain.model
                                                .Quantity(available, total.unit),
                                        ),
                                    ),
                            )
                    ) {
                        is Outcome.Failure -> return result
                        is Outcome.Success -> result.value
                    }
                val shortfall = (report.required + report.optional).single()
                val product = productsByIngredient[group.ingredientId]?.firstOrNull()
                val preview =
                    RequirementPreviewLine(
                        ingredientId = group.ingredientId,
                        ingredientName = names[group.ingredientId] ?: "已删除食材",
                        required = shortfall.required,
                        available = shortfall.available,
                        shortfall = shortfall.shortfall,
                        isOptional = shortfall.isOptional,
                        productId = product?.id,
                        productName = product?.name,
                    )
                if (shortfall.isOptional) {
                    optionalLines += preview
                } else {
                    requiredLines += preview
                    if (!shortfall.isCovered) shortfalls += shortfall
                }
            }
        }

        val productFor: (IngredientId) -> com.culinary.core.domain.model.ProductId? = { ingredientId ->
            productsByIngredient[ingredientId]?.firstOrNull()?.id
        }
        val purchaseList = purchaseListBuilder.build(shortfalls, now, productFor)
        val recordLines =
            (requiredLines + optionalLines).map { line ->
                RequirementLine(line.ingredientId, line.required, line.isOptional)
            }
        val record =
            recordFactory.preview(
                id = CalculationRecordId(idGenerator.newId()),
                recipeVersionId = recipeVersionId,
                servings = Servings(targetServings),
                lines = recordLines,
                createdAt = now,
            )
        return Outcome.success(
            RequirementPlan(
                recipeVersionId = recipeVersionId,
                recipeName = recipe.name,
                baseServings = expanded.baseServings,
                targetServings = targetServings,
                requiredLines = requiredLines,
                optionalLines = optionalLines,
                purchaseList = purchaseList,
                record = record,
                generatedAt = now,
            ),
        )
    }

    private suspend fun availableFor(
        group: RequirementGroup,
        targetUnit: Unit,
        items: List<InventoryItem>,
        productByProductId: Map<com.culinary.core.domain.model.ProductId, Product>,
        cache: MutableMap<IngredientId, List<com.culinary.core.domain.model.IngredientUnitConversion>>,
    ): Double {
        val conversions = cache.getOrPut(group.ingredientId) { unitRepository.conversionsFor(group.ingredientId) }
        return items
            .asSequence()
            .filter { item -> productByProductId[item.productId]?.ingredientId == group.ingredientId }
            .sumOf { item ->
                when (
                    val converted =
                        unitConverter.convert(
                            com.culinary.core.domain.model
                                .Quantity(item.available, item.stockUnit),
                            targetUnit,
                            conversions,
                            group.ingredientId,
                        )
                ) {
                    is Outcome.Success -> converted.value.amount
                    is Outcome.Failure -> 0.0
                }
            }
    }

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))
}
