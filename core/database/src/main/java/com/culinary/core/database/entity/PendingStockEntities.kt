package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 扫码待入库商品快照；不参与正式库存数量统计。 */
@Entity(
    tableName = "pending_stock_item",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["stock_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_product_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = InventoryArchiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["archive_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archive_id"), Index("sort_order"), Index("stock_unit_id"), Index("source_product_id")],
)
data class PendingStockItemEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "archive_id") val archiveId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "brand") val brand: String? = null,
    @ColumnInfo(name = "category_name") val categoryName: String? = null,
    @ColumnInfo(name = "specification") val specification: String? = null,
    @ColumnInfo(name = "image_uri") val imageUri: String? = null,
    @ColumnInfo(name = "stock_unit_id") val stockUnitId: String,
    @ColumnInfo(name = "source_product_id") val sourceProductId: String? = null,
    @ColumnInfo(name = "quantity") val quantity: Double,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 同一商品允许拆分多个生产日期批次。 */
@Entity(
    tableName = "pending_stock_date_allocation",
    foreignKeys = [
        ForeignKey(
            entity = PendingStockItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("item_id")],
)
data class PendingStockDateAllocationEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "production_date") val productionDate: String,
    @ColumnInfo(name = "quantity") val quantity: Double,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
