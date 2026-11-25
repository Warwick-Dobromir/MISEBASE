package com.culinary.core.domain.repository

import java.time.LocalDate

data class PendingStockDraft(
    val archiveId: String,
    val name: String,
    val brand: String? = null,
    val categoryName: String? = null,
    val specification: String? = null,
    val imageUri: String? = null,
    val stockUnitId: String,
    val sourceProductId: String? = null,
    val quantity: Double = 1.0,
)

data class PendingStockDateAllocation(
    val id: String,
    val productionDate: LocalDate,
    val quantity: Double,
    val sortOrder: Int,
)

data class PendingStockEntry(
    val id: String,
    val archiveId: String,
    val name: String,
    val brand: String?,
    val categoryName: String?,
    val specification: String?,
    val imageUri: String?,
    val stockUnitId: String,
    val sourceProductId: String?,
    val quantity: Double,
    val sortOrder: Int,
    val allocations: List<PendingStockDateAllocation>,
) {
    val allocatedQuantity: Double get() = allocations.sumOf { it.quantity }
    val unallocatedQuantity: Double get() = (quantity - allocatedQuantity).coerceAtLeast(0.0)
}

interface PendingStockRepository {
    suspend fun list(): List<PendingStockEntry>
    suspend fun addOrIncrement(draft: PendingStockDraft): String
    suspend fun setQuantity(itemId: String, quantity: Double)
    suspend fun replaceAllocations(itemId: String, allocations: List<PendingStockDateAllocation>)
    suspend fun delete(itemId: String)
    suspend fun clear()
}
