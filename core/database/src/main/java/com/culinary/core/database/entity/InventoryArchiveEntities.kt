package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.ArchiveKindDb
import com.culinary.core.database.model.EntityStatusDb

/**
 * 库存档案（v9，MB-INV-AUDIT-001 Q1 决策：档案与采购商品分离）。
 *
 * 一个档案代表厨房实际管理的库存对象；采购 SKU 通过
 * [InventoryArchiveProductEntity] 关联，一个档案可以对应多个采购商品。
 * 档案只描述"这是什么"，实际数量由批次与流水决定。
 */
@Entity(
    tableName = "inventory_archive",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SauceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sauce_id"],
            onDelete = ForeignKey.SET_NULL,
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
        Index(value = ["category_id"]),
        Index(value = ["archive_kind"]),
        Index(value = ["status"]),
        Index(value = ["sauce_id"]),
        Index(value = ["stock_unit_id"]),
        Index(value = ["default_location_id"]),
        Index(value = ["normalized_name"]),
    ],
)
data class InventoryArchiveEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "english_name")
    val englishName: String? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "archive_kind")
    val archiveKind: ArchiveKindDb = ArchiveKindDb.INGREDIENT,
    /** 酱汁现货档案绑定的酱汁 ID；普通食材档案为 null。 */
    @ColumnInfo(name = "sauce_id")
    val sauceId: String? = null,
    @ColumnInfo(name = "stock_unit_id")
    val stockUnitId: String,
    @ColumnInfo(name = "minimum_stock")
    val minimumStock: Double? = null,
    @ColumnInfo(name = "target_stock")
    val targetStock: Double? = null,
    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null,
    @ColumnInfo(name = "storage_method")
    val storageMethod: String? = null,
    @ColumnInfo(name = "storage_temperature")
    val storageTemperature: String? = null,
    @ColumnInfo(name = "default_location_id")
    val defaultLocationId: String? = null,
    @ColumnInfo(name = "description")
    val description: String? = null,
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

/**
 * 库存档案 ↔ 采购商品（v9）。
 *
 * `conversion_ratio` 表示 1 个采购商品内容量折算成多少个库存单位，
 * 例如"1 箱 = 24 个"时 `conversion_ratio = 24`。测试版一个商品只归属一个档案。
 */
@Entity(
    tableName = "inventory_archive_product",
    foreignKeys = [
        ForeignKey(
            entity = InventoryArchiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["archive_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["archive_id", "product_id"], unique = true),
        Index(value = ["product_id"]),
    ],
)
data class InventoryArchiveProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "archive_id")
    val archiveId: String,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "conversion_ratio")
    val conversionRatio: Double = 1.0,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
