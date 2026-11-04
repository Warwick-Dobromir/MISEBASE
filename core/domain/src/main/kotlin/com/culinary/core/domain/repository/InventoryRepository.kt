package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry

/**
 * 库存读写端口（MB-D-014）。
 *
 * - 余额来自批次物化字段，流水只追加；
 * - [appendIfAbsent] 必须原子地"查幂等键 → 写流水 → 调整批次余额"，
 *   返回 `false` 表示该幂等键已存在、本次未写入；
 * - 不提供直接改余额或删除流水的方法。
 */
interface InventoryRepository {
    suspend fun listItems(): Outcome<List<InventoryItem>>

    suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?>

    suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?>

    suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean>

    suspend fun ledger(): Outcome<List<InventoryLedgerEntry>>
}
