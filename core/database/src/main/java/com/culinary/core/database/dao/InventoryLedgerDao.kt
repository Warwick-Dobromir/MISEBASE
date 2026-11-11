package com.culinary.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.InventoryLedgerEntity
import com.culinary.core.database.model.LedgerTypeDb

/** 批次维度的入库/出库合计（v9）：用于批次视图的原始入库量与累计出库量。 */
data class InventoryBatchLedgerSum(
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "inbound")
    val inbound: Double,
    @ColumnInfo(name = "outbound")
    val outbound: Double,
)

/**
 * 库存流水读写（v9）。
 *
 * 只暴露 `insert` 与查询：UPDATE/DELETE 在数据库层被触发器拒绝，
 * 这里不提供方法，避免业务代码误以为可以修改流水。
 */
@Dao
interface InventoryLedgerDao {
    @Insert
    suspend fun insert(entity: InventoryLedgerEntity)

    @Query("SELECT * FROM inventory_ledger WHERE batch_id = :batchId ORDER BY occurred_at")
    suspend fun listByBatch(batchId: String): List<InventoryLedgerEntity>

    @Query("SELECT * FROM inventory_ledger WHERE archive_id = :archiveId ORDER BY occurred_at DESC")
    suspend fun listByArchive(archiveId: String): List<InventoryLedgerEntity>

    @Query("SELECT * FROM inventory_ledger WHERE idempotency_key = :key")
    suspend fun findByIdempotencyKey(key: String): InventoryLedgerEntity?

    @Query("SELECT * FROM inventory_ledger WHERE id = :id")
    suspend fun findById(id: String): InventoryLedgerEntity?

    @Query("SELECT * FROM inventory_ledger WHERE reference_type = :refType AND reference_id = :refId ORDER BY occurred_at, id")
    suspend fun listByReference(refType: String, refId: String): List<InventoryLedgerEntity>

    @Query("SELECT * FROM inventory_ledger ORDER BY occurred_at, id")
    suspend fun listAll(): List<InventoryLedgerEntity>

    @Query("SELECT * FROM inventory_ledger ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<InventoryLedgerEntity>

    @Query(
        "SELECT * FROM inventory_ledger WHERE (:type IS NULL OR type = :type) " +
            "AND occurred_at >= :from AND occurred_at <= :to ORDER BY occurred_at DESC, id DESC",
    )
    suspend fun listFiltered(type: LedgerTypeDb?, from: Long, to: Long): List<InventoryLedgerEntity>

    /**
     * 每个批次的入库合计与出库合计；只读聚合，不改变"余额是物化值、流水是事实"的约束。
     *
     * 零变动流水（冻结/解冻/转移）对两个合计都不贡献；`GROUP BY` 只返回有流水的批次。
     */
    @Query(
        "SELECT batch_id, " +
            "SUM(CASE WHEN on_hand_delta > 0 THEN on_hand_delta ELSE 0 END) AS inbound, " +
            "SUM(CASE WHEN on_hand_delta < 0 THEN -on_hand_delta ELSE 0 END) AS outbound " +
            "FROM inventory_ledger GROUP BY batch_id",
    )
    suspend fun ledgerSumsByBatch(): List<InventoryBatchLedgerSum>

    @Query("SELECT COUNT(*) FROM inventory_ledger")
    suspend fun count(): Int
}
