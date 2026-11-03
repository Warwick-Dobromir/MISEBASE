package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.EntityStatusDb

/**
 * 采购 SKU（A06 §5.2）：一个商品只属于一个食材；库存按 `stock_unit_id` 记账，
 * `stock_to_content_factor` 把库存单位换算成包装内容量。
 */
@Entity(
    tableName = "product",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["content_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["stock_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_location_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["ingredient_id", "status"]),
        Index(value = ["supplier_id"]),
        Index(value = ["normalized_name"]),
        Index(value = ["default_location_id"]),
        Index(value = ["content_unit_id"]),
        Index(value = ["stock_unit_id"]),
    ],
)
data class ProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "ingredient_id")
    val ingredientId: String,
    @ColumnInfo(name = "supplier_id")
    val supplierId: String? = null,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "brand")
    val brand: String? = null,
    @ColumnInfo(name = "sku")
    val sku: String? = null,
    @ColumnInfo(name = "package_label")
    val packageLabel: String? = null,
    @ColumnInfo(name = "content_qty")
    val contentQty: Double,
    @ColumnInfo(name = "content_unit_id")
    val contentUnitId: String,
    @ColumnInfo(name = "stock_unit_id")
    val stockUnitId: String,
    @ColumnInfo(name = "stock_to_content_factor")
    val stockToContentFactor: Double,
    @ColumnInfo(name = "default_location_id")
    val defaultLocationId: String? = null,
    @ColumnInfo(name = "shelf_life_days")
    val shelfLifeDays: Int? = null,
    @ColumnInfo(name = "storage_note")
    val storageNote: String? = null,
    @ColumnInfo(name = "last_unit_price_minor")
    val lastUnitPriceMinor: Long? = null,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String = "CNY",
    @ColumnInfo(name = "status")
    val status: EntityStatusDb = EntityStatusDb.ACTIVE,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "revision")
    val revision: Int = 1,
)
