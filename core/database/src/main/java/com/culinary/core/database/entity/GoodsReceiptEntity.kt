package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.ReceiptStatusDb

/**
 * 收货单（A06 §8.4）：`receipt_no` 唯一；POSTED 后明细不可再改，
 * 作废走 VOID 而不是删除。
 */
@Entity(
    tableName = "goods_receipt",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["receipt_no"], unique = true),
        Index(value = ["purchase_order_id", "status"]),
        Index(value = ["received_at"]),
        Index(value = ["location_id"]),
    ],
)
data class GoodsReceiptEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "purchase_order_id")
    val purchaseOrderId: String,
    @ColumnInfo(name = "receipt_no")
    val receiptNo: String,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    @ColumnInfo(name = "location_id")
    val locationId: String,
    @ColumnInfo(name = "status")
    val status: ReceiptStatusDb = ReceiptStatusDb.DRAFT,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "posted_at")
    val postedAt: Long? = null,
    @ColumnInfo(name = "voided_at")
    val voidedAt: Long? = null,
)
