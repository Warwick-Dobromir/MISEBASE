package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.culinary.core.database.entity.PendingStockDateAllocationEntity
import com.culinary.core.database.entity.PendingStockItemEntity

@Dao
interface PendingStockDao {
    @Query("SELECT * FROM pending_stock_item ORDER BY sort_order, created_at, id")
    suspend fun listItems(): List<PendingStockItemEntity>

    @Query("SELECT * FROM pending_stock_item WHERE id = :id")
    suspend fun findItem(id: String): PendingStockItemEntity?

    @Query("SELECT * FROM pending_stock_item WHERE archive_id = :archiveId LIMIT 1")
    suspend fun findByArchive(archiveId: String): PendingStockItemEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM pending_stock_item")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insertItem(item: PendingStockItemEntity)

    @Query("UPDATE pending_stock_item SET quantity = :quantity, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: Double, updatedAt: Long): Int

    @Query("UPDATE pending_stock_item SET quantity = quantity + :amount, updated_at = :updatedAt WHERE id = :id")
    suspend fun incrementQuantity(id: String, amount: Double, updatedAt: Long): Int

    @Query("UPDATE pending_stock_item SET name = :name, brand = :brand, category_name = :categoryName, specification = :specification, image_uri = :imageUri, stock_unit_id = :stockUnitId, source_product_id = :sourceProductId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSnapshot(
        id: String,
        name: String,
        brand: String?,
        categoryName: String?,
        specification: String?,
        imageUri: String?,
        stockUnitId: String,
        sourceProductId: String?,
        updatedAt: Long,
    ): Int

    @Transaction
    suspend fun addOrIncrement(item: PendingStockItemEntity): String {
        val existing = findByArchive(item.archiveId)
        if (existing != null) {
            incrementQuantity(existing.id, item.quantity, item.updatedAt)
            updateSnapshot(
                existing.id,
                item.name,
                item.brand,
                item.categoryName,
                item.specification,
                item.imageUri,
                item.stockUnitId,
                item.sourceProductId,
                item.updatedAt,
            )
            return existing.id
        }
        insertItem(item.copy(sortOrder = nextSortOrder()))
        return item.id
    }

    @Transaction
    suspend fun setQuantityAndTrim(itemId: String, quantity: Double, updatedAt: Long) {
        updateQuantity(itemId, quantity, updatedAt)
        var remaining = quantity
        val kept = listAllocations(itemId).mapNotNull { allocation ->
            if (remaining <= 0.0) return@mapNotNull null
            val amount = minOf(allocation.quantity, remaining)
            remaining -= amount
            allocation.copy(quantity = amount)
        }
        clearAllocations(itemId)
        if (kept.isNotEmpty()) insertAllocations(kept)
    }

    @Transaction
    suspend fun replaceAllocationsForItem(itemId: String, rows: List<PendingStockDateAllocationEntity>) {
        val item = findItem(itemId) ?: error("Pending stock item not found: $itemId")
        require(rows.sumOf { it.quantity } <= item.quantity + 1e-9)
        clearAllocations(itemId)
        if (rows.isNotEmpty()) insertAllocations(rows)
    }

    @Query("DELETE FROM pending_stock_item WHERE id = :id")
    suspend fun deleteItem(id: String): Int

    @Query("DELETE FROM pending_stock_item")
    suspend fun clearItems(): Int

    @Query("SELECT * FROM pending_stock_date_allocation WHERE item_id = :itemId ORDER BY sort_order, production_date")
    suspend fun listAllocations(itemId: String): List<PendingStockDateAllocationEntity>

    @Insert
    suspend fun insertAllocations(rows: List<PendingStockDateAllocationEntity>)

    @Query("DELETE FROM pending_stock_date_allocation WHERE item_id = :itemId")
    suspend fun clearAllocations(itemId: String)

    @Transaction
    suspend fun replaceAllocations(itemId: String, rows: List<PendingStockDateAllocationEntity>) {
        clearAllocations(itemId)
        if (rows.isNotEmpty()) insertAllocations(rows)
    }
}
