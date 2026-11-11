package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.InventoryBatchEntity
import com.culinary.core.database.model.BatchStatusDb

/**
 * 库存批次读写（v9）。
 *
 * 批次余额是物化值，流水是事实；批次不硬删，耗尽进回收站（`RECYCLED`）。
 * FIFO 顺序 = `received_at` 升序（先进先出），同日按 id 稳定排序。
 */
@Dao
interface InventoryBatchDao {
    @Insert
    suspend fun insert(entity: InventoryBatchEntity)

    @Query("SELECT * FROM inventory_batch WHERE id = :id")
    suspend fun findById(id: String): InventoryBatchEntity?

    @Query("SELECT * FROM inventory_batch WHERE product_id = :productId ORDER BY received_at")
    suspend fun listByProduct(productId: String): List<InventoryBatchEntity>

    @Query("SELECT * FROM inventory_batch WHERE status = 'ACTIVE' ORDER BY product_id, received_at, id")
    suspend fun listActive(): List<InventoryBatchEntity>

    /** 在库批次：正常与冻结都算"还在库里"，回收站除外。 */
    @Query("SELECT * FROM inventory_batch WHERE status IN ('ACTIVE', 'FROZEN') ORDER BY archive_id, received_at, id")
    suspend fun listLive(): List<InventoryBatchEntity>

    @Query("SELECT * FROM inventory_batch WHERE archive_id = :archiveId AND status IN ('ACTIVE', 'FROZEN') ORDER BY received_at, id")
    suspend fun listByArchive(archiveId: String): List<InventoryBatchEntity>

    /** FIFO 可用批次：未冻结、余额为正。 */
    @Query(
        "SELECT * FROM inventory_batch WHERE archive_id = :archiveId AND status = 'ACTIVE' " +
            "AND quantity_on_hand > 0 ORDER BY received_at, id",
    )
    suspend fun listFifoAvailable(archiveId: String): List<InventoryBatchEntity>

    @Query("SELECT * FROM inventory_batch WHERE status = :status ORDER BY received_at DESC")
    suspend fun listByStatus(status: BatchStatusDb): List<InventoryBatchEntity>

    @Query("SELECT * FROM inventory_batch WHERE status = 'RECYCLED' ORDER BY recycled_at DESC")
    suspend fun listRecycled(): List<InventoryBatchEntity>

    @Query("SELECT * FROM inventory_batch WHERE is_temporary = 1 AND status IN ('ACTIVE', 'FROZEN') ORDER BY temporary_since")
    suspend fun listTemporary(): List<InventoryBatchEntity>

    @Query("SELECT COUNT(*) FROM inventory_batch WHERE archive_id = :archiveId AND status IN ('ACTIVE', 'FROZEN')")
    suspend fun countByArchive(archiveId: String): Int

    /** 库位上的在库批次数（正常 + 冻结），库位管理页展示用。 */
    @Query(
        "SELECT COUNT(*) FROM inventory_batch WHERE location_id = :locationId " +
            "AND status IN ('ACTIVE', 'FROZEN')",
    )
    suspend fun countAtLocation(locationId: String): Int

    @Query(
        "SELECT COALESCE(SUM(quantity_on_hand), 0) FROM inventory_batch " +
            "WHERE archive_id = :archiveId AND status IN ('ACTIVE', 'FROZEN')",
    )
    suspend fun sumOnHand(archiveId: String): Double

    /**
     * 原子调整余额：只有调整后余额非负、且预留不超过在手量时才更新。
     * 返回 0 表示并发冲突或约束不满足，调用方必须视为失败，不得重试半条流水。
     */
    @Query(
        "UPDATE inventory_batch SET " +
            "quantity_on_hand = quantity_on_hand + :onHandDelta, " +
            "quantity_reserved = quantity_reserved + :reservedDelta, " +
            "updated_at = :updatedAt " +
            "WHERE id = :id " +
            "AND quantity_on_hand + :onHandDelta >= 0 " +
            "AND quantity_reserved + :reservedDelta >= 0 " +
            "AND quantity_reserved + :reservedDelta <= quantity_on_hand + :onHandDelta",
    )
    suspend fun applyDeltas(
        id: String,
        onHandDelta: Double,
        reservedDelta: Double,
        updatedAt: Long,
    ): Int

    @Query("UPDATE inventory_batch SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: BatchStatusDb, updatedAt: Long): Int

    @Query(
        "UPDATE inventory_batch SET status = 'FROZEN', frozen_at = :at, frozen_reason = :reason, " +
            "updated_at = :at WHERE id = :id",
    )
    suspend fun freeze(id: String, at: Long, reason: String): Int

    @Query(
        "UPDATE inventory_batch SET status = 'ACTIVE', frozen_at = NULL, frozen_reason = NULL, " +
            "updated_at = :at WHERE id = :id",
    )
    suspend fun unfreeze(id: String, at: Long): Int

    @Query(
        "UPDATE inventory_batch SET status = 'RECYCLED', recycled_at = :at, updated_at = :at " +
            "WHERE id = :id",
    )
    suspend fun recycle(id: String, at: Long): Int

    @Query(
        "UPDATE inventory_batch SET status = 'ACTIVE', recycled_at = NULL, updated_at = :at " +
            "WHERE id = :id",
    )
    suspend fun restore(id: String, at: Long): Int

    @Query("UPDATE inventory_batch SET location_id = :locationId, updated_at = :at WHERE id = :id")
    suspend fun updateLocation(id: String, locationId: String, at: Long): Int

    @Query("UPDATE inventory_batch SET archive_id = :archiveId, updated_at = :at WHERE id = :id")
    suspend fun updateArchive(id: String, archiveId: String, at: Long): Int

    @Query(
        "UPDATE inventory_batch SET is_temporary = :temporary, temporary_since = :since, " +
            "updated_at = :at WHERE id = :id",
    )
    suspend fun setTemporary(id: String, temporary: Boolean, since: Long?, at: Long): Int

    @Query("SELECT COUNT(*) FROM inventory_batch")
    suspend fun count(): Int

    @Query("DELETE FROM inventory_batch WHERE id = :id")
    suspend fun deleteById(id: String)
}
