package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.ChangeActionDb

/**
 * 变更日志（A06 §9.2）：只追加，不存敏感信息；UPDATE/DELETE 由触发器拒绝。
 * 不建立外键，避免删除业务实体时被历史日志阻塞。
 */
@Entity(
    tableName = "change_log",
    indices = [
        Index(value = ["entity_type", "entity_id", "created_at"]),
        Index(value = ["created_at"]),
    ],
)
data class ChangeLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "action")
    val action: ChangeActionDb,
    @ColumnInfo(name = "before_json")
    val beforeJson: String? = null,
    @ColumnInfo(name = "after_json")
    val afterJson: String? = null,
    @ColumnInfo(name = "change_reason")
    val changeReason: String? = null,
    @ColumnInfo(name = "app_version")
    val appVersion: String? = null,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
