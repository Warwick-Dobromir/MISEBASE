package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.database.model.KitchenReferenceKindDb

/**
 * 厨房新增参考资料（烹饪技术 / 制作标准 / 储存标准）。
 *
 * 独立新表，避免把不同业务字段塞进 sauce/recipe/ingredient，也不改变旧表。
 */
@Entity(
    tableName = "kitchen_reference",
    indices = [
        Index(value = ["kind"]),
        Index(value = ["status"]),
        Index(value = ["kind", "normalized_name"]),
        Index(value = ["seed_id"], unique = true),
    ],
)
data class KitchenReferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "kind")
    val kind: KitchenReferenceKindDb,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "category")
    val category: String? = null,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "english_name")
    val englishName: String? = null,
    @ColumnInfo(name = "seed_note")
    val seedNote: String? = null,
    @ColumnInfo(name = "seed_id")
    val seedId: String? = null,
    @ColumnInfo(name = "seed_version")
    val seedVersion: String? = null,
    @ColumnInfo(name = "status")
    val status: EntityStatusDb = EntityStatusDb.ACTIVE,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "revision")
    val revision: Int = 1,
)
