package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.CalculationRecordStatusDb

/**
 * 计算记录头（MB-D-017 / Schema v2）。
 *
 * 不可变语义：`CONFIRMED` 之后任何字段都不得修改；由领域模型、Repository 与
 * `CulinarySchemaExtras` 的触发器共同保证。`payload_hash` 唯一，重复提交不会产生第二条记录。
 */
@Entity(
    tableName = "calculation_record",
    foreignKeys = [
        ForeignKey(
            entity = RecipeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_version_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["recipe_version_id"]),
        Index(value = ["payload_hash"], unique = true),
        Index(value = ["recipe_version_id", "created_at"]),
        Index(value = ["status"]),
    ],
)
data class CalculationRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "recipe_version_id")
    val recipeVersionId: String,
    /** 计算时使用的份数；允许半份，因此为 REAL。 */
    @ColumnInfo(name = "servings")
    val servings: Double,
    @ColumnInfo(name = "engine_version")
    val engineVersion: String,
    @ColumnInfo(name = "payload_hash")
    val payloadHash: String,
    @ColumnInfo(name = "status")
    val status: CalculationRecordStatusDb,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "confirmed_at")
    val confirmedAt: Long? = null,
)
