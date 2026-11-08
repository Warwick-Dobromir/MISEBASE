package com.culinary.core.data.mapper

import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.UnitId

/**
 * 食材 Entity ↔ 领域模型。
 *
 * 数据库层与领域层各自声明枚举，映射必须显式穷举，避免新增枚举值时静默落默认分支。
 */
object IngredientMapper {
    fun toDomain(entity: IngredientEntity): Ingredient =
        Ingredient(
            id = IngredientId(entity.id),
            name = entity.name,
            normalizedName = entity.normalizedName,
            defaultUnitId = entity.defaultUnitId?.let(::UnitId),
            status =
                when (entity.status) {
                    EntityStatusDb.ACTIVE -> EntityStatus.ACTIVE
                    EntityStatusDb.ARCHIVED -> EntityStatus.ARCHIVED
                },
            category = entity.category,
            scientificName = entity.scientificName,
            description = entity.description,
            flavorProfile = entity.flavorProfile,
            seasonality = entity.seasonality,
            origin = entity.origin,
            selectionNotes = entity.selectionNotes,
            storageNotes = entity.storageNotes,
            prepNotes = entity.prepNotes,
            allergenNotes = entity.allergenNotes,
            englishName = entity.englishName,
            exampleProducts = entity.exampleProducts,
            seedNote = entity.seedNote,
            seedId = entity.seedId,
            seedVersion = entity.seedVersion,
        )

    fun toEntity(
        ingredient: Ingredient,
        createdAt: Long,
        updatedAt: Long,
        revision: Int,
    ): IngredientEntity =
        IngredientEntity(
            id = ingredient.id.value,
            name = ingredient.name,
            normalizedName = ingredient.normalizedName,
            defaultUnitId = ingredient.defaultUnitId?.value,
            category = ingredient.category,
            scientificName = ingredient.scientificName,
            description = ingredient.description,
            flavorProfile = ingredient.flavorProfile,
            seasonality = ingredient.seasonality,
            origin = ingredient.origin,
            selectionNotes = ingredient.selectionNotes,
            storageNotes = ingredient.storageNotes,
            prepNotes = ingredient.prepNotes,
            allergenNotes = ingredient.allergenNotes,
            englishName = ingredient.englishName,
            exampleProducts = ingredient.exampleProducts,
            seedNote = ingredient.seedNote,
            seedId = ingredient.seedId,
            seedVersion = ingredient.seedVersion,
            status =
                when (ingredient.status) {
                    EntityStatus.ACTIVE -> EntityStatusDb.ACTIVE
                    EntityStatus.ARCHIVED -> EntityStatusDb.ARCHIVED
                },
            createdAt = createdAt,
            updatedAt = updatedAt,
            revision = revision,
        )
}
