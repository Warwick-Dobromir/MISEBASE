package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.FreezeActionDb

/** 冻结/解冻记录（v9）：追加式留痕，不覆盖历史。 */
@Entity(
    tableName = "inventory_freeze_record",
    foreignKeys = [
        ForeignKey(
            entity = InventoryBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["batch_id", "occurred_at"]),
        Index(value = ["action"]),
    ],
)
data class InventoryFreezeRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "action")
    val action: FreezeActionDb,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "operator")
    val operator: String? = null,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
)

/** 库位转移记录（v9）：转移不新建批次、不改变库存总量。 */
@Entity(
    tableName = "inventory_location_transfer",
    foreignKeys = [
        ForeignKey(
            entity = InventoryBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_location_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_location_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["batch_id", "occurred_at"]),
        Index(value = ["from_location_id"]),
        Index(value = ["to_location_id"]),
    ],
)
data class InventoryLocationTransferEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "from_location_id")
    val fromLocationId: String,
    @ColumnInfo(name = "to_location_id")
    val toLocationId: String,
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
)
