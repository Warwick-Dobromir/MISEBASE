package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.LedgerTypeDb

/**
 * 库存流水（A06 §6.3）：**追加式**，UPDATE/DELETE 由触发器拒绝。
 *
 * `idempotency_key` 全局唯一，防止重复过账；`on_hand_after`/`reserved_after`
 * 是操作后的物化快照，便于审计与对账。
 */
@Entity(
    tableName = "inventory_ledger",
    foreignKeys = [
        ForeignKey(
            entity = InventoryBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
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
        Index(value = ["batch_id", "occurred_at"]),
        Index(value = ["product_id", "occurred_at"]),
        Index(value = ["reference_type", "reference_id"]),
        Index(value = ["location_id"]),
        Index(value = ["stock_unit_id"]),
        Index(value = ["idempotency_key"], unique = true),
    ],
)
data class InventoryLedgerEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "location_id")
    val locationId: String,
    @ColumnInfo(name = "type")
    val type: LedgerTypeDb,
    @ColumnInfo(name = "on_hand_delta")
    val onHandDelta: Double = 0.0,
    @ColumnInfo(name = "reserved_delta")
    val reservedDelta: Double = 0.0,
    @ColumnInfo(name = "stock_unit_id")
    val stockUnitId: String,
    @ColumnInfo(name = "on_hand_after")
    val onHandAfter: Double,
    @ColumnInfo(name = "reserved_after")
    val reservedAfter: Double,
    @ColumnInfo(name = "from_location_id")
    val fromLocationId: String? = null,
    @ColumnInfo(name = "to_location_id")
    val toLocationId: String? = null,
    @ColumnInfo(name = "reference_type")
    val referenceType: String? = null,
    @ColumnInfo(name = "reference_id")
    val referenceId: String? = null,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
