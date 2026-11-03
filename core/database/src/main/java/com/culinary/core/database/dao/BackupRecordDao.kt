package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.BackupRecordEntity

/** 备份记录读写。 */
@Dao
interface BackupRecordDao {
    @Insert
    suspend fun insert(entity: BackupRecordEntity)

    @Query("SELECT * FROM backup_record ORDER BY created_at DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<BackupRecordEntity>

    @Query("SELECT COUNT(*) FROM backup_record")
    suspend fun count(): Int
}
