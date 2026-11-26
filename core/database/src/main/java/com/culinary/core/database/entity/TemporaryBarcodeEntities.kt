package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "temporary_barcode",
    foreignKeys = [ForeignKey(
        entity = InventoryBatchEntity::class,
        parentColumns = ["id"],
        childColumns = ["current_batch_id"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["status", "created_at"]),
        Index(value = ["current_batch_id"], unique = true),
    ],
)
data class TemporaryBarcodeEntity(
    @PrimaryKey val id: String,
    val code: String,
    val status: String = "AVAILABLE",
    @ColumnInfo(name = "current_batch_id") val currentBatchId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "temporary_barcode_history",
    foreignKeys = [ForeignKey(
        entity = TemporaryBarcodeEntity::class,
        parentColumns = ["id"],
        childColumns = ["barcode_id"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index(value = ["barcode_id", "occurred_at"]), Index(value = ["batch_id"])],
)
data class TemporaryBarcodeHistoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "barcode_id") val barcodeId: String,
    @ColumnInfo(name = "barcode_code") val barcodeCode: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    val action: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)
