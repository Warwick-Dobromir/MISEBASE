package com.culinary.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.InventoryIssueSlipEntity
import com.culinary.core.database.entity.InventoryIssueSlipLineEntity
import com.culinary.core.database.entity.InventoryPrintJobEntity
import com.culinary.core.database.model.IssueSlipTypeDb

/** 出库单按来源的计数（v9）。 */
data class IssueTypeCount(
    @ColumnInfo(name = "type")
    val type: IssueSlipTypeDb,
    @ColumnInfo(name = "count")
    val count: Int,
)

/** 出库单、出库明细与打印任务（v9）。 */
@Dao
interface InventoryIssueDao {
    @Insert
    suspend fun insertSlip(entity: InventoryIssueSlipEntity)

    @Insert
    suspend fun insertLines(entities: List<InventoryIssueSlipLineEntity>)

    @Query("SELECT * FROM inventory_issue_slip WHERE id = :id")
    suspend fun findSlip(id: String): InventoryIssueSlipEntity?

    @Query("SELECT * FROM inventory_issue_slip WHERE idempotency_key = :key")
    suspend fun findByKey(key: String): InventoryIssueSlipEntity?

    @Query("SELECT * FROM inventory_issue_slip ORDER BY occurred_at DESC LIMIT :limit")
    suspend fun recentSlips(limit: Int): List<InventoryIssueSlipEntity>

    @Query("SELECT * FROM inventory_issue_slip_line WHERE slip_id = :slipId ORDER BY line_no")
    suspend fun linesOf(slipId: String): List<InventoryIssueSlipLineEntity>

    @Query("SELECT type, COUNT(*) AS count FROM inventory_issue_slip GROUP BY type")
    suspend fun countsByType(): List<IssueTypeCount>

    @Insert
    suspend fun insertPrintJob(entity: InventoryPrintJobEntity)

    @Update
    suspend fun updatePrintJob(entity: InventoryPrintJobEntity)

    @Query("SELECT * FROM inventory_print_job WHERE id = :id")
    suspend fun findPrintJob(id: String): InventoryPrintJobEntity?

    @Query("SELECT * FROM inventory_print_job WHERE ref_type = :refType AND ref_id = :refId ORDER BY created_at DESC LIMIT 1")
    suspend fun latestPrintJob(refType: String, refId: String): InventoryPrintJobEntity?

    @Query("SELECT * FROM inventory_print_job WHERE status = 'FAILED' ORDER BY created_at DESC")
    suspend fun failedPrintJobs(): List<InventoryPrintJobEntity>

    @Query("SELECT * FROM inventory_print_job ORDER BY created_at DESC LIMIT :limit")
    suspend fun recentPrintJobs(limit: Int): List<InventoryPrintJobEntity>
}
