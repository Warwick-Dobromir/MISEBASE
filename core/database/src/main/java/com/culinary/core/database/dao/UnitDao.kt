package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.UnitEntity

/** 单位表读写；停用单位保留在数据库中以维持历史引用。 */
@Dao
interface UnitDao {
    @Query("SELECT * FROM unit WHERE is_active = 1 ORDER BY sort_order, code")
    suspend fun allActive(): List<UnitEntity>

    @Query("SELECT * FROM unit ORDER BY dimension, sort_order, code")
    suspend fun all(): List<UnitEntity>

    @Query("SELECT * FROM unit WHERE code = :code")
    suspend fun findByCode(code: String): UnitEntity?

    @Query("SELECT * FROM unit WHERE id = :id")
    suspend fun findById(id: String): UnitEntity?

    @Query("SELECT COUNT(*) FROM unit")
    suspend fun count(): Int

    @Insert
    suspend fun insert(unit: UnitEntity)

    @Update
    suspend fun update(unit: UnitEntity)

    @Query("UPDATE unit SET is_active = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("DELETE FROM unit WHERE id = :id")
    suspend fun deleteById(id: String)
}
