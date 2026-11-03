package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.ReservationStatusDb

/**
 * 库存预留（A06 §6.4）：计划确认时按需求预留批次；释放/消耗是状态变更，不删行。
 * "同一需求对同一批次至多一条 ACTIVE 预留"由部分唯一索引保证。
 */
@Entity(
    tableName = "inventory_reservation",
    foreignKeys = [
        ForeignKey(
            entity = ProductionPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProductionPlanItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_item_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MaterialRequirementEntity::class,
            parentColumns = ["id"],
            childColumns = ["requirement_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["stock_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["plan_id", "status"]),
        Index(value = ["batch_id", "status"]),
        Index(value = ["requirement_id"]),
        Index(value = ["plan_item_id"]),
        Index(value = ["batch_id"]),
        Index(value = ["stock_unit_id"]),
    ],
)
data class InventoryReservationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "plan_id")
    val planId: String,
    @ColumnInfo(name = "plan_item_id")
    val planItemId: String? = null,
    @ColumnInfo(name = "requirement_id")
    val requirementId: String? = null,
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    @ColumnInfo(name = "stock_unit_id")
    val stockUnitId: String,
    @ColumnInfo(name = "status")
    val status: ReservationStatusDb = ReservationStatusDb.ACTIVE,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "released_at")
    val releasedAt: Long? = null,
    @ColumnInfo(name = "consumed_at")
    val consumedAt: Long? = null,
)
