package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.ProcurementStatusDb

/**
 * 采购需求（A06 §8.1）：把计划缺口转为可采购项；一个缺口可由多个商品/采购行满足，
 * 因此不强制唯一。
 */
@Entity(
    tableName = "procurement_requirement",
    foreignKeys = [
        ForeignKey(
            entity = ProductionPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProductionPlanSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["snapshot_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MaterialRequirementEntity::class,
            parentColumns = ["id"],
            childColumns = ["requirement_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PurchaseOrderItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_item_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["plan_id", "status"]),
        Index(value = ["requirement_id"]),
        Index(value = ["product_id"]),
        Index(value = ["snapshot_id"]),
        Index(value = ["purchase_order_item_id"]),
        Index(value = ["ingredient_id"]),
        Index(value = ["unit_id"]),
    ],
)
data class ProcurementRequirementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "plan_id")
    val planId: String,
    @ColumnInfo(name = "snapshot_id")
    val snapshotId: String,
    @ColumnInfo(name = "requirement_id")
    val requirementId: String,
    @ColumnInfo(name = "ingredient_id")
    val ingredientId: String,
    @ColumnInfo(name = "product_id")
    val productId: String? = null,
    @ColumnInfo(name = "requested_qty")
    val requestedQty: Double,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "status")
    val status: ProcurementStatusDb = ProcurementStatusDb.OPEN,
    @ColumnInfo(name = "purchase_order_item_id")
    val purchaseOrderItemId: String? = null,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
