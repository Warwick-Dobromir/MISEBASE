package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.PurchaseOrderStatusDb

/**
 * 采购单（A06 §8.2）：`code` 唯一；`allow_over_receipt` 对应 O-09，
 * 默认禁止超收，需显式开启并留原因。
 */
@Entity(
    tableName = "purchase_order",
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProductionPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_plan_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["status", "ordered_at"]),
        Index(value = ["supplier_id"]),
        Index(value = ["source_plan_id"]),
    ],
)
data class PurchaseOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "code")
    val code: String,
    @ColumnInfo(name = "supplier_id")
    val supplierId: String? = null,
    @ColumnInfo(name = "status")
    val status: PurchaseOrderStatusDb = PurchaseOrderStatusDb.DRAFT,
    @ColumnInfo(name = "ordered_at")
    val orderedAt: Long? = null,
    @ColumnInfo(name = "expected_date")
    val expectedDate: String? = null,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String = "CNY",
    @ColumnInfo(name = "allow_over_receipt")
    val allowOverReceipt: Boolean = false,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "source_plan_id")
    val sourcePlanId: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "revision")
    val revision: Int = 1,
)
