package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 商品条码（A06 §5.3）：一个条码只对应一个商品；每个商品至多一个主条码
 * （主条码唯一由 `CulinarySchemaExtras` 的部分唯一索引保证）。
 */
@Entity(
    tableName = "barcode",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["product_id"]),
    ],
)
data class BarcodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "code")
    val code: String,
    @ColumnInfo(name = "symbology")
    val symbology: String? = null,
    @ColumnInfo(name = "is_primary")
    val isPrimary: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
