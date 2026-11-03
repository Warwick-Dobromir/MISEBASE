package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 收货明细（A06 §8.5）：过账时生成库存批次并回填 `created_batch_id`，
 * 同一事务写入批次与流水（B 端要求）。
 */
@Entity(
    tableName = "goods_receipt_item",
    foreignKeys = [
        ForeignKey(
            entity = GoodsReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["goods_receipt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PurchaseOrderItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_item_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["stock_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["created_batch_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["goods_receipt_id"]),
        Index(value = ["purchase_order_item_id"]),
        Index(value = ["created_batch_id"]),
        Index(value = ["product_id"]),
        Index(value = ["stock_unit_id"]),
    ],
)
data class GoodsReceiptItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "goods_receipt_id")
    val goodsReceiptId: String,
    @ColumnInfo(name = "purchase_order_item_id")
    val purchaseOrderItemId: String,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    @ColumnInfo(name = "stock_unit_id")
    val stockUnitId: String,
    @ColumnInfo(name = "batch_code")
    val batchCode: String? = null,
    @ColumnInfo(name = "expiry_date")
    val expiryDate: String? = null,
    @ColumnInfo(name = "unit_cost_minor")
    val unitCostMinor: Long? = null,
    @ColumnInfo(name = "created_batch_id")
    val createdBatchId: String? = null,
    @ColumnInfo(name = "note")
    val note: String? = null,
)
