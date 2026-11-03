package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.BackupKindDb
import com.culinary.core.database.model.BackupStatusDb

/** 备份记录（A06 §9.3）：只记录结果与校验信息，不存备份内容。 */
@Entity(
    tableName = "backup_record",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["kind", "created_at"]),
    ],
)
data class BackupRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "kind")
    val kind: BackupKindDb,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_uri")
    val fileUri: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "sha256")
    val sha256: String,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    @ColumnInfo(name = "app_version_name")
    val appVersionName: String,
    @ColumnInfo(name = "status")
    val status: BackupStatusDb = BackupStatusDb.SUCCESS,
    @ColumnInfo(name = "note")
    val note: String? = null,
)
