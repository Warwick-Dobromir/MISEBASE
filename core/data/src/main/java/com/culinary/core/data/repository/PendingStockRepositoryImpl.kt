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
        val id = idGenerator.newId()
        return dao.addOrIncrement(
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
    }

    override suspend fun setQuantity(itemId: String, quantity: Double) {
        require(quantity.isFinite() && quantity > 0.0)
        dao.setQuantityAndTrim(itemId, quantity, clock.now().toEpochMilli())
    }

    override suspend fun replaceAllocations(itemId: String, allocations: List<PendingStockDateAllocation>) {
        require(allocations.all { it.quantity.isFinite() && it.quantity > 0.0 })
        dao.replaceAllocationsForItem(
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
