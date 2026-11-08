package com.culinary.core.data.mapper

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.database.entity.SauceComponentEntity
import com.culinary.core.database.entity.SauceEntity
import com.culinary.core.database.entity.SauceVersionEntity
import com.culinary.core.database.model.ComponentTypeDb
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.database.model.VersionStatusDb
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceComponent
import com.culinary.core.domain.model.SauceDifficulty
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.VersionStatus

/**
 * 酱汁 Entity ↔ 领域模型。
 *
 * 组成行三选一（INGREDIENT / SAUCE / RECIPE）全部映射为领域目标，不静默丢弃；
 * 目标 id 缺失时返回 `CUL-REP-004`。
 */
object SauceMapper {
    fun toDomain(entity: SauceEntity): Sauce =
        Sauce(
            id = SauceId(entity.id),
            name = entity.name,
            normalizedName = entity.normalizedName,
            status =
                when (entity.status) {
                    EntityStatusDb.ACTIVE -> EntityStatus.ACTIVE
                    EntityStatusDb.ARCHIVED -> EntityStatus.ARCHIVED
                },
            family = entity.family,
            texture = entity.texture,
            flavorProfile = entity.flavorProfile,
            usageNotes = entity.usageNotes,
            storageNotes = entity.storageNotes,
            shelfLifeDays = entity.shelfLifeDays,
            englishName = entity.englishName,
            description = entity.description,
            difficulty = entity.difficulty?.let { SauceDifficulty.valueOf(it) },
            heroCaption = entity.heroCaption,
            updatedAtEpochMillis = entity.updatedAt,
            mainIngredients = entity.mainIngredients,
            preparationSummary = entity.preparationSummary,
            keyParameters = entity.keyParameters,
            adjustableNotes = entity.adjustableNotes,
            seedNote = entity.seedNote,
            seedId = entity.seedId,
            seedVersion = entity.seedVersion,
        )

    fun toEntity(
        sauce: Sauce,
        createdAt: Long,
        updatedAt: Long,
        revision: Int,
    ): SauceEntity =
        SauceEntity(
            id = sauce.id.value,
            name = sauce.name,
            normalizedName = sauce.normalizedName,
            family = sauce.family,
            texture = sauce.texture,
            flavorProfile = sauce.flavorProfile,
            usageNotes = sauce.usageNotes,
            storageNotes = sauce.storageNotes,
            shelfLifeDays = sauce.shelfLifeDays,
            englishName = sauce.englishName,
            description = sauce.description,
            difficulty = sauce.difficulty?.name,
            heroCaption = sauce.heroCaption,
            mainIngredients = sauce.mainIngredients,
            preparationSummary = sauce.preparationSummary,
            keyParameters = sauce.keyParameters,
            adjustableNotes = sauce.adjustableNotes,
            seedNote = sauce.seedNote,
            seedId = sauce.seedId,
            seedVersion = sauce.seedVersion,
            status =
                when (sauce.status) {
                    EntityStatus.ACTIVE -> EntityStatusDb.ACTIVE
                    EntityStatus.ARCHIVED -> EntityStatusDb.ARCHIVED
                },
            createdAt = createdAt,
            updatedAt = updatedAt,
            revision = revision,
        )

    fun toVersionEntity(
        version: SauceVersion,
        publishedAt: Long?,
        createdAt: Long,
    ): SauceVersionEntity =
        SauceVersionEntity(
            id = version.id.value,
            sauceId = version.sauceId.value,
            versionNo = version.versionNo,
            status =
                when (version.status) {
                    VersionStatus.DRAFT -> VersionStatusDb.DRAFT
                    VersionStatus.PUBLISHED -> VersionStatusDb.PUBLISHED
                    VersionStatus.SUPERSEDED -> VersionStatusDb.SUPERSEDED
                },
            yieldQty = version.yield.amount,
            yieldUnitId = version.yield.unit.id.value,
            instructionsMd = version.instructions,
            stepsJson = version.stepsJson,
            isCurrent = version.isCurrent,
            createdAt = createdAt,
            publishedAt = publishedAt,
        )

