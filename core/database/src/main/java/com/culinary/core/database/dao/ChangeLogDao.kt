package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.ChangeLogEntity

/**
 * 变更日志读写：只有追加与查询。
 * UPDATE/DELETE 在数据库层被触发器拒绝（A06 §9.2）。
 */
@Dao
interface ChangeLogDao {
    @Insert
    suspend fun insert(entity: ChangeLogEntity)

    @Query(
        "SELECT * FROM change_log WHERE entity_type = :entityType AND entity_id = :entityId " +
            "ORDER BY created_at",
    )
    suspend fun listByEntity(
        entityType: String,
        entityId: String,
    ): List<ChangeLogEntity>

    @Query("SELECT COUNT(*) FROM change_log")
    suspend fun count(): Int
}
