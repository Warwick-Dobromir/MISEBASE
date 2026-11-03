package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.SauceVersionEntity

/** 酱汁版本读写；发布与当前版本切换是显式操作，避免业务层直接拼 SQL。 */
@Dao
interface SauceVersionDao {
    @Insert
    suspend fun insert(entity: SauceVersionEntity)

    @Query("SELECT * FROM sauce_version WHERE id = :id")
    suspend fun findById(id: String): SauceVersionEntity?

    @Query("SELECT * FROM sauce_version WHERE sauce_id = :sauceId AND is_current = 1")
    suspend fun findCurrent(sauceId: String): SauceVersionEntity?

    @Query("UPDATE sauce_version SET status = 'PUBLISHED', published_at = :at WHERE id = :id")
    suspend fun publish(
        id: String,
        at: Long,
    )

    @Query("UPDATE sauce_version SET is_current = :current WHERE id = :id")
    suspend fun setCurrent(
        id: String,
        current: Boolean,
    )

    @Query("UPDATE sauce_version SET yield_qty = :qty WHERE id = :id")
    suspend fun updateYield(
        id: String,
        qty: Double,
    )

    @Query("SELECT COUNT(*) FROM sauce_version")
    suspend fun count(): Int
}