    fun toDomain(
        version: SauceVersionEntity,
        components: List<SauceComponentEntity>,
        units: Map<String, Unit>,
    ): Outcome<SauceVersion> {
        val yieldUnit =
            units[version.yieldUnitId]
                ?: return Outcome.failure(CulinaryError.NotFound(entity = "unit", id = version.yieldUnitId))
        val mapped = mutableListOf<SauceComponent>()
        for (component in components) {
            val unit =
                units[component.unitId]
                    ?: return Outcome.failure(CulinaryError.NotFound(entity = "unit", id = component.unitId))
            val target =
                when (val resolved = toTarget(component)) {
                    is Outcome.Failure -> return resolved
                    is Outcome.Success -> resolved.value
                }
            mapped +=
                SauceComponent(
                    lineNo = component.lineNo,
                    target = target,
                    quantity = Quantity(component.quantity, unit),
                    isOptional = component.isOptional,
                    labelOverride = component.labelOverride,
                    labelOverrideEn = component.labelOverrideEn,
                )
        }
        return Outcome.success(
            SauceVersion(
                id = SauceVersionId(version.id),
                sauceId = SauceId(version.sauceId),
                versionNo = version.versionNo,
                status =
                    when (version.status) {
                        VersionStatusDb.DRAFT -> VersionStatus.DRAFT
                        VersionStatusDb.PUBLISHED -> VersionStatus.PUBLISHED
                        VersionStatusDb.SUPERSEDED -> VersionStatus.SUPERSEDED
                    },
                yield = Quantity(version.yieldQty, yieldUnit),
                components = mapped,
                isCurrent = version.isCurrent,
                instructions = version.instructionsMd,
                stepsJson = version.stepsJson,
            ),
        )
    }

    fun toComponentEntity(
        versionId: SauceVersionId,
        component: SauceComponent,
        id: String,
        createdAt: Long,
    ): SauceComponentEntity {
        val base =
            SauceComponentEntity(
                id = id,
                sauceVersionId = versionId.value,
                lineNo = component.lineNo,
                componentType = ComponentTypeDb.INGREDIENT,
                quantity = component.quantity.amount,
                unitId = component.quantity.unit.id.value,
                isOptional = component.isOptional,
                labelOverride = component.labelOverride,
                labelOverrideEn = component.labelOverrideEn,
                sortOrder = component.lineNo,
                createdAt = createdAt,
            )
        return when (val target = component.target) {
            is ComponentTarget.Ingredient ->
                base.copy(
                    componentType = ComponentTypeDb.INGREDIENT,
                    ingredientId = target.id.value,
                )

            is ComponentTarget.Recipe ->
                base.copy(
                    componentType = ComponentTypeDb.RECIPE,
                    referencedRecipeId = target.recipeId.value,
                    referencedRecipeVersionId = target.versionId.value,
                )

            is ComponentTarget.Sauce ->
                base.copy(
                    componentType = ComponentTypeDb.SAUCE,
                    referencedSauceId = target.sauceId.value,
                    referencedSauceVersionId = target.versionId.value,
                )
        }
    }

    private fun toTarget(component: SauceComponentEntity): Outcome<ComponentTarget> =
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

            ComponentTypeDb.SAUCE -> {
                val sauceId = component.referencedSauceId
                val versionId = component.referencedSauceVersionId
                if (sauceId == null || versionId == null) {
                    invalidComponent(component)
                } else {
                    Outcome.success(ComponentTarget.Sauce(SauceId(sauceId), SauceVersionId(versionId)))
                }
            }
        }

    private fun invalidComponent(component: SauceComponentEntity): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Domain(
                detail =
                    DomainDetail.InvalidState(
                        entity = "sauce_component",
                        expected = "target id present",
                        actual = "component ${component.id}",
                    ),
                code = ErrorCode.REP_004,
            ),
        )
}
