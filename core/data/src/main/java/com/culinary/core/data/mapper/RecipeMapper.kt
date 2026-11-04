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
 * 遇到时返回 `CUL-REP-004`，不静默丢弃。`recipe_version` 暂无 `servings` 列，
 * 映射按"整批 = 1 份"处理。
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
