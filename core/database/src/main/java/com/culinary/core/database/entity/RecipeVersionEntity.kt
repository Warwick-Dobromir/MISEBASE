package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.VersionStatusDb

/**
 * 菜谱版本（A06 §4.5）。`UNIQUE(recipe_id, version_no)`；当前版本唯一由部分索引保证；
 * 已发布版本的内容不可修改，由触发器保护。
 */
@Entity(
    tableName = "recipe_version",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["yield_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecipeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["based_on_version_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["recipe_id", "version_no"], unique = true),
        Index(value = ["recipe_id", "status"]),
        Index(value = ["yield_unit_id"]),
        Index(value = ["based_on_version_id"]),
    ],
)
data class RecipeVersionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "recipe_id")
    val recipeId: String,
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
    /** 版本基准份数（MB-SC-001）；历史数据迁移后按 1 份处理。 */
    @ColumnInfo(name = "servings", defaultValue = "1")
    val servings: Int = 1,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "published_at")
    val publishedAt: Long? = null,
)
