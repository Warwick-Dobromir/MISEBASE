package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.PurchaseOrderItemStatusDb

/**
 * 采购明细（A06 §8.3）：`received_qty` 由收货累加，`cancelled_qty` 不得超过订购量。
 */
@Entity(
    tableName = "purchase_order_item",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_id"],
            onDelete = ForeignKey.CASCADE,
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
            childColumns = ["unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["purchase_order_id"]),
        Index(value = ["product_id"]),
        Index(value = ["unit_id"]),
    ],
)
data class PurchaseOrderItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "purchase_order_id")
    val purchaseOrderId: String,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "ordered_qty")
    val orderedQty: Double,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "unit_price_minor")
    val unitPriceMinor: Long? = null,
    @ColumnInfo(name = "received_qty")
    val receivedQty: Double = 0.0,
    @ColumnInfo(name = "cancelled_qty")
    val cancelledQty: Double = 0.0,
    @ColumnInfo(name = "status")
    val status: PurchaseOrderItemStatusDb = PurchaseOrderItemStatusDb.OPEN,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
