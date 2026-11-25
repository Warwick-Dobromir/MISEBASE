package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.database.dao.PendingStockDao
import com.culinary.core.database.entity.PendingStockDateAllocationEntity
import com.culinary.core.database.entity.PendingStockItemEntity
import com.culinary.core.domain.repository.PendingStockDateAllocation
import com.culinary.core.domain.repository.PendingStockDraft
import com.culinary.core.domain.repository.PendingStockEntry
import com.culinary.core.domain.repository.PendingStockRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingStockRepositoryImpl @Inject constructor(
    private val dao: PendingStockDao,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) : PendingStockRepository {
    override suspend fun list(): List<PendingStockEntry> =
        dao.listItems().map { row ->
            PendingStockEntry(
                id = row.id,
                archiveId = row.archiveId,
                name = row.name,
                brand = row.brand,
                categoryName = row.categoryName,
                specification = row.specification,
                imageUri = row.imageUri,
                stockUnitId = row.stockUnitId,
                sourceProductId = row.sourceProductId,
                quantity = row.quantity,
                sortOrder = row.sortOrder,
                allocations = dao.listAllocations(row.id).map {
                    PendingStockDateAllocation(
                        id = it.id,
                        productionDate = LocalDate.parse(it.productionDate),
                        quantity = it.quantity,
                        sortOrder = it.sortOrder,
                    )
                },
            )
        }

    override suspend fun addOrIncrement(draft: PendingStockDraft): String {
        require(draft.quantity.isFinite() && draft.quantity > 0.0)
        val now = clock.now().toEpochMilli()
        val existing = dao.findByArchive(draft.archiveId)
        if (existing != null) {
            dao.incrementQuantity(existing.id, draft.quantity, now)
            dao.updateSnapshot(
                existing.id, draft.name, draft.brand, draft.categoryName,
                draft.specification, draft.imageUri, now,
            )
            return existing.id
        }
        val id = idGenerator.newId()
        dao.insertItem(
            PendingStockItemEntity(
                id = id,
                archiveId = draft.archiveId,
                name = draft.name,
                brand = draft.brand,
                categoryName = draft.categoryName,
                specification = draft.specification,
                imageUri = draft.imageUri,
                stockUnitId = draft.stockUnitId,
                sourceProductId = draft.sourceProductId,
                quantity = draft.quantity,
                sortOrder = dao.nextSortOrder(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    override suspend fun setQuantity(itemId: String, quantity: Double) {
        require(quantity.isFinite() && quantity > 0.0)
        dao.updateQuantity(itemId, quantity, clock.now().toEpochMilli())
        val current = dao.listAllocations(itemId)
        var remaining = quantity
        val kept = current.mapNotNull { allocation ->
            if (remaining <= 0.0) return@mapNotNull null
            val amount = minOf(allocation.quantity, remaining)
            remaining -= amount
            allocation.copy(quantity = amount)
        }
        dao.replaceAllocations(itemId, kept)
    }

    override suspend fun replaceAllocations(itemId: String, allocations: List<PendingStockDateAllocation>) {
        require(allocations.all { it.quantity.isFinite() && it.quantity > 0.0 })
        val item = dao.findItem(itemId) ?: error("Pending stock item not found: $itemId")
        require(allocations.sumOf { it.quantity } <= item.quantity + 1e-9)
        dao.replaceAllocations(
            itemId,
            allocations.mapIndexed { index, allocation ->
                PendingStockDateAllocationEntity(
                    id = allocation.id.ifBlank { idGenerator.newId() },
                    itemId = itemId,
                    productionDate = allocation.productionDate.toString(),
                    quantity = allocation.quantity,
                    sortOrder = index,
                )
            },
        )
    }

    override suspend fun delete(itemId: String) {
        dao.deleteItem(itemId)
    }

    override suspend fun clear() {
        dao.clearItems()
    }
}
