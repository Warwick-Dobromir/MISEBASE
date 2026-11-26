package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "temporary_barcode", indices = [Index(value = ["barcode"], unique = true), Index(value = ["status", "updated_at"]), Index(value = ["batch_id"], unique = true)])
data class TemporaryBarcodeEntity(
 @PrimaryKey @ColumnInfo(name = "id") val id: String,
 @ColumnInfo(name = "barcode") val barcode: String,
 @ColumnInfo(name = "status", defaultValue = "FREE") val status: String = "FREE",
 @ColumnInfo(name = "batch_id") val batchId: String? = null,
 @ColumnInfo(name = "allocated_at") val allocatedAt: Long? = null,
 @ColumnInfo(name = "released_at") val releasedAt: Long? = null,
 @ColumnInfo(name = "created_at") val createdAt: Long,
 @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "temporary_barcode_history", indices = [Index(value = ["barcode_id", "occurred_at"]), Index(value = ["batch_id", "occurred_at"])])
data class TemporaryBarcodeHistoryEntity(
 @PrimaryKey @ColumnInfo(name = "id") val id: String,
 @ColumnInfo(name = "barcode_id") val barcodeId: String,
 @ColumnInfo(name = "barcode") val barcode: String,
 @ColumnInfo(name = "batch_id") val batchId: String,
 @ColumnInfo(name = "action") val action: String,
 @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)
