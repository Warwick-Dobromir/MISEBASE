package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.VersionStatusDb

/**
 * 酱汁版本（A06 §4.2）。
 *
 * `UNIQUE(sauce_id, version_no)`；"每个酱汁至多一个当前版本"是部分唯一索引，
 * 由 `CulinarySchemaExtras` 创建。已发布版本的内容不可修改，由触发器保护。
 */
@Entity(
    tableName = "sauce_version",
    foreignKeys = [
        ForeignKey(
            entity = SauceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sauce_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["yield_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SauceVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["based_on_version_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sauce_id", "version_no"], unique = true),
        Index(value = ["sauce_id", "status"]),
        Index(value = ["yield_unit_id"]),
        Index(value = ["based_on_version_id"]),
    ],
)
data class SauceVersionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "sauce_id")
    val sauceId: String,
    @ColumnInfo(name = "version_no")
    val versionNo: Int,
    @ColumnInfo(name = "status")
    val status: VersionStatusDb = VersionStatusDb.DRAFT,
    @ColumnInfo(name = "yield_qty")
    val yieldQty: Double,
    @ColumnInfo(name = "yield_unit_id")
    val yieldUnitId: String,
    @ColumnInfo(name = "instructions_md")
    val instructionsMd: String? = null,
    @ColumnInfo(name = "change_note")
    val changeNote: String? = null,
    @ColumnInfo(name = "based_on_version_id")
    val basedOnVersionId: String? = null,
    @ColumnInfo(name = "is_current")
    val isCurrent: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "published_at")
    val publishedAt: Long? = null,
)
