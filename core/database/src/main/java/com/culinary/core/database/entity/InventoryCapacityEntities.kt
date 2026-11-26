package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 系统生成的逻辑容量格；不是实际库位，不改变批次的物理 location_id。 */
@Entity(
    tableName = "inventory_capacity_cell",
    foreignKeys = [ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["base_location_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["base_location_id", "expiry_zone", "sequence"], unique = true)],
)
data class InventoryCapacityCellEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "base_location_id") val baseLocationId: String,
    @ColumnInfo(name = "expiry_zone") val expiryZone: String,
    @ColumnInfo(name = "sequence") val sequence: Int,
    @ColumnInfo(name = "capacity_cu") val capacityCu: Double,
    @ColumnInfo(name = "occupied_cu") val occupiedCu: Double = 0.0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 一个批次可分配到多个逻辑容量格；数量和 CU 同步保存以支持部分出库释放。 */
@Entity(
    tableName = "inventory_batch_capacity_allocation",
    foreignKeys = [
        ForeignKey(entity = InventoryBatchEntity::class, parentColumns = ["id"], childColumns = ["batch_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryCapacityCellEntity::class, parentColumns = ["id"], childColumns = ["cell_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["batch_id", "cell_id"], unique = true), Index(value = ["cell_id"])],
)
data class InventoryBatchCapacityAllocationEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "cell_id") val cellId: String,
    @ColumnInfo(name = "quantity") val quantity: Double,
    @ColumnInfo(name = "occupied_cu") val occupiedCu: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
