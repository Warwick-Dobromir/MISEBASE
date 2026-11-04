package com.culinary.core.data.mapper

import com.culinary.core.database.entity.ProductEntity
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Unit

/** 商品 Entity ↔ 领域模型；单位由调用方批量加载。 */
object ProductMapper {
    fun toDomain(
        entity: ProductEntity,
        stockUnit: Unit,
        contentUnit: Unit,
    ): Product =
        Product(
            id = ProductId(entity.id),
            ingredientId = IngredientId(entity.ingredientId),
            name = entity.name,
            stockUnit = stockUnit,
            contentQty = entity.contentQty,
            contentUnit = contentUnit,
            stockToContentFactor = entity.stockToContentFactor,
            defaultLocationId = entity.defaultLocationId?.let(::LocationId),
            status =
                when (entity.status) {
                    EntityStatusDb.ACTIVE -> EntityStatus.ACTIVE
                    EntityStatusDb.ARCHIVED -> EntityStatus.ARCHIVED
                },
        )

    fun toEntity(
        product: Product,
        normalizedName: String,
        createdAt: Long,
        updatedAt: Long,
        revision: Int,
    ): ProductEntity =
        ProductEntity(
            id = product.id.value,
            ingredientId = product.ingredientId.value,
            name = product.name,
            normalizedName = normalizedName,
            contentQty = product.contentQty,
            contentUnitId = product.contentUnit.id.value,
            stockUnitId = product.stockUnit.id.value,
            stockToContentFactor = product.stockToContentFactor,
            defaultLocationId = product.defaultLocationId?.value,
            status =
                when (product.status) {
                    EntityStatus.ACTIVE -> EntityStatusDb.ACTIVE
                    EntityStatus.ARCHIVED -> EntityStatusDb.ARCHIVED
                },
            createdAt = createdAt,
            updatedAt = updatedAt,
            revision = revision,
        )
}

/** 储存位置 Entity ↔ 领域模型。 */
object StorageLocationMapper {
    fun toDomain(
        entity: com.culinary.core.database.entity.StorageLocationEntity,
    ): com.culinary.core.domain.model.StorageLocation =
        com.culinary.core.domain.model.StorageLocation(
            id = LocationId(entity.id),
            name = entity.name,
            parentId = entity.parentId?.let(::LocationId),
            status =
                when (entity.status) {
                    EntityStatusDb.ACTIVE -> EntityStatus.ACTIVE
                    EntityStatusDb.ARCHIVED -> EntityStatus.ARCHIVED
                },
        )

    fun toEntity(
        location: com.culinary.core.domain.model.StorageLocation,
        normalizedName: String,
        createdAt: Long,
        updatedAt: Long,
    ): com.culinary.core.database.entity.StorageLocationEntity =
        com.culinary.core.database.entity.StorageLocationEntity(
            id = location.id.value,
            name = location.name,
            normalizedName = normalizedName,
            parentId = location.parentId?.value,
            status =
                when (location.status) {
                    EntityStatus.ACTIVE -> EntityStatusDb.ACTIVE
                    EntityStatus.ARCHIVED -> EntityStatusDb.ARCHIVED
                },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
