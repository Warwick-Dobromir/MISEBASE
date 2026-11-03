package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用级元数据（A06 §9.4）：键值表，用于 install_id、last_backup_at、
 * schema_engine_version 等低频状态；不存业务数据。
 */
@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
