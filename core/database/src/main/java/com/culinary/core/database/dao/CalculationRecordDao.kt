package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.culinary.core.database.entity.CalculationRecordEntity
import com.culinary.core.database.entity.CalculationRecordLineEntity
import com.culinary.core.database.model.CalculationRecordStatusDb

/**
 * 计算记录读写（MB-D-017）。
 *
 * `insertConfirmed` 在一个事务内写入头与需求行；`payload_hash` 已存在时返回 `false`，
 * 由调用方读取既有记录，保证重复提交不会产生第二条记录。
 */
@Dao
abstract class CalculationRecordDao {
    @Query("SELECT * FROM calculation_record WHERE id = :id")
    abstract suspend fun findById(id: String): CalculationRecordEntity?

    @Query("SELECT * FROM calculation_record WHERE payload_hash = :payloadHash")
    abstract suspend fun findByPayloadHash(payloadHash: String): CalculationRecordEntity?

    @Query(
        "SELECT * FROM calculation_record WHERE recipe_version_id = :recipeVersionId " +
            "ORDER BY created_at, id",
    )
    abstract suspend fun findByRecipeVersion(recipeVersionId: String): List<CalculationRecordEntity>

    @Query("SELECT * FROM calculation_record_line WHERE calculation_record_id = :recordId ORDER BY line_no")
    abstract suspend fun findLines(recordId: String): List<CalculationRecordLineEntity>

    @Query("SELECT * FROM calculation_record WHERE status = :status ORDER BY created_at, id")
    abstract suspend fun findByStatus(status: CalculationRecordStatusDb): List<CalculationRecordEntity>

    @Query("SELECT COUNT(*) FROM calculation_record")
    abstract suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM calculation_record_line")
    abstract suspend fun countLines(): Int

    @Insert
    abstract suspend fun insertRecord(record: CalculationRecordEntity)

    @Insert
    abstract suspend fun insertLines(lines: List<CalculationRecordLineEntity>)

    @Query(
        "UPDATE calculation_record SET status = 'CONFIRMED', confirmed_at = :confirmedAt " +
            "WHERE id = :id AND status = 'PREVIEW'",
    )
    abstract suspend fun markConfirmed(
        id: String,
        confirmedAt: Long,
    ): Int

    @Transaction
    open suspend fun insertConfirmed(
        record: CalculationRecordEntity,
        lines: List<CalculationRecordLineEntity>,
    ): Boolean {
        if (findByPayloadHash(record.payloadHash) != null) return false
        val confirmedAt = requireNotNull(record.confirmedAt) { "confirmed record needs confirmed_at" }
        // 先以 PREVIEW 写入，插入需求行后再确认；整个过程在同一事务内，外部不可见。
        insertRecord(record.copy(status = CalculationRecordStatusDb.PREVIEW, confirmedAt = null))
        insertLines(lines)
        check(markConfirmed(record.id, confirmedAt) == 1) { "cannot confirm calculation record" }
        return true
    }
}
