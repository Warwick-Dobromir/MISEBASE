package com.culinary.core.data.mapper

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.database.entity.RecipeComponentEntity
import com.culinary.core.database.entity.RecipeEntity
import com.culinary.core.database.entity.RecipeVersionEntity
import com.culinary.core.database.model.ComponentTypeDb
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.database.model.RecipeCategoryDb
import com.culinary.core.database.model.VersionStatusDb
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.VersionStatus

/**
 * 菜谱 Entity ↔ 领域模型。
 *
 * 阶段 2 边界：`SAUCE` 组成行暂不能映射为领域目标（酱汁档案在 Stage 3 落地），
 * 遇到时返回 `CUL-REP-004`，不静默丢弃。v2 起 `recipe_version.servings` 直接映射到
 * 领域模型；v1 历史数据迁移后为 1（整批 = 1 份）。
 */
object RecipeMapper {
    fun toDomain(entity: RecipeEntity): Recipe =
        Recipe(
            id = RecipeId(entity.id),
            name = entity.name,
            normalizedName = entity.normalizedName,
            category =
                when (entity.category) {
                    RecipeCategoryDb.STOCK -> RecipeCategory.STOCK
                    RecipeCategoryDb.PREP -> RecipeCategory.PREP
                    RecipeCategoryDb.COMPONENT -> RecipeCategory.COMPONENT
                    RecipeCategoryDb.DISH -> RecipeCategory.DISH
                    RecipeCategoryDb.OTHER -> RecipeCategory.OTHER
                },
            status =
                when (entity.status) {
                    EntityStatusDb.ACTIVE -> EntityStatus.ACTIVE
                    EntityStatusDb.ARCHIVED -> EntityStatus.ARCHIVED
                },
        )

    fun toEntity(
        recipe: Recipe,
        createdAt: Long,
        updatedAt: Long,
        revision: Int,
    ): RecipeEntity =
        RecipeEntity(
            id = recipe.id.value,
            name = recipe.name,
            normalizedName = recipe.normalizedName,
            category =
                when (recipe.category) {
                    RecipeCategory.STOCK -> RecipeCategoryDb.STOCK
                    RecipeCategory.PREP -> RecipeCategoryDb.PREP
                    RecipeCategory.COMPONENT -> RecipeCategoryDb.COMPONENT
                    RecipeCategory.DISH -> RecipeCategoryDb.DISH
                    RecipeCategory.OTHER -> RecipeCategoryDb.OTHER
                },
            status =
                when (recipe.status) {
                    EntityStatus.ACTIVE -> EntityStatusDb.ACTIVE
                    EntityStatus.ARCHIVED -> EntityStatusDb.ARCHIVED
                },
            createdAt = createdAt,
            updatedAt = updatedAt,
            revision = revision,
        )

    fun toVersionEntity(
        version: RecipeVersion,
        publishedAt: Long?,
        createdAt: Long,
    ): RecipeVersionEntity =
        RecipeVersionEntity(
            id = version.id.value,
            recipeId = version.recipeId.value,
            versionNo = version.versionNo,
            status =
                when (version.status) {
                    VersionStatus.DRAFT -> VersionStatusDb.DRAFT
                    VersionStatus.PUBLISHED -> VersionStatusDb.PUBLISHED
                    VersionStatus.SUPERSEDED -> VersionStatusDb.SUPERSEDED
                },
            yieldQty = version.yield.amount,
            yieldUnitId = version.yield.unit.id.value,
            servings = version.servings,
            isCurrent = version.isCurrent,
            createdAt = createdAt,
            publishedAt = publishedAt,
        )

    fun toDomain(
        version: RecipeVersionEntity,
        components: List<RecipeComponentEntity>,
        units: Map<String, Unit>,
    ): Outcome<RecipeVersion> {
        val yieldUnit =
            units[version.yieldUnitId]
                ?: return Outcome.failure(
                    CulinaryError.NotFound(entity = "unit", id = version.yieldUnitId),
                )
        val mapped = mutableListOf<RecipeComponent>()
        for (component in components) {
            val unit =
                units[component.unitId]
                    ?: return Outcome.failure(
                        CulinaryError.NotFound(entity = "unit", id = component.unitId),
                    )
            val target =
                when (val resolved = toTarget(component)) {
                    is Outcome.Failure -> return resolved
                    is Outcome.Success -> resolved.value
                }
            mapped +=
                RecipeComponent(
                    lineNo = component.lineNo,
                    target = target,
                    quantity = Quantity(component.quantity, unit),
                    isOptional = component.isOptional,
                )
        }
        return Outcome.success(
            RecipeVersion(
                id = RecipeVersionId(version.id),
                recipeId = RecipeId(version.recipeId),
                versionNo = version.versionNo,
                status =
                    when (version.status) {
                        VersionStatusDb.DRAFT -> VersionStatus.DRAFT
                        VersionStatusDb.PUBLISHED -> VersionStatus.PUBLISHED
                        VersionStatusDb.SUPERSEDED -> VersionStatus.SUPERSEDED
                    },
                yield = Quantity(version.yieldQty, yieldUnit),
                components = mapped,
                servings = version.servings,
                isCurrent = version.isCurrent,
            ),
        )
    }

    private fun toTarget(component: RecipeComponentEntity): Outcome<ComponentTarget> =
        when (component.componentType) {
            ComponentTypeDb.INGREDIENT -> {
                val ingredientId = component.ingredientId
                if (ingredientId == null) {
                    invalidComponent(component)
                } else {
                    Outcome.success(ComponentTarget.Ingredient(IngredientId(ingredientId)))
                }
            }

            ComponentTypeDb.RECIPE -> {
                val recipeId = component.referencedRecipeId
                val versionId = component.referencedRecipeVersionId
                if (recipeId == null || versionId == null) {
                    invalidComponent(component)
                } else {
                    Outcome.success(ComponentTarget.Recipe(RecipeId(recipeId), RecipeVersionId(versionId)))
                }
            }

            ComponentTypeDb.SAUCE -> sauceComponentUnsupported(component)
        }

    private fun invalidComponent(component: RecipeComponentEntity): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Domain(
                detail =
                    DomainDetail.InvalidState(
                        entity = "recipe_component",
                        expected = "target id present",
                        actual = "component ${component.id}",
                    ),
                code = ErrorCode.REP_004,
            ),
        )

    private fun sauceComponentUnsupported(component: RecipeComponentEntity): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Domain(
                detail =
                    DomainDetail.InvalidState(
                        entity = "recipe_component",
                        expected = "INGREDIENT or RECIPE target",
                        actual = "SAUCE (component ${component.id})",
                    ),
                code = ErrorCode.REP_004,
            ),
        )
}
