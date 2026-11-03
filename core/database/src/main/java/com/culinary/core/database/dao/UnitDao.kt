package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.UnitEntity

/** 单位读取；写入只通过种子或后续设置界面，接口保持最小。 */
@Dao
interface UnitDao {
    @Query("SELECT * FROM unit WHERE is_active = 1 ORDER BY sort_order, code")
    suspend fun allActive(): List<UnitEntity>

    @Query("SELECT * FROM unit WHERE code = :code")
    suspend fun findByCode(code: String): UnitEntity?

    @Query("SELECT * FROM unit WHERE id = :id")
    suspend fun findById(id: String): UnitEntity?

    @Query("SELECT COUNT(*) FROM unit")
    suspend fun count(): Int

    @Insert
    suspend fun insert(unit: UnitEntity)
}
