package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.SauceVersionEntity
import com.culinary.core.database.model.VersionStatusDb

/** 酱汁版本读写；发布与当前版本切换是显式操作，避免业务层直接拼 SQL。 */
@Dao
interface SauceVersionDao {
    @Insert
    suspend fun insert(entity: SauceVersionEntity)

    @Query("SELECT * FROM sauce_version WHERE id = :id")
    suspend fun findById(id: String): SauceVersionEntity?

    @Query("SELECT * FROM sauce_version WHERE sauce_id = :sauceId AND is_current = 1")
    suspend fun findCurrent(sauceId: String): SauceVersionEntity?

    @Query("SELECT * FROM sauce_version WHERE sauce_id = :sauceId ORDER BY version_no DESC")
    suspend fun listBySauce(sauceId: String): List<SauceVersionEntity>

    @Query("SELECT COALESCE(MAX(version_no), 0) FROM sauce_version WHERE sauce_id = :sauceId")
    suspend fun maxVersionNo(sauceId: String): Int

    @Query("UPDATE sauce_version SET status = 'PUBLISHED', published_at = :at WHERE id = :id")
    suspend fun publish(
        id: String,
        at: Long,
    )

    @Query("UPDATE sauce_version SET status = :status, published_at = :at WHERE id = :id")
    suspend fun setStatus(
        id: String,
        status: VersionStatusDb,
        at: Long?,
    )

    @Query("UPDATE sauce_version SET is_current = :current WHERE id = :id")
    suspend fun setCurrent(
        id: String,
        current: Boolean,
    )

    @Query("UPDATE sauce_version SET is_current = 0 WHERE sauce_id = :sauceId AND is_current = 1")
    suspend fun clearCurrent(sauceId: String)

    @Query("UPDATE sauce_version SET yield_qty = :qty WHERE id = :id")
    suspend fun updateYield(
        id: String,
        qty: Double,
    )

    @Query(
        "UPDATE sauce_version SET yield_qty = :yieldQty, yield_unit_id = :yieldUnitId, " +
            "instructions_md = :instructionsMd, steps_json = :stepsJson WHERE id = :id AND status = 'DRAFT'",
    )
    suspend fun updateDraft(
        id: String,
        yieldQty: Double,
        yieldUnitId: String,
        instructionsMd: String?,
        stepsJson: String?,
    )

    @Query("SELECT COUNT(*) FROM sauce_version")
    suspend fun count(): Int
}
