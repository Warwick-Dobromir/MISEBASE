package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 临时条码资源池。条码编号永久保留，释放后仅清空当前批次归属。 */
@Entity(
    tableName = "temporary_batch_barcode",
    indices = [Index(value = ["batch_id"]), Index(value = ["state"])],
)
data class TemporaryBatchBarcodeEntity(
    @PrimaryKey @ColumnInfo(name = "barcode") val barcode: String,
    @ColumnInfo(name = "batch_id") val batchId: String? = null,
    @ColumnInfo(name = "state") val state: String = "FREE",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "assigned_at") val assignedAt: Long? = null,
    @ColumnInfo(name = "released_at") val releasedAt: Long? = null,
)

/** 每次分配/释放均追加记录，不随资源回收删除。 */
@Entity(
    tableName = "temporary_batch_barcode_history",
    indices = [Index(value = ["barcode"]), Index(value = ["batch_id"]), Index(value = ["occurred_at"])],
)
data class TemporaryBatchBarcodeHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "barcode") val barcode: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)
