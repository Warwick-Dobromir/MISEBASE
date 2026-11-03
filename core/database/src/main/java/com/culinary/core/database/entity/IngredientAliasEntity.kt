package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 食材别名（A06 §3.3）。
 *
 * `normalized_alias` 全局唯一：避免扫码/搜索时把同一别名指向多个食材。
 * 食材被硬删除时别名级联删除；正常业务只做软删除，不会触发级联。
 */
@Entity(
    tableName = "ingredient_alias",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["normalized_alias"], unique = true),
        Index(value = ["ingredient_id"]),
    ],
)
data class IngredientAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "ingredient_id")
    val ingredientId: String,
    @ColumnInfo(name = "alias")
    val alias: String,
    @ColumnInfo(name = "normalized_alias")
    val normalizedAlias: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
